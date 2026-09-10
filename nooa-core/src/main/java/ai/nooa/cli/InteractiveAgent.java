package ai.nooa.cli;

import ai.nooa.Agent;
import ai.nooa.AgentFactory;
import ai.nooa.annotations.Generate;
import ai.nooa.llm.UnifiedLLM;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import java.util.*;
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

    public enum TurnStatus { COMPLETED, NEED_INPUT, COMMAND, ERROR }

    public record TurnResult(TurnStatus status, String output, String error,
                             Object value) {
        public static TurnResult completed(Object value) {
            return new TurnResult(TurnStatus.COMPLETED,
                value == null ? "" : value.toString(), null, value);
        }

        public static TurnResult needInput(String prompt) {
            return new TurnResult(TurnStatus.NEED_INPUT, prompt, null, null);
        }

        public static TurnResult error(Throwable error) {
            Throwable cause = error instanceof InvocationTargetException ite
                && ite.getCause() != null ? ite.getCause() : error;
            return new TurnResult(TurnStatus.ERROR, null,
                cause.getMessage() == null ? cause.toString() : cause.getMessage(), null);
        }
    }

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

    /** Create an interactive session without starting a console loop. */
    public static InteractiveAgent create(Agent agent) {
        return new InteractiveAgent(agent);
    }

    public InteractiveAgent onOutput(Consumer<String> handler) {
        this.outputHandler = handler;
        return this;
    }

    /** Queue a user turn without starting the console loop. */
    public InteractiveAgent submit(String message) {
        queueManager.submit("user", message);
        return this;
    }

    /** Queue a system event for the next turn. */
    public InteractiveAgent system(Object event) {
        queueManager.submit("system", event);
        return this;
    }

    /** Execute one queued user/system turn. */
    public TurnResult turn() {
        var item = queueManager.poll(0);
        if (item.isEmpty()) {
            return TurnResult.needInput("Waiting for input");
        }
        if (!"user".equals(item.get().channel())) {
            return TurnResult.completed(item.get().payload());
        }
        String message = item.get().payload().toString();
        return message.startsWith("/") ? dispatchCommand(message) : dispatchMessage(message);
    }

    /** Register a custom slash command. */
    public InteractiveAgent command(String name, String description,
                                     Consumer<List<String>> handler) {
        commands.put("/" + name, new Command(description, handler));
        return this;
    }

    /** Execute a slash command and return its typed outcome. */
    public TurnResult commandResult(String input) {
        return dispatchCommand(input);
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

        try (var scanner = new Scanner(System.in)) {
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
    }

    private void handleCommand(String input) {
        TurnResult result = dispatchCommand(input);
        if (result.output() != null && !result.output().isBlank()) output(result.output());
        if (result.error() != null) output("Error: " + result.error());
    }

    private void handleMessage(String input) {
        output("Processing: " + input);
        submit(input);
        TurnResult result = turn();
        if (result.output() != null && !result.output().isBlank()) output(result.output());
        if (result.error() != null) output("Error: " + result.error());
    }

    private TurnResult dispatchMessage(String input) {
        try {
            Method method = findMessageMethod(agent.getClass());
            return TurnResult.completed(method.invoke(agent, input));
        } catch (ReflectiveOperationException | RuntimeException error) {
            return TurnResult.error(error);
        }
    }

    private TurnResult dispatchCommand(String input) {
        String[] parts = input.strip().split("\\s+");
        String commandName = parts[0].toLowerCase(Locale.ROOT);
        List<String> args = parts.length > 1
            ? Arrays.asList(parts).subList(1, parts.length) : List.of();
        if ("/exit".equals(commandName) || "/quit".equals(commandName)) {
            running = false;
            return new TurnResult(TurnStatus.COMMAND, "Goodbye.", null, commandName);
        }
        Command command = commands.get(commandName);
        if (command == null) {
            return new TurnResult(TurnStatus.ERROR, null,
                "Unknown command: " + commandName, commandName);
        }
        try {
            command.handler.accept(args);
            return new TurnResult(TurnStatus.COMMAND, null, null, commandName);
        } catch (Exception error) {
            return TurnResult.error(error);
        }
    }

    private static Method findMessageMethod(Class<?> type) {
        for (Class<?> current = type; current != null && current != Agent.class;
             current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Generate.class)
                    && method.getParameterCount() == 1
                    && method.getParameterTypes()[0] == String.class) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        throw new IllegalStateException(
            "Interactive agent requires a one-String @Generate method");
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
