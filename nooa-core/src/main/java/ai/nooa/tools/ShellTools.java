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

    /**
     * Run a shell command in the workspace, enforcing permissions and capturing
     * stdout/stderr. A timeout or failure is represented by exit code {@code -1}.
     */
    public ShellResult run(String command) {
        return run(command, 60);
    }

    /** Run a shell command with a timeout in seconds. */
    public ShellResult run(String command, int timeoutSeconds) {
        // Check permissions
        var level = permissions.checkCommand(command);
        if (level == Permissions.Level.DENY) {
            return new ShellResult("", "Permission denied: " + command, -1);
        }
        if (level == Permissions.Level.ASK
            && (permissionCallback == null
                || !permissionCallback.approve("command", command))) {
            // Fail closed: an approval-required command with no callback is denied.
            return new ShellResult("", "User denied: " + command, -1);
        }

        Path tempDir = null;
        Path outFile = null;
        Path errFile = null;
        try {
            // Redirect output to files rather than pipes. A child process (e.g. a
            // surefire fork spawned by `mvn`) can inherit the pipe fd and keep it
            // open after the parent exits, making readAllBytes() block forever.
            // Files cannot block the reader the same way.
            tempDir = Files.createTempDirectory(workspace, ".nooa-shell-");
            outFile = tempDir.resolve("stdout.log");
            errFile = tempDir.resolve("stderr.log");
            Files.createFile(outFile);
            Files.createFile(errFile);

            var pb = new ProcessBuilder("/bin/bash", "-c", command)
                .directory(workspace.toFile())
                .redirectOutput(outFile.toFile())
                .redirectError(errFile.toFile());

            currentProcess = pb.start();
            Process proc = currentProcess;

            return await(proc, outFile, errFile, timeoutSeconds);
        } catch (Exception e) {
            return new ShellResult("", e.getMessage(), -1);
        } finally {
            currentProcess = null;
            deleteQuietly(outFile);
            deleteQuietly(errFile);
            deleteQuietly(tempDir);
        }
    }

    private static ShellResult await(Process proc, Path outFile, Path errFile, int timeoutSeconds) {
        try {
            if (!proc.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                killTree(proc);
                return new ShellResult(readFile(outFile),
                    readFile(errFile) + "\n[KILLED: timeout after " + timeoutSeconds + "s]", -1);
            }
            return new ShellResult(readFile(outFile), readFile(errFile), proc.exitValue());
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            killTree(proc);
            return new ShellResult("", "Interrupted", -1);
        }
    }

    private static void killTree(Process proc) {
        if (proc == null) {
            return;
        }
        try {
            proc.descendants().forEach(ProcessHandle::destroyForcibly);
        } catch (Exception _) {
            // The process is forcibly destroyed below even if descendant lookup fails.
        }
        proc.destroyForcibly();
    }

    private static String readFile(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException _) {
            return "";
        }
    }

    private static void deleteQuietly(Path path) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException _) {
                // Cleanup is best effort after command execution has completed.
            }
        }
    }

    /** Read a file relative to the workspace; path escapes and I/O failures become error text. */
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

    /** Write content to a workspace-relative file, rejecting path escapes and creating parents. */
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

    /** View a workspace-relative file, truncating successful results after 2,000 characters. */
    public String view(String path) {
        String content = read(path);
        if (content.startsWith("[ERROR")) return content;
        if (content.length() > 2000) {
            return content.substring(0, 2000) + "\n... [truncated, "
                + content.length() + " total chars]";
        }
        return content;
    }

    /** Return the workspace directory used for commands and file operations. */
    public Path workspace() { return workspace; }

    @Override
    /** Terminate an active command process, if any. */
    public void close() {
        if (currentProcess != null) {
            currentProcess.destroyForcibly();
        }
    }
}
