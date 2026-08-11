package ai.nooa.mcp;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.util.concurrent.*;

/**
 * SSE (Server-Sent Events) transport for remote MCP servers.
 * Connects via HTTP POST for sending, SSE stream for receiving.
 */
final class SseTransport implements McpTransport {

    private final HttpClient http;
    private final String sseUrl;
    private final String messageEndpoint;
    private final BlockingQueue<JsonRpcMessage> pending = new LinkedBlockingQueue<>();
    private volatile boolean connected = false;
    private Thread sseThread;

    SseTransport(String sseUrl) {
        this.http = HttpClient.newHttpClient();
        this.sseUrl = sseUrl;
        this.messageEndpoint = sseUrl.replace("/sse", "/message");
        connect();
    }

    private void connect() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(sseUrl))
                .header("Accept", "text/event-stream")
                .GET()
                .build();

            HttpResponse<InputStream> response = http.send(req,
                HttpResponse.BodyHandlers.ofInputStream());

            connected = response.statusCode() == 200;
            if (!connected) return;

            this.sseThread = Thread.ofVirtual().start(() -> {
                try (var reader = new BufferedReader(
                        new InputStreamReader(response.body()))) {
                    String line;
                    StringBuilder data = new StringBuilder();
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            data.append(line.substring(6));
                        } else if (line.isBlank() && !data.isEmpty()) {
                            try {
                                pending.put(JsonRpcMessage.deserialize(data.toString()));
                            } catch (Exception ignored) {}
                            data.setLength(0);
                        }
                    }
                } catch (IOException e) {
                    connected = false;
                }
            });

            // Send initialize immediately
            send(JsonRpcMessage.initializeRequest());
        } catch (Exception e) {
            connected = false;
            throw new RuntimeException("Failed to connect SSE: " + sseUrl, e);
        }
    }

    @Override
    public void send(JsonRpcMessage message) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(messageEndpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(message.serialize()))
                .build();
            http.send(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            throw new RuntimeException("SSE send failed", e);
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

    @Override public boolean isConnected() { return connected; }

    @Override public void close() {
        connected = false;
        if (sseThread != null) sseThread.interrupt();
    }
}
