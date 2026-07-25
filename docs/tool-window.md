# GitButler tool window

A dedicated **GitButler** tool window that mirrors `but status` inside the IDE — see your unassigned changes, virtual-branch stacks, and their commits without leaving IntelliJ.

## Where to find it

The tool window is registered on the **left stripe, secondary group** — the bottom-left corner, alongside the Git tool window. It has its own 13×13 icon with a matching dark variant.

If your project isn't checked out on `gitbutler/workspace`, the tree stays empty and shows **"Not a GitButler workspace"**.

## The tree

The panel renders `but status` as a single tree:

- **Unassigned changes (N)** — top-level group listing every uncommitted change that isn't yet assigned to a stack. File rows show the change type prefix (e.g. `modified`, `added`) followed by the path, with the filename highlighted and the directory grayed.
- **`<branch name>`** — one node per applied virtual branch, name in bold. A status suffix follows the name:
  - `✓ pushed` when the branch has nothing left to push,
  - `unpushed` when the branch has unpushed commits (also shown for `completelyUnpushed`),
  - any other CLI status string is shown verbatim.
- **Assigned changes (N)** — a child group under the *first* branch of a stack, containing the changes that are staged to that stack (mirroring the "staged to `<branch>`" lane in `but status`).
- **Commits** — each commit under its branch renders as `<7-char sha> <message first line>`. Conflicted commits are marked in red with `(conflicted)`.
- **(no commits)** — placeholder shown under a branch that has no commits yet.

File rows are colored using the IDE's VCS `FileStatus` palette by change type, the same colors the Commit tool window uses: added = green, modified = blue, deleted = the IDE's deleted color; renamed/moved files are shown with the modified color. Unknown change types fall back to the regular foreground.

## Navigation

- **Double-click** or press **Enter** on a file row → opens the working-tree diff for that file. Files that aren't known to VCS (e.g. untracked) fall back to opening the file in the editor.
- **Double-click** or press **Enter** on a commit row → jumps to that commit in the Git Log tool window.
- **Double-click / Enter** on a container row (branch, group) toggles expansion, same as any tree.

## Toolbar actions

| Action | What it runs |
|---|---|
| **Refresh** | Re-runs `but status` and repaints the tree. |
| **Pull Workspace** | Runs `but pull` in the background — fetches the remote and rebases every applied branch onto the updated target. Success and error results land in the "GitButler" notification group. Disabled outside a `gitbutler/workspace` checkout, and while another operation is running. |

## Context-menu actions (right-click a branch row)

| Action | What it runs |
|---|---|
| **Unapply Branch** | `but unapply <branch>` — stash-like: takes the branch out of the workspace without losing its work. Reversible. |
| **Push Branch** | `but push <branch>` — pushes just that branch. |

All actions run as background tasks, surface a success or error balloon in the "GitButler" notification group, and refresh the tree when they finish. Actions are disabled while another GitButler operation is in flight so they can never overlap.

## Context-menu actions (right-click a commit row)

| Action | What it runs |
|---|---|
| **Rename Commit** | Prompts for a new commit message (prefilled with the current one), then runs `but reword <commit>` if you confirm a non-blank, changed message. |
| **Uncommit** | `but uncommit <commit>` — removes the commit and moves its changes back into your working tree as uncommitted changes. No confirmation dialog. |

Both actions target the GitButler change id of the selected commit and are disabled when no commit row is selected or another GitButler operation is in flight.

## Drag and drop

Drag one or more **uncommitted changes** from the tree to reassign them without leaving the tool window:

- **Drop onto a branch** — preselects that branch in the commit toolbar and opens the IDE's Commit tool window with exactly the dragged files checked (everything else unchecked), so you finish the commit through the normal flow with the branch already chosen.
- **Drop onto a commit** — amends the dragged files into that commit (`but amend`), after a confirmation you can permanently suppress with "Don't ask again". Locked-file or other `but` errors surface as a notification with the CLI's message.

Only selections made entirely of uncommitted change rows can be dragged; branches and commits are the only valid drop targets.

## Auto-refresh

The panel subscribes to `GitRepository.GIT_REPO_CHANGE`, so the tree re-renders itself whenever the working tree, branches, or commits change. Bursts are debounced through a 500 ms merging queue to keep repaints cheap. The subscription is scoped to the tool window content, so it dies with the panel.

You can also hit **Refresh** in the toolbar at any time.
