package ai.nooa.atif;

import ai.nooa.Agent;
import ai.nooa.context.Event;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ATIF (Agent Trace Interchange Format) trajectory export.
 * Subscribes to an agent's event manager and writes structured
 * trajectory logs for evaluation.
 *
 * <pre>{@code
 * var atif = AtifExporter.attach(agent, Path.of("trajectories"));
 * // ... agent work happens ...
 * atif.flush();
 * }</pre>
 */
public final class AtifExporter implements AutoCloseable {

    private static final ObjectMapper JSON = new ObjectMapper()
        .configure(SerializationFeature.INDENT_OUTPUT, false);

    private final String trajectoryId;
    private final Path outputDir;
    private final List<Map<String, Object>> steps = new ArrayList<>();
    private final String agentId;

    private AtifExporter(String agentId, Path outputDir) {
        this.trajectoryId = UUID.randomUUID().toString();
        this.outputDir = outputDir;
        this.agentId = agentId;
        try { Files.createDirectories(outputDir); } catch (IOException e) {
            throw new RuntimeException("Cannot create ATIF output dir", e);
        }
    }

    /** Attach an exporter to an agent — subscribes to events. */
    public static AtifExporter attach(Agent agent, Path outputDir) {
        var exporter = new AtifExporter(agent.agentId(), outputDir);
        agent.eventManager().onEvent(event -> exporter.record(event));
        return exporter;
    }

    private void record(Event event) {
        var step = new java.util.LinkedHashMap<String, Object>();
        step.put("timestamp", event.timestamp().toString());
        step.put("type", event.getClass().getSimpleName());
        step.put("event_id", event.id().toString());

        // Capture content from conversation events
        switch (event) {
            case Event.Task t -> step.put("content", t.content());
            case Event.LLMOutput o -> step.put("content", o.content() != null ? o.content() : "");
            case Event.ErrorEvent e -> step.put("content", e.message());
            case Event.ExecutionOutput eo -> {
                if (eo.stdout() != null) step.put("stdout", eo.stdout());
                if (eo.error() != null) step.put("error", eo.error());
            }
            default -> {}
        }
        steps.add(step);
    }

    /** Write the accumulated trajectory to a JSONL file. */
    public void flush() {
        try {
            var node = JSON.createObjectNode();
            node.put("trajectory_id", trajectoryId);
            node.put("agent_id", agentId);
            node.put("created_at", Instant.now().toString());
            node.put("step_count", steps.size());

            ArrayNode stepsNode = node.putArray("steps");
            for (var step : steps) {
                ObjectNode sn = stepsNode.addObject();
                for (var entry : step.entrySet()) {
                    sn.put(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }

            Path out = outputDir.resolve("trajectory_" + trajectoryId + ".json");
            Files.writeString(out, JSON.writeValueAsString(node) + "\n",
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write ATIF trajectory", e);
        }
    }

    @Override public void close() { flush(); }
}
