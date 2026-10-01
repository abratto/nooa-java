package ai.nooa.eval.cli;

import ai.nooa.Agent;
import ai.nooa.eval.EvalAssertions;
import ai.nooa.eval.EvalDataset;
import ai.nooa.eval.EvalHistoryStore;
import ai.nooa.eval.EvalReport;
import ai.nooa.eval.EvalRunner;
import ai.nooa.eval.Mode;
import ai.nooa.llm.UnifiedLLM;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Command-line runner for {@link EvalDataset}s, mirroring
 * {@code examples/run.sh}.
 *
 * <pre>{@code
 * nooa-eval --agent ai.nooa.examples.SentimentAgent \
 *           --dataset sentiment.jsonl --trials 3 \
 *           --report report.json --markdown report.md \
 *           --min-weighted 0.8 --gate SecretLeak=1.0
 * }</pre>
 */
public final class EvalCli {

    private EvalCli() {}

    public static void main(String[] args) {
        int exit = 1;
        try {
            exit = run(args, LlmFactory.fromEnv(), System.out, System.err);
        } catch (Exception e) {
            System.err.println("nooa-eval: " + e.getMessage());
        }
        if (exit != 0) {
            System.exit(exit);
        }
    }

    /** Testable entry point: returns an exit code instead of exiting. */
    public static int run(String[] args, UnifiedLLM llm, PrintStream out, PrintStream err)
            throws Exception {
        Map<String, String> options = parse(args);
        String agentName = options.get("agent");
        String datasetPath = options.get("dataset");
        if (agentName == null || datasetPath == null) {
            err.println("usage: nooa-eval --agent <fqcn> --dataset <jsonl> "
                + "[--trials N] [--mode LIVE|RECORD|REPLAY] [--recording PATH] "
                + "[--report PATH] [--markdown PATH] [--history PATH] "
                + "[--min-weighted X] [--gate scorer=min]");
            return 2;
        }

        Class<?> raw = Class.forName(agentName);
        if (!Agent.class.isAssignableFrom(raw)) {
            err.println("nooa-eval: " + agentName + " does not extend ai.nooa.Agent");
            return 2;
        }
        @SuppressWarnings("unchecked")
        Class<? extends Agent> agentClass = (Class<? extends Agent>) raw;

        EvalDataset dataset = EvalDataset.load(Path.of(datasetPath));
        EvalRunner.Builder builder = EvalRunner.builder(agentClass, llm)
            .trials(intOption(options, "trials", 1))
            .mode(modeOption(options));
        if (options.containsKey("recording")) {
            builder.recordingPath(Path.of(options.get("recording")));
        }

        EvalReport report = builder.build().run(dataset);
        out.println(report.toMarkdown());

        if (options.containsKey("report")) {
            Path path = Path.of(options.get("report"));
            createParent(path);
            Files.writeString(path, report.toJson(), StandardCharsets.UTF_8);
            out.println("wrote JSON report to " + path);
        }
        if (options.containsKey("markdown")) {
            Path path = Path.of(options.get("markdown"));
            createParent(path);
            Files.writeString(path, report.toMarkdown(), StandardCharsets.UTF_8);
            out.println("wrote Markdown report to " + path);
        }
        if (options.containsKey("history")) {
            try (EvalHistoryStore store = new EvalHistoryStore(options.get("history"))) {
                long id = store.record(report);
                out.println("recorded run #" + id + " in history");
            }
        }

        try {
            if (options.containsKey("min-weighted")) {
                EvalAssertions.assertPass(report,
                    Double.parseDouble(options.get("min-weighted")));
            }
            for (String gate : gates(options)) {
                String[] parts = gate.split("=", 2);
                EvalAssertions.assertGate(report, parts[0], Double.parseDouble(parts[1]));
            }
        } catch (AssertionError gateFailure) {
            err.println("nooa-eval: " + gateFailure.getMessage());
            return 1;
        }
        return 0;
    }

    private static Map<String, String> parse(String[] args) {
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) {
                continue;
            }
            String key = arg.substring(2);
            if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                if (key.equals("gate") && options.containsKey("gate")) {
                    options.put("gate", options.get("gate") + "," + args[++i]);
                } else {
                    options.put(key, args[++i]);
                }
            } else {
                options.put(key, "true");
            }
        }
        return options;
    }

    private static List<String> gates(Map<String, String> options) {
        List<String> gates = new ArrayList<>();
        String raw = options.get("gate");
        if (raw != null) {
            for (String gate : raw.split(",")) {
                if (!gate.isBlank()) {
                    gates.add(gate.strip());
                }
            }
        }
        return gates;
    }

    private static int intOption(Map<String, String> options, String key, int fallback) {
        String raw = options.get(key);
        return raw == null ? fallback : Integer.parseInt(raw);
    }

    private static Mode modeOption(Map<String, String> options) {
        String raw = options.get("mode");
        return raw == null ? Mode.LIVE : Mode.valueOf(raw.toUpperCase(java.util.Locale.ROOT));
    }

    private static void createParent(Path path) throws java.io.IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }
}
