package ai.nooa.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;

/**
 * Wraps another {@link UnifiedLLM} and records every request/response pair as
 * JSONL so a run can be replayed deterministically (see {@link ReplayLLMClient}).
 *
 * <p>Appends eagerly after each response, so a partial recording remains usable
 * when a run fails midway. The wrapped client's model name is adopted so
 * pricing and traces stay accurate.</p>
 */
public final class RecordingLLMClient extends UnifiedLLM implements AutoCloseable {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Object LOCK = new Object();

    private final UnifiedLLM delegate;
    private final Path file;

    public RecordingLLMClient(UnifiedLLM delegate, Path file) {
        super("recording", "recording://", delegate.model());
        this.delegate = delegate;
        this.file = file;
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to prepare recording file " + file, e);
        }
    }

    /**
     * Records at the {@link Type} overload, which the {@code Class} overload
     * delegates to, so both entry points are captured.
     */
    @Override
    public LLMResponse chat(List<Message> messages, List<Tool> tools,
                            Type outputModel, Map<String, Object> samplingParams) {
        // Prefer the Class overload when possible: some clients (and the
        // test-scope FakeLLMClient) only override the Class entry point.
        LLMResponse response = outputModel instanceof Class<?> clazz
            ? delegate.chat(messages, tools, clazz, samplingParams)
            : delegate.chat(messages, tools, outputModel, samplingParams);
        append(ReplayFingerprint.of(delegate.model(), messages, tools, outputModel, samplingParams),
            outputModel, response);
        return response;
    }

    private void append(String fingerprint, Type outputModel, LLMResponse response) {
        ObjectNode line = JSON.createObjectNode();
        line.put("fingerprint", fingerprint);
        line.put("model", delegate.model());
        line.put("outputModel", outputModel == null ? null : outputModel.getTypeName());
        line.set("response", JSON.valueToTree(response));
        try {
            synchronized (LOCK) {
                Files.writeString(file, line.toString() + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to append recording to " + file, e);
        }
    }

    @Override
    public void close() {
        // Appends are eager; nothing to flush.
    }
}
