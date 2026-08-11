package ai.nooa.examples;

import ai.nooa.Agent;
import ai.nooa.AgentFactory;
import ai.nooa.Standalone;
import ai.nooa.annotations.Generate;
import ai.nooa.annotations.Strategy;
import ai.nooa.config.CodeActConfig;
import ai.nooa.config.PredictConfig;
import ai.nooa.llm.UnifiedLLM;
import ai.nooa.runtime.TokenBudgetSummarizer;
import ai.nooa.strategy.CodeActStrategy;
import ai.nooa.strategy.PredictStrategy;
import ai.nooa.strategy.ReflexionStrategy;
import ai.nooa.tracing.Tracing;

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

    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null) {
            System.out.println("Set OPENAI_API_KEY to run (examples compile without it).");
            return;
        }
        var llm = UnifiedLLM.create(
            UnifiedLLM.openAI(apiKey, "gpt-4o").build());

        // ---- 04: Strategy Comparison ----
        System.out.println("=== 04: Strategy Comparison ===");
        var strategyAgent = AgentFactory.create(StrategyDemoAgent.class, llm);

        // Choose strategy by decorator, not by calling different methods:
        //   solveWithCode()     → CodeActStrategy (default)
        //   classifyProblem()   → PredictStrategy
        //   solveWithReflection() → ReflexionStrategy
        System.out.println("Agent created with 3 strategy variants.");
        strategyAgent.close();

        // ---- 06: Tracing ----
        System.out.println("\n=== 06: Tracing ===");
        Tracing.enable(Tracing.jsonl(Path.of("./traces_demo")));
        var traceAgent = AgentFactory.create(TraceDemoAgent.class, llm);
        // All agent calls, LLM calls, and code execution now emit OTel spans
        // Check ./traces_demo/traces.jsonl for output
        System.out.println("Tracing enabled → ./traces_demo/traces.jsonl");
        traceAgent.close();
        Tracing.shutdown();

        // ---- 09: Token Budget ----
        System.out.println("\n=== 09: Token Budget Summarization ===");
        var summaryAgent = AgentFactory.create(SummarizationDemoAgent.class, llm);
        summaryAgent.installSummarizer(100_000);
        System.out.println("Summarizer installed: auto-compacts at 85% of 100k tokens.");
        summaryAgent.close();

        // ---- 12: Snapshots ----
        System.out.println("\n=== 12: Snapshots ===");
        var snapAgent = AgentFactory.create(SnapshotDemoAgent.class, llm);
        snapAgent.context().put("session", "demo-123");
        snapAgent.eventManager().add(
            new ai.nooa.context.Event.Task("analyse input data"));

        var snap = ai.nooa.runtime.AgentSnapshot.take(snapAgent);
        ai.nooa.runtime.AgentSnapshot.save(snap, Path.of("./snapshot_demo.json"));
        System.out.println("Snapshot saved → ./snapshot_demo.json");

        // Later: load and restore
        var loaded = ai.nooa.runtime.AgentSnapshot.load(Path.of("./snapshot_demo.json"));
        snapAgent.eventManager().clear();
        ai.nooa.runtime.AgentSnapshot.restoreEvents(snapAgent, loaded);
        System.out.println("Restored " + snapAgent.eventManager().size() + " events.");
        snapAgent.close();

        // ---- 13: Shell Tools ----
        System.out.println("\n=== 13: Shell Tools ===");
        var shell = new ai.nooa.tools.ShellTools(Path.of("/tmp"));
        var result = shell.run("echo 'hello from shell' && ls /tmp | head -3");
        System.out.println("Shell result:\n" + result.stdout());
        shell.close();

        // ---- 15: Standalone Functions ----
        System.out.println("\n=== 15: Standalone Functions ===");
        System.out.println("Use Standalone.call() to invoke @Generate static methods:");
        System.out.println("  String summary = Standalone.call(llm,");
        System.out.println("      StandaloneFunctions.class, \"summarize\", text);");
        System.out.println("  Keywords kw = Standalone.call(llm,");
        System.out.println("      StandaloneFunctions.class, \"extractKeywords\", text);");

        System.out.println("\nAll examples demonstrated.");
    }
}
