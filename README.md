<div align="center">

<img src="src/main/resources/META-INF/pluginIcon.svg" width="96" height="96" alt="GitButler mark" />

# GitButler — Version Control

### Commit to GitButler virtual branches without leaving your JetBrains IDE.

Assign changes to a virtual branch straight from the IntelliJ commit window — no context-switch to the GitButler app or terminal.

<br/>

[![License: MIT](https://img.shields.io/badge/License-MIT-74D3D1.svg?style=flat-square)](LICENSE)
[![IntelliJ Platform](https://img.shields.io/badge/IntelliJ%20Platform-2025.1%2B-74D3D1?style=flat-square&logo=intellijidea&logoColor=white)](https://plugins.jetbrains.com/)
[![Built with Kotlin](https://img.shields.io/badge/Kotlin-JDK%2021-74D3D1?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![CI: Woodpecker](https://img.shields.io/badge/CI-Woodpecker-74D3D1?style=flat-square&logo=woodpeckerci&logoColor=white)](https://ci.codeberg.org)

</div>

> [!NOTE]
> **Unofficial** plugin — not affiliated with or endorsed by GitButler.
> The GitButler mark is used under its [CC0-1.0 brand assets](https://github.com/gitbutlerapp/gitbutler-brand-assets).

---

## What it does

When your project is on the `gitbutler/workspace` branch, the Commit tool window's message-area toolbar (right next to the **Amend** toggle) gains an always-visible **GitButler branch** selector. Pick a virtual branch, check your files, and the commit is routed through the GitButler CLI (`but commit`) instead of plain git.

<div align="center">

`select branch` → `check files` → `Commit` → routed through `but` ✔

</div>

## ✨ Features

- 🔍 **Zero-config detection** — activates only on a `gitbutler/workspace` branch; stays completely dormant otherwise
- 🎯 **Inline branch selector** — always-visible virtual-branch combo in the commit toolbar, beside the Amend toggle
- ✅ **Commits exactly what you check** — selected files are mapped to GitButler change IDs via `but status`
- 🚀 **Commit and Push…** — also pushes the virtual branch (`but push`)
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

1. Open a GitButler-managed project (branch `gitbutler/workspace`).
2. Open the Commit tool window — the **GitButler branch** selector sits in the message-area toolbar next to the Amend toggle.
3. Choose a virtual branch — or `Git: no virtual branch` for a normal git commit.
4. Select files, write a message, hit **Commit** (or **Commit and Push…**).

## ⚙️ How it works

The plugin registers a `CheckinHandler` that intercepts the commit flow. All GitButler operations go through the `but` CLI with `--format json`:

| Step | Command |
|---|---|
| List branches, map files → change IDs | `but status` |
| Commit exactly the selected changes | `but commit <branch> -m <message> --changes <ids>` |
| Push (via *Commit and Push*) | `but push <branch>` |

If no virtual branch is selected, the handler steps aside and IntelliJ's normal git commit runs untouched.

## 👩‍💻 Development

```bash
./gradlew build      # compile + unit tests
./gradlew runIde     # launch a sandbox IDE with the plugin
./gradlew buildPlugin
```

Kotlin · IntelliJ Platform Gradle Plugin 2.x · JDK 21.

## 🔄 Continuous integration

Every push to `main` runs [Woodpecker CI](https://ci.codeberg.org) (`.woodpecker.yml`): it builds the plugin and publishes a Codeberg release versioned with CalVer (`YYYY.M.D.<build>`), with the plugin `.zip` attached as an asset. The same CalVer version is stamped into the built plugin.

## 🚧 Known limitations

- Single git repository per project
- Virtual branches with identical names across stacks are ambiguous (committed by name)
- No way to create a new virtual branch from the commit window yet (`but commit -c` makes this a natural next feature)

## 📄 License

[MIT](LICENSE) © 2026 BartInTheField

<div align="center"><sub>Built with 🎀 for the GitButler workflow.</sub></div>
