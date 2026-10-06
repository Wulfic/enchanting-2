# Current State

> Rewritten at every phase boundary — not appended to. Keep it under ~30 lines so it is
> cheap to read at the start of every session. History belongs in `decisions.md` and
> `gotchas.md`; this file only answers "where am I right now?".

**Last updated:** 2026-10-06
**Branch:** main (commit straight to main and push; no CI; see decisions.md)

## Where we are
Phase 1 is done except 1.6 (`runClient` by the owner). `./gradlew build` is green: 3 JUnit + 4 GameTests.
The dev server works: `scripts/dev-server-smoke.sh [commands.txt]` uses `run/server` on port 25566.
New cross-cutting requirement: safe to add or remove mid-playthrough, with SP/LAN/MP parity (DESIGN §2a).
Removal probe: items survive and only `enchanting2:` enchantments are dropped (gotchas.md).

## What's next
1. Phase 2 (config). Expand 2.x into file-level steps in TODO.md first. Overrides go in ONE file,
   `config/enchanting2/enchantments.json5` (owner). Config is per-`MinecraftServer`, no statics (§2a).
   Add the Mod Menu, YACL and Jankson deps at that point.
2. Owner, for 1.6: `runClient` → title screen → SP world → open to LAN → quit → reopen. Also check a
   player-inventory item after a datapack enchantment is removed.

## Open questions / blockers
- Owner: removal drops new enchantments permanently. OK as documented, or build a restore mirror?

## Do not forget
- Plugin ID is `net.fabricmc.fabric-loom-remap` for 1.21.11. 1.21.11 Mojang names use `Identifier`, not `ResourceLocation`.
- Never kill java by name. The dev server uses 25566; the owner's other instance may be on 25565.
- 1.21.x enchantments are data-driven: use `EnchantmentEvents.MODIFY`, not override JSON.
- `.env` and MCP configs are tracked with live credentials (owner's call). Rotate the `ghp_` PAT.
- `.claude/skills/` and `.github/skills/` are hand-synced copies.
