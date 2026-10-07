# Current State

> Rewritten at every phase boundary — not appended to. Keep it under ~30 lines so it is
> cheap to read at the start of every session. History belongs in `decisions.md` and
> `gotchas.md`; this file only answers "where am I right now?".

**Last updated:** 2026-10-06
**Branch:** main (commit straight to main and push; no CI; see decisions.md)

## Where we are
Phase 1 done except 1.6 (owner `runClient`). Phase 2 done except owner in-client checks.
**Phase 3 done** except the owner HUD check. 3A: pure `ExperienceCurve`, no level cap. 3B: `PlayerMixin` routes
points-to-next through the curve. 3B.7 (owner chose a TAIL snap) makes XP points exact: `giveExperiencePoints` keeps
its vanilla body, then snaps to the exact total and stores the smallest float `f` with `f × n ≥ p`. `ServerPlayerMixin` does the same for
`/xp set points`, and `ServerPlayerAccessor` forces a client resync. **3C** (owner: saturating add): `totalExperience` and `getScore()` no longer wrap. Build: 73 JUnit + 20 GameTests, all green.

## What's next
1. Phase 4 (enchantment rules engine): starts with the 4.0 `/reload` probe for `EnchantmentEvents.MODIFY`.
2. Owner, in one `runClient` session: 1.6 + 2B.9 + 2C.7 + 3B.6 HUD check (bar at level ~1,000,000).

## Open questions / blockers
- None. Owner (2026-10-06): a lowered max level never touches existing items (decisions.md).

## Do not forget
- Plugin ID is `net.fabricmc.fabric-loom-remap` for 1.21.11. 1.21.11 Mojang names use `Identifier`, not `ResourceLocation`.
- Never kill java by name. The dev server uses 25566; the owner's other instance may be on 25565.
- 1.21.x enchantments are data-driven: use `EnchantmentEvents.MODIFY`, not override JSON. MODIFY runs
  before the server exists, so Phase 4 reads the config file directly (gotchas.md).
- Config code never rewrites an existing file on load. Only the GUI save writes, and it makes a `.bak` first.
- `.env` and MCP configs are tracked with live credentials (owner's call). Rotate the `ghp_` PAT.
- `.claude/skills/` and `.github/skills/` are hand-synced copies.
