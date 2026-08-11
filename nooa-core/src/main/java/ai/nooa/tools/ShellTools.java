package ai.nooa.tools;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

import ai.nooa.security.PermissionCallback;
import ai.nooa.security.Permissions;

/**
 * Persistent shell session for agents. Generated code can run commands,
 * read/write files.
 *
 * <pre>{@code
 * var shell = new ShellTools(Path.of("/tmp/workspace"));
 * var result = shell.run("ls -la");
 * var content = shell.read("src/main/Main.java");
 * }</pre>
 */
public final class ShellTools implements AutoCloseable {

    private final Path workspace;
    private final Permissions permissions;
    private final PermissionCallback permissionCallback;
    private Process currentProcess;

    public ShellTools(Path workspace) {
        this(workspace, new Permissions(), null);
    }

    public ShellTools(Path workspace, Permissions permissions, PermissionCallback callback) {
        this.workspace = workspace;
        this.permissions = permissions;
        this.permissionCallback = callback;
        try { Files.createDirectories(workspace); } catch (IOException e) {
            throw new RuntimeException("Cannot create workspace: " + workspace, e);
        }
    }

    public record ShellResult(String stdout, String stderr, int exitCode) {
        public boolean success() { return exitCode == 0; }

        @Override
        public String toString() {
            var sb = new StringBuilder();
            if (!stdout.isEmpty()) sb.append(stdout);
            if (!stderr.isEmpty()) {
                if (!sb.isEmpty()) sb.append("\n");
                sb.append("[stderr]\n").append(stderr);
            }
            sb.append("\n[exit: ").append(exitCode).append("]");
            return sb.toString();
        }
    }

    public ShellResult run(String command) {
        return run(command, 60);
    }

    public ShellResult run(String command, int timeoutSeconds) {
        // Check permissions
        var level = permissions.checkCommand(command);
        if (level == Permissions.Level.DENY) {
            return new ShellResult("", "Permission denied: " + command, -1);
        }
        if (level == Permissions.Level.ASK && permissionCallback != null) {
            if (!permissionCallback.approve("command", command)) {
                return new ShellResult("", "User denied: " + command, -1);
            }
        }
        try {
            var pb = new ProcessBuilder("/bin/bash", "-c", command)
                .directory(workspace.toFile())
                .redirectErrorStream(false);

            currentProcess = pb.start();
            var stdout = new String(currentProcess.getInputStream().readAllBytes());
            var stderr = new String(currentProcess.getErrorStream().readAllBytes());
            boolean finished = currentProcess.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            if (!finished) {
                currentProcess.destroyForcibly();
                return new ShellResult(stdout, stderr + "\n[KILLED: timeout]", -1);
            }

            int exitCode = currentProcess.exitValue();
            return new ShellResult(stdout, stderr, exitCode);
        } catch (Exception e) {
            return new ShellResult("", e.getMessage(), -1);
        } finally {
            currentProcess = null;
        }
    }

    /** Read a file relative to the workspace. */
    public String read(String path) {
        try {
            Path resolved = workspace.resolve(path).normalize();
            if (!resolved.startsWith(workspace)) {
                return "[ERROR: path escape attempted: " + path + "]";
            }
            return Files.readString(resolved);
        } catch (IOException e) {
            return "[ERROR: " + e.getMessage() + "]";
        }
    }

    /** Write content to a file relative to the workspace. */
    public void writeFile(String path, String content) {
        try {
            Path resolved = workspace.resolve(path).normalize();
            if (!resolved.startsWith(workspace)) {
                throw new SecurityException("Path escape: " + path);
            }
            Files.createDirectories(resolved.getParent());
            Files.writeString(resolved, content);
        } catch (IOException e) {
            throw new RuntimeException("Write failed: " + path, e);
        }
    }

    /** View a file (read-only, truncation safe). */
    public String view(String path) {
        String content = read(path);
        if (content.startsWith("[ERROR")) return content;
        if (content.length() > 2000) {
            return content.substring(0, 2000) + "\n... [truncated, "
                + content.length() + " total chars]";
        }
        return content;
    }

    public Path workspace() { return workspace; }

    @Override
    public void close() {
        if (currentProcess != null) {
            currentProcess.destroyForcibly();
        }
    }
}
