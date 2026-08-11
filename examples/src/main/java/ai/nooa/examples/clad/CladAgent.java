package ai.nooa.examples.clad;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.annotations.Hidden;
import ai.nooa.annotations.Strategy;
import ai.nooa.annotations.SystemPrompt;
import ai.nooa.llm.UnifiedLLM;
import ai.nooa.security.Permissions;
import ai.nooa.strategy.PredictStrategy;
import ai.nooa.tools.ShellTools;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

// =========================================================================
// CLAD (Contract-Led, Artefact-Driven) Agent
// =========================================================================
//
// This agent uses the NOOA SDK to implement the CLAD methodology.
// Each CLAD stage has a CONTEXT.md contract specifying inputs, a process,
// outputs, and verification steps. The agent reads the contract, produces
// artefacts, verifies them, and advances through quality gates.
//
// === Architecture ===
//
//   readContract()  →  produceArtefacts()  →  verifyStage()  →  advance()
//   (PredictStrategy)  (CodeActStrategy)       (deterministic)   (ShellTools)
//
// The agent is an ORCHESTRATOR — pure Java workflow that calls @Generate
// methods for LLM-powered tasks:
//   - parseContract(): structured extraction from CONTEXT.md
//   - executeProcess(): runs the stage's process step using tools
//   - produceOutput(): generates artefact files
//   - verifyPass(): decides if self-audit checks pass
//
// === Usage ===
//
//   var agent = AgentFactory.create(CladAgent.class, llm);
//   agent.context().put("project_dir", "/path/to/clad/project");
//   agent.context().put("feature_name", "login");
//   agent.executeStage();  // runs current stage
//   agent.executeAllStages();  // runs all pending stages
// =========================================================================

@SystemPrompt("""
    You are a CLAD methodology agent. You follow contract-led, artefact-driven
    development: every change has a contract, every contract produces an artefact.
    You read CONTEXT.md files, produce output files, run verification, and
    advance through quality gates. Be precise and follow the contract exactly.
    """)
public class CladAgent extends Agent {

    @Hidden private ShellTools shell;
    @Hidden private Path projectDir;

    public CladAgent(UnifiedLLM llm) {
        super(llm);
    }

    // ---- Stage 1: Parse Contract (PredictStrategy) ----

    /**
     * Parse a CONTEXT.md file into a structured StageContract.
     * This is a classification/extraction task — use PredictStrategy
     * for structured output.
     */
    @Generate @Strategy(PredictStrategy.class)
    public StageContract parseContract(String contextMdContent) {
        throw new UnsupportedOperationException("Generated at runtime");
    }

    // ---- Stage 2: Execute Process (CodeActStrategy) ----

    /**
     * Execute the process step of a CONTEXT.md contract. The LLM has access
     * to shell tools (run commands), file I/O (read/write), and memory
     * (recall past artefacts). It should follow the contract's process
     * description and produce the required output files.
     */
    @Generate
    public String executeProcess(
        String stageId,
        String processDescription,
        List<String> inputs,
        List<String> outputs
    ) {
        throw new UnsupportedOperationException("Generated at runtime");
    }

    // ---- Stage 3: Verify (Deterministic) ----

    /**
     * Run the verification steps from the CONTEXT.md contract.
     * This is deterministic — no LLM call needed.
     */
    public VerificationResult runVerification(
        StageContract contract,
        Path stageDir
    ) {
        if (shell == null) shell = new ShellTools(stageDir,
            ai.nooa.security.Permissions.allowAll(), null);
        Path outputDir = stageDir.resolve("output");
        List<String> checks = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        // Check 1: All required outputs exist
        for (String output : contract.outputs()) {
            String filename = output.trim();
            Path file = outputDir.resolve(filename);
            checks.add("Output exists: " + filename);
            if (!Files.exists(file)) {
                failures.add("MISSING: " + filename);
            } else {
                try {
                    String content = Files.readString(file);
                    if (content.isBlank()) {
                        failures.add("EMPTY: " + filename);
                    }
                } catch (IOException e) {
                    failures.add("UNREADABLE: " + filename);
                }
            }
        }

        // Check 2: Stage-specific verify steps from contract
        for (String step : contract.verifySteps()) {
            checks.add("Verify: " + step.trim());
            // Custom verification runs via shell if step starts with "./"
            if (step.trim().startsWith("./")) {
                String cmd = step.trim();
                var result = shell.run("cd " + stageDir + " && " + cmd);
                if (!result.success()) {
                    failures.add("FAILED: " + cmd + " → " + result.stderr());
                }
            }
        }

        return failures.isEmpty()
            ? VerificationResult.pass(checks)
            : VerificationResult.fail(checks, failures);
    }

    // ---- Stage 4: Auto-Verify (PredictStrategy) ----

    /**
     * Read output files and decide if they satisfy the contract.
     * Used when verify steps are qualitative (not just file existence).
     */
    @Generate @Strategy(PredictStrategy.class)
    public AutoVerifyResult autoVerify(
        StageContract contract,
        Map<String, String> outputFiles
    ) {
        throw new UnsupportedOperationException("Generated at runtime");
    }

    // ---- Gate Progression ----

    /**
     * Run ./clad advance to progress through the quality gate.
     * Returns the result and next stage path if advance succeeds.
     */
    public AdvanceResult advanceGate(Path projectDir) {
        var result = shell.run("./clad advance", 30);
        String output = result.stdout();

        if (result.exitCode() == 10) {
            // Human gate — extract summary
            String summary = extractBetween(output, "GATE:", "\n---");
            return new AdvanceResult(true, null, summary, true);
        }
        if (result.exitCode() == 0) {
            // Auto-advance — extract next stage
            String nextStage = extractNextStage(output);
            return new AdvanceResult(true, nextStage, output, false);
        }
        // Failed
        return new AdvanceResult(false, null, result.stderr(), false);
    }

    /**
     * Present artefacts for human review at a gate.
     * Generates a summary of what was produced.
     */
    @Generate
    public String presentGate(
        String stageId,
        List<String> artefacts,
        VerificationResult verification
    ) {
        throw new UnsupportedOperationException("Generated at runtime");
    }

    // ---- Orchestrators (Pure Java) ----

    /**
     * Execute a single CLAD stage: read contract → produce → verify → advance.
     */
    public StageResult executeStage() throws IOException {
        if (projectDir == null) {
            projectDir = Path.of(System.getProperty("user.dir"));
        }

        // 1. Find current stage
        Path currentStage = findCurrentStage(projectDir);
        if (currentStage == null) {
            return new StageResult("none", false, "No pending stages found", List.of());
        }

        String stageId = currentStage.getFileName().toString();
        context().put("stage", stageId);
        context().put("stage_dir", currentStage.toString());

        // 2. Read CONTEXT.md
        Path contextMd = currentStage.resolve("CONTEXT.md");
        if (!Files.exists(contextMd)) {
            return new StageResult(stageId, false,
                "CONTEXT.md not found in " + currentStage, List.of());
        }

        String content = Files.readString(contextMd);
        StageContract contract = parseContract(content);

        System.out.println("=== " + contract + " ===");

        // 3. Produce artefacts (LLM-powered)
        String processResult = executeProcess(
            contract.stageId(),
            contract.process(),
            contract.inputs(),
            contract.outputs()
        );
        context().put("process_result", processResult);

        // 4. Collect produced files
        Path outputDir = currentStage.resolve("output");
        Files.createDirectories(outputDir);
        List<String> producedFiles = contract.outputs().stream()
            .map(f -> outputDir.resolve(f.trim()))
            .filter(Files::exists)
            .map(Path::toString)
            .collect(Collectors.toList());

        // 5. Verify
        VerificationResult verification = runVerification(contract, currentStage);
        if (!verification.passed()) {
            System.out.println("Verification FAILED: " + verification.failures());
            return new StageResult(stageId, false,
                "Verification failed: " + verification.failures(), producedFiles);
        }

        // 6. Advance
        AdvanceResult advance = advanceGate(projectDir);
        if (advance.isHumanGate()) {
            String presentation = presentGate(stageId, producedFiles, verification);
            System.out.println("\n[HUMAN GATE] " + contract.stageName());
            System.out.println(presentation);
            System.out.println("\nReview artefacts and run: ./clad approve-gate");
            return new StageResult(stageId, true,
                "Gate awaiting human approval: " + advance.summary(), producedFiles);
        }

        if (!advance.passed()) {
            return new StageResult(stageId, false,
                "Advance failed: " + advance.summary(), producedFiles);
        }

        System.out.println("Stage " + stageId + " complete → " + advance.nextStage());
        return new StageResult(stageId, true, advance.summary(), producedFiles);
    }

    /** Execute all pending stages in sequence. */
    public List<StageResult> executeAllStages() throws IOException {
        List<StageResult> results = new ArrayList<>();
        if (projectDir == null) {
            projectDir = Path.of(System.getProperty("user.dir"));
        }

        // Find stages directory
        Path featuresDir = projectDir.resolve("features");
        if (!Files.exists(featuresDir)) {
            System.out.println("No features/ directory found — running standalone stages");
            featuresDir = projectDir;
        }

        while (true) {
            Path current = findCurrentStage(featuresDir);
            if (current == null) break;

            var result = executeStage();
            results.add(result);

            if (!result.success()) {
                System.out.println("Stopping at " + result.stageId() + ": " + result.summary());
                break;
            }

            // Check if human gate — stop for approval
            if (result.summary().contains("human approval")) break;
        }

        return results;
    }

    // ---- Helpers ----

    private Path findCurrentStage(Path projectDir) {
        // Find the first stage directory without a .gate-receipt.json
        try (var dirs = Files.list(projectDir)) {
            return dirs
                .filter(Files::isDirectory)
                .filter(d -> {
                    String name = d.getFileName().toString();
                    return name.matches("\\d+.*"); // starts with number
                })
                .filter(d -> !Files.exists(d.resolve(".gate-receipt.json")))
                .sorted()
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private String extractBetween(String text, String start, String end) {
        int s = text.indexOf(start);
        if (s < 0) return text;
        s += start.length();
        int e = text.indexOf(end, s);
        return e > s ? text.substring(s, e).strip() : text.substring(s).strip();
    }

    private String extractNextStage(String advanceOutput) {
        for (String line : advanceOutput.split("\n")) {
            if (line.contains("NEXT_STAGE:")) {
                return line.substring(line.indexOf(":") + 1).strip();
            }
        }
        return advanceOutput;
    }

    @Override
    public void close() {
        if (shell != null) shell.close();
        super.close();
    }
}
