package ai.nooa.llm;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UnifiedLLMStructuredOutputTest {

    record Report(String title, String summary) {}

    private boolean wantsStructuredOutput(java.lang.reflect.Type type, List<Tool> tools) throws Exception {
        Method method = UnifiedLLM.class.getDeclaredMethod(
            "wantsStructuredOutput", java.lang.reflect.Type.class, List.class);
        method.setAccessible(true);
        return (boolean) method.invoke(
            UnifiedLLM.create(UnifiedLLM.openAI("fake", "fake-model").build()),
            type, tools);
    }

    @Test
    void recordTargetsRequestStructuredOutput() throws Exception {
        assertThat(wantsStructuredOutput(Report.class, List.of())).isTrue();
    }

    @Test
    void objectTargetsRequestStructuredOutput() throws Exception {
        // Object is a generic JSON-shaped target; constraining it is correct.
        assertThat(wantsStructuredOutput(Object.class, List.of())).isTrue();
    }

    @Test
    void stringTargetsDoNotForceJson() throws Exception {
        assertThat(wantsStructuredOutput(String.class, List.of())).isFalse();
        assertThat(wantsStructuredOutput(CharSequence.class, List.of())).isFalse();
    }

    @Test
    void primitiveAndWrapperTargetsDoNotForceJson() throws Exception {
        assertThat(wantsStructuredOutput(int.class, List.of())).isFalse();
        assertThat(wantsStructuredOutput(Double.class, List.of())).isFalse();
        assertThat(wantsStructuredOutput(Boolean.class, List.of())).isFalse();
    }

    @Test
    void nullTargetsDoNotForceJson() throws Exception {
        assertThat(wantsStructuredOutput(null, List.of())).isFalse();
    }

    @Test
    void toolCallingRequestsNeverForceJson() throws Exception {
        var tool = Tool.builder().name("t").description("d").parameter("a", "string", "a").build();
        assertThat(wantsStructuredOutput(Report.class, List.of(tool))).isFalse();
        assertThat(wantsStructuredOutput(String.class, List.of(tool))).isFalse();
    }
}
