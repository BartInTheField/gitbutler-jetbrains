# GitButler for IntelliJ

An IntelliJ Platform plugin that brings [GitButler](https://gitbutler.com) virtual branches into the IDE's regular commit window.

When your project is on the `gitbutler/workspace` branch, the Commit tool window's message-area toolbar (next to the Amend toggle) gains an always-visible **GitButler branch** selector. Pick a virtual branch, select your files, and the commit is routed through the GitButler CLI (`but commit`) instead of plain git — no switching to the GitButler app or terminal.

## Features

- Detects a GitButler workspace automatically (current branch is `gitbutler/workspace`); stays completely inactive otherwise
- Always-visible virtual-branch selector combo in the Commit tool window's message-area toolbar, right next to the Amend toggle
- Commits only the files you checked, mapped to GitButler change IDs via `but status`
- **Commit and Push…** also pushes the virtual branch (`but push`)
- Clear notifications for committed / committed and pushed / push failed / commit failed — a failed commit never loses your commit message
- Remembers your last-used virtual branch per project

## Requirements

- IntelliJ IDEA 2025.1 or newer (Community or Ultimate)
- [GitButler CLI](https://docs.gitbutler.com/cli-overview) `but` 0.21+ on your `PATH` (also found in `~/.local/bin`, `/opt/homebrew/bin`, `/usr/local/bin`)
- A project set up with GitButler (`but setup`)

## Installation

Not on the JetBrains Marketplace yet — build from source:

```bash
./gradlew buildPlugin
```

Then in the IDE: **Settings → Plugins → ⚙ → Install Plugin from Disk…** and pick `build/distributions/gitbutler-intellij-<version>.zip`.

## Usage

1. Open a GitButler-managed project (branch `gitbutler/workspace`).
2. Open the Commit tool window; the **GitButler branch** selector sits in the message-area toolbar next to the Amend toggle.
3. Choose a virtual branch in the selector — or `Git: no virtual branch` for a normal git commit.
4. Select files, write a message, hit **Commit** (or **Commit and Push…**).

## How it works

The plugin registers a `CheckinHandler` that intercepts the commit flow. All GitButler operations go through the `but` CLI with `--format json`:

- `but status` — list virtual branches and map selected files to change IDs
- `but commit <branch> -m <message> --changes <ids>` — commit exactly the selected changes
- `but push <branch>` — when invoked via Commit and Push

If no virtual branch is selected, the handler steps aside and IntelliJ's normal git commit runs untouched.

## Development

```bash
./gradlew build      # compile + unit tests
./gradlew runIde     # launch a sandbox IDE with the plugin
./gradlew buildPlugin
```

Kotlin, IntelliJ Platform Gradle Plugin 2.x, JDK 21.

## Known limitations

- Single git repository per project
- Virtual branches with identical names across stacks are ambiguous (committed by name)
- No way to create a new virtual branch from the commit window yet (`but commit -c` makes this a natural next feature)

## License

[MIT](LICENSE) © 2026 BartInTheField
