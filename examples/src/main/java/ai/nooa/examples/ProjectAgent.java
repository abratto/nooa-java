package ai.nooa.examples;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.annotations.Hidden;
import ai.nooa.annotations.SystemPrompt;
import ai.nooa.llm.UnifiedLLM;

import java.util.ArrayList;
import java.util.List;

/**
 * Object state in Java, rendered to the model through a dynamic context block.
 *
 * <p><b>NOOA features demonstrated</b></p>
 * <ul>
 *   <li>Object state — {@link #tasks} is an ordinary mutable field. The agent
 *       is a real object with a lifecycle, not a stateless prompt wrapper.</li>
 *   <li>Dynamic context block — {@code context().putDynamic("project_status",
 *       "self.formatProjectStatus()")} re-evaluates
 *       {@link #formatProjectStatus()} before <em>every</em> model call, so the
 *       prompt always reflects the current state without rebuilding it.</li>
 *   <li>Expression resolution — the {@code self.formatProjectStatus()}
 *       expression is evaluated against the live agent instance by the
 *       runtime's expression evaluator.</li>
 *   <li>{@code @Hidden} state + public renderer — the task list is hidden from
 *       the model's API surface while the derived one-line summary stays
 *       visible in the prompt. Raw state stays in Java; the model sees a
 *       compact, controlled projection.</li>
 *   <li>Deterministic mutators — {@link #addTask(String)} and
 *       {@link #completeTask(String)} are plain Java; the model never edits
 *       the list directly.</li>
 *   <li>Reasoning effort — planning from a one-line status summary:
 *       {@code "low"} is enough.</li>
 * </ul>
 *
 * <p><b>Run</b> via {@link QuickstartAdvanced} (Example 07):
 * <pre>{@code
 * mvn -pl examples exec:java -Dexec.mainClass=ai.nooa.examples.QuickstartAdvanced
 * }</pre>
 */
@SystemPrompt("You are a project manager. Use the current project status as the only source of truth. Create a realistic, short daily plan that prioritizes completed tasks, blocked work, and the next most important action. Do not invent tasks or claim items are complete unless the status shows they are.")
public class ProjectAgent extends Agent {
    public record Task(String name, boolean complete) {}

    @Hidden private final List<Task> tasks = new ArrayList<>();

    public ProjectAgent(UnifiedLLM llm) {
        super(llm);
        // Register the dynamic block once; the runtime re-renders it per call.
        context().putDynamic("project_status", "self.formatProjectStatus()");
        ExampleLLM.tune(this, "low", 2048);
    }

    public String formatProjectStatus() {
        long done = tasks.stream().filter(Task::complete).count();
        return "Tasks: " + done + "/" + tasks.size() + " complete";
    }

    void addTask(String name) { tasks.add(new Task(name, false)); }

    void completeTask(String name) {
        tasks.stream().filter(t -> t.name().equals(name)).findFirst()
            .ifPresent(t -> tasks.set(tasks.indexOf(t), new Task(name, true)));
    }

    @Generate(prompt = "Create a short daily plan from the current project status. Prioritize blocked work and the next most important action; do not invent tasks.")
    public String planDay() { throw new UnsupportedOperationException(); }
}
