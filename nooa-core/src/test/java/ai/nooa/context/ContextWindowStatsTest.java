package ai.nooa.context;

import ai.nooa.llm.LLMResponse;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ContextWindowStats")
class ContextWindowStatsTest {

    @Test
    @DisplayName("empty starts at zero")
    void emptyStartsAtZero() {
        var stats = ContextWindowStats.empty();
        assertThat(stats.totalTokens()).isZero();
        assertThat(stats.overallUtilizationPercent()).isZero();
    }

    @Test
    @DisplayName("accumulate merges usage correctly")
    void accumulateMerges() {
        var stats = ContextWindowStats.empty();
        var usage = new LLMResponse.Usage(100, 50, 150);

        stats = stats.accumulate(usage, 500, 1000);
        assertThat(stats.totalTokens()).isEqualTo(150);
        assertThat(stats.promptTokens()).isEqualTo(100);
        assertThat(stats.completionTokens()).isEqualTo(50);
        assertThat(stats.contextBlocksChars()).isEqualTo(500);
        assertThat(stats.eventsChars()).isEqualTo(1000);
    }

    @Test
    @DisplayName("utilization percentage scales correctly")
    void utilizationPercentage() {
        var stats = ContextWindowStats.empty();
        // 50k tokens + 100k chars (≈25k token equivalents)
        stats = stats.accumulate(new LLMResponse.Usage(50000, 0, 50000), 100000, 0);
        assertThat(stats.overallUtilizationPercent()).isGreaterThan(20);

        // 190k tokens → near limit
        stats = stats.accumulate(new LLMResponse.Usage(140000, 0, 190000), 0, 0);
        assertThat(stats.overallUtilizationPercent()).isGreaterThan(85);
    }

    @Test
    @DisplayName("summary is human-readable")
    void summaryReadable() {
        var stats = ContextWindowStats.empty()
            .accumulate(new LLMResponse.Usage(100, 50, 150), 500, 300);
        String summary = stats.summary();
        assertThat(summary).contains("150").contains("500").contains("300");
    }
}
