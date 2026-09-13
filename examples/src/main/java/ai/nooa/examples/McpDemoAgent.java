package ai.nooa.examples;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.llm.UnifiedLLM;

/**
 * Agent type for MCP-backed capabilities.
 *
 * <p><b>NOOA features demonstrated</b></p>
 * <ul>
 *   <li>A minimal {@code @Generate} surface that an application can combine
 *       with MCP tools discovered by {@link ai.nooa.mcp.McpManager}.</li>
 * </ul>
 *
 * <p><b>Important wiring note.</b> MCP tools are <em>not</em> automatically
 * attached to an agent. {@link McpDemo} shows the standalone flow:
 * {@code McpManager.connectStdio(...)} discovers tools over a transport and
 * {@code callTool(...)} invokes them. To offer MCP tools to the model inside a
 * CodeAct loop, an application passes {@code mcp.allTools()} into its own
 * strategy configuration or wraps the manager in a helper method the model can
 * call via {@code __agent__}. This agent exists as the typed counterpart for
 * such setups.</p>
 *
 * <p><b>Run</b> via {@link McpDemo} (requires Node.js/npx; no model call):
 * <pre>{@code
 * mvn -pl examples exec:java -Dexec.mainClass=ai.nooa.examples.McpDemo
 * }</pre>
 */
public class McpDemoAgent extends Agent {
    public McpDemoAgent(UnifiedLLM llm) {
        super(llm);
        ExampleLLM.tune(this, "low", 2048);
    }

    @Generate(prompt = "Analyze the input using the available MCP-backed capabilities and return a concise, grounded answer.")
    public String analyze(String input) {
        throw new UnsupportedOperationException();
    }
}
