package ai.nooa.runtime.sandbox;

import ai.nooa.Agent;
import ai.nooa.strategy.ExecutionResult;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.*;
import jdk.jshell.JShell;
import jdk.jshell.Snippet;
import jdk.jshell.SnippetEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps {@code jdk.jshell.JShell} to execute LLM-generated Java code
 * with timeout and import restrictions.
 */
public final class JShellSandbox implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(JShellSandbox.class);

    private static final Set<String> BLOCKED_PACKAGES = Set.of(
        "java.lang.reflect", "java.lang.invoke", "sun.",
        "jdk.internal", "java.lang.ProcessBuilder", "java.lang.Runtime",
        "java.io.File", "java.nio.file", "java.net.Socket",
        "java.lang.System", "java.net.URL", "java.net.URI",
        "java.lang.Class.forName", "java.lang.Thread"
    );

    private static final long DEFAULT_TIMEOUT_MS = 30_000;

    private final JShell jshell;
    private final ByteArrayOutputStream stdoutCapture = new ByteArrayOutputStream();
    private final ByteArrayOutputStream stderrCapture = new ByteArrayOutputStream();
    private final long timeoutMs;
    private long executionCount;

    public JShellSandbox(Agent agent) {
        this(agent, DEFAULT_TIMEOUT_MS);
    }

    public JShellSandbox(Agent agent, long timeoutMs) {
        this.timeoutMs = timeoutMs;
        this.jshell = JShell.builder()
            .out(new PrintStream(stdoutCapture))
            .err(new PrintStream(stderrCapture))
            .build();
        loadPreamble(agent);
    }

    private void loadPreamble(Agent agent) {
        List.of(
            "import java.util.*",
            "import java.util.stream.*",
            "import java.util.concurrent.*",
            "import com.fasterxml.jackson.databind.ObjectMapper"
        ).forEach(jshell::eval);

        SandboxContext.setAgent(agent);
        jshell.eval(
            "var __agent__ = ai.nooa.runtime.sandbox.SandboxContext.getAgent();");
        jshell.eval(
            "var __context__ = __agent__.context();\n"
            + "var __events__ = __agent__.events();");

        jshell.eval("""
            Object returnResult(Object value) {
                ai.nooa.runtime.sandbox.SandboxContext.setReturnValue(value);
                return value;
            }
            """);
    }

    /**
     * Execute a code snippet with timeout enforcement.
     */
    public ExecutionResult execute(String code) {
        executionCount++;
        log.debug("Sandbox execution #{}", executionCount);
        stdoutCapture.reset();
        stderrCapture.reset();

        if (containsBlockedImports(code)) {
            return new ExecutionResult("", "", "Blocked import or API used", null, false);
        }

        try {
            return executeWithTimeout(code);
        } catch (TimeoutException _) {
            return new ExecutionResult("", "", "Execution timed out after " + timeoutMs + "ms",
                null, false);
        }
    }

    private ExecutionResult executeWithTimeout(String code) throws TimeoutException {
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<ExecutionResult> future = executor.submit(() -> {
                List<SnippetEvent> events = jshell.eval(code);
                return buildResult(events);
            });

            try {
                return future.get(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
                return new ExecutionResult("", "", "Interrupted", null, false);
            } catch (java.util.concurrent.ExecutionException e) {
                return new ExecutionResult("", "",
                    e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), null, false);
            }
        }
    }

    private ExecutionResult buildResult(List<SnippetEvent> events) {
        String stdout = stdoutCapture.toString();
        String stderr = stderrCapture.toString();
        stdoutCapture.reset();
        stderrCapture.reset();

        String error = null;
        Object returnValue = null;

        for (SnippetEvent event : events) {
            if (event.status() == Snippet.Status.REJECTED) {
                error = jshell.diagnostics(event.snippet())
                    .map(d -> d.getMessage(Locale.getDefault()))
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("Unknown error");
            } else if (event.exception() != null) {
                error = formatException(event.exception());
            } else if (event.status() == Snippet.Status.VALID && event.value() != null) {
                returnValue = event.value();
            }
        }

        // Check if returnResult was called
        Object sandboxReturn = SandboxContext.consumeReturnValue();
        if (sandboxReturn != null) {
            returnValue = sandboxReturn;
        }

        boolean success = error == null;
        return new ExecutionResult(stdout, stderr, error, returnValue, success);
    }

    private String formatException(Exception ex) {
        if (ex == null) { return "Unknown exception"; }
        String msg = ex.getClass().getSimpleName() + ": " + ex.getMessage();
        if (ex.getCause() != null) {
            msg += "\nCaused by: " + ex.getCause().toString();
        }
        return msg;
    }

    private boolean containsBlockedImports(String code) {
        for (String blocked : BLOCKED_PACKAGES) {
            if (!code.contains(blocked)) {
                continue;
            }

            log.warn("Blocked API usage: {}", blocked);
            var perms = SandboxContext.getAgent().permissions();
            return !isAllowedByPermissions(code, blocked, perms);
        }
        return false;
    }

    private boolean isAllowedByPermissions(String code, String blocked,
            ai.nooa.security.Permissions perms) {
        if (blocked.startsWith("java.io.File") || blocked.startsWith("java.nio.file")) {
            return isFileAccessAllowed(code, perms);
        }
        if (blocked.equals("java.net.URL") || blocked.equals("java.net.URI")) {
            return isUrlAccessAllowed(code, perms);
        }
        return false;
    }

    private boolean isFileAccessAllowed(String code, ai.nooa.security.Permissions perms) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
            "\"(/[^\"]+)\"|'([^']+)'").matcher(code);
        while (matcher.find()) {
            String path = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (path != null && perms.checkFile(path) == ai.nooa.security.Permissions.Level.ALLOW) {
                return true;
            }
        }
        return false;
    }

    private boolean isUrlAccessAllowed(String code, ai.nooa.security.Permissions perms) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
            "\"(https?://[^\"]+)\"|'(https?://[^']+)'").matcher(code);
        while (matcher.find()) {
            String url = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (url != null && perms.checkUrl(url) == ai.nooa.security.Permissions.Level.ALLOW) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void close() {
        try {
            SandboxContext.clear();
            jshell.close();
        } catch (Exception e) {
            log.debug("Error closing JShell sandbox", e);
        }
    }
}
