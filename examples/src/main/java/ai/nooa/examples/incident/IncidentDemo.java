package ai.nooa.examples.incident;

import ai.nooa.AgentFactory;
import ai.nooa.examples.ExampleLLM;
import ai.nooa.llm.UnifiedLLM;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Runs the {@link IncidentAgent} workflow over the canned scenarios and prints
 * each state transition. With a terminal it prompts an {@link ApprovalGate}; in
 * a non-interactive run it auto-approves.
 *
 * <pre>{@code
 * mvn -pl examples exec:java -Dexec.mainClass=ai.nooa.examples.incident.IncidentDemo
 * }</pre>
 */
public final class IncidentDemo {

    private IncidentDemo() {}

    public static void main(String[] args) {
        UnifiedLLM llm = ExampleLLM.create();
        ApprovalGate gate = consoleGate();

        for (IncidentScenarios.Scenario scenario : IncidentScenarios.all()) {
            System.out.println();
            System.out.println("════════════════════════════════════════════════════════════");
            System.out.println("  Incident " + scenario.report().id() + " — " + scenario.name());
            System.out.println("  " + scenario.report().summary());
            System.out.println("════════════════════════════════════════════════════════════");

            try (IncidentAgent agent = AgentFactory.create(IncidentAgent.class, llm, scenario)) {
                IncidentOutcome outcome = agent.respond(gate);
                System.out.println();
                System.out.println("Transitions:");
                outcome.audit().forEach(step -> System.out.println("  - " + step));
                System.out.println();
                System.out.println("Final status: " + outcome.finalStatus());
                if (outcome.finalState() instanceof IncidentState.Resolved resolved) {
                    System.out.println("Postmortem: " + resolved.postmortem().summary());
                } else if (outcome.finalState() instanceof IncidentState.Escalated escalated) {
                    System.out.println("Escalated: " + escalated.reason());
                }
            } catch (RuntimeException e) {
                System.out.println();
                System.out.println("Incident demo failed: " + e.getMessage());
                System.out.println("Check the model endpoint — set NOOA_BASE_URL/NOOA_MODEL, "
                    + "or start a local Ollama on http://localhost:11434.");
                return;
            }
        }
    }

    private static ApprovalGate consoleGate() {
        if (System.console() == null) {
            return plan -> {
                System.out.println("[auto-approve] " + plan.steps());
                return true;
            };
        }
        return plan -> {
            System.out.println();
            System.out.println("Proposed remediation:");
            plan.steps().forEach(step -> System.out.println("  - " + step));
            System.out.println("Risk: " + plan.risk() + " | Rollback: " + plan.rollback());
            System.out.print("Approve? [y/N] ");
            System.out.flush();
            try (var reader = new BufferedReader(new InputStreamReader(System.in))) {
                String line = reader.readLine();
                return line != null && line.trim().equalsIgnoreCase("y");
            } catch (java.io.IOException e) {
                return false;
            }
        };
    }
}
