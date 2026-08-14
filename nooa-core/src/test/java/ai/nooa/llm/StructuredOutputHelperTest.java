package ai.nooa.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("StructuredOutputHelper")
class StructuredOutputHelperTest {

    record IntakeResult(String category, String urgency, String nextStep, String summary) {}

    @Test
    @DisplayName("retries until valid structured JSON is returned")
    void retriesUntilValidJsonIsReturned() {
        FakeLLMClient llm = new FakeLLMClient();
        llm.respondWith("The user has sent the same command 'handle(String message)' twice.");
        llm.respondWith("{\"category\":\"housing\",\"urgency\":\"urgent\",\"nextStep\":\"connect to housing specialist\",\"summary\":\"Heating has been broken for months.\"}");

        StructuredOutputHelper helper = new StructuredOutputHelper(3);
        IntakeResult result = helper.extract(
            List.of(Message.user("Help classify this legal intake.")),
            IntakeResult.class,
            llm,
            Map.of("temperature", 0.2)
        );

        assertThat(result.category()).isEqualTo("housing");
        assertThat(result.urgency()).isEqualTo("urgent");
        assertThat(result.nextStep()).isEqualTo("connect to housing specialist");
        assertThat(result.summary()).contains("Heating");
    }

    @Test
    @DisplayName("rejects responses missing required values")
    void rejectsMissingRequiredValues() {
        FakeLLMClient llm = new FakeLLMClient();
        llm.respondWith("{\"category\":\"housing\"}");
        llm.respondWith("{\"category\":\"housing\",\"urgency\":\"low\",\"nextStep\":\"connect to housing specialist\",\"summary\":\"This is a valid retry.\"}");

        StructuredOutputHelper helper = new StructuredOutputHelper(2);

        IntakeResult result = helper.extract(
            List.of(Message.user("Help classify this legal intake.")),
            IntakeResult.class,
            llm,
            Map.of("temperature", 0.2)
        );

        assertThat(result.category()).isEqualTo("housing");
        assertThat(result.summary()).contains("valid retry");
    }

    @Test
    @DisplayName("throws when valid structured output is never produced")
    void throwsWhenValidOutputNeverProduced() {
        FakeLLMClient llm = new FakeLLMClient();
        llm.respondWith("This is not JSON");
        llm.respondWith("This is still not JSON");

        StructuredOutputHelper helper = new StructuredOutputHelper(2);

        assertThatThrownBy(() -> helper.extract(
            List.of(Message.user("Help classify this legal intake.")),
            IntakeResult.class,
            llm,
            Map.of("temperature", 0.2)
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Structured output");
    }
}
