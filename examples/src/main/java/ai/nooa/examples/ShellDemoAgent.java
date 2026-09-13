package ai.nooa.examples;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.llm.UnifiedLLM;

import java.nio.file.Path;

/**
 * Java helper supplies the artifact; the model reviews it.
 *
 * <p><b>NOOA features demonstrated</b></p>
 * <ul>
 *   <li>File access stays in Java — {@link #readFile(String)} performs the
 *       I/O deterministically. The model reviews the content it is handed; it
 *       does not get filesystem access of its own. For broader file work,
 *       combine {@link ai.nooa.tools.ShellTools} with a {@code Permissions}
 *       policy.</li>
 *   <li>Visibility rule — {@code readFile} is public so it appears in
 *       {@code AgentDoc} and is callable from generated code via
 *       {@code __agent__.readFile(path)}. Package-private methods are not
 *       model-visible.</li>
 *   <li>Helper-to-capability hand-off — the CodeAct loop can call
 *       {@code __agent__.readFile(path)} and reason over the returned source
 *       text, demonstrating pass-by-reference for large artifacts without
 *       duplicating them into the prompt by hand.</li>
 *   <li>Grounded review prompt — the instruction forbids inventing unseen
 *       code, keeping the review tied to the file contents.</li>
 *   <li>Reasoning effort — code review benefits from care, but the demo file
 *       is small: {@code "low"} keeps local runs fast; raise it for real
 *       review workloads.</li>
 * </ul>
 *
 * <p>There is no bundled launcher for this agent; use it from application code
 * exactly as {@link QuickstartExamples} uses the other agents:
 * <pre>{@code
 * var agent = AgentFactory.create(ShellDemoAgent.class, ExampleLLM.create());
 * System.out.println(agent.reviewCode("path/to/File.java"));
 * }</pre>
 */
public class ShellDemoAgent extends Agent {
    public ShellDemoAgent(UnifiedLLM llm) {
        super(llm);
        ExampleLLM.tune(this, "low", 4096);
    }

    public String readFile(String path) throws java.io.IOException {
        return java.nio.file.Files.readString(Path.of(path));
    }

    @Generate(prompt = "Review the provided source file using the helper output. Identify correctness issues and practical improvements without inventing unseen code.")
    public String reviewCode(String filePath) {
        throw new UnsupportedOperationException();
    }
}
