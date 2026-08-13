package ai.nooa.examples;

import ai.nooa.Agent;
import ai.nooa.AgentFactory;
import ai.nooa.annotations.Generate;
import ai.nooa.annotations.Strategy;
import ai.nooa.annotations.SystemPrompt;
import ai.nooa.llm.UnifiedLLM;
import ai.nooa.memory.MemorySkill;
import ai.nooa.memory.MemoryStore;
import ai.nooa.strategy.PredictStrategy;

import java.util.List;

/**
 * Complete legal intake agent — classify → route → respond.
 *
 * <p>Demonstrates the core framework pattern:
 * <ol>
 *   <li>Classify the message using PredictStrategy (structured output)</li>
 *   <li>Route based on classification (deterministic Java)</li>
 *   <li>Check for emergencies, set context priority</li>
 *   <li>Persist to memory for future recall</li>
 *   <li>Respond with context-aware generation (CodeActStrategy)</li>
 * </ol>
 *
 * <p>Usage:
 * <pre>{@code
 * var llm = UnifiedLLM.create(
 *     UnifiedLLM.openAI(System.getenv("OPENAI_API_KEY"), "gpt-4o").build());
 * var agent = AgentFactory.create(LegalIntakeAgent.class, llm);
 * String response = agent.handle("My landlord won't fix the heating...");
 * }</pre>
 */
enum Priority { LOW, MEDIUM, HIGH, EMERGENCY }

enum LegalArea {
    FAMILY, CRIMINAL, CONTRACT, EMPLOYMENT, HOUSING, IMMIGRATION, OTHER
}

record LegalClassification(
    LegalArea area,
    Priority urgency,
    List<String> keyTerms,
    String summary
) {}

@SystemPrompt("""
    You are a legal intake specialist. You classify messages by:
    - Area of law (FAMILY, CRIMINAL, CONTRACT, EMPLOYMENT, HOUSING, IMMIGRATION, OTHER)
    - Urgency (LOW, MEDIUM, HIGH, EMERGENCY)
    - Key legal terms mentioned
    - A one-sentence summary

    Return ONLY valid JSON matching the schema. No commentary.""")
class LegalIntakeAgent extends Agent {

    private final MemorySkill memory;

    public LegalIntakeAgent(UnifiedLLM llm) {
        super(llm);
        var store = new MemoryStore(".nooa-legal-memory.db");
        store.scheduleReflection(3600); // prune/merge hourly
        this.memory = new MemorySkill(this, store);
    }

    // ---- Step 1: Classify (PredictStrategy — one LLM call) ----
    @Generate @Strategy(PredictStrategy.class)
    public LegalClassification classify(String message) {
        throw new UnsupportedOperationException();
    }

    // ---- Step 2: Route (deterministic Java, no LLM) ----
    public String route(LegalClassification classification) {
        return switch (classification.area()) {
            case FAMILY     -> "family_law_department";
            case CRIMINAL   -> "criminal_defense_team";
            case CONTRACT   -> "contract_law_specialist";
            case EMPLOYMENT -> "employment_rights_division";
            case HOUSING    -> "housing_authority";
            case IMMIGRATION -> "immigration_services";
            default         -> "general_intake";
        };
    }

    // ---- Step 3: Respond (CodeActStrategy — LLM can call helpers) ----
    boolean isPastEmergencyWindow(Priority urgency) {
        return urgency != Priority.EMERGENCY;
    }

    @Generate
    public String respond(String message,
                          LegalClassification classification,
                          String department) {
        throw new UnsupportedOperationException();
    }

    // ---- Orchestrator (pure Java — calls other methods) ----
    public String handle(String message) {
        // 1. Classify
        var classification = classify(message);

        // 2. Route
        var department = route(classification);

        // 3. Emergency check — set context priority
        if (classification.urgency() == Priority.EMERGENCY) {
            context().put("priority",
                "EMERGENCY — respond urgently, suggest immediate actions");
        } else {
            context().remove("priority");
        }
        context().put("department", department);

        // 4. Persist to memory for future recall
        memory.write("message",
            classification.area() + ": " + classification.summary(),
            classification.urgency() == Priority.EMERGENCY ? 1.0 : 0.5,
            List.of(classification.area().name().toLowerCase(),
                    classification.urgency().name().toLowerCase()));

        // 5. Recall relevant past messages for context
        var relevant = memory.recall(
            List.of(classification.area().name().toLowerCase()), 3);
        if (!relevant.isEmpty()) {
            context().put("history",
                "Past similar inquiries: " + relevant.size() + " records found");
        }

        // 6. Generate response with full context
        return respond(message, classification, department);
    }

    @Override
    public void close() {
        memory.close();
        super.close();
    }
}

/**
 * Demo entry point. Requires OPENAI_API_KEY.
 */
public final class LegalIntakeDemo {
    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null) {
            System.out.println("Set OPENAI_API_KEY environment variable.");
            return;
        }

        var llm = UnifiedLLM.create(
            UnifiedLLM.openAI(apiKey, "gpt-4o").build());
        var agent = AgentFactory.create(LegalIntakeAgent.class, llm);

        // Simulated intake flow
        String[] messages = {
            "My landlord hasn't fixed the heating for 3 months. It's freezing.",
            "URGENT: I'm being evicted tomorrow with no notice!",
            "I need help drafting a non-compete clause for my startup.",
            "My ex-spouse is violating the custody agreement. Again.",
        };

        for (String msg : messages) {
            System.out.println("\n=== Incoming: " + msg);
            String response = agent.handle(msg);
            System.out.println("Response: " + response);
        }

        agent.close();
    }
}
