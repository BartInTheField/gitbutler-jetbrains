package me.inthefield.gitbutlerforjetbrains.toolwindow;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangesViewWorkflowManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.vcs.commit.ChangesViewCommitWorkflowHandler;

import java.util.Collection;

/**
 * Java on purpose: {@link ChangesViewCommitWorkflowHandler} is Kotlin-{@code internal}, so
 * Kotlin sources cannot reference it, but it is {@code public} in bytecode and its
 * {@code setCommitState} is the platform's own mechanism for replacing the non-modal commit
 * inclusion (what the Commit tool window shows as checked). Javac only sees the bytecode
 * visibility, giving a statically-typed call without reflection.
 */
final class CommitInclusion {

    private CommitInclusion() {
    }

    /**
     * Replaces the Commit tool window's checked state with {@code items} ({@code Change}s for
     * tracked files, {@code FilePath}s for unversioned ones). Returns false when the non-modal
     * commit workflow handler is unavailable (e.g. commit-dialog mode).
     */
    static boolean setInclusion(Project project, LocalChangeList changeList, Collection<Object> items) {
        ChangesViewCommitWorkflowHandler handler =
                ChangesViewWorkflowManager.getInstance(project).getCommitWorkflowHandler();
        if (handler == null) {
            return false;
        }
        handler.setCommitState(changeList, items, true);
        return true;
    }
}
