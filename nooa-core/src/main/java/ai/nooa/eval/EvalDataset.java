package ai.nooa.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An ordered collection of {@link EvalCase}s, loadable from and savable to
 * JSONL (one case object per line).
 */
public final class EvalDataset {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final String id;
    private final List<EvalCase> cases;

    private EvalDataset(String id, List<EvalCase> cases) {
        this.id = id;
        this.cases = List.copyOf(cases);
    }

    public static EvalDataset of(String id, List<EvalCase> cases) {
        return new EvalDataset(id, cases);
    }

    public String id() {
        return id;
    }

    public List<EvalCase> cases() {
        return cases;
    }

    /** Load a dataset from a JSONL file; the file name (sans extension) is the id. */
    public static EvalDataset load(Path file) {
        List<EvalCase> loaded = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                loaded.add(fromNode(JSON.readTree(line)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load dataset " + file, e);
        }
        String name = file.getFileName().toString().replaceFirst("\\.[^.]+$", "");
        return new EvalDataset(name, loaded);
    }

    /** Write the dataset as JSONL. */
    public void save(Path file) {
        StringBuilder out = new StringBuilder();
        for (EvalCase evalCase : cases) {
            try {
                out.append(JSON.writeValueAsString(toMap(evalCase))).append('\n');
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to serialize case " + evalCase.id(), e);
            }
        }
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write dataset " + file, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static EvalCase fromNode(JsonNode node) {
        String id = node.path("id").asText();
        String target = node.path("targetMethod").asText();
        Map<String, Object> input = node.hasNonNull("input")
            ? JSON.convertValue(node.get("input"), Map.class) : Map.of();
        Object expected = node.hasNonNull("expected")
            ? JSON.convertValue(node.get("expected"), Object.class) : null;
        Map<String, Object> goal = node.hasNonNull("expectedGoal")
            ? JSON.convertValue(node.get("expectedGoal"), Map.class) : Map.of();
        List<EvalCase.Milestone> milestones = new ArrayList<>();
        if (node.hasNonNull("milestones")) {
            for (JsonNode m : node.get("milestones")) {
                milestones.add(new EvalCase.Milestone(
                    m.path("name").asText(),
                    m.path("weight").asDouble(1.0),
                    m.hasNonNull("expected")
                        ? JSON.convertValue(m.get("expected"), Map.class) : Map.of()));
            }
        }
        Set<String> tags = node.hasNonNull("tags")
            ? Set.copyOf(JSON.convertValue(node.get("tags"), Set.class)) : Set.of();
        Map<String, Object> metadata = node.hasNonNull("metadata")
            ? JSON.convertValue(node.get("metadata"), Map.class) : Map.of();
        return new EvalCase(id, target, input, expected, goal, milestones, tags, metadata);
    }

    private static Map<String, Object> toMap(EvalCase evalCase) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", evalCase.id());
        map.put("targetMethod", evalCase.targetMethod());
        map.put("input", evalCase.input());
        if (evalCase.expected() != null) {
            map.put("expected", evalCase.expected());
        }
        if (!evalCase.expectedGoal().isEmpty()) {
            map.put("expectedGoal", evalCase.expectedGoal());
        }
        if (!evalCase.milestones().isEmpty()) {
            List<Map<String, Object>> milestones = new ArrayList<>();
            for (EvalCase.Milestone m : evalCase.milestones()) {
                Map<String, Object> mm = new LinkedHashMap<>();
                mm.put("name", m.name());
                mm.put("weight", m.weight());
                mm.put("expected", m.expected());
                milestones.add(mm);
            }
            map.put("milestones", milestones);
        }
        if (!evalCase.tags().isEmpty()) {
            map.put("tags", evalCase.tags());
        }
        if (!evalCase.metadata().isEmpty()) {
            map.put("metadata", evalCase.metadata());
        }
        return map;
    }
}
