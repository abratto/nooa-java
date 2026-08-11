package ai.nooa.clad;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.*;
import java.util.concurrent.Callable;

/**
 * CLAD methodology CLI — init projects, run stages, scaffold artefacts.
 *
 * <pre>{@code
 * nooa clad init my-project
 * nooa clad run
 * nooa clad run --auto --feature UC-01-register
 * }</pre>
 */
@Command(
    name = "nooa",
    subcommands = {CladCli.InitCommand.class, CladCli.RunCommand.class},
    mixinStandardHelpOptions = true,
    description = "NOOA CLAD — Contract-Led, Artefact-Driven agent"
)
public class CladCli implements Callable<Integer> {

    @Override
    public Integer call() {
        System.out.println("Usage: nooa clad <command> [options]");
        System.out.println("Commands: init, run");
        System.out.println("Try 'nooa clad init --help' or 'nooa clad run --help'");
        return 0;
    }

    /**
     * nooa clad init &lt;project-name&gt;
     */
    @Command(name = "init", description = "Create a new CLAD project from templates")
    static class InitCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "Project name")
        String projectName;

        @Option(names = "--dir", description = "Target directory (default: current)")
        Path targetDir = Path.of(".");

        @Override
        public Integer call() throws Exception {
            Path projectDir = targetDir.resolve(projectName).toAbsolutePath();
            System.out.println("Creating CLAD project: " + projectDir);

            ProjectScaffolder scaffolder = new ProjectScaffolder();
            scaffolder.scaffold(projectDir, projectName);

            System.out.println("\nProject created. Next steps:");
            System.out.println("  cd " + projectName);
            System.out.println("  nooa clad run");
            System.out.println("  mvn test");
            return 0;
        }
    }

    /**
     * nooa clad run [--auto] [--stage STAGE] [--feature FEATURE]
     */
    @Command(name = "run", description = "Execute CLAD stages")
    static class RunCommand implements Callable<Integer> {

        @Option(names = "--auto", description = "Auto-advance non-gate stages")
        boolean auto;

        @Option(names = "--stage", description = "Run a specific stage")
        String stage;

        @Option(names = "--feature", description = "Feature directory to run")
        String feature;

        @Option(names = "--model", description = "LLM model (default: gpt-4o)")
        String model = "gpt-4o";

        @Option(names = "--dir", description = "Project directory")
        Path projectDir = Path.of(".");

        @Override
        public Integer call() throws Exception {
            String apiKey = System.getenv("OPENAI_API_KEY");
            if (apiKey == null) {
                System.err.println("Set OPENAI_API_KEY environment variable");
                return 1;
            }

            var llm = ai.nooa.llm.UnifiedLLM.create(
                ai.nooa.llm.UnifiedLLM.openAI(apiKey, model).build());

            var agent = ai.nooa.AgentFactory.create(
                CladAgent.class, llm);

            agent.context().put("project_dir", projectDir.toAbsolutePath().toString());
            if (feature != null) {
                agent.context().put("feature", feature);
            }

            if (stage != null) {
                System.out.println("Running stage: " + stage);
                var result = agent.executeStage();
                System.out.println(result.success() ? "OK" : "FAILED: " + result.summary());
            } else {
                agent.executeAllStages();
            }
            return 0;
        }
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new CladCli()).execute(args);
        System.exit(exitCode);
    }
}
