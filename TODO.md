# TODO

The working plan. `plan-work` writes here **before** any code is written — a plan that
lives only in the chat window did not happen. Keep this current as you execute; a stale
TODO is worse than an empty one, because the next session trusts it.

> Long-lived context does **not** belong here. Decisions and gotchas go to
> `.agent/memory/`; this file is the active work list.

---

## Task template

```markdown
## <Task name>

**Goal:** <one sentence — what "done" means>
**Approach:** <chosen approach, and the one-line reason it beat the alternative>
**Not doing:** <explicit out-of-scope list — this is what stops scope creep>
**Blast radius:** <what this destroys or overwrites, and where it comes back from —
                  or "none: additive only">

- [ ] 1. <concrete step — names a real file>
- [ ] 2. <concrete step>
- [ ] 3. <the test that proves it works>

**Rollback:** <the exact command that undoes this — not "revert the change">
```

Rules for steps: each names a real file or command, each is independently verifiable, and
tests are steps rather than an afterthought. More than ~8 steps means it's a Tier 2 task —
split it into stages with commits between them.

`Blast radius:` and `Rollback:` are **mandatory** — "none, additive only" is a valid
answer, a blank field is not. Destructive steps are marked as such and gated through
`guard-destructive` when you reach them; irreversible steps go last.

---

## Active

## Destructive-action doctrine

**Goal:** The template treats anything irreversible as a gated operation — the agent
names the blast radius, proves recoverability, and confirms before acting; and code the
agent *writes* ships with dry-run, confirmation, and reversibility built in.

**Approach:** Two enforcement layers. (1) Doctrine in `AGENTS.md` + a `guard-destructive`
skill, which works in every tool including Copilot. (2) Mechanical `permissions.deny` /
`permissions.ask` rules in `.claude/settings.json`, which actually block in Claude Code.
Prose alone loses under a truncated context; rules alone only cover one of the two tools.

**Not doing:** No `PreToolUse` hook script — a hook that silently no-ops is worse than no
hook (same reasoning as the `mem0` decision), and an LLM-prompt hook taxes every shell
call. Documented as an opt-in escalation instead. Not touching the tracked `.env`
(owner's standing decision), not adding `disableBypassPermissionsMode` (locks the owner
out of their own tool without asking).

- [x] 1. `.claude/skills/guard-destructive/SKILL.md` — classification, five gates, absolute
      stops, guards-in-code requirements; byte-identical copy to `.github/skills/`
- [x] 2. `.claude/settings.json` — deny (unrecoverable) + ask (destructive-shaped) rules
- [x] 3. `AGENTS.md` — non-negotiable bullet, `Destructive Actions` section, workflow step,
      skill table row, Definition of Done items
- [x] 4. `.github/copilot-instructions.md` — condensed version of the same rules
- [x] 5. `code-review` — destruction & data-safety checklist, unguarded destruction = CRITICAL
- [x] 6. `git-github` / `build-run` / `plan-work` / `test-iterate` — per-skill guards
- [x] 7. Blast radius + Rollback made mandatory in the `TODO.md` and `plan-work` templates
- [x] 8. Verify: `settings.json` parses, both skill trees byte-identical

**Rollback:** `git revert` the commit. Every change is additive documentation plus one new
JSON file — nothing existing is deleted, so revert is clean and total.

## Done

_Move completed tasks here, or delete them once they're in the git history._
