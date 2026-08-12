package ai.nooa.examples;

import ai.nooa.Agent;
import ai.nooa.AgentFactory;
import ai.nooa.annotations.Generate;
import ai.nooa.annotations.Strategy;
import ai.nooa.llm.UnifiedLLM;
import ai.nooa.runtime.TokenBudgetSummarizer;
import ai.nooa.strategy.PredictStrategy;
import ai.nooa.strategy.ReflexionStrategy;
import ai.nooa.tracing.Tracing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

// =========================================================================
// 04 — Strategy Comparison: CodeAct vs Predict vs Reflexion
// =========================================================================

record MathSolution(String answer, List<String> steps) {}
record MathClassification(String type, String difficulty) {}

class StrategyDemoAgent extends Agent {
    public StrategyDemoAgent(UnifiedLLM llm) { super(llm); }

    // CodeAct (default): LLM writes code, calls helpers, iterates
    @Generate
    public MathSolution solveWithCode(String problem) {
        // LLM gets executeJava tool — can compute, format, build solution
        throw new UnsupportedOperationException();
    }

    // PredictStrategy: single-shot classification, validated against record
    @Generate @Strategy(PredictStrategy.class)
    public MathClassification classifyProblem(String problem) {
        // LLM returns structured JSON → validated → returned
        throw new UnsupportedOperationException();
    }

    // ReflexionStrategy: generate → critique → improve loop
    @Generate @Strategy(ReflexionStrategy.class)
    public MathSolution solveWithReflection(String problem) {
        // LLM generates, critiques its output, generates improved version
        throw new UnsupportedOperationException();
    }
}

// =========================================================================
// 06 — Tracing: Enable JSONL export, inspect spans
// =========================================================================

class TraceDemoAgent extends Agent {
    public TraceDemoAgent(UnifiedLLM llm) { super(llm); }
    @Generate
    public String greet(String name) { throw new UnsupportedOperationException(); }
}

// =========================================================================
// 09 — Token Budget: Auto-compaction when approaching context limit
// =========================================================================

class SummarizationDemoAgent extends Agent {
    public SummarizationDemoAgent(UnifiedLLM llm) { super(llm); }

    void installSummarizer(int tokenBudget) {
        var summarizer = new TokenBudgetSummarizer(this, tokenBudget);
        summarizer.install(); // subscribes to LLMComplete events
    }

    @Generate
    public String chat(String message) { throw new UnsupportedOperationException(); }
}

// =========================================================================
// 12 — Snapshots: Save and restore agent state
// =========================================================================

class SnapshotDemoAgent extends Agent {
    public SnapshotDemoAgent(UnifiedLLM llm) { super(llm); }
    @Generate
    public String analyze(String input) { throw new UnsupportedOperationException(); }
}

// =========================================================================
// 13 — Shell Tools: Agent runs bash commands
// =========================================================================

class ShellDemoAgent extends Agent {
    public ShellDemoAgent(UnifiedLLM llm) { super(llm); }

    String readFile(String path) throws java.io.IOException {
        return java.nio.file.Files.readString(Path.of(path));
    }

    @Generate
    public String reviewCode(String filePath) { throw new UnsupportedOperationException(); }
}

// =========================================================================
// 15 — Standalone Functions: @Generate on static methods
// =========================================================================

class StandaloneFunctions {
    record Keywords(List<String> terms) {}

    @Generate
    public static String summarize(String text) {
        throw new UnsupportedOperationException();
    }

    @Generate @Strategy(PredictStrategy.class)
    public static Keywords extractKeywords(String text) {
        throw new UnsupportedOperationException();
    }
}

// =========================================================================
// Main — demonstrates all patterns
// =========================================================================

public final class Examples04to15 {
    private static final Logger log = LoggerFactory.getLogger(Examples04to15.class);

    public static void main(String[] args) {
        if (args.length > 0) {
            log.info("Demo args provided: {}", args.length);
        }

        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null) {
            log.info("Set OPENAI_API_KEY to run (examples compile without it).");
            return;
        }
        var llm = UnifiedLLM.create(
            UnifiedLLM.openAI(apiKey, "gpt-4o").build());

        // ---- 04: Strategy Comparison ----
        log.info("=== 04: Strategy Comparison ===");
        var strategyAgent = AgentFactory.create(StrategyDemoAgent.class, llm);

        // Choose strategy by decorator, not by calling different methods:
        //   solveWithCode()     → CodeActStrategy (default)
        //   classifyProblem()   → PredictStrategy
        //   solveWithReflection() → ReflexionStrategy
        log.info("Agent created with 3 strategy variants.");
        strategyAgent.close();

        // ---- 06: Tracing ----
        log.info("\n=== 06: Tracing ===");
        Tracing.enable(Tracing.jsonl(Path.of("./traces_demo")));
        var traceAgent = AgentFactory.create(TraceDemoAgent.class, llm);
        // All agent calls, LLM calls, and code execution now emit OTel spans
        // Check ./traces_demo/traces.jsonl for output
        log.info("Tracing enabled → ./traces_demo/traces.jsonl");
        traceAgent.close();
        Tracing.shutdown();

        // ---- 09: Token Budget ----
        log.info("\n=== 09: Token Budget Summarization ===");
        var summaryAgent = AgentFactory.create(SummarizationDemoAgent.class, llm);
        summaryAgent.installSummarizer(100_000);
        log.info("Summarizer installed: auto-compacts at 85% of 100k tokens.");
        summaryAgent.close();

        // ---- 12: Snapshots ----
        log.info("\n=== 12: Snapshots ===");
        var snapAgent = AgentFactory.create(SnapshotDemoAgent.class, llm);
        snapAgent.context().put("session", "demo-123");
        snapAgent.eventManager().add(
            new ai.nooa.context.Event.Task("analyse input data"));

        var snap = ai.nooa.runtime.AgentSnapshot.take(snapAgent);
        ai.nooa.runtime.AgentSnapshot.save(snap, Path.of("./snapshot_demo.json"));
        log.info("Snapshot saved → ./snapshot_demo.json");

        // Later: load and restore
        var loaded = ai.nooa.runtime.AgentSnapshot.load(Path.of("./snapshot_demo.json"));
        snapAgent.eventManager().clear();
        ai.nooa.runtime.AgentSnapshot.restoreEvents(snapAgent, loaded);
        log.info("Restored {} events.", snapAgent.eventManager().size());
        snapAgent.close();

        // ---- 13: Shell Tools ----
        log.info("\n=== 13: Shell Tools ===");
        var shell = new ai.nooa.tools.ShellTools(Path.of(System.getProperty("user.home")));
        var result = shell.run("echo 'hello from shell' && ls ~ | head -3");
        log.info("Shell result:\n{}", result.stdout());
        shell.close();

        // ---- 15: Standalone Functions ----
        log.info("\n=== 15: Standalone Functions ===");
        log.info("Use Standalone.call() to invoke @Generate static methods:");
        log.info("  String summary = Standalone.call(llm,");
        log.info("      StandaloneFunctions.class, \"summarize\", text);");
        log.info("  Keywords kw = Standalone.call(llm,");
        log.info("      StandaloneFunctions.class, \"extractKeywords\", text);");

        log.info("\nAll examples demonstrated.");
    }
}
