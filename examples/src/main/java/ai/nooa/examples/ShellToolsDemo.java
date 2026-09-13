package ai.nooa.examples;

import ai.nooa.security.Permissions;
import ai.nooa.tools.ShellTools;

import java.nio.file.Path;

/**
 * Runs a small trusted-environment shell command through {@link ShellTools}.
 *
 * <p><b>NOOA features demonstrated</b></p>
 * <ul>
 *   <li>{@link ShellTools#run(String)} — persistent shell sessions with
 *       captured stdout/stderr, rooted at a working directory.</li>
 *   <li>The {@link Permissions} policy layer — by default every command is
 *       denied; this demo explicitly grants {@code allowAll()} because it is a
 *       trusted local demo. Real deployments should grant narrow
 *       {@code Permissions.command("git *", ALLOW)}-style rules instead.</li>
 *   <li>{@link AutoCloseable} lifecycle — the session (and its child
 *       processes) is released with try-with-resources.</li>
 * </ul>
 *
 * <p><b>Safety.</b> {@code ShellTools} executes real local commands. This demo
 * is educational, not a security boundary: run it only in a disposable or
 * explicitly trusted environment.</p>
 *
 * <p><b>Run</b> (no model required):
 * <pre>{@code
 * mvn -pl examples exec:java -Dexec.mainClass=ai.nooa.examples.ShellToolsDemo
 * }</pre>
 * or {@code examples/run.sh ShellToolsDemo}.</p>
 */
public final class ShellToolsDemo {
    private ShellToolsDemo() {}

    public static void main(String[] args) {
        // Permissions deny everything by default. This is a trusted demo, so
        // it grants allowAll() explicitly; production agents should instead
        // add narrow rules (e.g. Permissions.command("git *", ALLOW)).
        try (var shell = new ShellTools(Path.of(System.getProperty("user.home")),
            Permissions.allowAll(), null)) {
            var result = shell.run("echo 'hello from shell' && ls ~ | head -3");
            System.out.print(result.stdout());
            if (!result.stderr().isBlank()) {
                System.out.println("[stderr] " + result.stderr());
            }
        }
    }
}
