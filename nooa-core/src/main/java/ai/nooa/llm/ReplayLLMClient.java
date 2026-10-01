package ai.nooa.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serves LLM responses from a {@link RecordingLLMClient} recording, matching
 * requests by fingerprint.
 *
 * <p>By default a missing recording fails hard so replay runs cannot silently
 * diverge. Use {@link #fallThroughOnMiss(UnifiedLLM)} to delegate misses to a
 * live client instead.</p>
 */
public final class ReplayLLMClient extends UnifiedLLM {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Map<String, LLMResponse> responses;
    private final String recordedModel;
    private UnifiedLLM fallback;

    public ReplayLLMClient(Path file) {
        super("replay", "replay://", "replay");
        Loaded loaded = load(file);
        this.responses = loaded.responses();
        this.recordedModel = loaded.model();
    }

    /** Delegate to {@code fallback} when a request has no recorded response. */
    public ReplayLLMClient fallThroughOnMiss(UnifiedLLM fallback) {
        this.fallback = fallback;
        return this;
    }

    @Override
    public LLMResponse chat(List<Message> messages, List<Tool> tools,
                            Type outputModel, Map<String, Object> samplingParams) {
        String fingerprint = ReplayFingerprint.of(
            recordedModel, messages, tools, outputModel, samplingParams);
        LLMResponse response = responses.get(fingerprint);
        if (response != null) {
            return response;
        }
        if (fallback != null) {
            return outputModel instanceof Class<?> clazz
                ? fallback.chat(messages, tools, clazz, samplingParams)
                : fallback.chat(messages, tools, outputModel, samplingParams);
        }
        throw new LLMException("No recorded response for request (fingerprint "
            + fingerprint.substring(0, Math.min(12, fingerprint.length())) + ")", 0);
    }

    private record Loaded(Map<String, LLMResponse> responses, String model) {}

    private static Loaded load(Path file) {
        Map<String, LLMResponse> map = new LinkedHashMap<>();
        String model = "replay";
        if (!Files.exists(file)) {
            return new Loaded(map, model);
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode node = JSON.readTree(line);
                String fingerprint = node.path("fingerprint").asText();
                LLMResponse response = JSON.treeToValue(node.get("response"), LLMResponse.class);
                map.put(fingerprint, response);
                if (node.hasNonNull("model")) {
                    model = node.get("model").asText();
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load recording " + file, e);
        }
        return new Loaded(map, model);
    }
}
