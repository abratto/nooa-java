package ai.nooa.observability;

import ai.nooa.Agent;
import ai.nooa.context.Event;
import ai.nooa.llm.Message;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * JSONL prompt recorder for tuning runs.
 *
 * <p>Subscribes to an agent's event manager and writes one JSON object per
 * {@link Event.PromptBuilt} event. Pair with prompt logging enabled:</p>
 *
 * <pre>{@code
 * // Set NOOA_LOG_PROMPTS=true (and optionally NOOA_LOG_PROMPTS_RAW=true)
 * var recorder = PromptRecorder.attach(agent, Path.of("prompts.jsonl"));
 * // ... agent work happens ...
 * recorder.close();
 * }</pre>
 *
 * <p>Events are appended eagerly so partial runs remain inspectable. The
 * recorder is passive: it only observes PromptBuilt events already emitted by
 * the runtime and does not force redaction or raw mode itself.</p>
 *
 * <p>Callers can attach immutable run metadata (or a {@link MetadataProvider}
 * that computes it per event); each entry becomes an extra top-level JSON
 * field on the recorded line. Recording is best-effort by design: if the
 * target file becomes unwritable, recording continues in memory and the
 * agent loop is never interrupted.</p>
 */
public final class PromptRecorder implements AutoCloseable {

    /**
     * Supplies additional, generic metadata fields for each recorded prompt
     * event. Implementations must return only JSON-serializable values
     * (String, Number, Boolean, Map, List) and must be side-effect free:
     * it is invoked synchronously on every PromptBuilt event.
     */
    public interface MetadataProvider {
        Map<String, Object> metadata(Event.PromptBuilt event);
    }

    private static final ObjectMapper JSON = new ObjectMapper()
        .configure(SerializationFeature.INDENT_OUTPUT, false);

    private final Path file;
    private final List<Event.PromptBuilt> recorded = new ArrayList<>();
    private final Object writeLock = new Object();
    private final Consumer<Event> listener = this::onEvent;

    private volatile MetadataProvider metadataProvider;
    private volatile boolean fileUsable = true;
    private volatile int writeFailures;

    private PromptRecorder(Path file) {
        this.file = file;
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (Files.isDirectory(file)) {
                throw new IOException("prompt log path is a directory: " + file);
            }
        } catch (IOException e) {
            fileUsable = false;
        }
    }

    /** Attach a recorder to an agent — subscribes to events and writes eagerly. */
    public static PromptRecorder attach(Agent agent, Path file) {
        return attach(agent, file, null);
    }

    /**
     * Attach a recorder with per-event metadata enrichment. Every extra field
     * returned by the provider becomes a top-level JSON field on the line.
     */
    public static PromptRecorder attach(Agent agent, Path file, MetadataProvider metadata) {
        var recorder = new PromptRecorder(file);
        recorder.metadataProvider = metadata;
        agent.eventManager().onEvent(recorder.listener);
        recorder.bindAgent(agent);
        return recorder;
    }

    /** Replaces the metadata provider used for subsequent events. */
    public PromptRecorder metadataProvider(MetadataProvider provider) {
        this.metadataProvider = provider;
        return this;
    }

    private void onEvent(Event event) {
        if (!(event instanceof Event.PromptBuilt pb)) {
            return;
        }
        synchronized (writeLock) {
            recorded.add(pb);
        }
        append(pb);
    }

    private void append(Event.PromptBuilt pb) {
        synchronized (writeLock) {
            if (!fileUsable) {
                return; // recording continues in memory; the agent loop is unaffected
            }
            try {
                Files.writeString(file, toJson(pb) + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                // Prompt recording must never break the agent loop.
                writeFailures++;
            }
        }
    }

    private String toJson(Event.PromptBuilt pb) {
        ObjectNode node = JSON.createObjectNode();
        node.put("event_id", pb.id().toString());
        node.put("timestamp", pb.timestamp().toString());
        node.put("model", pb.modelName());
        node.put("redacted", pb.redacted());
        node.put("output_model", pb.outputModel());
        node.put("message_count", pb.messages().size());

        ArrayNode toolNames = node.putArray("tool_names");
        for (String tool : pb.toolNames()) {
            toolNames.add(tool);
        }

        ArrayNode messages = node.putArray("messages");
        for (Message m : pb.messages()) {
            ObjectNode mn = messages.addObject();
            mn.put("role", m.role());
            mn.put("content", m.content());
            mn.put("name", m.name());
            mn.put("tool_call_id", m.toolCallId());
            if (m.toolCalls() != null) {
                mn.putPOJO("tool_calls", m.toolCalls());
            }
        }

        if (pb.samplingParams() != null && !pb.samplingParams().isEmpty()) {
            node.putPOJO("sampling_params", pb.samplingParams());
        }

        MetadataProvider provider = metadataProvider;
        if (provider != null) {
            Map<String, Object> metadata = provider.metadata(pb);
            if (metadata != null) {
                for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                    if (entry.getKey() == null || entry.getKey().isBlank()) {
                        continue;
                    }
                    node.set(entry.getKey(), JSON.valueToTree(entry.getValue()));
                }
            }
        }
        return node.toString();
    }

    /** The file this recorder writes to. */
    public Path file() {
        return file;
    }

    /** In-memory copy of all PromptBuilt events observed so far. */
    public List<Event.PromptBuilt> recorded() {
        synchronized (writeLock) {
            return List.copyOf(recorded);
        }
    }

    /** Number of PromptBuilt events observed so far. */
    public int count() {
        synchronized (writeLock) {
            return recorded.size();
        }
    }

    /** Number of failed file appends since attach (writes keep failing silently). */
    public int writeFailures() {
        return writeFailures;
    }

    /** Parses a recorded line back into a JSON tree (validation helper). */
    public static JsonNode parseLine(String jsonlLine) {
        try {
            return JSON.readTree(jsonlLine);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        if (agentRef != null) {
            agentRef.eventManager().removeListener(listener);
        }
    }

    private void bindAgent(ai.nooa.Agent agent) {
        this.agentRef = agent;
    }

    private volatile ai.nooa.Agent agentRef;
}
