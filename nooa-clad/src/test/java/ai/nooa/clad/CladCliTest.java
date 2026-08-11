package ai.nooa.clad;

import org.junit.jupiter.api.*;

import java.nio.file.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CLAD CLI")
class CladCliTest {

    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("clad-test");
    }

    @AfterEach
    void tearDown() throws Exception {
        try (var files = Files.walk(tempDir)) {
            files.sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} });
        }
    }

    @Test
    @DisplayName("init creates project directory structure")
    void initCreatesStructure() throws Exception {
        ProjectScaffolder scaffolder = new ProjectScaffolder();
        Path projectDir = tempDir.resolve("test-project");
        scaffolder.scaffold(projectDir, "test-project");

        assertThat(projectDir).exists();
        assertThat(projectDir.resolve("README.md")).exists();
        assertThat(projectDir.resolve("pom.xml")).exists();
        assertThat(projectDir.resolve("CLAUDE.md")).exists();

        // methodology dir may exist if clad submodule is present
        if (Files.exists(Path.of("clad/methodology"))) {
            assertThat(projectDir.resolve("methodology")).exists();
        }

        // features/_system exists
        assertThat(projectDir.resolve("features/_system")).exists();
    }

    @Test
    @DisplayName("init creates valid pom.xml with runtime dependency")
    void initCreatesPomXml() throws Exception {
        ProjectScaffolder scaffolder = new ProjectScaffolder();
        Path projectDir = tempDir.resolve("test-pom");
        scaffolder.scaffold(projectDir, "test-pom");

        String pom = Files.readString(projectDir.resolve("pom.xml"));
        assertThat(pom).contains("nooa-clad-runtime");
        assertThat(pom).contains("test-pom");
        assertThat(pom).contains("maven.compiler.release");
    }

    @Test
    @DisplayName("init creates README with getting started instructions")
    void initCreatesReadme() throws Exception {
        ProjectScaffolder scaffolder = new ProjectScaffolder();
        Path projectDir = tempDir.resolve("test-readme");
        scaffolder.scaffold(projectDir, "test-readme");

        String readme = Files.readString(projectDir.resolve("README.md"));
        assertThat(readme).contains("test-readme");
        assertThat(readme).contains("nooa clad run");
        assertThat(readme).contains("Getting Started");
    }

    @Test
    @DisplayName("findCurrentStage returns first pending stage")
    void findCurrentStage() throws Exception {
        // Create a minimal CLAD project structure
        Path featuresDir = tempDir.resolve("features/_system/stages");
        Files.createDirectories(featuresDir);

        Path stage00 = featuresDir.resolve("00_actor-goal");
        Files.createDirectories(stage00);
        Files.writeString(stage00.resolve("CONTEXT.md"), "# Stage 00");

        Path stage01 = featuresDir.resolve("01_usecase");
        Files.createDirectories(stage01);
        Files.writeString(stage01.resolve("CONTEXT.md"), "# Stage 01");

        // Stage 00 has receipt → complete. Stage 01 has no receipt → pending.
        Files.writeString(stage00.resolve(".gate-receipt.json"), "{}");

        var llm = ai.nooa.llm.UnifiedLLM.create(
            ai.nooa.llm.UnifiedLLM.openAI("sk-test", "gpt-4o").build());
        var agent = ai.nooa.AgentFactory.create(CladAgent.class, llm);

        Path current = agent.findCurrentStage(tempDir);
        assertThat(current).isNotNull();
        assertThat(current.getFileName().toString()).isEqualTo("01_usecase");
        agent.close();
    }

    @Test
    @DisplayName("findCurrentStage returns null when all complete")
    void allStagesComplete() throws Exception {
        Path featuresDir = tempDir.resolve("features/_system/stages");
        Files.createDirectories(featuresDir);

        Path stage00 = featuresDir.resolve("00_actor-goal");
        Files.createDirectories(stage00);
        Files.writeString(stage00.resolve("CONTEXT.md"), "# Stage 00");
        Files.writeString(stage00.resolve(".gate-receipt.json"), "{}");

        var llm = ai.nooa.llm.UnifiedLLM.create(
            ai.nooa.llm.UnifiedLLM.openAI("sk-test", "gpt-4o").build());
        var agent = ai.nooa.AgentFactory.create(CladAgent.class, llm);

        Path current = agent.findCurrentStage(tempDir);
        assertThat(current).isNull();
        agent.close();
    }
}
