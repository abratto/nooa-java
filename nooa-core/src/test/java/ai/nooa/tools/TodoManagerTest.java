package ai.nooa.tools;

import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("TodoManager")
class TodoManagerTest {

    @Test
    @DisplayName("add and retrieve task")
    void addAndRetrieve() {
        var tm = new TodoManager();
        var task = tm.add("Write tests", "high");
        assertThat(task.title()).isEqualTo("Write tests");
        assertThat(task.status()).isEqualTo(TodoManager.Status.PENDING);
    }

    @Test
    @DisplayName("mark completed transitions status")
    void markCompleted() {
        var tm = new TodoManager();
        var task = tm.add("Fix bug", "high");
        tm.markCompleted(task.id());
        assertThat(tm.get(task.id()).get().status()).isEqualTo(TodoManager.Status.COMPLETED);
    }

    @Test
    @DisplayName("active excludes completed and cancelled")
    void activeExcludesDone() {
        var tm = new TodoManager();
        var t1 = tm.add("A", "high");
        var t2 = tm.add("B", "low");
        tm.markCompleted(t1.id());
        tm.markCancelled(t2.id());
        assertThat(tm.active()).isEmpty();
    }

    @Test
    @DisplayName("showActive renders active tasks")
    void showActive() {
        var tm = new TodoManager();
        tm.add("Task 1", "high");
        tm.add("Task 2", "medium");
        String output = tm.showActive();
        assertThat(output).contains("Task 1").contains("Task 2");
    }
}
