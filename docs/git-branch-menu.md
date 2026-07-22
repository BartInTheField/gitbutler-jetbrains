# Git branch menu — GitButler submenu

A **GitButler** submenu in the IDE's native Git branch context menu — apply or unapply a branch to the GitButler workspace without leaving the branches UI.

## Where to find it

Right-click a branch anywhere the IDE shows the Git branch context menu — the **Branches** panel in the Git tool window (Git Log), or the branches popup. The **GitButler** submenu sits at the bottom of the menu with two items:

| Action | What it runs |
|---|---|
| **Apply** | `but apply <branch>` — brings the branch into the GitButler workspace as an applied branch. |
| **Unapply** | `but unapply <branch>` — takes the branch out of the workspace without losing its work. Reversible via **Apply**. |

## When it appears

The submenu hides itself entirely unless all of the following hold:

- the project is a GitButler workspace (some repository is checked out on `gitbutler/workspace`),
- exactly one branch is selected,
- the selected branch is not `gitbutler/workspace` itself (nor its remote form, e.g. `origin/gitbutler/workspace`).

Which items you see depends on the branch:

- **local branch** — **Apply** and **Unapply**,
- **remote branch** — **Apply** only. The branch is applied by its short name (e.g. `origin/feature/x` → `but apply feature/x`), so a remote-only branch lands in the workspace as a local applied branch. If a local branch with the same short name already exists, Apply is hidden on the remote branch — the CLI would resolve the short name to the local branch, not the one you clicked.

Visible items are always enabled — the plugin doesn't track applied state per branch (that would need a CLI call on every menu open). Applying an already-applied branch, or unapplying one that isn't applied, simply surfaces the CLI's own error message. The one exception: while another branch-menu operation is still running, the items are disabled so mutations never overlap.

## Behaviour

Both actions run as background tasks and report through the **"GitButler"** notification group: a success balloon (`Applied <branch>` / `Unapplied <branch>`) or the CLI's error message on failure. On success the plugin re-reads the repository state, so the [GitButler tool window](tool-window.md) refreshes to reflect the new workspace.
