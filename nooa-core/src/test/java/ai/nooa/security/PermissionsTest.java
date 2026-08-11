package ai.nooa.security;

import org.junit.jupiter.api.*;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Permissions")
class PermissionsTest {

    @Test
    @DisplayName("default denies everything")
    void defaultDeniesAll() {
        var perms = new Permissions();
        assertThat(perms.checkFile("/tmp/test.txt")).isEqualTo(Permissions.Level.DENY);
        assertThat(perms.checkCommand("ls -la")).isEqualTo(Permissions.Level.DENY);
        assertThat(perms.checkUrl("https://example.com")).isEqualTo(Permissions.Level.DENY);
        assertThat(perms.checkClassLoad("java.util.ArrayList")).isEqualTo(Permissions.Level.DENY);
    }

    @Test
    @DisplayName("explicit ALLOW permits resource")
    void allowPermits() {
        var perms = new Permissions()
            .file("/tmp/**", Permissions.Level.ALLOW)
            .command("ls *", Permissions.Level.ALLOW);

        assertThat(perms.checkFile("/tmp/test.txt")).isEqualTo(Permissions.Level.ALLOW);
        assertThat(perms.checkFile("/tmp/sub/deep/file.java")).isEqualTo(Permissions.Level.ALLOW);
        assertThat(perms.checkFile("/etc/passwd")).isEqualTo(Permissions.Level.DENY);

        assertThat(perms.checkCommand("ls -la")).isEqualTo(Permissions.Level.ALLOW);
        assertThat(perms.checkCommand("rm -rf /")).isEqualTo(Permissions.Level.DENY);
    }

    @Test
    @DisplayName("ASK returns to callback and respects decision")
    void askReturnsCorrectLevel() {
        var perms = new Permissions()
            .command("rm *", Permissions.Level.ASK);

        assertThat(perms.checkCommand("rm -rf /tmp/test")).isEqualTo(Permissions.Level.ASK);
    }

    @Test
    @DisplayName("last matching rule wins — narrow after broad")
    void lastRuleWins() {
        var perms = new Permissions()
            .file("**", Permissions.Level.DENY)               // broad deny
            .file("/tmp/**", Permissions.Level.ALLOW)         // narrow allow
            .file("/tmp/secrets/**", Permissions.Level.DENY); // narrower deny

        assertThat(perms.checkFile("/tmp/data.txt")).isEqualTo(Permissions.Level.ALLOW);
        assertThat(perms.checkFile("/tmp/secrets/key.txt")).isEqualTo(Permissions.Level.DENY);
        assertThat(perms.checkFile("/etc/passwd")).isEqualTo(Permissions.Level.DENY);
    }

    @Test
    @DisplayName("URL patterns — narrow rules override broad")
    void urlPatterns() {
        var perms = new Permissions()
            .url("*", Permissions.Level.DENY)              // broad: deny all
            .url("https://api.example.com/**", Permissions.Level.ALLOW); // narrow: allow

        assertThat(perms.checkUrl("https://api.example.com/v1/users")).isEqualTo(Permissions.Level.ALLOW);
        assertThat(perms.checkUrl("https://evil.com")).isEqualTo(Permissions.Level.DENY);
    }

    @Test
    @DisplayName("allowAll convenience method")
    void allowAll() {
        var perms = Permissions.allowAll();
        assertThat(perms.checkFile("/anything")).isEqualTo(Permissions.Level.ALLOW);
        assertThat(perms.checkCommand("anything")).isEqualTo(Permissions.Level.ALLOW);
        assertThat(perms.checkUrl("https://anything")).isEqualTo(Permissions.Level.ALLOW);
    }

    @Test
    @DisplayName("command glob patterns — narrow rules override broad ones")
    void commandGlobs() {
        var perms = new Permissions()
            .command("*", Permissions.Level.DENY)        // broad: deny all
            .command("git *", Permissions.Level.ALLOW)   // narrow: allow git
            .command("docker *", Permissions.Level.ASK); // narrow: ask for docker

        assertThat(perms.checkCommand("git status")).isEqualTo(Permissions.Level.ALLOW);
        assertThat(perms.checkCommand("git push origin main")).isEqualTo(Permissions.Level.ALLOW);
        assertThat(perms.checkCommand("docker ps")).isEqualTo(Permissions.Level.ASK);
        assertThat(perms.checkCommand("rm -rf /")).isEqualTo(Permissions.Level.DENY);
    }

    @Test
    @DisplayName("class load patterns work")
    void classLoadPatterns() {
        var perms = new Permissions()
            .classLoad("java.util.*", Permissions.Level.ALLOW)
            .classLoad("java.lang.reflect.*", Permissions.Level.DENY);

        assertThat(perms.checkClassLoad("java.util.ArrayList")).isEqualTo(Permissions.Level.ALLOW);
        assertThat(perms.checkClassLoad("java.lang.reflect.Field")).isEqualTo(Permissions.Level.DENY);
    }

    @Test
    @DisplayName("file check normalizes relative paths")
    void fileCheckNormalizes() {
        var perms = new Permissions()
            .file("/home/user/**", Permissions.Level.ALLOW);

        assertThat(perms.checkFile(Path.of("/home/user/../user/docs/file.txt")))
            .isEqualTo(Permissions.Level.ALLOW);
    }
}
