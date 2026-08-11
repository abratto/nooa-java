package ai.nooa.cli;

import ai.nooa.Agent;
import ai.nooa.AgentFactory;
import ai.nooa.annotations.Generate;
import ai.nooa.llm.UnifiedLLM;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Interactive console-based agent. Reads user input, dispatches
 * slash commands, and routes messages to @Generate methods.
 *
 * <pre>{@code
 * var agent = AgentFactory.create(MyAgent.class, llm);
 * InteractiveAgent.run(agent);
 * }</pre>
 */
public final class InteractiveAgent {

    private final Agent agent;
    private final QueueManager queueManager;
    private final Map<String, Command> commands = new LinkedHashMap<>();
    private final List<String> history = new ArrayList<>();
    private boolean running = true;
    private Consumer<String> outputHandler = System.out::println;

    private InteractiveAgent(Agent agent) {
        this.agent = agent;
        this.queueManager = new QueueManager();
        registerBuiltins();
    }

    /** Create and start an interactive session. */
    public static InteractiveAgent run(Agent agent) {
        var ia = new InteractiveAgent(agent);
        ia.start();
        return ia;
    }

    public InteractiveAgent onOutput(Consumer<String> handler) {
        this.outputHandler = handler;
        return this;
    }

    /** Register a custom slash command. */
    public InteractiveAgent command(String name, String description,
                                     Consumer<List<String>> handler) {
        commands.put("/" + name, new Command(description, handler));
        return this;
    }

    private void registerBuiltins() {
        command("help", "Show available commands", args -> {
            output("Available commands:");
            for (var entry : commands.entrySet()) {
                output("  " + entry.getKey() + " — " + entry.getValue().description);
            }
            output("  /exit    — Exit the session");
            output("  /clear   — Clear conversation history");
            output("  /history — Show command history");
            output("  /model   — Show current model");
        });

        command("clear", "Clear conversation history", args -> {
            agent.eventManager().clear();
            output("Conversation cleared.");
        });

        command("history", "Show command history", args -> {
            for (int i = 0; i < history.size(); i++) {
                output("  " + (i + 1) + ". " + history.get(i));
            }
        });

        command("model", "Show current model", args -> {
            output("Model: " + agent.llm().model());
        });
    }

    private void start() {
        output("=== NOOA Interactive Agent ===");
        output("Agent: " + agent.getClass().getSimpleName());
        output("Model: " + agent.llm().model());
        output("Type /help for commands, or just type to chat.");
        output("");

        var scanner = new Scanner(System.in);
        while (running) {
            System.out.print("> ");
            if (!scanner.hasNextLine()) break;
            String input = scanner.nextLine().strip();
            if (input.isEmpty()) continue;

            history.add(input);

            if (input.startsWith("/")) {
                handleCommand(input);
            } else {
                handleMessage(input);
            }
        }
    }

    private void handleCommand(String input) {
        String[] parts = input.split("\\s+");
        String cmd = parts[0].toLowerCase();
        List<String> args = parts.length > 1
            ? Arrays.asList(parts).subList(1, parts.length)
            : List.of();

        if ("/exit".equals(cmd) || "/quit".equals(cmd)) {
            running = false;
            output("Goodbye.");
            return;
        }

        Command command = commands.get(cmd);
        if (command != null) {
            try {
                command.handler.accept(args);
            } catch (Exception e) {
                output("Error: " + e.getMessage());
            }
        } else {
            output("Unknown command: " + cmd + ". Type /help for available commands.");
        }
    }

    private void handleMessage(String input) {
        // Find a @Generate method to route the message to
        output("Processing: " + input);
        queueManager.submit(input);

        // For a real implementation, this would call the agent's generation method.
        // Here we signal that the message was queued.
    }

    public void output(String message) {
        outputHandler.accept(message);
    }

    public Agent agent() { return agent; }
    public QueueManager queueManager() { return queueManager; }

    public void stop() { running = false; }

    private record Command(String description, Consumer<List<String>> handler) {}

    // ---- Main entry point ----

    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null) {
            System.err.println("Set OPENAI_API_KEY environment variable.");
            System.exit(1);
        }

        var llm = UnifiedLLM.create(
            UnifiedLLM.openAI(apiKey, "gpt-4o").build());

        // Create a minimal interactive agent
        var agent = AgentFactory.create(InteractiveChatAgent.class, llm);
        run(agent);
    }

    /** Minimal agent for interactive chat demos. */
    public static class InteractiveChatAgent extends Agent {
        public InteractiveChatAgent(UnifiedLLM llm) { super(llm); }

        @Generate
        public String chat(String message) { throw new UnsupportedOperationException(); }
    }
}
