package ai.nooa.examples;

import ai.nooa.AgentFactory;
import ai.nooa.tracing.Tracing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public final class Examples04to15 {
    private static final Logger log = LoggerFactory.getLogger(Examples04to15.class);

    public static void main(String[] args) {
        if (args.length > 0) {
            log.info("Demo args provided: {}", args.length);
        }

        var llm = ExampleLLM.create();

        log.info("=== 04: Strategy Comparison ===");
        var strategyAgent = AgentFactory.create(StrategyDemoAgent.class, llm);
        log.info("Agent created with 3 strategy variants.");
        strategyAgent.close();

        log.info("\n=== 06: Tracing ===");
        Tracing.enable(Tracing.jsonl(Path.of("./traces_demo")));
        var traceAgent = AgentFactory.create(TraceDemoAgent.class, llm);
        log.info("Tracing enabled → ./traces_demo/traces.jsonl");
        traceAgent.close();
        Tracing.shutdown();

        log.info("\n=== 09: Token Budget Summarization ===");
        var summaryAgent = AgentFactory.create(SummarizationDemoAgent.class, llm);
        summaryAgent.installSummarizer(100_000);
        log.info("Summarizer installed: auto-compacts at 85% of 100k tokens.");
        summaryAgent.close();

        log.info("\n=== 12: Snapshots ===");
        var snapAgent = AgentFactory.create(SnapshotDemoAgent.class, llm);
        snapAgent.context().put("session", "demo-123");
        snapAgent.eventManager().add(
            new ai.nooa.context.Event.Task("analyse input data"));

        var snap = ai.nooa.runtime.AgentSnapshot.take(snapAgent);
        ai.nooa.runtime.AgentSnapshot.save(snap, Path.of("./snapshot_demo.json"));
        log.info("Snapshot saved → ./snapshot_demo.json");

        var loaded = ai.nooa.runtime.AgentSnapshot.load(Path.of("./snapshot_demo.json"));
        snapAgent.eventManager().clear();
        ai.nooa.runtime.AgentSnapshot.restoreEvents(snapAgent, loaded);
        log.info("Restored {} events.", snapAgent.eventManager().size());
        snapAgent.close();

        log.info("\n=== 13: Shell Tools ===");
        var shell = new ai.nooa.tools.ShellTools(Path.of(System.getProperty("user.home")));
        var result = shell.run("echo 'hello from shell' && ls ~ | head -3");
        log.info("Shell result:\n{}", result.stdout());
        shell.close();

        log.info("\n=== 15: Standalone Functions ===");
        log.info("Use Standalone.call() to invoke @Generate static methods:");
        log.info("  String summary = Standalone.call(llm,");
        log.info("      StandaloneFunctions.class, \"summarize\", text);");
        log.info("  Keywords kw = Standalone.call(llm,");
        log.info("      StandaloneFunctions.class, \"extractKeywords\", text);");

        log.info("\nAll examples demonstrated.");
    }
}
