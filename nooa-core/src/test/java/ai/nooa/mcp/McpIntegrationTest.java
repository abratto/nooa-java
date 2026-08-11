package ai.nooa.mcp;

import org.junit.jupiter.api.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("MCP Integration")
class McpIntegrationTest {

    private McpClient client;

    @BeforeEach
    void setUp() {
        String javaHome = System.getProperty("java.home");
        String classpath = System.getProperty("java.class.path");
        client = McpClient.stdio(List.of(
            javaHome + "/bin/java",
            "-cp", classpath,
            MockMcpServer.class.getName()
        ));
    }

    @AfterEach
    void tearDown() {
        if (client != null) client.close();
    }

    @Test
    @DisplayName("connect and discover tools")
    void discoverTools() {
        var tools = client.discoverTools();
        assertThat(tools).hasSize(2);
        assertThat(tools.stream().map(McpToolSpec::name))
            .contains("echo", "add");
    }

    @Test
    @DisplayName("call echo tool")
    void callEcho() {
        Object result = client.callTool("echo", Map.of("message", "hello world"));
        assertThat(result.toString()).contains("ECHO: hello world");
    }

    @Test
    @DisplayName("call add tool")
    void callAdd() {
        Object result = client.callTool("add", Map.of("a", 3, "b", 4));
        assertThat(result.toString()).contains("7");
    }

    @Test
    @DisplayName("McpManager manages multiple servers")
    void mcpManager() {
        var manager = new McpManager();
        manager.connectStdio("mock", List.of(
            System.getProperty("java.home") + "/bin/java",
            "-cp", System.getProperty("java.class.path"),
            MockMcpServer.class.getName()
        ));

        assertThat(manager.serverNames()).contains("mock");

        var tools = manager.toolsFor("mock");
        assertThat(tools).hasSize(2);

        var result = manager.callTool("mock", "echo",
            Map.of("message", "test"));
        assertThat(result.toString()).contains("ECHO: test");

        manager.close();
    }
}
