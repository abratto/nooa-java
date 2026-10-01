package ai.nooa.eval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("eval (Phase 3 history)")
class EvalPhase3Test {

    @Test
    void historyStoreRecordsQueriesAndReloadsReports(@TempDir Path dir) {
        EvalReport report = sampleReport();
        Path db = dir.resolve("history.db");

        try (EvalHistoryStore store = new EvalHistoryStore(db.toString())) {
            long id = store.record(report);
            assertThat(id).isPositive();

            assertThat(store.recent("smoke", 10)).hasSize(1);
            EvalHistoryStore.RunSummary summary = store.recent("smoke", 10).get(0);
            assertThat(summary.datasetId()).isEqualTo("smoke");
            assertThat(summary.weightedMean()).isEqualTo(1.0);
            assertThat(summary.passHatK()).isEqualTo(1.0);

            EvalReport loaded = store.load(id).orElseThrow();
            assertThat(loaded.cases()).hasSize(1);
            assertThat(loaded.cases().get(0).caseId()).isEqualTo("c1");
            assertThat(loaded.cases().get(0).scores().get(0).scorer()).isEqualTo("ExactMatch");
            assertThat(loaded.aggregate().weightedMean()).isEqualTo(1.0);
        }

        // Reopening the store preserves history.
        try (EvalHistoryStore reopened = new EvalHistoryStore(db.toString())) {
            assertThat(reopened.recent("smoke", 10)).hasSize(1);
            assertThat(reopened.recent("other", 10)).isEmpty();
        }
    }

    private static EvalReport sampleReport() {
        CompletionMetrics completion = new CompletionMetrics(1.0, 1.0, 1.0, 0.0);
        EvalReport.CaseReport caseReport = new EvalReport.CaseReport(
            "c1", Set.of("canonical"),
            List.of(Score.of("ExactMatch", true, "matched")),
            1.0, GoalVerifier.Completion.unscored(), completion,
            List.of(true), "out", 1);
        EvalReport.Aggregate aggregate = new EvalReport.Aggregate(
            Map.of("ExactMatch", 1.0), 1.0, completion);
        return new EvalReport(Instant.now().toString(), "ai.nooa.TestAgent",
            "smoke", "LIVE", 1, List.of(caseReport), aggregate);
    }
}
