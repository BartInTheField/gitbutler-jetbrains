# Virtual-branch commit

Commit to a GitButler virtual branch straight from the IntelliJ Commit tool window — no context-switch to the GitButler app or terminal.

## What it does

When your project is checked out on the `gitbutler/workspace` branch, a **GitButler branch** selector appears in the Commit tool window's message-area toolbar, right next to the **Amend** toggle. Pick a virtual branch, check your files, hit **Commit**, and the commit is routed through the `but` CLI instead of plain git.

The selector always shows — no per-project setup, no keybinding — and quietly stays dormant when the project isn't on `gitbutler/workspace`.

## Usage

1. Open a GitButler-managed project (branch `gitbutler/workspace`).
2. Open the Commit tool window.
3. In the message-area toolbar, choose a virtual branch from the **GitButler branch** combo — or pick `Git: no virtual branch` to fall through to a regular git commit.
4. Select the files to include and write a commit message.
5. Hit **Commit** or **Commit and Push…**.

The plugin remembers the last-used virtual branch per project.

## CLI commands used

Every GitButler action runs the `but` CLI with `--json` (plus `BUT_OUTPUT_FORMAT=json` in the environment):

| Step | Command |
|---|---|
| List branches; map file paths → change IDs | `but status -f --json` |
| Commit the selected changes | `but commit -b <branch> -m <message> --json <change-ids>` |
| Push (only for *Commit and Push*) | `but push <branch> --json` |

If no virtual branch is picked, the plugin's checkin handler steps aside and IntelliJ's normal git commit runs untouched.

## Notifications

All commit outcomes land in the "GitButler" notification group:

- **Committed to `<branch>`** — commit succeeded; the subtitle is the short SHA, or the literal "Commit created" when the CLI didn't return one.
- **Committed to `<branch>` and pushed** — *Commit and Push* succeeded end-to-end.
- **Committed to `<branch>`, but push failed** — commit is in; only the push failed, with the CLI's error as the subtitle.
- **GitButler commit failed** — the commit itself failed. The commit dialog stays open and your message is preserved.
- **GitButler commit failed — Commit message is empty** — sent when you try to commit through a virtual branch without a message.

## Limitations

- Single git repository per project.
- Virtual branches with identical names across stacks are ambiguous — the CLI is invoked by branch name.
- There's no way to create a new virtual branch from the commit window yet. (`but commit -b <new-branch>` is the natural hook for this.)
