package ai.nooa.mcp;

import java.io.*;
import java.util.List;
import java.util.concurrent.*;

/**
 * Stdio transport — spawns an MCP server as a subprocess and communicates
 * via stdin/stdout with newline-delimited JSON-RPC messages.
 */
final class StdioTransport implements McpTransport {

    private final Process process;
    private final BufferedWriter writer;
    private final BufferedReader reader;
    private final BlockingQueue<JsonRpcMessage> pending = new LinkedBlockingQueue<>();
    private volatile boolean connected = true;
    private final Thread readerThread;

    StdioTransport(List<String> command) {
        try {
            var pb = new ProcessBuilder(command)
                .redirectError(ProcessBuilder.Redirect.DISCARD);
            this.process = pb.start();
            this.writer = new BufferedWriter(
                new OutputStreamWriter(process.getOutputStream()));
            this.reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));

            this.readerThread = Thread.ofVirtual().start(() -> {
                try {
                    String line;
                    while (connected && (line = reader.readLine()) != null) {
                        if (line.isBlank()) continue;
                        try {
                            pending.put(JsonRpcMessage.deserialize(line));
                        } catch (Exception e) {
                            // Skip malformed messages
                        }
                    }
                } catch (IOException e) {
                    connected = false;
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("Failed to start MCP stdio server: "
                + String.join(" ", command), e);
        }
    }

    @Override
    public void send(JsonRpcMessage message) {
        try {
            writer.write(message.serialize());
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            connected = false;
            throw new RuntimeException("Failed to send MCP message", e);
        }
    }

    @Override
    public JsonRpcMessage receive() {
        try {
            return pending.poll(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    @Override
    public boolean isConnected() { return connected && process.isAlive(); }

    @Override
    public void close() {
        connected = false;
        try { readerThread.interrupt(); } catch (Exception ignored) {}
        try { writer.close(); } catch (Exception ignored) {}
        try { reader.close(); } catch (Exception ignored) {}
        process.destroy();
        try { process.waitFor(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        if (process.isAlive()) process.destroyForcibly();
    }
}
