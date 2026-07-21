# Changelog

Every PR must add an entry under `## Unreleased` in the matching section
(Features / Fixes / Internal improvements) — CI blocks PRs that don't touch
this file. On release, the Unreleased section becomes the release body.

## Unreleased

### Features

### Fixes

### Internal improvements

- Releases are now cut manually from the Woodpecker UI instead of on every
  push to main, and CI gates PRs on a CHANGELOG.md entry; the Unreleased
  section becomes the release body and is cut back into the changelog after
  each release.
