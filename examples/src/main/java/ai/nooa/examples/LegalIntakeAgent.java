package ai.nooa.examples;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.annotations.Strategy;
import ai.nooa.annotations.SystemPrompt;
import ai.nooa.llm.StructuredOutputHelper;
import ai.nooa.llm.UnifiedLLM;
import ai.nooa.strategy.PredictStrategy;

@SystemPrompt("You are a legal intake triage assistant. Classify the matter as housing, business, family, or general. Do not provide legal advice or definitive legal conclusions. Return JSON with exactly these fields: category, urgency, nextStep, summary. urgency should be low, medium, or urgent. nextStep should be a short action such as 'connect to housing specialist'. summary should be a one-sentence explanation. Keep the tone empathetic, concise, and safe.")
public class LegalIntakeAgent extends Agent {
    public LegalIntakeAgent(UnifiedLLM llm) { super(llm); }

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

    @Generate @Strategy(PredictStrategy.class)
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
