# CLAUDE.md

@AGENT.md

The full agent guide lives in `AGENT.md` (imported above) — single source of truth for build commands, testing rules, architecture, and gotchas. Keep the two in sync by editing only `AGENT.md`.

Quick reminders that trip agents up most often:

- **Write and run tests for every change**: `JAVA_HOME` must point at a JDK 21, then `./gradlew test`. CLI-contract changes need an integration test (real repo in Docker + real `but status` — see AGENT.md § Testing).
- **New feature → update `docs/`**; README and `plugin.xml` description get one lean line + a link to the doc page, nothing more.
- Build the distributable with `./gradlew buildPlugin`, not `build`.
- Commit via GitButler (`but` CLI / gitbutler skill), not raw `git commit` — this repo is on `gitbutler/workspace`.
