package ai.nooa.examples;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.annotations.Strategy;
import ai.nooa.annotations.SystemPrompt;
import ai.nooa.llm.StructuredOutputHelper;
import ai.nooa.llm.UnifiedLLM;
import ai.nooa.strategy.PredictStrategy;

/**
 * Constrained classification with a legal-safety prompt boundary.
 *
 * <p>This example is an intake triage classifier, not legal advice. Its prompt
 * and result schema classify and route a message; an application must still
 * apply its own legal review and escalation policy.</p>
 *
 * <p><b>NOOA features demonstrated</b></p>
 * <ul>
 *   <li>Safety boundary in {@code @SystemPrompt} — the persona explicitly
 *       forbids legal advice or definitive conclusions; the schema fields
 *       ({@code category}, {@code urgency}, {@code nextStep}, {@code summary})
 *       keep the output actionable and bounded.</li>
 *   <li>{@code @Generate} + {@link PredictStrategy} —
 *       {@link #handle(String)} returns a validated {@link LegalIntakeResult}
 *       record in a single model call.</li>
 *   <li>{@link StructuredOutputHelper} — {@link #handleStructured(String)}
 *       shows the standalone, provider-agnostic extraction path: build the
 *       messages yourself, target the record class, and get retry-with-
 *       validation without the agent runtime. Useful when code owns the
 *       prompt entirely.</li>
 *   <li>Deterministic routing helpers — {@link #classify(String)} and
 *       {@link #route(String)} are plain Java keyword routers an application
 *       could use to cross-check the model's classification.</li>
 *   <li>Reasoning effort — keyword-level triage: {@code "low"}, low
 *       temperature-style determinism is desirable.</li>
 * </ul>
 *
 * <p><b>Run</b> via {@link LegalIntakeDemo}:
 * <pre>{@code
 * mvn -pl examples exec:java -Dexec.mainClass=ai.nooa.examples.LegalIntakeDemo
 * }</pre>
 */
@SystemPrompt("You are a legal intake triage assistant. Classify the matter as housing, business, family, or general. Do not provide legal advice or definitive legal conclusions. Return JSON with exactly these fields: category, urgency, nextStep, summary. urgency should be low, medium, or urgent. nextStep should be a short action such as 'connect to housing specialist'. summary should be a one-sentence explanation. Keep the tone empathetic, concise, and safe.")
public class LegalIntakeAgent extends Agent {
    public LegalIntakeAgent(UnifiedLLM llm) {
        super(llm);
        ExampleLLM.tune(this, "low", 1024);
    }

    String classify(String msg) {
        if (msg.toLowerCase().contains("evict") || msg.toLowerCase().contains("landlord")) {
            return "housing";
        }
        if (msg.toLowerCase().contains("non-compete") || msg.toLowerCase().contains("startup")) {
            return "business";
        }
        if (msg.toLowerCase().contains("custody") || msg.toLowerCase().contains("spouse")) {
            return "family";
        }
        return "general";
    }

    String route(String category) {
        return switch (category) {
            case "housing" -> "Connect to housing specialist";
            case "business" -> "Connect to business counsel";
            case "family" -> "Connect to family law specialist";
            default -> "Provide general legal information";
        };
    }

    @Generate(prompt = "Classify the matter and return JSON with category, urgency, nextStep, and a one-sentence summary. Do not provide legal advice or definitive conclusions.")
    @Strategy(PredictStrategy.class)
    public LegalIntakeResult handle(String message) { throw new UnsupportedOperationException(); }

    public LegalIntakeResult handleStructured(String message) {
        var helper = new StructuredOutputHelper(3);
        return helper.extract(
            java.util.List.of(
                ai.nooa.llm.Message.user(
                    "You are a legal intake triage assistant. Classify the matter as housing, business, family, or general. " +
                    "Return valid JSON with exactly these fields: category, urgency, nextStep, summary. " +
                    "Urgency must be low, medium, or urgent. summary must be a one-sentence explanation. " +
                    "Do not include any prose outside the JSON. Input: " + message
                )
            ),
            LegalIntakeResult.class,
            this.llm(),
            java.util.Map.of("temperature", 0.2)
        );
    }
}
