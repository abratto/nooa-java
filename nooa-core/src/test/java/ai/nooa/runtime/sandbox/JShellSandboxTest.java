package ai.nooa.runtime.sandbox;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.llm.FakeLLMClient;
import ai.nooa.llm.UnifiedLLM;
import org.junit.jupiter.api.*;


import static org.assertj.core.api.Assertions.*;

@DisplayName("JShellSandbox")
class JShellSandboxTest {

    static class TestAgent extends Agent {
        public TestAgent(UnifiedLLM llm) { super(llm); }
        @Generate public String generate(String x) {
            throw new UnsupportedOperationException();
        }
    }

    private JShellSandbox sandbox;

    @BeforeEach
    void setUp() {
        var agent = new TestAgent(new FakeLLMClient());
        sandbox = new JShellSandbox(agent, 5000);
    }

    @AfterEach
    void tearDown() {
        sandbox.close();
    }

    @Test
    @DisplayName("simple expression evaluation")
    void simpleExpression() {
        var result = sandbox.execute("int x = 1 + 2;");
        assertThat(result.success()).isTrue();
        assertThat(result.error()).isNull();
    }

    @Test
    @DisplayName("variable state persists across snippets")
    void statePersists() {
        var r1 = sandbox.execute("int counter = 10;");
        assertThat(r1.success()).isTrue();
        var r2 = sandbox.execute("counter = counter + 5;");
        assertThat(r2.success()).isTrue();
        var r3 = sandbox.execute("counter");
        assertThat(r3.success()).isTrue();
    }

    @Test
    @DisplayName("error on division by zero — but JShell may reject at parse time")
    void divisionByZero() {
        var result = sandbox.execute("int x = 1 / 0;");
        // JShell may detect this at compile time or runtime
        // Either way, it should NOT be a success
        if (result.success()) {
            // If JShell accepts it, the runtime should catch it
            // Some JShell versions handle this as a REJECTED snippet
        }
        assertThat(result.error() != null || !result.success())
            .as("Division by zero should produce an error")
            .isTrue();
    }

    @Test
    @DisplayName("returns value from expression")
    void returnsExpressionValue() {
        var result = sandbox.execute("42");
        assertThat(result.success()).isTrue();
    }

    @Test
    @DisplayName("blocks reflective API access")
    void blocksReflection() {
        var result = sandbox.execute(
            "java.lang.reflect.Field f = String.class.getDeclaredField(\"value\");");
        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("Blocked");
    }

    @Test
    @DisplayName("blocks File API")
    void blocksFileAccess() {
        var result = sandbox.execute("java.io.File f = new java.io.File(\"/etc/passwd\");");
        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("Blocked");
    }

    @Test
    @DisplayName("blocks ProcessBuilder")
    void blocksProcessBuilder() {
        var result = sandbox.execute(
            "java.lang.ProcessBuilder pb = new java.lang.ProcessBuilder(\"ls\");");
        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("Blocked");
    }

    @Test
    @DisplayName("close cleans up resources")
    void closeCleansUp() {
        sandbox.close();
        assertThatCode(sandbox::close).doesNotThrowAnyException();
    }
}
