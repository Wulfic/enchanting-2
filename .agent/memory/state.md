# Current State

> Rewritten at every phase boundary — not appended to. Keep it under ~30 lines so it is
> cheap to read at the start of every session. History belongs in `decisions.md` and
> `gotchas.md`; this file only answers "where am I right now?".

**Last updated:** 2026-10-06
**Branch:** main (commit straight to main and push; no CI; see decisions.md)

## Where we are
Phase 1 is done except 1.6 (`runClient` by the owner). **Phase 2 (2A, 2B, 2C) is done** except the owner's in-client checks.
2C: `config/ConfigView` (pure mode resolver), `client/ConfigEditing` (save → `.bak` → `ServerConfigs.reload` when
hosting), `client/ConfigScreen` (YACL), `client/ModMenuIntegration`, `lang/en_us.json`. Owner: the title screen edits
the file, and all SP worlds share one file. `./gradlew build`: 56 JUnit + 8 GameTests. The config-reload dev check passes.

## What's next
1. **Owner review of 2C** (stopped here at the owner's request: stop after each major section).
2. Owner, in one `runClient` session: 1.6 + 2B.9 + 2C.7 (TODO.md lists each check).
3. Phase 3 (experience curve): expand 3.1–3.5 into file-level steps in TODO.md first.

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
