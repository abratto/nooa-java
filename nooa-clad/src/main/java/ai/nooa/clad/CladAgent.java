package ai.nooa.clad;

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

/**
 * CLAD methodology agent — reads CONTEXT.md contracts, produces artefacts,
 * verifies them, and advances through quality gates.
 *
 * <p>Adapted for real CLAD project structure with nested feature directories
 * and RESUME.md-based progression.</p>
 */
@SystemPrompt("""
    You are a CLAD methodology agent. You follow contract-led, artefact-driven
    development. Every change has a contract, every contract produces an artefact.
    You read CONTEXT.md files, produce output files, run verification, and
    respect quality gates. Be precise and follow the contract exactly.
    """)
public class CladAgent extends Agent {

    @Hidden private ShellTools shell;
    @Hidden private Path projectDir;

    public CladAgent(UnifiedLLM llm) { super(llm); }

    // ---- Contract Parsing ----

    @Generate @Strategy(PredictStrategy.class)
    public CladStage parseContract(String contextMdContent) {
        throw new UnsupportedOperationException("Generated at runtime");
    }

    // ---- Process Execution ----

    @Generate
    public String executeProcess(
        String stageId, String processDescription,
        List<String> inputs, List<String> outputs
    ) {
        throw new UnsupportedOperationException("Generated at runtime");
    }

    // ---- Verification ----

    public VerificationResult runVerification(CladStage contract, Path stageDir) {
        if (shell == null) shell = new ShellTools(stageDir, Permissions.allowAll(), null);
        Path outputDir = stageDir.resolve("output");
        List<String> checks = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        for (String output : contract.outputs()) {
            Path file = outputDir.resolve(output.trim());
            checks.add("Output exists: " + output);
            if (!Files.exists(file)) {
                failures.add("MISSING: " + output);
            } else {
                try {
                    if (Files.readString(file).isBlank()) {
                        failures.add("EMPTY: " + output);
                    }
                } catch (IOException e) {
                    failures.add("UNREADABLE: " + output);
                }
            }
        }

        for (String step : contract.verifySteps()) {
            checks.add("Verify: " + step.trim());
            if (step.trim().startsWith("./")) {
                var result = shell.run("cd " + stageDir + " && " + step.trim());
                if (!result.success()) {
                    failures.add("FAILED: " + step + " — " + result.stderr());
                }
            }
        }

        return failures.isEmpty()
            ? VerificationResult.pass(checks)
            : VerificationResult.fail(checks, failures);
    }

    @Generate @Strategy(PredictStrategy.class)
    public VerifyOutcome autoVerify(CladStage contract, Map<String, String> outputFiles) {
        throw new UnsupportedOperationException("Generated at runtime");
    }

    // ---- Gate ----

    @Generate
    public String presentGate(String stageId, List<String> artefacts, VerificationResult verification) {
        throw new UnsupportedOperationException("Generated at runtime");
    }

    // ---- Progression ----

    /**
     * Find the next uncompleted stage in the CLAD project.
     * Returns null if all stages are complete.
     */
    public Path findCurrentStage(Path projectDir) {
        // 1. Find features directory
        Path featuresDir = projectDir.resolve("features");
        if (!Files.exists(featuresDir)) {
            // Try system stages at root
            return findStageInDir(projectDir);
        }

        // 2. Try _system first
        Path systemStages = featuresDir.resolve("_system/stages");
        if (Files.exists(systemStages)) {
            Path pending = findStageInDir(systemStages);
            if (pending != null) return pending;
        }

        // 3. Try each UC directory
        try (var ucDirs = Files.list(featuresDir)) {
            return ucDirs
                .filter(Files::isDirectory)
                .filter(d -> {
                    String name = d.getFileName().toString();
                    return name.startsWith("UC-") || name.startsWith("_");
                })
                .filter(d -> !d.getFileName().toString().equals("_system"))
                .sorted()
                .map(d -> findStageInDir(d.resolve("stages")))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private Path findStageInDir(Path stagesDir) {
        if (!Files.exists(stagesDir)) return null;
        try (var dirs = Files.list(stagesDir)) {
            return dirs
                .filter(Files::isDirectory)
                .filter(d -> {
                    String name = d.getFileName().toString();
                    return name.matches("\\d+.*"); // starts with digit
                })
                .filter(d -> !Files.exists(d.resolve(".gate-receipt.json")))
                .sorted()
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    // ---- Execute Stage ----

    public CladResult executeStage() throws IOException {
        if (projectDir == null) {
            projectDir = Path.of(System.getProperty("user.dir"));
        }

        Path currentStage = findCurrentStage(projectDir);
        if (currentStage == null) {
            return new CladResult("none", false, "No pending stages", List.of());
        }

        String stageId = currentStage.getFileName().toString();
        context().put("stage", stageId);

        // Read CONTEXT.md
        Path contextMd = currentStage.resolve("CONTEXT.md");
        if (!Files.exists(contextMd)) {
            return new CladResult(stageId, false, "CONTEXT.md not found", List.of());
        }

        CladStage contract = parseContract(Files.readString(contextMd));
        System.out.println("=== " + contract.stageId() + ": " + contract.stageName() + " ===");

        // Produce artefacts
        String processResult = executeProcess(
            contract.stageId(), contract.process(),
            contract.inputs(), contract.outputs());
        context().put("process_result", processResult);

        // Verify
        Path outputDir = currentStage.resolve("output");
        Files.createDirectories(outputDir);
        List<String> files = contract.outputs().stream()
            .map(f -> outputDir.resolve(f.trim()))
            .filter(Files::exists)
            .map(Path::toString)
            .collect(Collectors.toList());

        VerificationResult verification = runVerification(contract, currentStage);
        if (!verification.passed()) {
            return new CladResult(stageId, false,
                "Verification failed: " + verification.failures(), files);
        }

        // Write receipt
        Files.writeString(currentStage.resolve(".gate-receipt.json"),
            "{\"stage\": \"" + stageId + "\", \"passed\": true, \"time\": \""
            + java.time.Instant.now() + "\"}\n");

        // Present at gate
        String presentation = presentGate(stageId, files, verification);
        System.out.println("\n[HUMAN GATE] " + contract.stageName());
        System.out.println(presentation);
        System.out.println("\nReview artefacts then type 'Proceed to next stage' to continue.");

        return new CladResult(stageId, true, presentation, files);
    }

    public List<CladResult> executeAllStages() throws IOException {
        List<CladResult> results = new ArrayList<>();
        if (projectDir == null) projectDir = Path.of(System.getProperty("user.dir"));

        while (true) {
            Path current = findCurrentStage(projectDir);
            if (current == null) break;
            var result = executeStage();
            results.add(result);
            if (!result.success()) {
                System.out.println("Stopping: " + result.summary());
                break;
            }
        }
        return results;
    }

    @Override
    public void close() {
        if (shell != null) shell.close();
        super.close();
    }
}
