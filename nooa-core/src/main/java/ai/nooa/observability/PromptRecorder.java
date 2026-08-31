package ai.nooa.observability;

import ai.nooa.Agent;
import ai.nooa.context.Event;
import ai.nooa.llm.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

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
 */
public final class PromptRecorder implements AutoCloseable {

    private static final ObjectMapper JSON = new ObjectMapper()
        .configure(SerializationFeature.INDENT_OUTPUT, false);

    private final Path file;
    private final List<Event.PromptBuilt> recorded = new ArrayList<>();
    private final Object writeLock = new Object();

    private PromptRecorder(Path file) {
        this.file = file;
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            throw new RuntimeException("Cannot create prompt log dir: " + file, e);
        }
    }

    /** Attach a recorder to an agent — subscribes to events and writes eagerly. */
    public static PromptRecorder attach(Agent agent, Path file) {
        var recorder = new PromptRecorder(file);
        agent.eventManager().onEvent(recorder::onEvent);
        return recorder;
    }

    private void onEvent(Event event) {
        if (!(event instanceof Event.PromptBuilt pb)) {
            return;
        }
        recorded.add(pb);
        append(pb);
    }

    private void append(Event.PromptBuilt pb) {
        synchronized (writeLock) {
            try {
                Files.writeString(file, toJson(pb) + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                // Prompt recording must never break the agent loop.
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

    @Override
    public void close() {
        // Writes are appended eagerly; nothing is buffered.
    }
}
