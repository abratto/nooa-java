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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * CLAD methodology agent — reads CONTEXT.md contracts, produces artefacts,
 * verifies them, and advances through quality gates.
 */
@SystemPrompt("""
    You are a CLAD methodology agent. You follow contract-led, artefact-driven
    development. Every change has a contract, every contract produces an artefact.
    You read CONTEXT.md files, produce output files, run verification, and
    respect quality gates. Be precise and follow the contract exactly.
    """)
public class CladAgent extends Agent {

    private static final Logger log = LoggerFactory.getLogger(CladAgent.class);
    private static final Pattern STAGE_NAME_PATTERN = Pattern.compile("\\d+.*");

    @Hidden private ShellTools shell;
    @Hidden private Path projectDir;

    public CladAgent(UnifiedLLM llm) { super(llm); }

    @Generate @Strategy(PredictStrategy.class)
    public CladStage parseContract(String contextMdContent) {
        throw new UnsupportedOperationException("Generated at runtime");
    }

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

        checkOutputsExist(contract.outputs(), outputDir, checks, failures);
        runCustomVerifyScripts(contract.verifySteps(), stageDir, checks, failures);

        return failures.isEmpty()
            ? VerificationResult.pass(checks)
            : VerificationResult.fail(checks, failures);
    }

    private void checkOutputsExist(List<String> outputs, Path outputDir,
                                    List<String> checks, List<String> failures) {
        for (String output : outputs) {
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
    }

    private void runCustomVerifyScripts(List<String> verifySteps, Path stageDir,
                                         List<String> checks, List<String> failures) {
        for (String step : verifySteps) {
            checks.add("Verify: " + step.trim());
            if (step.trim().startsWith("./")) {
                var result = shell.run("cd " + stageDir + " && " + step.trim());
                if (!result.success()) {
                    failures.add("FAILED: " + step + " — " + result.stderr());
                }
            }
        }
    }

    @Generate @Strategy(PredictStrategy.class)
    public VerifyOutcome autoVerify(CladStage contract, Map<String, String> outputFiles) {
        throw new UnsupportedOperationException("Generated at runtime");
    }

    @Generate
    public String presentGate(String stageId, List<String> artefacts, VerificationResult verification) {
        throw new UnsupportedOperationException("Generated at runtime");
    }

    // ---- Progression ----

    public Path findCurrentStage(Path projectDir) {
        Path featuresDir = projectDir.resolve("features");
        if (!Files.exists(featuresDir)) {
            return findStageInDir(projectDir);
        }

        Path systemStages = featuresDir.resolve("_system/stages");
        if (Files.exists(systemStages)) {
            Path pending = findStageInDir(systemStages);
            if (pending != null) return pending;
        }

        try (var ucDirs = Files.list(featuresDir)) {
            return ucDirs
                .filter(Files::isDirectory)
                .filter(d -> isUcDirectory(d))
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

    private boolean isUcDirectory(Path dir) {
        String name = dir.getFileName().toString();
        return name.startsWith("UC-") || name.startsWith("_");
    }

    private Path findStageInDir(Path stagesDir) {
        if (!Files.exists(stagesDir)) return null;
        try (var dirs = Files.list(stagesDir)) {
            return dirs
                .filter(Files::isDirectory)
                .filter(d -> STAGE_NAME_PATTERN.matcher(d.getFileName().toString()).matches())
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
        projectDir = resolveProjectDir();
        Path currentStage = findCurrentStage(projectDir);
        if (currentStage == null) {
            return new CladResult("none", false, "No pending stages", List.of());
        }

        String stageId = currentStage.getFileName().toString();
        context().put("stage", stageId);

        CladStage contract = readContract(currentStage);
        if (contract == null) {
            return new CladResult(stageId, false, "CONTEXT.md not found", List.of());
        }

        log.info("=== {}: {} ===", contract.stageId(), contract.stageName());

        String processResult = executeProcess(
            contract.stageId(), contract.process(),
            contract.inputs(), contract.outputs());
        context().put("process_result", processResult);

        List<String> files = collectOutputFiles(contract, currentStage);
        VerificationResult verification = runVerification(contract, currentStage);
        if (!verification.passed()) {
            return new CladResult(stageId, false,
                "Verification failed: " + verification.failures(), files);
        }

        writeGateReceipt(currentStage, stageId);

        String presentation = presentGate(stageId, files, verification);
        log.info("[HUMAN GATE] {}\n{}\nReview artefacts then proceed.", contract.stageName(), presentation);

        return new CladResult(stageId, true, presentation, files);
    }

    private Path resolveProjectDir() {
        if (projectDir == null) {
            projectDir = Path.of(System.getProperty("user.dir"));
        }
        return projectDir;
    }

    private CladStage readContract(Path stageDir) throws IOException {
        Path contextMd = stageDir.resolve("CONTEXT.md");
        if (!Files.exists(contextMd)) return null;
        return parseContract(Files.readString(contextMd));
    }

    private List<String> collectOutputFiles(CladStage contract, Path stageDir) throws IOException {
        Path outputDir = stageDir.resolve("output");
        Files.createDirectories(outputDir);
        return contract.outputs().stream()
            .map(f -> outputDir.resolve(f.trim()))
            .filter(Files::exists)
            .map(Path::toString)
            .toList();
    }

    private void writeGateReceipt(Path stageDir, String stageId) throws IOException {
        Files.writeString(stageDir.resolve(".gate-receipt.json"),
            "{\"stage\": \"" + stageId + "\", \"passed\": true, \"time\": \""
            + java.time.Instant.now() + "\"}\n");
    }

    public List<CladResult> executeAllStages() throws IOException {
        List<CladResult> results = new ArrayList<>();
        projectDir = resolveProjectDir();

        Path current = findCurrentStage(projectDir);
        while (current != null) {
            var result = executeStage();
            results.add(result);
            if (!result.success()) {
                log.info("Stopping: {}", result.summary());
                return results;
            }
            current = findCurrentStage(projectDir);
        }
        return results;
    }

    @Override
    public void close() {
        if (shell != null) shell.close();
        super.close();
    }
}
