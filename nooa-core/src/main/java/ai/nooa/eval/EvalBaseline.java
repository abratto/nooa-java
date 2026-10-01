package ai.nooa.eval;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A stored summary of a previous {@link EvalReport}, used as the regression
 * reference for later runs. Comparison is explicit: {@link #deltas} returns
 * current-minus-baseline values, and {@link #regressed} reports whether any
 * tracked metric dropped beyond a tolerance.
 */
public final class EvalBaseline {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final String datasetId;
    private final String agentClass;
    private final int trials;
    private final double weightedMean;
    private final CompletionMetrics completion;
    private final Map<String, Double> scorerMeans;
    private final Map<String, Double> caseWeighted;

    private EvalBaseline(String datasetId, String agentClass, int trials,
                         double weightedMean, CompletionMetrics completion,
                         Map<String, Double> scorerMeans, Map<String, Double> caseWeighted) {
        this.datasetId = datasetId;
        this.agentClass = agentClass;
        this.trials = trials;
        this.weightedMean = weightedMean;
        this.completion = completion;
        this.scorerMeans = Map.copyOf(scorerMeans);
        this.caseWeighted = Map.copyOf(caseWeighted);
    }

    public static EvalBaseline from(EvalReport report) {
        Map<String, Double> caseWeighted = new LinkedHashMap<>();
        for (EvalReport.CaseReport caseReport : report.cases()) {
            caseWeighted.put(caseReport.caseId(), caseReport.weighted());
        }
        return new EvalBaseline(
            report.datasetId(), report.agentClass(), report.trials(),
            report.aggregate().weightedMean(), report.aggregate().completion(),
            report.aggregate().scorerMeans(), caseWeighted);
    }

    public double weightedMean() {
        return weightedMean;
    }

    public CompletionMetrics completion() {
        return completion;
    }

    public EvalBaseline save(Path file) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("datasetId", datasetId);
        data.put("agentClass", agentClass);
        data.put("trials", trials);
        data.put("weightedMean", weightedMean);
        data.put("completion", completion);
        data.put("scorerMeans", scorerMeans);
        data.put("caseWeighted", caseWeighted);
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file,
                JSON.writerWithDefaultPrettyPrinter().writeValueAsString(data),
                StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save baseline " + file, e);
        }
        return this;
    }

    @SuppressWarnings("unchecked")
    public static EvalBaseline load(Path file) {
        try {
            var node = JSON.readTree(Files.readString(file, StandardCharsets.UTF_8));
            Map<String, Double> scorerMeans = new LinkedHashMap<>();
            if (node.has("scorerMeans")) {
                node.get("scorerMeans").fields().forEachRemaining(
                    e -> scorerMeans.put(e.getKey(), e.getValue().asDouble()));
            }
            Map<String, Double> caseWeighted = new LinkedHashMap<>();
            if (node.has("caseWeighted")) {
                node.get("caseWeighted").fields().forEachRemaining(
                    e -> caseWeighted.put(e.getKey(), e.getValue().asDouble()));
            }
            var c = node.path("completion");
            CompletionMetrics completion = new CompletionMetrics(
                c.path("passAt1").asDouble(),
                c.path("passAtK").asDouble(),
                c.path("passHatK").asDouble(),
                c.path("flakiness").asDouble());
            return new EvalBaseline(
                node.path("datasetId").asText(),
                node.path("agentClass").asText(),
                node.path("trials").asInt(1),
                node.path("weightedMean").asDouble(),
                completion,
                scorerMeans,
                caseWeighted);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load baseline " + file, e);
        }
    }

    /** Current-minus-baseline deltas for weighted, completion, and per-case scores. */
    public Map<String, Double> deltas(EvalReport current) {
        Map<String, Double> deltas = new LinkedHashMap<>();
        deltas.put("weightedMean", current.aggregate().weightedMean() - weightedMean);
        CompletionMetrics c = current.aggregate().completion();
        deltas.put("passAt1", c.passAt1() - completion.passAt1());
        deltas.put("passAtK", c.passAtK() - completion.passAtK());
        deltas.put("passHatK", c.passHatK() - completion.passHatK());
        for (EvalReport.CaseReport caseReport : current.cases()) {
            Double base = caseWeighted.get(caseReport.caseId());
            if (base != null) {
                deltas.put("case." + caseReport.caseId(), caseReport.weighted() - base);
            }
        }
        return deltas;
    }

    /** Whether any tracked metric dropped by more than {@code tolerance}. */
    public boolean regressed(EvalReport current, double tolerance) {
        for (double delta : deltas(current).values()) {
            if (delta < -Math.abs(tolerance)) {
                return true;
            }
        }
        return false;
    }
}
