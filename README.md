<div align="center">

<img src="src/main/resources/META-INF/pluginIcon.svg" width="96" height="96" alt="GitButler mark" />

# GitButler for IDE

### Work with GitButler virtual branches without leaving your JetBrains IDE.

Commit to a virtual branch straight from the IntelliJ commit window, and see your whole GitButler workspace — unassigned changes, stacks, branches, commits — in a dedicated tool window. No context-switch to the GitButler app or terminal.

<br/>

[![License: MIT](https://img.shields.io/badge/License-MIT-74D3D1.svg?style=flat-square)](LICENSE)
[![IntelliJ Platform](https://img.shields.io/badge/IntelliJ%20Platform-2025.1%2B-74D3D1?style=flat-square&logo=intellijidea&logoColor=white)](https://plugins.jetbrains.com/)
[![Built with Kotlin](https://img.shields.io/badge/Kotlin-JDK%2021-74D3D1?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![CI: GitHub Actions](https://img.shields.io/badge/CI-GitHub%20Actions-74D3D1?style=flat-square&logo=githubactions&logoColor=white)](https://github.com/BartInTheField/gitbutler-jetbrains/actions)

</div>

> [!NOTE]
> **Unofficial** plugin — not affiliated with or endorsed by GitButler.
> The GitButler mark is used under its [CC0-1.0 brand assets](https://github.com/gitbutlerapp/gitbutler-brand-assets).

---

## What it does

The plugin adds two GitButler surfaces to the IDE, both active only when your project is on `gitbutler/workspace`:

1. **Virtual-branch commit** — the Commit tool window's message-area toolbar (right next to the **Amend** toggle) gains an always-visible **GitButler branch** selector. Pick a virtual branch, check your files, and the commit is routed through the GitButler CLI (`but commit`) instead of plain git.
2. **GitButler tool window** — a dedicated tool window on the bottom-left stripe (alongside the Git window) mirrors `but status` as a tree: unassigned changes, per-stack assigned changes, applied branches with push status, and their commits. Double-click a file to open its diff (untracked files open in the editor); double-click a commit to jump to it in the Git Log.

<div align="center">

`select branch` → `check files` → `Commit` → routed through `but` ✔

</div>

## ✨ Features

- 🔍 **Zero-config detection** — activates only on a `gitbutler/workspace` branch; stays completely dormant otherwise
- 🎯 **Inline branch selector** — always-visible virtual-branch combo in the commit toolbar, beside the Amend toggle
- ✅ **Commits exactly what you check** — selected files are mapped to GitButler change IDs via `but status`
- 🚀 **Commit and Push…** — also pushes the virtual branch (`but push`)
- 🌳 **GitButler tool window** — live `but status` tree with VCS-colored file rows, diff-on-double-click, and jump-to-commit in the Git Log
- 🧰 **Workspace actions in-IDE** — Pull Workspace (`but pull`) from the toolbar; Unapply Branch and Push Branch from the branch context menu
- 🌿 **GitButler submenu in the Git branch menu** — Apply / Unapply a branch right from the IDE's native branch context menu (remote-only branches can be applied too)
- 🔄 **Auto-refreshing** — the tool window re-renders on git repository changes (500 ms debounced) and via a manual Refresh button
- 🔔 **Clear notifications** — committed / committed & pushed / push failed / commit failed; a failed commit never loses your message
- 💾 **Remembers your last-used branch** per project

## 📦 Requirements

| | |
|---|---|
| **IDE** | IntelliJ IDEA 2025.1+ (Community or Ultimate) |
| **CLI** | [GitButler `but`](https://docs.gitbutler.com/cli-overview) 0.21+ on your `PATH` (also auto-detected in `~/.local/bin`, `/opt/homebrew/bin`, `/usr/local/bin`) |
| **Project** | Set up with GitButler (`but setup`) |

## 🛠 Installation

Not on the JetBrains Marketplace yet — build from source:

```bash
./gradlew buildPlugin
```

Then in the IDE: **Settings → Plugins → ⚙ → Install Plugin from Disk…** and pick
`build/distributions/gitbutler-intellij-<version>.zip`.

## 🚀 Usage

### Commit to a virtual branch

1. Open a GitButler-managed project (branch `gitbutler/workspace`).
2. Open the Commit tool window — the **GitButler branch** selector sits in the message-area toolbar next to the Amend toggle.
3. Choose a virtual branch — or `Git: no virtual branch` for a normal git commit.
4. Select files, write a message, hit **Commit** (or **Commit and Push…**).

### GitButler tool window

Open the **GitButler** tool window from the bottom-left stripe (same corner as the Git window). It shows unassigned changes, each stack's assigned changes, and every applied branch with its commits — refreshed automatically on repository changes. Use the toolbar's **Pull Workspace** button to run `but pull`, and right-click a branch for **Unapply Branch** or **Push Branch**.

## ⚙️ How it works

The plugin registers a `CheckinHandler` that intercepts the commit flow and a tool window that renders `but status`. All GitButler operations go through the `but` CLI with `--format json`:

| Step | Command |
|---|---|
| List branches, map files → change IDs; render the tool window | `but status` |
| Commit exactly the selected changes | `but commit <branch> -m <message> --changes <ids>` |
| Push (via *Commit and Push*, or tool-window context menu) | `but push <branch>` |
| Pull Workspace toolbar button | `but pull` |
| Unapply Branch context menu | `but unapply <branch>` |
| GitButler submenu in the Git branch menu | `but apply <branch>` / `but unapply <branch>` |

If no virtual branch is selected in the commit toolbar, the handler steps aside and IntelliJ's normal git commit runs untouched.

## 📚 Documentation

- [Virtual-branch commit](docs/virtual-branch-commit.md) — the commit-window integration
- [GitButler tool window](docs/tool-window.md) — the workspace tree, toolbar and branch context menu
- [Git branch menu](docs/git-branch-menu.md) — the GitButler Apply/Unapply submenu in the native branch context menu

## 👩‍💻 Development

```bash
./gradlew build      # compile + unit tests
./gradlew runIde     # launch a sandbox IDE with the plugin
./gradlew buildPlugin
```

Kotlin · IntelliJ Platform Gradle Plugin 2.x · JDK 21.

## 🔄 Continuous integration

[GitHub Actions](https://github.com/BartInTheField/gitbutler-jetbrains/actions) (`.github/workflows/ci.yml`) tests every PR and push to `main`, and requires each PR to add a `CHANGELOG.md` entry. Releases live in a separate `release.yml` triggered manually from the Actions tab: it builds a CalVer-versioned (`YYYY.M.D.<build>`) plugin `.zip` and publishes a GitHub release with the `## Unreleased` changelog section as its body.

## 🚧 Known limitations

- Single git repository per project
- Virtual branches with identical names across stacks are ambiguous (committed by name)
- No way to create a new virtual branch from the commit window yet (`but commit -c` makes this a natural next feature)

## 📄 License

[MIT](LICENSE) © 2026 BartInTheField

<div align="center"><sub>Built with 🎀 for the GitButler workflow.</sub></div>
