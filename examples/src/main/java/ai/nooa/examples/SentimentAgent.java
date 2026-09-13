package ai.nooa.examples;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.annotations.Strategy;
import ai.nooa.annotations.SystemPrompt;
import ai.nooa.llm.UnifiedLLM;
import ai.nooa.strategy.PredictStrategy;

/**
 * The result record for {@link SentimentAgent#analyze(String)}.
 * The record's components are the output schema the runtime enforces.
 */
record SentimentResult(String sentiment, double confidence, String reasoning) {}

/**
 * Typed structured output with {@link PredictStrategy}.
 *
 * <p><b>NOOA features demonstrated</b></p>
 * <ul>
 *   <li>{@code @Strategy(PredictStrategy.class)} — replaces the default
 *       CodeAct loop with a single-shot structured call. The declared return
 *       type {@code SentimentResult} becomes the JSON contract; the runtime
 *       requests JSON ({@code response_format} on OpenAI-compatible
 *       endpoints, {@code format:"json"} on native Ollama) and parses the
 *       reply into the record, retrying with a corrective diagnostic when the
 *       shape is wrong.</li>
 *   <li>Typed I/O — the caller gets a real {@code SentimentResult}, never a
 *       string to parse. This is the "records as enforced return type
 *       contracts" design idea.</li>
 *   <li>{@code @SystemPrompt} — carries the stricter schema wording (exact
 *       fields, allowed values, no extra keys) that complements the runtime
 *       enforcement.</li>
 *   <li>Reasoning effort — single-pass classification needs no deliberation,
 *       so the example requests {@code "low"}.</li>
 * </ul>
 *
 * <p><b>Run</b> via {@link QuickstartExamples} (Example 2):
 * <pre>{@code
 * mvn -pl examples exec:java -Dexec.mainClass=ai.nooa.examples.QuickstartExamples
 * }</pre>
 */
@SystemPrompt("You are a sentiment analysis agent. Analyze the text and return valid JSON with exactly these fields: sentiment (positive | neutral | negative), confidence (number between 0 and 1), reasoning (one short sentence, max 18 words). Do not add extra keys or prose.")
public class SentimentAgent extends Agent {
    public SentimentAgent(UnifiedLLM llm) {
        super(llm);
        // Single-pass classification: low effort, small output budget.
        ExampleLLM.tune(this, "low", 1024);
    }

    @Generate(prompt = "Analyze the text and return valid JSON with sentiment, confidence, and one short reasoning sentence.")
    @Strategy(PredictStrategy.class)
    public SentimentResult analyze(String text) { throw new UnsupportedOperationException(); }
}
