# Changelog

Every PR must add an entry under `## Unreleased` in the matching section
(Features / Fixes / Internal improvements) — CI blocks PRs that don't touch
this file. On release, the Unreleased section becomes the release body.

## Unreleased

### Features

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
