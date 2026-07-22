# Changelog

Every PR must add an entry under `## Unreleased` in the matching section
(Features / Fixes / Internal improvements) — CI blocks PRs that don't touch
this file. On release, the Unreleased section becomes the release body.

## Unreleased

### Features

### Fixes

### Internal improvements

## 2026.7.22.1 - 2026-07-22

### Features
- Workspace tool window gains actions: a **Pull Workspace** toolbar button
  (`but pull`) and a per-branch right-click menu with **Unapply**
  (`but unapply`) and **Push** (`but push`). All run as background tasks with
  success/error balloons and a tree refresh, gated by a single in-flight flag
  so operations never overlap; Pull is disabled outside a GitButler workspace.
  See [docs/tool-window.md](docs/tool-window.md).
- GitButler submenu in the IDE's native Git branch context menu: Apply and
  Unapply the selected branch via `but apply` / `but unapply`. Local branches
  get both actions; remote branches get Apply (by short name, hidden when a
  same-named local branch exists). Works on both the 2025.1 and 2026.1 IDE
  lines. See [docs/git-branch-menu.md](docs/git-branch-menu.md).

### Fixes

### Internal improvements

- Releases are now cut manually from the Woodpecker UI instead of on every
  push to main, and CI gates PRs on a CHANGELOG.md entry; the Unreleased
  section becomes the release body and is cut back into the changelog after
  each release.
