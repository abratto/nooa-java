package ai.nooa.runtime.sandbox;

import ai.nooa.Agent;
import ai.nooa.context.Event;
import ai.nooa.strategy.ExecutionResult;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import jdk.jshell.JShell;
import jdk.jshell.Snippet;
import jdk.jshell.SourceCodeAnalysis;
import jdk.jshell.SnippetEvent;
import jdk.jshell.execution.LocalExecutionControl;
import jdk.jshell.spi.ExecutionControl;
import jdk.jshell.spi.ExecutionControlProvider;
import jdk.jshell.spi.ExecutionEnv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps {@code jdk.jshell.JShell} to execute LLM-generated Java code
 * with timeout and import restrictions.
 */
public final class JShellSandbox implements SandboxExecutor {

    private static final String UNKNOWN_ERROR = "Unknown error";

    private static final Logger log = LoggerFactory.getLogger(JShellSandbox.class);

    private static final long DEFAULT_TIMEOUT_MS = 30_000;
    private static final int MAX_CAPTURE_BYTES = 1_048_576;

    private final Agent agent;
    private final JShell jshell;
    private final CappedOutputStream stdoutCapture = new CappedOutputStream(MAX_CAPTURE_BYTES);
    private final CappedOutputStream stderrCapture = new CappedOutputStream(MAX_CAPTURE_BYTES);
    private final long timeoutMs;
    private long executionCount;

    public JShellSandbox(Agent agent) {
        this(agent, DEFAULT_TIMEOUT_MS);
    }

    public JShellSandbox(Agent agent, long timeoutMs) {
        this.agent = agent;
        this.timeoutMs = timeoutMs;
        this.jshell = JShell.builder()
            .out(new PrintStream(stdoutCapture))
            .err(new PrintStream(stderrCapture))
            .executionEngine(
                new AppClassLoaderExecutionControlProvider(agent.getClass().getClassLoader()),
                Map.of())
            .build();
        registerAgentClasspath(agent);
        loadPreamble(agent);
    }

    /**
     * Make the agent's classpath visible to the JShell compiler.
     * <p>{@link LocalExecutionControl} only routes <em>execution</em> to the
     * agent's classloader; JShell still compiles snippets against the JVM's
     * {@code java.class.path}. Under embedding classloaders such as Maven's
     * {@code exec:java} realm or application servers, framework classes are not
     * on the system classpath, so snippets that reference them (e.g.
     * {@code SandboxContext}) fail to compile with
     * {@code package ... does not exist}. Registering the classloader's URLs
     * closes that gap.</p>
     */
    private void registerAgentClasspath(Agent agent) {
        var entries = new LinkedHashSet<String>();
        var systemClasspath = System.getProperty("java.class.path");
        if (systemClasspath != null && !systemClasspath.isBlank()) {
            Collections.addAll(entries, systemClasspath.split(File.pathSeparator));
        }
        for (ClassLoader loader = agent.getClass().getClassLoader();
             loader != null; loader = loader.getParent()) {
            if (loader instanceof URLClassLoader urlLoader) {
                for (URL url : urlLoader.getURLs()) {
                    try {
                        entries.add(new File(url.toURI()).getPath());
                    } catch (IllegalArgumentException | URISyntaxException _) {
                        entries.add(url.toString());
                    }
                }
            }
        }
        if (!entries.isEmpty()) {
            jshell.addToClasspath(String.join(File.pathSeparator, entries));
        }
    }

    /**
     * Execution control that runs generated code in-process against the
     * agent's own classloader, so framework classes (and the agent instance)
     * are shared instead of being re-loaded in an isolated engine classloader.
     */
    private static final class AppClassLoaderExecutionControlProvider implements ExecutionControlProvider {
        private final ClassLoader classLoader;

        AppClassLoaderExecutionControlProvider(ClassLoader classLoader) {
            this.classLoader = classLoader;
        }

        @Override
        public String name() {
            return "nooa-app-classloader";
        }

        @Override
        public ExecutionControl generate(ExecutionEnv env, Map<String, String> parameters) {
            return new LocalExecutionControl(classLoader);
        }
    }

    private void loadPreamble(Agent agent) {
        List.of(
            "import java.util.*",
            "import java.util.stream.*",
            "import java.util.concurrent.*",
            "import com.fasterxml.jackson.databind.ObjectMapper"
        ).forEach(jshell::eval);

        SandboxContext.setAgent(agent);
        Class<?> agentType = agent.getClass();
        Class<?> superType = agentType.getSuperclass();
        if (superType != null && superType != Agent.class && Agent.class.isAssignableFrom(superType)) {
            agentType = superType;
        }
        String agentTypeName = agentType.getCanonicalName();
        if (agentTypeName == null) {
            agentTypeName = agentType.getName();
        }
        eval("__agent__",
            "var __agent__ = (" + agentTypeName + ")"
            + " ai.nooa.runtime.sandbox.SandboxContext.getAgent();");
        eval("__context__/__events__",
            "var __context__ = __agent__.context();\n"
            + "var __events__ = __agent__.events();");

        eval("returnResult", """
            Object returnResult(Object value) {
                ai.nooa.runtime.sandbox.SandboxContext.setReturnValue(value);
                return value;
            }
            """);
    }

    /**
     * Evaluate a snippet and log any rejection. Silent JShell failures make
     * generated code look broken when the real cause is a missing binding.
     */
    private void eval(String label, String snippet) {
        for (SnippetEvent event : jshell.eval(snippet)) {
            if (event.status() == Snippet.Status.REJECTED) {
                String diagnostics = jshell.diagnostics(event.snippet())
                    .map(d -> d.getMessage(Locale.getDefault()))
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse(UNKNOWN_ERROR);
                log.warn("Sandbox preamble snippet '{}' rejected: {}", label, diagnostics);
            }
        }
    }

    /**
     * Bind a method argument as a typed REPL variable so generated code can
     * reference it by name (e.g. {@code stageId}, {@code inputs}).
     */
    public void bindVariable(String name, String typeName, Object value) {
        SandboxContext.setVariable(name, value);
        List<SnippetEvent> events = jshell.eval(
            "var " + name + " = (" + typeName + ")"
            + " ai.nooa.runtime.sandbox.SandboxContext.getVariable(\"" + name + "\");");
        for (SnippetEvent event : events) {
            if (event.status() != Snippet.Status.VALID) {
                String diagnostics = jshell.diagnostics(event.snippet())
                    .map(d -> d.getMessage(Locale.getDefault()))
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse(UNKNOWN_ERROR);
                log.warn("Sandbox variable binding '{}' as '{}' rejected: {}",
                    name, typeName, diagnostics);
            }
        }
    }

    /**
     * Execute a code snippet with timeout enforcement.
     */
    public ExecutionResult execute(String code) {
        executionCount++;
        log.debug("Sandbox execution #{}", executionCount);
        stdoutCapture.reset();
        stderrCapture.reset();

        CodePermissionAnalyzer.Decision decision = CodePermissionAnalyzer.analyze(
            code, agent.permissions(), agent.permissionCallback());
        emitPermissionDecisions(decision, code);
        if (!decision.allowed()) {
            return new ExecutionResult("", "", decision.reason(), null, false, false);
        }

        try {
            return executeWithTimeout(code);
        } catch (TimeoutException _) {
            try {
                jshell.stop();
            } catch (Exception e) {
                log.debug("Unable to stop timed-out JShell execution", e);
            }
            return new ExecutionResult("", "", "Execution timed out after " + timeoutMs + "ms",
                null, false, false);
        }
    }

    private ExecutionResult executeWithTimeout(String code) throws TimeoutException {
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<ExecutionResult> future = executor.submit(() ->
                buildResult(evalSnippets(code)));

            try {
                return future.get(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
                return new ExecutionResult("", "", "Interrupted", null, false, false);
            } catch (java.util.concurrent.ExecutionException e) {
                return new ExecutionResult("", "",
                    e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), null, false, false);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw e;
            }
        }
    }

    /**
     * Evaluate a cell the way the {@code jshell} REPL does: split the source
     * into individual complete snippets with {@link SourceCodeAnalysis} and
     * evaluate them one at a time.
     *
     * <p>Evaluating a multi-statement cell in a single {@code eval} call is
     * unreliable: when the first statement is a variable declaration, JShell
     * parses the input as a VAR snippet and silently drops everything after
     * it (e.g. {@code int x = 5; returnResult(x + 1);} executes the
     * declaration and ignores the return). Splitting mirrors the REPL and
     * makes multi-statement cells, including an inline {@code returnResult},
     * behave as written.</p>
     */
    private List<SnippetEvent> evalSnippets(String code) {
        List<SnippetEvent> events = new ArrayList<>();
        SourceCodeAnalysis analysis = jshell.sourceCodeAnalysis();
        String remaining = code.strip();
        while (!remaining.isEmpty()) {
            SourceCodeAnalysis.CompletionInfo info = analysis.analyzeCompletion(remaining);
            switch (info.completeness()) {
                case EMPTY -> {
                    return events;
                }
                case DEFINITELY_INCOMPLETE, CONSIDERED_INCOMPLETE -> {
                    // The leading statement is not complete on its own (for
                    // example a multi-line loop header); evaluate the rest of
                    // the cell as one snippet so JShell reports real errors.
                    events.addAll(jshell.eval(remaining));
                    return events;
                }
                case UNKNOWN -> {
                    // Cannot be analyzed; evaluate as-is like the REPL does
                    // for unparseable input.
                    events.addAll(jshell.eval(remaining));
                    return events;
                }
                default -> {
                    events.addAll(jshell.eval(info.source().strip()));
                    remaining = info.remaining().strip();
                }
            }
        }
        return events;
    }

    private ExecutionResult buildResult(List<SnippetEvent> events) {
        String stdout = stdoutCapture.toString();
        String stderr = stderrCapture.toString();
        if (stdoutCapture.truncated()) {
            stdout += "\n... [stdout truncated at " + MAX_CAPTURE_BYTES + " bytes]";
        }
        if (stderrCapture.truncated()) {
            stderr += "\n... [stderr truncated at " + MAX_CAPTURE_BYTES + " bytes]";
        }
        stdoutCapture.reset();
        stderrCapture.reset();

        String error = null;
        Object returnValue = null;

        for (SnippetEvent event : events) {
            if (event.status() == Snippet.Status.REJECTED) {
                error = jshell.diagnostics(event.snippet())
                    .map(d -> d.getMessage(Locale.getDefault()))
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse(UNKNOWN_ERROR);
            } else if (event.exception() != null) {
                error = formatException(event.exception());
            } else if (event.status() == Snippet.Status.VALID && event.value() != null) {
                returnValue = event.value();
            }
        }

        // Check if returnResult was called
        Object sandboxReturn = SandboxContext.consumeReturnValue();
        boolean explicitReturn = sandboxReturn != null;
        if (explicitReturn) {
            returnValue = sandboxReturn;
        }

        boolean success = error == null;
        return new ExecutionResult(stdout, stderr, error, returnValue, success, explicitReturn);
    }

    private String formatException(Exception ex) {
        if (ex == null) { return "Unknown exception"; }
        // JShell wraps user-thrown exceptions in EvalException whose own
        // message is null; the useful message lives on the wrapped cause.
        if (ex instanceof jdk.jshell.EvalException evalException) {
            return formatEvalException(evalException);
        }
        return formatGenericException(ex);
    }

    private static String formatEvalException(jdk.jshell.EvalException exception) {
        Throwable cause = exception.getCause();
        String message = exception.getMessage();
        if (message == null && cause != null) message = cause.getMessage();
        String className = exception.getExceptionClassName();
        if (message == null && className == null) return formatGenericException(exception);
        String result = (className != null ? className : exception.getClass().getSimpleName())
            + ": " + message;
        if (cause != null && cause.getCause() != null) {
            result += "\nCaused by: " + cause.getCause();
        }
        return result;
    }

    private static String formatGenericException(Exception exception) {
        String result = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        if (exception.getCause() != null) {
            result += "\nCaused by: " + exception.getCause();
        }
        return result;
    }

    /**
     * Emit a structured audit event for each restricted resource touched by the
     * cell, with the effective ALLOW / ASK / DENY level and a short code excerpt.
     */
    private void emitPermissionDecisions(CodePermissionAnalyzer.Decision decision, String code) {
        if (decision.findings().isEmpty()) {
            return;
        }
        String excerpt = code.strip();
        if (excerpt.length() > 200) {
            excerpt = excerpt.substring(0, 200) + "...";
        }
        for (CodePermissionAnalyzer.Finding finding : decision.findings()) {
            agent.eventManager().add(new Event.PermissionDecision(
                CodePermissionAnalyzer.resourceName(finding.category()),
                finding.value(),
                finding.level().name(),
                finding.detail(),
                excerpt));
        }
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

    /**
     * Output stream that stops buffering after a byte cap so a runaway cell
     * cannot exhaust heap with captured stdout/stderr.
     */
    static final class CappedOutputStream extends java.io.OutputStream {
        private final int cap;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private boolean truncated;

        CappedOutputStream(int cap) {
            this.cap = cap;
        }

        @Override
        public void write(int b) {
            if (buffer.size() < cap) {
                buffer.write(b);
            } else {
                truncated = true;
            }
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
            int remaining = cap - buffer.size();
            if (remaining > 0) {
                int allowed = Math.min(length, remaining);
                buffer.write(bytes, offset, allowed);
                if (allowed < length) {
                    truncated = true;
                }
            } else {
                truncated = true;
            }
        }

        void reset() {
            buffer.reset();
            truncated = false;
        }

        boolean truncated() {
            return truncated;
        }

        @Override
        public String toString() {
            return buffer.toString();
        }
    }
}
