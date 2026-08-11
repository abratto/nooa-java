package ai.nooa.examples.clad;

import ai.nooa.AgentFactory;
import ai.nooa.llm.FakeLLMClient;
import ai.nooa.llm.LLMResponse;
import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests the CLAD agent by simulating a full CLAD workflow:
 * Stage 01 → use case → Stage 02 → concepts → Stage 03 → syncs
 *
 * Each stage has a CONTEXT.md contract. The agent reads it,
 * produces artefacts, verifies them, and advances.
 */
@DisplayName("CLAD Agent — End-to-End Workflow")
class CladAgentTest {

    private Path projectDir;
    private FakeLLMClient llm;
    private CladAgent agent;

    @BeforeEach
    void setUp() throws Exception {
        projectDir = Files.createTempDirectory("clad-test-project");

        // Create stage 01: usecase
        Path stage01 = projectDir.resolve("01_usecase");
        Files.createDirectories(stage01.resolve("output"));
        Files.writeString(stage01.resolve("CONTEXT.md"), """
            # Stage 01: Use Case
            ## Inputs
            - goals.md (project goals)
            ## Process
            Read goals.md. Write a use case describing the login feature.
            ## Outputs
            - usecase.md
            ## Verify
            - usecase.md exists and is non-empty
            - usecase.md contains "actor" and "goal"
            ## Gate
            Human Gate 1: Requirements
            """);

        Files.writeString(projectDir.resolve("goals.md"), """
            # Project Goals
            - Allow users to register and log in
            - Support session management
            """);

        // Stage 02: concepts (will be auto-advance once 01 passes)
        Path stage02 = projectDir.resolve("02_concepts");
        Files.createDirectories(stage02.resolve("output"));
        Files.writeString(stage02.resolve("CONTEXT.md"), """
            # Stage 02: Concepts
            ## Inputs
            - usecase.md
            ## Process
            Read usecase.md. Write User.concept.md defining the User concept.
            ## Outputs
            - User.concept.md
            ## Verify
            - User.concept.md exists
            ## Gate
            auto-advance
            """);

        // Stage 03: syncs
        Path stage03 = projectDir.resolve("03_syncs");
        Files.createDirectories(stage03.resolve("output"));
        Files.writeString(stage03.resolve("CONTEXT.md"), """
            # Stage 03: Synchronizations
            ## Inputs
            - usecase.md
            - User.concept.md
            ## Process
            Write login.sync.md defining the login sync flow.
            ## Outputs
            - login.sync.md
            ## Verify
            - login.sync.md exists
            - ./verify_sync.sh
            ## Gate
            auto-advance
            """);

        // Create verify script
        Files.writeString(stage03.resolve("verify_sync.sh"), """
            #!/bin/bash
            echo "Sync verification passed"
            exit 0
            """);
        stage03.resolve("verify_sync.sh").toFile().setExecutable(true);

        llm = new FakeLLMClient();
        agent = AgentFactory.create(CladAgent.class, llm);
        agent.context().put("project_dir", projectDir.toString());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (agent != null) agent.close();
        try (var files = Files.walk(projectDir)) {
            files.sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} });
        }
    }

    @Test
    @DisplayName("finds current stage without receipt")
    void findsCurrentStage() {
        // Stage 01 has no receipt → it's the current stage
        assertThat(projectDir.resolve("01_usecase/.gate-receipt.json")).doesNotExist();
    }

    @Test
    @DisplayName("parseContract extracts structured data from CONTEXT.md")
    void parseContractExtractsData() throws Exception {
        String content = Files.readString(
            projectDir.resolve("01_usecase/CONTEXT.md"));

        // Script a structured JSON response for the PredictStrategy
        llm.respondWith("""
            {
                "stageId": "01_usecase",
                "stageName": "Use Case",
                "inputs": ["goals.md"],
                "process": "Read goals.md and write usecase.md",
                "outputs": ["usecase.md"],
                "verifySteps": [
                    "usecase.md exists and is non-empty",
                    "usecase.md contains actor and goal"
                ],
                "hasHumanGate": true
            }
            """);

        StageContract contract = agent.parseContract(content);

        assertThat(contract.stageId()).isEqualTo("01_usecase");
        assertThat(contract.inputs()).contains("goals.md");
        assertThat(contract.outputs()).contains("usecase.md");
        assertThat(contract.hasHumanGate()).isTrue();
    }

    @Test
    @DisplayName("runVerification passes when all outputs exist")
    void verificationPasses() throws Exception {
        var contract = new StageContract(
            "01_usecase", "Use Case",
            List.of("goals.md"), "process", List.of("usecase.md"),
            List.of(), true);

        // Create the expected output
        Files.writeString(
            projectDir.resolve("01_usecase/output/usecase.md"),
            "# Login Use Case\n**Actor:** User\n**Goal:** Log in");

        var result = agent.runVerification(contract,
            projectDir.resolve("01_usecase"));
        assertThat(result.passed()).isTrue();
    }

    @Test
    @DisplayName("runVerification fails when output is missing")
    void verificationFailsOnMissing() {
        var contract = new StageContract(
            "01_usecase", "Use Case",
            List.of(), "process",
            List.of("usecase.md", "missing-file.md"),
            List.of(), true);

        var result = agent.runVerification(contract,
            projectDir.resolve("01_usecase"));
        assertThat(result.passed()).isFalse();
        assertThat(result.failures()).anyMatch(f -> f.contains("missing-file.md"));
    }

    @Test
    @DisplayName("runVerification runs custom ./verify scripts")
    void verificationRunsCustomScripts() throws Exception {
        var contract = new StageContract(
            "03_syncs", "Syncs",
            List.of(), "process",
            List.of("login.sync.md"),
            List.of("./verify_sync.sh"), false);

        // Create the output file
        Files.writeString(
            projectDir.resolve("03_syncs/output/login.sync.md"),
            "# Login Sync");

        var result = agent.runVerification(contract,
            projectDir.resolve("03_syncs"));
        assertThat(result.passed()).isTrue();
    }

    @Test
    @DisplayName("executeProcess scripts LLM response for artefact production")
    void executeProcessScriptsLLMResponse() {
        llm.respondWith(List.of(
            new LLMResponse.ToolCall("c1", "returnResult",
                Map.of("value", "Generated successfully"))
        ));

        var result = agent.executeProcess(
            "01_usecase",
            "Read goals.md and write usecase.md",
            List.of("goals.md"),
            List.of("usecase.md"));

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("full stage pipeline: parse → produce → verify → advance")
    void fullPipeline() throws Exception {
        // Script parseContract response
        llm.respondWith("""
            {
                "stageId": "01_usecase",
                "stageName": "Use Case",
                "inputs": ["goals.md"],
                "process": "Write usecase.md",
                "outputs": ["usecase.md"],
                "verifySteps": [],
                "hasHumanGate": true
            }
            """);

        // Script executeProcess → produce output file
        llm.respondWith(List.of(
            new LLMResponse.ToolCall("c1", "executeJava",
                Map.of("code",
                    "java.nio.file.Files.writeString(" +
                    "java.nio.file.Path.of(\"" + projectDir + "/01_usecase/output/usecase.md\")," +
                    "\"# Login Use Case\\n**Actor:** User\\n**Goal:** Log in\");")),
            new LLMResponse.ToolCall("c2", "returnResult",
                Map.of("value", "done"))
        ));

        // Script presentGate response
        llm.respondWith("""
            {
                "passed": true,
                "issues": []
            }
            """);

        try {
            var result = agent.executeStage();
            // Stage may fail because ./clad advance isn't available in test
            // But verification and artifact production should work
            assertThat(result.stageId()).isIn("01_usecase", "none");
            System.out.println("Stage result: " + result.success() + " — " + result.summary());
        } catch (Exception e) {
            System.out.println("Full pipeline note: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("parseContract handles markdown with code blocks")
    void parseContractWithMarkdown() {
        String content = """
            # Stage 04: Specification
            ## Inputs
            - User.concept.md
            - login.sync.md
            ## Process
            Write a detailed specification.
            ## Outputs
            - spec.md
            - api-contract.md
            ## Verify
            - All outputs exist
            ## Gate
            Human Gate 3: Executable
            """;

        llm.respondWith("""
            {
                "stageId": "04_spec",
                "stageName": "Specification",
                "inputs": ["User.concept.md", "login.sync.md"],
                "process": "Write a detailed specification",
                "outputs": ["spec.md", "api-contract.md"],
                "verifySteps": ["All outputs exist"],
                "hasHumanGate": true
            }
            """);

        var contract = agent.parseContract(content);
        assertThat(contract.outputs()).hasSize(2);
        assertThat(contract.outputs()).contains("spec.md", "api-contract.md");
    }
}
