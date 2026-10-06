# Current State

> Rewritten at every phase boundary — not appended to. Keep it under ~30 lines so it is
> cheap to read at the start of every session. History belongs in `decisions.md` and
> `gotchas.md`; this file only answers "where am I right now?".

**Last updated:** 2026-10-06
**Branch:** main (commit straight to main and push; no CI; see decisions.md)

## Where we are
Phase 1 done except 1.6 (owner `runClient`). Phase 2 done except owner in-client checks.
**Phase 3 in progress.** 3A committed (`0bc80ba`): `experience/ExperienceCurve` (pure, exact long math, points-to-next
clamped to [1, MAX]) + 12 JUnit; `maximum_level` removed (owner: no cap, only Integer.MAX_VALUE). Build: 68 JUnit + 8 GameTests.
The XP call-site audit (javap) is written in TODO.md Phase 3: one mixin on `Player#getXpNeededForNextLevel` covers all curve math.

## What's next
1. **Owner review of 3A** (stopped here: stop after each major section).
2. Stage 3B (TODO.md): `experience/PlayerExperience`, `mixin/PlayerMixin`, client source from `ClientConfigState`,
   `ExperienceGameTest` (incl. the float round-trip check, which may force an exact-math sub-step).
3. Owner, in one `runClient` session: 1.6 + 2B.9 + 2C.7 (+ 3B.6 HUD check once 3B lands).

## Open questions / blockers
- None open.

## Do not forget
- Plugin ID is `net.fabricmc.fabric-loom-remap` for 1.21.11. 1.21.11 Mojang names use `Identifier`, not `ResourceLocation`.
- Never kill java by name. The dev server uses 25566; the owner's other instance may be on 25565.
- 1.21.x enchantments are data-driven: use `EnchantmentEvents.MODIFY`, not override JSON. MODIFY runs
  before the server exists, so Phase 4 reads the config file directly (gotchas.md).
- Config code never rewrites an existing file on load. Only the GUI save writes, and it makes a `.bak` first.
- `.env` and MCP configs are tracked with live credentials (owner's call). Rotate the `ghp_` PAT.
- `.claude/skills/` and `.github/skills/` are hand-synced copies.
