package com.git.hui.jobclaw.plugins.plannotebook;

import com.git.hui.jobclaw.plugins.plannotebook.model.SubTaskState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanNotebookTest {

    @TempDir
    Path workspace;

    @Test
    void persistsAndReloadsUserPlan() throws Exception {
        PlanNotebook notebook = notebook();

        notebook.create("user-1", "Find a job", List.of("Collect preferences", "Search jobs"));
        notebook.updateSubtask("user-1", 0, SubTaskState.DONE, "Preferences collected");

        PlanNotebook reloaded = notebook();
        var plan = reloaded.getCurrent("user-1").orElseThrow();

        assertThat(plan.name()).isEqualTo("Find a job");
        assertThat(plan.subtasks().get(0).state()).isEqualTo(SubTaskState.DONE);
        assertThat(plan.subtasks().get(0).result()).isEqualTo("Preferences collected");
        assertThat(reloaded.generateHint("user-1")).contains("Next action: Search jobs");
    }

    @Test
    void isolatesUsersAndAllowsOnlyOneInProgressSubtask() throws Exception {
        PlanNotebook notebook = notebook();
        notebook.create("user-1", "Plan one", List.of("First", "Second"));
        notebook.create("user-2", "Plan two", List.of("Other"));

        notebook.updateSubtask("user-1", 0, SubTaskState.IN_PROGRESS, null);

        assertThatThrownBy(() -> notebook.updateSubtask("user-1", 1, SubTaskState.IN_PROGRESS, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only one");
        assertThat(notebook.getCurrent("user-2").orElseThrow().name()).isEqualTo("Plan two");
    }

    @Test
    void rejectsPathTraversalUserId() throws Exception {
        PlanNotebook notebook = notebook();

        assertThatThrownBy(() -> notebook.create("../outside", "Bad", List.of("Nope")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid");
    }

    private PlanNotebook notebook() throws Exception {
        return new PlanNotebook(new FileSystemResource(workspace));
    }
}
