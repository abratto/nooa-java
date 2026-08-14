package ai.nooa.examples;

import ai.nooa.Agent;
import ai.nooa.annotations.Generate;
import ai.nooa.annotations.Hidden;
import ai.nooa.annotations.SystemPrompt;
import ai.nooa.llm.UnifiedLLM;

import java.util.ArrayList;
import java.util.List;

@SystemPrompt("You are a project manager. Use the current project status as the only source of truth. Create a realistic, short daily plan that prioritizes completed tasks, blocked work, and the next most important action. Do not invent tasks or claim items are complete unless the status shows they are.")
public class ProjectAgent extends Agent {
    public record Task(String name, boolean complete) {}

    @Hidden private final List<Task> tasks = new ArrayList<>();

    public ProjectAgent(UnifiedLLM llm) {
        super(llm);
        context().putDynamic("project_status", "self.formatProjectStatus()");
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

    @Generate
    public String planDay() { throw new UnsupportedOperationException(); }
}
