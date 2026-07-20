package me.inthefield.gitbutlerforjetbrains.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffAction
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.ui.treeStructure.Tree
import com.intellij.vcs.log.impl.VcsLogContentUtil
import me.inthefield.gitbutlerforjetbrains.core.ButResult
import me.inthefield.gitbutlerforjetbrains.core.ButStack
import me.inthefield.gitbutlerforjetbrains.core.GitButlerService
import me.inthefield.gitbutlerforjetbrains.core.UncommittedChange
import me.inthefield.gitbutlerforjetbrains.core.VirtualBranch
import me.inthefield.gitbutlerforjetbrains.core.WorkspaceStatus
import com.intellij.openapi.ui.SimpleToolWindowPanel
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.KeyStroke
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * Renders the GitButler workspace as a tree — unassigned changes, then each stack's
 * branches and their commits — visually analogous to `but status`. A Refresh toolbar
 * button re-runs the load. `status()` always runs on a pooled thread; every Swing
 * mutation happens on the EDT.
 */
class GitButlerStatusPanel(private val project: Project) : SimpleToolWindowPanel(true, true) {

    private val rootNode = DefaultMutableTreeNode()
    private val treeModel = DefaultTreeModel(rootNode)
    private val tree = Tree(treeModel).apply {
        isRootVisible = false
        showsRootHandles = true
        cellRenderer = StatusCellRenderer()
        emptyText.text = "Loading GitButler status…"
    }

    init {
        toolbar = buildToolbar()
        setContent(JBScrollPane(tree))
        installActivation()
    }

    /**
     * Makes rows navigable: double-clicking or pressing Enter on a change row opens its
     * working-tree diff, and on a commit row jumps to that commit in the Git Log. Other rows
     * fall through to the default tree behaviour (expand/collapse). [activate] returns true only
     * when it handled the node, so a double-click on a container still toggles it.
     */
    private fun installActivation() {
        object : DoubleClickListener() {
            override fun onDoubleClick(event: MouseEvent): Boolean {
                val path = tree.getPathForLocation(event.x, event.y) ?: return false
                return activate(path.lastPathComponent)
            }
        }.installOn(tree)

        tree.registerKeyboardAction(
            {
                val path = tree.selectionPath ?: return@registerKeyboardAction
                // Non-navigable rows keep their default Enter behaviour: toggle expansion.
                if (!activate(path.lastPathComponent)) {
                    if (tree.isExpanded(path)) tree.collapsePath(path) else tree.expandPath(path)
                }
            },
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
            JComponent.WHEN_FOCUSED,
        )
    }

    /** Dispatches on the node payload; returns true iff activation consumed the event. */
    private fun activate(component: Any?): Boolean {
        return when (val payload = (component as? DefaultMutableTreeNode)?.userObject) {
            is ChangeNode -> {
                openChangeDiff(payload.change)
                true
            }

            is CommitNode -> {
                VcsLogContentUtil.runInMainLog(project) { logUi ->
                    logUi.vcsLog.jumpToReference(payload.commitId)
                }
                true
            }

            else -> false
        }
    }

    private fun openChangeDiff(change: UncommittedChange) {
        val root = repoRoot() ?: return
        val absolutePath = "${root.path}/${change.filePath}"
        // LocalFilePath (not a VirtualFile lookup) so deleted files still resolve to a Change.
        val vcsChange = ChangeListManager.getInstance(project).getChange(LocalFilePath(absolutePath, false))
        if (vcsChange != null) {
            ShowDiffAction.showDiffForChange(project, listOf(vcsChange))
            return
        }
        // Untracked / unknown to VCS: fall back to opening the file, or silently do nothing.
        val file = root.findFileByRelativePath(change.filePath) ?: return
        OpenFileDescriptor(project, file).navigate(true)
    }

    private fun repoRoot() = GitButlerService.getInstance(project).workspaceRepository()?.root

    private fun buildToolbar(): JComponent {
        val group = DefaultActionGroup().apply { add(RefreshAction()) }
        val actionToolbar = ActionManager.getInstance()
            .createActionToolbar("GitButlerToolWindow", group, true)
        actionToolbar.targetComponent = tree
        return actionToolbar.component
    }

    /**
     * Subscribes to git repository changes so the tree auto-refreshes when the working tree,
     * branches or commits change. The listener fires on an arbitrary thread; bursts are
     * debounced through a [MergingUpdateQueue] (~500ms) whose flush runs [refresh] on the EDT.
     * [parent] scopes both the queue and the message-bus subscription to the tool window content.
     */
    fun installAutoRefresh(parent: Disposable) {
        val queue = MergingUpdateQueue("GitButlerAutoRefresh", 500, true, this, parent)
        project.messageBus.connect(parent).subscribe(
            GitRepository.GIT_REPO_CHANGE,
            GitRepositoryChangeListener {
                queue.queue(object : Update("refresh") {
                    override fun run() = refresh()
                })
            },
        )
    }

    /** Re-runs `but status` off the EDT and re-renders the tree on the EDT. */
    fun refresh() {
        if (project.isDisposed) {
            return
        }
        if (!GitButlerService.getInstance(project).isGitButlerWorkspace()) {
            showEmpty("Not a GitButler workspace")
            return
        }

        tree.emptyText.text = "Loading GitButler status…"
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = GitButlerService.getInstance(project).status()
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) {
                    return@invokeLater
                }
                when (result) {
                    is ButResult.Ok -> renderStatus(result.value)
                    is ButResult.Err -> showEmpty(result.message)
                }
            }
        }
    }

    private fun showEmpty(message: String) {
        rootNode.removeAllChildren()
        treeModel.reload()
        tree.emptyText.text = message
    }

    private fun renderStatus(status: WorkspaceStatus) {
        rootNode.removeAllChildren()

        // Identity set of every change assigned to a stack; the flat uncommittedChanges list
        // holds the same instances, so identity filtering yields the truly-unassigned changes.
        val assignedInstances = java.util.Collections.newSetFromMap(
            java.util.IdentityHashMap<UncommittedChange, Boolean>(),
        )
        status.stacks.forEach { stack -> assignedInstances.addAll(stack.assignedChanges) }
        val trulyUnassigned = status.uncommittedChanges.filterNot { it in assignedInstances }

        val unassigned = DefaultMutableTreeNode(
            UnassignedNode(trulyUnassigned.size),
        )
        trulyUnassigned.forEach { change ->
            unassigned.add(DefaultMutableTreeNode(ChangeNode(change)))
        }
        rootNode.add(unassigned)

        status.stacks.forEach { stack -> addStack(stack) }

        treeModel.reload()
        tree.emptyText.text = ""
        TreeUtil.expandAll(tree)
    }

    private fun addStack(stack: ButStack) {
        stack.branches.forEachIndexed { index, branch ->
            val branchNode = DefaultMutableTreeNode(BranchNode(branch))
            // The stack's assigned changes belong to its first branch — surface them there,
            // before the commit children, mirroring `but status`'s "staged to <branch>" lane.
            if (index == 0 && stack.assignedChanges.isNotEmpty()) {
                val assignedNode = DefaultMutableTreeNode(AssignedNode(stack.assignedChanges.size))
                stack.assignedChanges.forEach { change ->
                    assignedNode.add(DefaultMutableTreeNode(ChangeNode(change)))
                }
                branchNode.add(assignedNode)
            }
            if (branch.commits.isEmpty()) {
                branchNode.add(DefaultMutableTreeNode(NoCommitsNode))
            } else {
                branch.commits.forEach { commit ->
                    branchNode.add(DefaultMutableTreeNode(CommitNode(commit.commitId, commit.message, commit.conflicted)))
                }
            }
            rootNode.add(branchNode)
        }
    }

    private inner class RefreshAction :
        AnAction("Refresh", "Re-run GitButler status", AllIcons.Actions.Refresh) {
        override fun actionPerformed(e: AnActionEvent) {
            refresh()
        }
    }

    // --- Tree node payloads -------------------------------------------------

    private class UnassignedNode(val count: Int)
    private class AssignedNode(val count: Int)
    private class ChangeNode(val change: UncommittedChange)
    private class BranchNode(val branch: VirtualBranch)
    private class CommitNode(val commitId: String, val message: String, val conflicted: Boolean)
    private object NoCommitsNode

    private class StatusCellRenderer : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(
            tree: JTree,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean,
        ) {
            when (val payload = (value as? DefaultMutableTreeNode)?.userObject) {
                is UnassignedNode -> {
                    append("Unassigned changes (${payload.count})")
                }

                is AssignedNode -> {
                    append("Assigned changes (${payload.count})", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }

                is ChangeNode -> {
                    val statusAttributes = changeTypeAttributes(payload.change.changeType)
                    append("${payload.change.changeType} ", statusAttributes)
                    val path = payload.change.filePath
                    val slash = path.lastIndexOf('/')
                    if (slash >= 0) {
                        append(path.substring(0, slash + 1), SimpleTextAttributes.GRAYED_ATTRIBUTES)
                        append(path.substring(slash + 1), statusAttributes)
                    } else {
                        append(path, statusAttributes)
                    }
                }

                is BranchNode -> {
                    append(payload.branch.name, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                    val suffix = statusSuffix(payload.branch.branchStatus)
                    if (suffix.isNotEmpty()) {
                        append("  $suffix", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    }
                }

                is CommitNode -> {
                    append("${payload.commitId.take(7)} ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    append(payload.message.lineSequence().firstOrNull().orEmpty())
                    if (payload.conflicted) {
                        append(" (conflicted)", SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.RED))
                    }
                }

                NoCommitsNode -> {
                    append("(no commits)", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
            }
        }

        /**
         * Same theme-aware colors the IDE commit window uses for changed files, keyed on the
         * CLI's changeType words. Unknown types stay in the regular foreground.
         */
        private fun changeTypeAttributes(changeType: String): SimpleTextAttributes {
            val status = when (changeType.lowercase()) {
                "added", "addition", "a", "untracked" -> FileStatus.ADDED
                "modified", "modification", "m" -> FileStatus.MODIFIED
                "deleted", "deletion", "d" -> FileStatus.DELETED
                "renamed", "rename", "r", "moved" -> FileStatus.MODIFIED
                else -> null
            } ?: return SimpleTextAttributes.REGULAR_ATTRIBUTES
            return SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, status.color)
        }

        private fun statusSuffix(branchStatus: String): String = when (branchStatus) {
            "" -> ""
            "nothingToPush" -> "✓ pushed"
            "unpushedCommits" -> "unpushed"
            "completelyUnpushed" -> "unpushed"
            else -> branchStatus
        }
    }
}
