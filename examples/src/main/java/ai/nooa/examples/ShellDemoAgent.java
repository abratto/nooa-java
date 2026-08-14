package ai.nooa.examples;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.llm.UnifiedLLM;

import java.nio.file.Path;

public class ShellDemoAgent extends Agent {
    public ShellDemoAgent(UnifiedLLM llm) { super(llm); }

    String readFile(String path) throws java.io.IOException {
        return java.nio.file.Files.readString(Path.of(path));
    }

    @Generate
    public String reviewCode(String filePath) {
        throw new UnsupportedOperationException();
    }
}
