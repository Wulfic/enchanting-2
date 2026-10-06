# Current State

> Rewritten at every phase boundary — not appended to. Keep it under ~30 lines so it is
> cheap to read at the start of every session. History belongs in `decisions.md` and
> `gotchas.md`; this file only answers "where am I right now?".

**Last updated:** 2026-10-06
**Branch:** main

## Where we are
Project is **Enchantment 2**, a Fabric 1.21.11 mod (XP curve flattening, unlimited applicable
enchantments, additive cost model, readable enchanting table, new enchantments). No code yet.
The repo is still the agent template plus the plan.

Phase 0 (plan) is done: the spec is in `docs/DESIGN.md` with the owner's answers folded in,
and the 12-phase roadmap is in `TODO.md`. Uncommitted.

## What's next
1. Commit the plan (`docs:`), then start Phase 1: scaffold, verifying toolchain versions
   first via `research-docs`.

## Open questions / blockers
- None. Q1–Q14 resolved 2026-10-06 (see decisions.md).
- The placed-block tracker for Fortune is ported from `../mcMMO-Singleplayer` (`util/PlacedBlockTracker`,
  `util/BlockRules`). That repo targets 26.3 with Mojang names, so expect small API drift
  back to 1.21.11.

## Do not forget
- 1.21.x enchantments are data-driven. Modify vanilla ones through Fabric
  `EnchantmentEvents.MODIFY`, never by overriding their JSON.
- 1.21.11 is the last Yarn version, so use Mojang mappings.
- `.env` and MCP configs are tracked with live credentials (owner's call). Rotate the `ghp_` PAT.
- `.claude/skills/` and `.github/skills/` are hand-synced copies.
