# Current State

> Rewritten at every phase boundary — not appended to. Keep it under ~30 lines so it is
> cheap to read at the start of every session. History belongs in `decisions.md` and
> `gotchas.md`; this file only answers "where am I right now?".

**Last updated:** 2026-10-07
**Branch:** main (commit straight to main and push; no CI; see decisions.md)

## Where we are
Phase 1 done except 1.6 (owner `runClient`). Phase 2 done except owner in-client checks.
**Phase 3 done** except the owner HUD check. 3A: pure `ExperienceCurve`, no level cap. 3B: `PlayerMixin` routes
points-to-next through the curve. 3B.7 (owner chose a TAIL snap) makes XP points exact: `giveExperiencePoints` keeps
its vanilla body, then snaps to the exact total and stores the smallest float `f` with `f × n ≥ p`. `ServerPlayerMixin` does the same for
`/xp set points`, and `ServerPlayerAccessor` forces a client resync. **3C** (owner: saturating add): `totalExperience` and `getScore()` no longer wrap.
**Stage 4A done** (2026-10-07): `MaxLevels` (§8 table, Lure/Riptide stay 3 until 4D) + `MaxLevelHook` (`MODIFY`) +
`max_level_override`. Needs a restart; `/reload` doesn't apply it (probed).
**Stage 4B done** (2026-10-07): pure `ConflictFamily` + `common.json5` `allow_together.<family>` (owner's key name) +
a "Conflicts" screen category.
**Stage 4C done** (2026-10-07): `EnchantRules.check` (INVALID_LEVEL → ABOVE_MAX → NOT_APPLICABLE (books skip) →
ALREADY_AT_LEVEL → CONFLICT, new IDs only) + `withEnchantment` (higher level wins). Owner: books obey conflicts, existing
pairs are grandfathered, no +1 merge. Nothing calls it until Phase 6. Build: 97 JUnit + 28 GameTests, all green.

## What's next
1. Phase 4 in TODO.md: 4A ✅ → 4B ✅ → 4C ✅ → 4D Lure/Riptide 5 (read `FishingHook` + the moved-too-quickly
   threshold, write the arithmetic into TODO, then GameTest). Next: 4D.1.
2. Owner, in one `runClient` session: 1.6 + 2B.9 + 2C.7 + 3B.6 HUD check + 4A.7 (synced max level) (bar at level ~1,000,000).

## Open questions / blockers
- None. Owner (2026-10-06): a lowered max level never touches existing items (decisions.md).

## Do not forget
- Plugin ID is `net.fabricmc.fabric-loom-remap` for 1.21.11. 1.21.11 Mojang names use `Identifier`, not `ResourceLocation`.
- Never kill java by name. The dev server uses 25566; the owner's other instance may be on 25565.
- 1.21.x enchantments are data-driven: use `EnchantmentEvents.MODIFY`, not override JSON. MODIFY runs
  before the server exists and `/reload` doesn't re-run it (gotchas.md). FAPI 0.141.6 = item-api 11.6.0.
- Config code never rewrites an existing file on load. Only the GUI save writes, and it makes a `.bak` first.
- `.env` and MCP configs are tracked with live credentials (owner's call). Rotate the `ghp_` PAT.
- `.claude/skills/` and `.github/skills/` are hand-synced copies.
