# Current State

> Rewritten at every phase boundary — not appended to. Keep it under ~30 lines so it is
> cheap to read at the start of every session. History belongs in `decisions.md` and
> `gotchas.md`; this file only answers "where am I right now?".

**Last updated:** 2026-10-06
**Branch:** main (commit straight to main and push; no CI; see decisions.md)

## Where we are
Phase 1 (scaffold) is mostly done. `./gradlew build` is green: it compiles, runs 3 JUnit tests,
and runs 2 server GameTests (Loom wires `runGameTest` into `check`). Versions are pinned in
`gradle.properties` and listed in TODO.md Phase 1.

## What's next
1. 1.6: owner runs `./gradlew runClient` once to confirm the client reaches the title screen.
2. 1.8: dump the 1.21.11 enchantment registry and check it against DESIGN §8 (Lunge, effect
   components), and confirm the chunk Data Attachment API persists on 1.21.11.
3. Phase 2 (config). Add the Mod Menu, YACL and Jankson deps then, not before.

## Open questions / blockers
- None.

## Do not forget
- Plugin ID is `net.fabricmc.fabric-loom-remap` for 1.21.11 (gotchas.md).
- The owner often runs another mod's tests in a second VS Code instance. Never kill java
  processes by name, and check that port 25565 is free before `runServer`.
- 1.21.x enchantments are data-driven: use `EnchantmentEvents.MODIFY`, not override JSON.
- `.env` and MCP configs are tracked with live credentials (owner's call). Rotate the `ghp_` PAT.
- `.claude/skills/` and `.github/skills/` are hand-synced copies.
