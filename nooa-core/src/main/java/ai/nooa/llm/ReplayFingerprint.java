package ai.nooa.llm;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * Stable fingerprint of an LLM request, used to key recorded responses for
 * deterministic replay. Mirrors the prompt-fingerprint convention used by
 * {@code PromptRecorder}.
 */
final class ReplayFingerprint {

    private static final ObjectMapper JSON = new ObjectMapper();

    private ReplayFingerprint() {}

    static String of(String model, List<Message> messages, List<Tool> tools,
                     java.lang.reflect.Type outputModel, Map<String, Object> samplingParams) {
        try {
            StringBuilder material = new StringBuilder();
            material.append(model == null ? "" : model).append('|')
                .append(outputModel == null ? "" : outputModel.getTypeName()).append('|')
                .append(JSON.writeValueAsString(tools == null ? List.of() : tools)).append('|')
                .append(JSON.writeValueAsString(messages == null ? List.of() : messages)).append('|')
                .append(JSON.writeValueAsString(samplingParams == null ? Map.of() : samplingParams));
            return sha256(material.toString());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Failed to fingerprint LLM request", e);
        }
    }

    private static String sha256(String value) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
