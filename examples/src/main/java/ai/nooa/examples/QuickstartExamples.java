package ai.nooa.examples;

import ai.nooa.Agent;
import ai.nooa.AgentFactory;
import ai.nooa.annotations.Generate;
import ai.nooa.annotations.Hidden;
import ai.nooa.annotations.Strategy;
import ai.nooa.annotations.SystemPrompt;
import ai.nooa.llm.UnifiedLLM;
import ai.nooa.strategy.PredictStrategy;

import java.util.Map;

@SystemPrompt("You are a friendly greeting agent who speaks in haiku form.")
class GreetingAgent extends Agent {
    public GreetingAgent(UnifiedLLM llm) { super(llm); }
    @Generate
    public String greet(String name) { throw new UnsupportedOperationException(); }
}

record SentimentResult(String sentiment, double confidence, String reasoning) {}

@SystemPrompt("You are a sentiment analysis agent. Return valid JSON.")
class SentimentAgent extends Agent {
    public SentimentAgent(UnifiedLLM llm) { super(llm); }
    @Generate @Strategy(PredictStrategy.class)
    public SentimentResult analyze(String text) { throw new UnsupportedOperationException(); }
}

@SystemPrompt("You are a support agent. Use the available methods to help users.")
class SupportAgent extends Agent {
    @Hidden private final Map<String, Integer> inventory;
    public SupportAgent(UnifiedLLM llm, Map<String, Integer> inventory) {
        super(llm);
        this.inventory = Map.copyOf(inventory);
    }
    int getStock(String item) { return inventory.getOrDefault(item.toLowerCase(), 0); }
    @Generate
    public String checkOrder(String item, int quantity) { throw new UnsupportedOperationException(); }
}

@SystemPrompt("You summarise incoming news articles into a short digest.")
class NewsDigestAgent extends Agent {
    public NewsDigestAgent(UnifiedLLM llm) { super(llm); }

    String fetchArticle() {
        return "Acme announced a new battery chemistry that cuts charging time by 40% while reducing heat and extending cycle life.";
    }

    @Generate
    public String summarizeNews(String articleText) { throw new UnsupportedOperationException(); }

    public String digestCurrentNews() {
        return summarizeNews(fetchArticle());
    }
}

public final class QuickstartExamples {

    public static void main(String[] args) throws Exception {
        var llm = UnifiedLLM.create(
            UnifiedLLM.openAI(System.getenv("OPENAI_API_KEY"), "gpt-4o").build());

        System.out.println("--- Example 1: Greeting ---");
        var greetingAgent = AgentFactory.create(GreetingAgent.class, llm);
        System.out.println("Agent created. Would call: greetingAgent.greet(\"Alice\")");
        greetingAgent.close();

        System.out.println("\n--- Example 2: Sentiment ---");
        var sentimentAgent = AgentFactory.create(SentimentAgent.class, llm);
        System.out.println("Agent created. Would call: sentimentAgent.analyze(\"...\")");
        sentimentAgent.close();

        System.out.println("\n--- Example 3: Support Agent ---");
        var supportAgent = AgentFactory.create(SupportAgent.class, llm,
            Map.of("widget", 5, "gadget", 0));
        System.out.println("Agent created. Would call: supportAgent.checkOrder(\"widget\", 3)");
        supportAgent.close();

        System.out.println("\n--- Example 4: Periodic News Digest ---");
        var newsAgent = AgentFactory.create(NewsDigestAgent.class, llm);
        System.out.println("Agent created. Would call: newsAgent.digestCurrentNews() ");
        newsAgent.close();
    }
}
