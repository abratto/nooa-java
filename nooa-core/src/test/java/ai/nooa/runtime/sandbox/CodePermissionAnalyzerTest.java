package ai.nooa.runtime.sandbox;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.context.Event;
import ai.nooa.llm.FakeLLMClient;
import ai.nooa.llm.UnifiedLLM;
import ai.nooa.security.Permissions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CodePermissionAnalyzer (Stage 1)")
class CodePermissionAnalyzerTest {

    private static final Permissions DENY_ALL = new Permissions();

    static class TestAgent extends Agent {
        public TestAgent(UnifiedLLM llm) {
            super(llm);
        }

        @Generate
        public String generate(String x) {
            throw new UnsupportedOperationException();
        }
    }

    @Test
    void blocksQualifiedFileAccess() {
        var decision = CodePermissionAnalyzer.analyze(
            "java.io.File f = new java.io.File(\"/etc/passwd\");", DENY_ALL, null);
        assertThat(decision.allowed()).isFalse();
    }

    @Test
    void blocksSimpleFileAccess() {
        var decision = CodePermissionAnalyzer.analyze(
            "Files.readString(Path.of(\"/etc/passwd\"));", DENY_ALL, null);
        assertThat(decision.allowed()).isFalse();
    }

    @Test
    void blocksWildcardImport() {
        var decision = CodePermissionAnalyzer.analyze(
            "import java.io.*;", DENY_ALL, null);
        assertThat(decision.allowed()).isFalse();
    }

    @Test
    void blocksReflectionByMethodName() {
        var decision = CodePermissionAnalyzer.analyze(
            "String.class.getDeclaredField(\"value\");", DENY_ALL, null);
        assertThat(decision.allowed()).isFalse();
    }

    @Test
    void blocksClassForNameLiteral() {
        var decision = CodePermissionAnalyzer.analyze(
            "Class.forName(\"java.io.File\");", DENY_ALL, null);
        assertThat(decision.allowed()).isFalse();
    }

    @Test
    void blocksClassForNameBuiltFromPieces() {
        var decision = CodePermissionAnalyzer.analyze(
            "Class.forName(\"java.io.\" + \"File\");", DENY_ALL, null);
        assertThat(decision.allowed()).isFalse();
    }

    @Test
    void blocksNonLiteralPathArgument() {
        var decision = CodePermissionAnalyzer.analyze(
            "Files.readString(Path.of(target));", DENY_ALL, null);
        assertThat(decision.allowed()).isFalse();
    }

    @Test
    void skipsBlockedTextInsideCommentsAndPlainStrings() {
        // The word "java.io.File" in a comment or a plain string is not access.
        var decision = CodePermissionAnalyzer.analyze(
            "// java.io.File is mentioned here\nString s = \"java.io.File\"; returnResult(s);",
            DENY_ALL, null);
        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void allowsPlainComputation() {
        var decision = CodePermissionAnalyzer.analyze(
            "int x = 1 + 2; returnResult(x);", DENY_ALL, null);
        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void allowsFileWithinAllowedRoot() {
        Permissions perms = new Permissions().file("/tmp/**", Permissions.Level.ALLOW);
        var decision = CodePermissionAnalyzer.analyze(
            "Files.readString(Path.of(\"/tmp/note.txt\"));", perms, null);
        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void allowsReflectionWhenClassLoadPermitted() {
        Permissions perms = new Permissions()
            .classLoad("java.lang.reflect.**", Permissions.Level.ALLOW);
        var decision = CodePermissionAnalyzer.analyze(
            "java.lang.reflect.Field f = null;", perms, null);
        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void asksCallbackForDynamicResource() {
        Permissions perms = new Permissions();
        var denied = CodePermissionAnalyzer.analyze(
            "Files.readString(Path.of(target));", perms, (resource, detail) -> false);
        assertThat(denied.allowed()).isFalse();

        var approved = CodePermissionAnalyzer.analyze(
            "Files.readString(Path.of(target));", perms, (resource, detail) -> true);
        assertThat(approved.allowed()).isTrue();
    }

    @Test
    void emitsPermissionDecisionEvents() {
        var agent = new TestAgent(new FakeLLMClient());
        try (var sandbox = new JShellSandbox(agent, 5000)) {
            var result = sandbox.execute("new java.io.File(\"/etc/passwd\");");
            assertThat(result.success()).isFalse();
            assertThat(result.error()).contains("Blocked");
            assertThat(agent.eventManager().all())
                .anyMatch(event -> event instanceof Event.PermissionDecision decision
                    && decision.level().equals("DENY"));
        } finally {
            agent.close();
        }
    }
}
