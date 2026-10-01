package ai.nooa.eval;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.annotations.Strategy;
import ai.nooa.eval.cli.EvalCli;
import ai.nooa.llm.FakeLLMClient;
import ai.nooa.llm.UnifiedLLM;
import ai.nooa.strategy.PredictStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("eval CLI")
class EvalCliTest {

    public static class EchoAgent extends Agent {
        public EchoAgent(UnifiedLLM llm) {
            super(llm);
        }

        @Generate(prompt = "Return exactly the provided text.")
        @Strategy(PredictStrategy.class)
        public String echo(String text) {
            throw new UnsupportedOperationException();
        }
    }

    @Test
    void runsDatasetAndWritesReports(@TempDir Path dir) throws Exception {
        Path dataset = dir.resolve("smoke.jsonl");
        EvalDataset.of("smoke",
            List.of(EvalCase.of("c1", "echo", Map.of("text", "hello"), "hello")))
            .save(dataset);

        FakeLLMClient llm = new FakeLLMClient();
        llm.respondWith("hello");

        Path json = dir.resolve("report.json");
        Path markdown = dir.resolve("report.md");
        Path history = dir.resolve("history.db");
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        int exit = EvalCli.run(new String[]{
                "--agent", EchoAgent.class.getName(),
                "--dataset", dataset.toString(),
                "--report", json.toString(),
                "--markdown", markdown.toString(),
                "--history", history.toString(),
                "--min-weighted", "0.5",
                "--gate", "ExactMatch=1.0"},
            llm, new PrintStream(out), new PrintStream(out));

        assertThat(exit).isZero();
        assertThat(json).exists();
        assertThat(markdown).exists();
        assertThat(out.toString()).contains("pass@1");
    }

    @Test
    void returnsFailureWhenGateNotMet(@TempDir Path dir) throws Exception {
        Path dataset = dir.resolve("smoke.jsonl");
        EvalDataset.of("smoke",
            List.of(EvalCase.of("c1", "echo", Map.of("text", "hello"), "hello")))
            .save(dataset);

        FakeLLMClient llm = new FakeLLMClient();
        llm.respondWith("wrong");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int exit = EvalCli.run(new String[]{
                "--agent", EchoAgent.class.getName(),
                "--dataset", dataset.toString(),
                "--min-weighted", "0.99"},
            llm, new PrintStream(out), new PrintStream(out));

        assertThat(exit).isEqualTo(1);
    }

    @Test
    void returnsUsageErrorWithoutRequiredArgs() throws Exception {
        int exit = EvalCli.run(new String[]{},
            new FakeLLMClient(), new PrintStream(new ByteArrayOutputStream()),
            new PrintStream(new ByteArrayOutputStream()));
        assertThat(exit).isEqualTo(2);
    }
}
