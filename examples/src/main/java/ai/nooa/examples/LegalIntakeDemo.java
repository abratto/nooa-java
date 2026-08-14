package ai.nooa.examples;

import ai.nooa.AgentFactory;

public final class LegalIntakeDemo {
    public static void main(String[] args) throws Exception {
        var llm = ExampleLLM.create();
        var agent = AgentFactory.create(LegalIntakeAgent.class, llm);

        String[] messages = {
            "My landlord hasn't fixed the heating for 3 months. It's freezing.",
            "URGENT: I'm being evicted tomorrow with no notice!",
            "I need help drafting a non-compete clause for my startup.",
            "My ex-spouse is violating the custody agreement. Again.",
        };

        for (String msg : messages) {
            System.out.println("\n=== Incoming: " + msg);
            LegalIntakeResult response = agent.handleStructured(msg);
            System.out.println("Result: " + response);
            System.out.println("category=" + response.category());
            System.out.println("urgency=" + response.urgency());
            System.out.println("nextStep=" + response.nextStep());
            System.out.println("summary=" + response.summary());
        }

        agent.close();
    }
}
