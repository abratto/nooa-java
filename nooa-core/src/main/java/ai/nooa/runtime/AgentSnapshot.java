package ai.nooa.runtime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import ai.nooa.Agent;
import ai.nooa.AgentFactory;
import ai.nooa.context.ContextBlock;
import ai.nooa.context.Event;
import ai.nooa.llm.LLMResponse;
import ai.nooa.llm.UnifiedLLM;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/**
 * Serializes agent state to JSON for session persistence.
 * Captures: events, context blocks, agent identity, timestamp.
 */
public final class AgentSnapshot {

    private static final ObjectMapper JSON = new ObjectMapper()
        .configure(SerializationFeature.INDENT_OUTPUT, false)
        .registerModule(new JavaTimeModule())
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    public record Snapshot(
        String agentClass,
        String agentId,
        Instant createdAt,
        List<Map<String, Object>> events,
        Map<String, String> contextBlocks,
        Map<String, String> instanceValues,
        String model
    ) {}

    /** Take a snapshot of the current agent state. */
    public static Snapshot take(Agent agent) {
        List<Map<String, Object>> eventList = new ArrayList<>();
        for (Event e : agent.eventManager().all()) {
            eventList.add(serializeEvent(e));
        }

        Map<String, String> blocks = new LinkedHashMap<>();
        for (var entry : agent.contextManager().allBlocks().entrySet()) {
            if (entry.getValue() instanceof ContextBlock.Static s) {
                blocks.put(entry.getKey(), s.value());
            }
        }

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("agentClass", agent.getClass().getName());

        return new Snapshot(
            agent.getClass().getName(),
            agent.agentId(),
            Instant.now(),
            eventList,
            blocks,
            fields,
            agent.llm().model()
        );
    }

    /** Save a snapshot to a JSON file. */
    public static void save(Snapshot snapshot, Path path) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, JSON.writeValueAsString(snapshot) + "\n",
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save snapshot: " + path, e);
        }
    }

    /** Load a snapshot from a JSON file. */
    public static Snapshot load(Path path) {
        try {
            return JSON.readValue(Files.readString(path), Snapshot.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load snapshot: " + path, e);
        }
    }

    /** Restore events from a snapshot into an agent's event manager. */
    public static void restoreEvents(Agent agent, Snapshot snapshot) {
        agent.eventManager().clear();
        for (var map : snapshot.events()) {
            Event event = deserializeEvent(map);
            if (event != null) {
                agent.eventManager().add(event);
            }
        }
    }

    /** Restore context blocks from a snapshot. */
    public static void restoreContext(Agent agent, Snapshot snapshot) {
        for (var entry : snapshot.contextBlocks().entrySet()) {
            try {
                agent.contextManager().put(entry.getKey(), entry.getValue());
            } catch (IllegalArgumentException ignored) {
                // protected block — skip
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> serializeEvent(Event e) {
        var map = new LinkedHashMap<String, Object>();
        map.put("type", e.getClass().getSimpleName());
        map.put("id", e.id().toString());
        map.put("timestamp", e.timestamp().toString());

        switch (e) {
            case Event.Task t -> map.put("content", t.content());
            case Event.LLMOutput o -> {
                map.put("content", o.content());
                if (o.toolCalls() != null && !o.toolCalls().isEmpty()) {
                    List<Map<String, Object>> tcList = new ArrayList<>();
                    for (var tc : o.toolCalls()) {
                        tcList.add(Map.of(
                            "id", tc.id(), "name", tc.name(),
                            "arguments", tc.arguments()));
                    }
                    map.put("toolCalls", tcList);
                }
            }
            case Event.ExecutionOutput eo -> {
                map.put("stdout", eo.stdout());
                map.put("stderr", eo.stderr());
                map.put("error", eo.error());
            }
            case Event.ErrorEvent ee -> map.put("message", ee.message());
            case Event.Feedback f -> map.put("content", f.content());
            default -> {}
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private static Event deserializeEvent(Map<String, Object> map) {
        String type = (String) map.get("type");
        String id = (String) map.get("id");
        String ts = (String) map.get("timestamp");
        Instant timestamp = Instant.parse(ts);
        UUID uuid = UUID.fromString(id);

        return switch (type) {
            case "Task" -> new Event.Task(uuid, timestamp, (String) map.get("content"));
            case "LLMOutput" -> {
                List<LLMResponse.ToolCall> tcs = new ArrayList<>();
                if (map.containsKey("toolCalls")) {
                    for (var tcMap : (List<Map<String, Object>>) map.get("toolCalls")) {
                        tcs.add(new LLMResponse.ToolCall(
                            (String) tcMap.get("id"),
                            (String) tcMap.get("name"),
                            (Map<String, Object>) tcMap.get("arguments")));
                    }
                }
                yield new Event.LLMOutput(uuid, timestamp, (String) map.get("content"), tcs);
            }
            case "ExecutionOutput" -> new Event.ExecutionOutput(uuid, timestamp,
                (String) map.getOrDefault("stdout", ""),
                (String) map.getOrDefault("stderr", ""),
                (String) map.get("error"));
            case "ErrorEvent" -> new Event.ErrorEvent(uuid, timestamp,
                (String) map.get("message"));
            case "Feedback" -> new Event.Feedback(uuid, timestamp,
                (String) map.get("content"));
            default -> null;
        };
    }
}
