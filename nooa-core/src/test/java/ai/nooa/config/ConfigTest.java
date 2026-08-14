package ai.nooa.config;

import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Configuration")
class ConfigTest {

    @Test
    @DisplayName("TruncationConfig defaults are reasonable")
    void truncationDefaults() {
        var config = TruncationConfig.defaults();
        assertThat(config.maxStdout()).isEqualTo(8192);
        assertThat(config.maxStderr()).isEqualTo(4096);
        assertThat(config.maxString()).isEqualTo(500);
        assertThat(config.maxDepth()).isEqualTo(4);
    }

    @Test
    @DisplayName("TruncationConfig merge with override")
    void truncationMerge() {
        var base = TruncationConfig.defaults();
        var override = new TruncationConfig(100, 200, 300, 10, 1000, 2);
        var merged = base.mergeWith(override);

        assertThat(merged.maxStdout()).isEqualTo(100);
        assertThat(merged.maxStderr()).isEqualTo(200);
        assertThat(merged.maxString()).isEqualTo(1000);
        assertThat(merged.maxLength()).isEqualTo(10);
        assertThat(merged.maxDepth()).isEqualTo(2);
    }

    @Test
    @DisplayName("TruncationConfig merge keeps base when override is default")
    void truncationMergeKeepsDefaults() {
        var base = new TruncationConfig(100, 200, 300, 10, 1000, 2);
        var defaults = TruncationConfig.defaults();
        var merged = base.mergeWith(defaults);

        // Override has default values, so base values are kept
        assertThat(merged.maxStdout()).isEqualTo(100);
        assertThat(merged.maxString()).isEqualTo(1000);
    }

    @Test
    @DisplayName("CodeActConfig defaults")
    void codeActDefaults() {
        var config = CodeActConfig.defaults();
        assertThat(config.maxIterations()).isEqualTo(50);
        assertThat(config.maxRetries()).isEqualTo(3);
        assertThat(config.cellTimeoutMillis()).isEqualTo(90_000);
        assertThat(config.allowTextFallback()).isTrue();
    }

    @Test
    @DisplayName("CodeActConfig builder")
    void codeActBuilder() {
        var config = CodeActConfig.builder()
            .maxIterations(10)
            .maxRetries(5)
            .cellTimeoutMillis(30_000)
            .allowTextFallback(false)
            .build();

        assertThat(config.maxIterations()).isEqualTo(10);
        assertThat(config.maxRetries()).isEqualTo(5);
        assertThat(config.cellTimeoutMillis()).isEqualTo(30_000);
        assertThat(config.allowTextFallback()).isFalse();
    }

    @Test
    @DisplayName("PredictConfig defaults")
    void predictDefaults() {
        var config = PredictConfig.defaults();
        assertThat(config.maxRetries()).isEqualTo(3);
    }

    @Test
    @DisplayName("ExecutionConfig defaults")
    void executionDefaults() {
        var config = ExecutionConfig.defaults();
        assertThat(config.maxNestingDepth()).isEqualTo(50);
    }

    @Test
    @DisplayName("AgentConfig defaults")
    void agentDefaults() {
        var config = AgentConfig.defaults();
        assertThat(config.maxNestingDepth()).isEqualTo(50);
        assertThat(config.enableTracing()).isTrue();
        assertThat(config.defaultStrategy()).isNotNull();
    }
}
