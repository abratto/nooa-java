package ai.nooa.mcp;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SSE (Server-Sent Events) transport for remote MCP servers.
 * Connects via HTTP POST for sending, SSE stream for receiving.
 */
final class SseTransport implements McpTransport {

    private static final Logger log = LoggerFactory.getLogger(SseTransport.class);

    private final HttpClient http;
    private final String sseUrl;
    private final String messageEndpoint;
    private final BlockingQueue<JsonRpcMessage> pending = new LinkedBlockingQueue<>();
    private volatile boolean connected = false;
    private volatile InputStream sseStream;
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

            if (response.statusCode() != 200) {
                connected = false;
                try {
                    response.body().close();
                } catch (IOException e) {
                    log.debug("Failed to close SSE response body on non-200 status", e);
                }
                return;
            }

            this.sseStream = response.body();
            connected = true;

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
                            } catch (Exception e) {
                                log.debug("Skipping malformed SSE JSON-RPC payload", e);
                            }
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

    /**
     * Return the next message, or {@code null} on timeout (30s) or interrupt.
     * On interrupt the calling thread's interrupt flag is restored.
     */
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
        // Closing the stream is what unblocks a reader parked in readLine().
        var stream = sseStream;
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException e) {
                log.debug("Failed to close SSE stream", e);
            }
        }
        if (sseThread != null) sseThread.interrupt();
    }
}
