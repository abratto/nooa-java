package ai.nooa.tools;

import org.junit.jupiter.api.*;

import ai.nooa.tools.ShellTools;
import ai.nooa.security.Permissions;
import org.junit.jupiter.api.*;

import java.nio.file.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ShellTools")
class ShellToolsTest {

    private Path workspace;
    private ShellTools shell;

    @BeforeEach
    void setUp() throws Exception {
        workspace = Files.createTempDirectory("nooa-shell-test");
        shell = new ShellTools(workspace,
            Permissions.allowAll(), null); // allow all for testing
    }

    @AfterEach
    void tearDown() throws Exception {
        shell.close();
        Files.walk(workspace)
            .sorted(java.util.Comparator.reverseOrder())
            .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} });
    }

    @Test
    @DisplayName("run executes shell commands")
    void runExecutesCommand() {
        var result = shell.run("echo hello");
        assertThat(result.stdout()).contains("hello");
        assertThat(result.exitCode()).isEqualTo(0);
    }

    @Test
    @DisplayName("run captures stderr")
    void runCapturesStderr() {
        var result = shell.run("echo error >&2");
        assertThat(result.stderr()).contains("error");
    }

    @Test
    @DisplayName("writeFile and read round-trip")
    void writeAndRead() {
        shell.writeFile("src/test.txt", "hello, world");
        String content = shell.read("src/test.txt");
        assertThat(content).isEqualTo("hello, world");
    }

    @Test
    @DisplayName("view truncates long files")
    void viewTruncates() {
        var sb = new StringBuilder();
        for (int i = 0; i < 500; i++) sb.append("line " + i + " abcdefghijklmnopqrstuvwxyz\n");
        shell.writeFile("big.txt", sb.toString());
        String viewed = shell.view("big.txt");
        assertThat(viewed.length()).isLessThan(sb.length());
        assertThat(viewed).contains("truncated");
    }

    @Test
    @DisplayName("path escape attempts are blocked")
    void pathEscapeBlocked() {
        String content = shell.read("../../etc/passwd");
        assertThat(content).contains("ERROR");
    }
}
