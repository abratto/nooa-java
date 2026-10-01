package ai.nooa.eval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Result of running an {@link EvalDataset} through an {@link EvalRunner}.
 *
 * @param runAt      ISO-8601 timestamp of the run
 * @param agentClass fully-qualified agent class name
 * @param datasetId  dataset identifier
 * @param mode       execution mode used
 * @param trials     trials per case
 * @param cases      per-case reports
 * @param aggregate  dataset-level summary
 */
public record EvalReport(
    String runAt,
    String agentClass,
    String datasetId,
    String mode,
    int trials,
    List<CaseReport> cases,
    Aggregate aggregate) {

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * @param weighted     rubric-weighted score for the case
     * @param goal         goal verification outcome (representative trial)
     * @param completion   reliability metrics across trials
     * @param trialSuccess per-trial success flags
     * @param output       string form of the first trial's output
     */
    public record CaseReport(
        String caseId,
        Set<String> tags,
        List<Score> scores,
        double weighted,
        GoalVerifier.Completion goal,
        CompletionMetrics completion,
        List<Boolean> trialSuccess,
        String output,
        int steps) {}

    /**
     * @param scorerMeans mean value per scorer across cases
     * @param weightedMean mean rubric-weighted score across cases
     * @param completion  dataset-level reliability metrics
     */
    public record Aggregate(
        Map<String, Double> scorerMeans,
        double weightedMean,
        CompletionMetrics completion) {}

    public String toJson() {
        try {
            return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize eval report", e);
        }
    }

    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Eval report: ").append(datasetId).append('\n').append('\n');
        sb.append("- agent: `").append(agentClass).append("`\n");
        sb.append("- mode: ").append(mode).append("  trials: ").append(trials).append('\n');
        sb.append("- run at: ").append(runAt).append('\n').append('\n');

        CompletionMetrics c = aggregate.completion();
        sb.append("## Reliability\n\n");
        sb.append("| pass@1 | pass@k | pass^k | flakiness |\n");
        sb.append("|---|---|---|---|\n");
        sb.append(String.format("| %.3f | %.3f | %.3f | %.3f |%n",
            c.passAt1(), c.passAtK(), c.passHatK(), c.flakiness()));
        sb.append('\n');

        sb.append("## Scores\n\n");
        sb.append("| scorer | mean |\n|---|---|\n");
        aggregate.scorerMeans().forEach((name, mean) ->
            sb.append(String.format("| %s | %.3f |%n", name, mean)));
        sb.append(String.format("| **weighted** | **%.3f** |%n", aggregate.weightedMean()));
        sb.append('\n');

        sb.append("## Cases\n\n");
        sb.append("| case | weighted | pass@1 | pass^k | tags |\n|---|---|---|---|---|\n");
        for (CaseReport report : cases) {
            sb.append(String.format("| %s | %.3f | %.3f | %.3f | %s |%n",
                report.caseId(), report.weighted(),
                report.completion().passAt1(), report.completion().passHatK(),
                String.join(",", report.tags())));
        }
        var horizon = HorizonReliability.of(cases);
        if (!horizon.isEmpty()) {
            sb.append("\n## Reliability by horizon\n\n");
            sb.append("| steps | cases | pass@1 | pass^k |\n|---|---|---|---|\n");
            for (HorizonReliability.Bucket bucket : horizon) {
                sb.append(String.format("| %s | %d | %.3f | %.3f |%n",
                    bucket.label(), bucket.cases(),
                    bucket.completion().passAt1(), bucket.completion().passHatK()));
            }
        }
        return sb.toString();
    }
}
