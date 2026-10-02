package ai.nooa.examples.release;

import ai.nooa.AgentFactory;
import ai.nooa.examples.ExampleLLM;

/** Runs the enum-style {@link ReleaseGateAgent} through a few outcomes. */
public final class ReleaseDemo {

    private ReleaseDemo() {}

    public static void main(String[] args) {
        var llm = ExampleLLM.create();
        record Case(String label, boolean testsPass, boolean approve) {}
        var cases = java.util.List.of(
            new Case("green build, approved", true, true),
            new Case("failing tests", false, true),
            new Case("approved build, declined", true, false));

        for (Case c : cases) {
            try (var agent = AgentFactory.create(ReleaseGateAgent.class, llm)) {
                String result = agent.run("TICKET-42 add pagination", c.testsPass(), c.approve());
                System.out.println();
                System.out.println("── " + c.label() + " ──");
                agent.history().forEach(step -> System.out.println("  - " + step));
                System.out.println("  " + result);
            }
        }
    }
}
