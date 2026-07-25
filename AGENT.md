# Agent guide — GitButler for JetBrains IDEs

Unofficial IntelliJ plugin (Kotlin, JDK 21, IntelliJ Platform Gradle Plugin 2.x, IC 2025.1+) that integrates GitButler virtual branches into the IDE: a branch selector in the commit window and a workspace tool window. All GitButler operations shell out to the `but` CLI with `--format json`. Active only when the project is on the `gitbutler/workspace` branch.

## Commands

```bash
./gradlew test          # unit + integration tests — run this after every change
./gradlew buildPlugin   # produces build/distributions/*.zip (NOT `build` — the
                        # lifecycle `build` task does not produce the zip)
./gradlew runIde        # sandbox IDE with the plugin installed
```

Gradle needs a JDK 21 (`kotlin { jvmToolchain(21) }`). If there is no system Java, point `JAVA_HOME` at one (e.g. `JAVA_HOME=/opt/homebrew/opt/openjdk@21` on macOS/Homebrew).

## Non-negotiable rules

1. **Every behavior change ships with tests, and you run them** (`./gradlew test`) before declaring done. Pure parsing/mapping logic → unit test; anything touching the real `but` CLI contract → integration test (see below).
2. **Every new feature or user-visible change updates `docs/`** — extend the matching page (`docs/virtual-branch-commit.md`, `docs/tool-window.md`) or add a new page for a new surface.
3. **README.md and the plugin description stay lean.** `README.md` and `<description>` in `src/main/resources/META-INF/plugin.xml` get at most a one-line mention of a new feature; the full explanation lives in `docs/` and the README links to it. Never grow either into a manual.
4. **Every PR adds a `CHANGELOG.md` entry** under `## Unreleased` in the matching section (Features / Fixes / Internal improvements). CI blocks PRs that don't touch `CHANGELOG.md`.

## Testing

### Unit tests (`src/test/.../core/`)

`ButJsonParserTest`, `ButPathMapperTest` — plain JUnit 4 against captured JSON fixtures. Fast, no Docker. Extend these when changing parsing, path mapping, or command construction.

### Integration tests (`src/test/.../integration/ButStatusIntegrationTest.kt`)

These validate the plugin's CLI contract end-to-end against the **real** GitButler CLI:

- Testcontainers builds a Debian container that installs the latest `but` via `gitbutler.com/install.sh` (no version pin exists; latest-by-design to catch contract drift — the version under test is printed at startup).
- Each test creates a **real git repository** inside the container via the `freshRepo(name)` helper (`but setup --init`), makes real file changes and branches, then runs the plugin's **own** `ButCommands` argument lists, feeds the real JSON through `ButJsonParser`/`ButPathMapper`, and asserts on the parsed `WorkspaceStatus` model.
- Pattern for a new test: `freshRepo` → arrange with `exec(repo, ...)` shell steps → act through `but(repo, ButCommands.xxx(...))` → assert on `status(repo)`. Always go through `ButCommands`, never hand-written arg lists — the point is testing what the plugin actually sends.
- **Self-skipping:** the whole class skips (never fails) when Docker is unavailable, via a hardened `Assume` in `@BeforeClass`. Keep it that way: construct anything Testcontainers-related lazily in `@BeforeClass`, never in static init (static init throws `java.lang.Error` on Docker-less machines and breaks the skip).
- Locally under colima, `build.gradle.kts` derives `DOCKER_HOST` from the docker context and disables Ryuk — don't remove that block.

Any change to `ButCommands`, `ButJsonParser`, `ButPathMapper`, or a new `but` subcommand needs a matching integration test that exercises it against the real CLI.

## Architecture

- `core/` — CLI plumbing: `GitButlerService` (project service, finds `but`, runs it), `ButCommands` (argument lists), `ButJsonParser` (JSON → model), `ButModel` (`WorkspaceStatus` etc.), `ButPathMapper` (absolute IDE paths → CLI change ids).
- `commit/` — commit-window integration: `GitButlerCheckinHandlerFactory`/`GitButlerCheckinHandler` intercept the commit flow, `GitButlerBranchComboAction` is the toolbar selector, `GitButlerCommitSelection` holds the chosen branch per project.
- `toolwindow/` — `GitButlerToolWindowFactory` + `GitButlerStatusPanel` render `but status -f` as a tree (branches → commits → the files each commit changed), auto-refreshed on `GitRepository.GIT_REPO_CHANGE` (500 ms debounce); `GitButlerTreeDnDSupport` adds drag-and-drop of uncommitted changes onto branches and commits.
- `src/main/resources/META-INF/plugin.xml` — extension points, tool window, actions, plugin description.

## Gotchas

- `but commit` JSON shape **varies by platform**: nested `{result:{commit_id,...}}` and flat `{commit_id,...}` both occur — `ButJsonParser.parseCommitResult` handles both; keep it tolerant. `but push` failure output is plain text, not JSON.
- Detect Commit-and-Push via `executor.id == "Git.Commit.And.Push.Executor"` — the class `git4idea.checkin.GitCommitAndPushExecutor` is Kotlin-`internal`, don't reference it.
- The `CheckinHandlerFactory` must always return the real handler (never a dummy): it runs once at commit-UI creation, possibly before git repos register.
- Branch names in `Presentation.setText` need `setText(text, false)` — `_`/`&` are otherwise eaten as mnemonics.
- CI is Woodpecker on Codeberg (`.woodpecker.yml`): no docker-in-docker, so integration tests self-skip there and only unit tests gate PRs. Run integration tests locally before pushing CLI-contract changes. Releases are **not** automatic on merge: cut one by manually running the Woodpecker pipeline on `main`, which tests, versions (CalVer, unchanged, stamped via `-PpluginVersion`), builds, creates the Codeberg release with the `## Unreleased` section of `CHANGELOG.md` as its body, then commits the changelog cut back to `main`.

## Version control

The repo itself is GitButler-managed (`gitbutler/workspace`). Use the `but` CLI (or the gitbutler skill, if available) for branches, commits, and pushes — not raw `git commit`. `but pr new` fails on Codeberg ("Unable to determine the forge"); create PRs through the Forgejo API instead.
