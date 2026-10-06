# Current State

> Rewritten at every phase boundary — not appended to. Keep it under ~30 lines so it is
> cheap to read at the start of every session. History belongs in `decisions.md` and
> `gotchas.md`; this file only answers "where am I right now?".

**Last updated:** 2026-10-06
**Branch:** main (commit straight to main and push; no CI; see decisions.md)

## Where we are
Phase 1 is done except 1.6 (`runClient` by the owner). Phase 2 plan approved (owner said "continue", 2026-10-06).
**Stage 2A is done** (pure-Java config core in `config/`, Jankson jar-in-jar). `./gradlew build`: 35 JUnit + 4 GameTests.
The experience keys `post_threshold_mode` and `preserve_vanilla_curve_below_threshold` were dropped (owner, decisions.md).

## What's next
1. Stage 2B (server wiring: holder mixin, `/reload`, sync payload, vanilla-client disconnect), then stop for review.
2. Owner, for 1.6 + 2C.5: `runClient` → SP world → open to LAN → quit → reopen; player-inventory removal check.

## Open questions / blockers
- None open. (Restore mirror: rejected, decisions.md 2026-10-06.)

## Do not forget
- Plugin ID is `net.fabricmc.fabric-loom-remap` for 1.21.11. 1.21.11 Mojang names use `Identifier`, not `ResourceLocation`.
- Never kill java by name. The dev server uses 25566; the owner's other instance may be on 25565.
- 1.21.x enchantments are data-driven: use `EnchantmentEvents.MODIFY`, not override JSON. MODIFY runs
  before the server exists, so Phase 4 reads the config file directly (gotchas.md).
- Config code never rewrites an existing file on load. Only the GUI save writes, and it makes a `.bak` first.
- `.env` and MCP configs are tracked with live credentials (owner's call). Rotate the `ghp_` PAT.
- `.claude/skills/` and `.github/skills/` are hand-synced copies.
