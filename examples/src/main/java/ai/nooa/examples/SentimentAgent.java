package ai.nooa.examples;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.annotations.Strategy;
import ai.nooa.annotations.SystemPrompt;
import ai.nooa.llm.UnifiedLLM;
import ai.nooa.strategy.PredictStrategy;

record SentimentResult(String sentiment, double confidence, String reasoning) {}

@SystemPrompt("You are a sentiment analysis agent. Analyze the text and return valid JSON with exactly these fields: sentiment (positive | neutral | negative), confidence (number between 0 and 1), reasoning (one short sentence, max 18 words). Do not add extra keys or prose.")
public class SentimentAgent extends Agent {
    public SentimentAgent(UnifiedLLM llm) { super(llm); }

    @Generate @Strategy(PredictStrategy.class)
    public SentimentResult analyze(String text) { throw new UnsupportedOperationException(); }
}
