package ai.nooa.runtime.sandbox;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.llm.FakeLLMClient;
import ai.nooa.llm.UnifiedLLM;
import ai.nooa.security.Permissions;
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
    @DisplayName("binds typed inputs and returns explicit values")
    void bindsInputsAndReturnsExplicitValues() {
        sandbox.bindVariable("input", "String", "hello");

        var result = sandbox.execute("returnResult(input.toUpperCase());");

        assertThat(result.success()).isTrue();
        assertThat(result.explicitReturn()).isTrue();
        assertThat(result.returnValue()).isEqualTo("HELLO");
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
    @DisplayName("timeout returns without waiting for the worker")
    void timeoutReturnsPromptly() {
        sandbox.close();
        var agent = new TestAgent(new FakeLLMClient());
        sandbox = new JShellSandbox(agent, 50);

        long startedAt = System.nanoTime();
        var result = sandbox.execute("Thread.sleep(1000);");
        long elapsedMillis = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
            System.nanoTime() - startedAt);

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("timed out");
        assertThat(elapsedMillis).isLessThan(500);
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
    @DisplayName("permission rules can explicitly allow a blocked file API")
    void allowsFileApiWhenPermissionIsGranted() {
        var agent = new TestAgent(new FakeLLMClient());
        agent.setPermissions(new Permissions()
            .file("/tmp/**", Permissions.Level.ALLOW));
        sandbox.close();
        sandbox = new JShellSandbox(agent, 5000);

        var result = sandbox.execute("new java.io.File(\"/tmp/nooa-test\");");

        assertThat(result.success()).isTrue();
        assertThat(result.error()).isNull();
        agent.close();
    }

    @Test
    @DisplayName("captured output is bounded by a byte cap")
    void capturedOutputIsBounded() {
        var capture = new JShellSandbox.CappedOutputStream(8);
        capture.write(new byte[20], 0, 20);
        assertThat(capture.truncated()).isTrue();
        assertThat(capture.toString()).hasSize(8);
    }

    @Test
    @DisplayName("close cleans up resources")
    void closeCleansUp() {
        SandboxContext.setAgent(new TestAgent(new FakeLLMClient()));
        SandboxContext.setVariable("stale", "value");
        SandboxContext.setReturnValue("stale");
        sandbox.close();
        assertThatCode(sandbox::close).doesNotThrowAnyException();
        assertThat(SandboxContext.getAgent()).isNull();
        assertThat(SandboxContext.getVariable("stale")).isNull();
        assertThat(SandboxContext.consumeReturnValue()).isNull();
    }
}
