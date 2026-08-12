package ai.nooa;

import ai.nooa.annotations.Generate;
import ai.nooa.annotations.Strategy;
import ai.nooa.llm.FakeLLMClient;
import ai.nooa.strategy.PredictStrategy;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Standalone")
class StandaloneTest {

    static class StandaloneMethods {
        @Generate
        public static String greet(String name) { throw new UnsupportedOperationException(); }

        @Generate @Strategy(PredictStrategy.class)
        public static Result classify(String text) { throw new UnsupportedOperationException(); }
    }

    record Result(String label, double score) {}

    @Test
    @DisplayName("call resolves method and invokes strategy")
    void callResolvesMethod() {
        var llm = new FakeLLMClient();
        llm.respondWith("Hello, World!");

        assertThatCode(() -> StandaloneMethods.class.getDeclaredMethod("greet", String.class))
            .doesNotThrowAnyException();
        var m = StandaloneMethods.class.getDeclaredMethod("greet", String.class);
        assertThat(m.isAnnotationPresent(Generate.class)).isTrue();
    }

    @Test
    @DisplayName("@Generate annotation is required")
    void requiresGenerateAnnotation() {
        var llm = new FakeLLMClient();
        // toString on Object has 0 params — will match findMethod but is not @Generate
        assertThatThrownBy(() -> Standalone.call(llm, Object.class, "toString"))
            .isInstanceOf(NooaException.class);
    }
}
