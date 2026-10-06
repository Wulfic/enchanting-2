# Current State

> Rewritten at every phase boundary — not appended to. Keep it under ~30 lines so it is
> cheap to read at the start of every session. History belongs in `decisions.md` and
> `gotchas.md`; this file only answers "where am I right now?".

**Last updated:** 2026-08-12
**Branch:** main

## Where we are
Agent tooling refactor is complete and pushed. `AGENTS.md` is built around tiered workflow
(Tier 0/1/2), 3-attempt loop budgets, and repo-local memory. MCP surface is `github` +
`context7` only. See `decisions.md` for why each server was dropped.

Destructive-action doctrine added (uncommitted at time of writing). `AGENTS.md` opens with
a `Destructive Actions` section as rule zero; new **11th skill `guard-destructive`** holds
the five gates and the guards-in-code requirements; `code-review`, `git-github`,
`build-run`, `plan-work`, and `test-iterate` each grew a destruction section. New
`.claude/settings.json` carries `permissions.deny` + `ask` rules as mechanical backup.

## What's next
1. **Restart the session, then verify the permission rules actually load** — run
   `dropdb --help`; it should prompt. See the `gotchas.md` entry: they did *not* fire in
   the session that created the file.
2. Commit the doctrine change (`feat(agents): ...`), then nothing queued.

## Open questions / blockers
- **`gh auth status` currently fails.** `gh` is the documented fallback for every GitHub
  operation, so that fallback is not actually available until this is fixed.
- The `github` MCP endpoint (`<lan-ip>:8000`) is unverified from this machine.
- `PowerShell(...)` permission-rule matching is **unverified** — the `Bash(...)` rules are
  the reliable path. Treat the PowerShell tool as ungated until proven otherwise.

## Do not forget
- `.env` and both MCP configs are tracked in git with live credentials; owner opted to
  leave this as-is. The `ghp_` PAT is in history and still worth rotating.
- `.claude/skills/` and `.github/skills/` are hand-synced copies — edit both or they drift.
- Old AGENTS.md prose described a docker-compose stack on `.186`; that layout is retired.
