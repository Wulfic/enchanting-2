# Current State

> Rewritten at every phase boundary — not appended to. Keep it under ~30 lines so it is
> cheap to read at the start of every session. History belongs in `decisions.md` and
> `gotchas.md`; this file only answers "where am I right now?".

**Last updated:** 2026-10-07
**Branch:** main (commit straight to main and push; no CI; see decisions.md)

## Where we are
Phases 1–6b done (TODO.md has each stage's notes; owner `runClient` checks still open, listed below).
- 3: `ExperienceCurve`, no level cap, exact XP points, counters saturate. 4: `MaxLevels` via `MODIFY` (restart to
  apply), `ConflictFamily` + `allow_together`, `EnchantRules`, Lure/Riptide 5 with safe caps.
- 5: `cost/` (`CostModel`, `PricingTable`, curse discount, `cleanseCost`, `extractCost`).
- 6: anvil on `AnvilCalculator` (book modes, no 40 cap, `repair_cost` never touched), S2C preview, side panel + tab,
  `client.json5`.
- 6b (2026-10-07): grindstone Extract / Cleanse / Disenchant / Repair, nothing until a pick (server-held, set by C2S
  `GrindstoneChoicePayload`), picker panel. `NetworkProtocol.VERSION` 3. Shared panel code in `screen/` and
  `client/SidePanel`.
- 7A (2026-10-07): `table/` calculator (`TableCalculator`, `TablePower`, `TablePool` + tag `offered_at_table`), `table`
  config section. 7B: `EnchantmentMenuMixin` (vanilla offers off, server-held selection, priced Enchant payload),
  `Table{Select,Enchant,Preview}Payload`, `NetworkProtocol.VERSION` 4. No client screen yet (7C/7D): on a real
  server the table shows vanilla's empty offer slots until then.
- Tests: 228 JUnit + 125 GameTests (`./gradlew build`) + 2 client GameTests (`./gradlew runClientGameTest`).

## What's next
1. Phase 7 (enchanting table), planned in TODO.md as 7A–7D (owner answers in decisions.md 2026-10-07 "Enchanting
   table"). 7A–7C done. Next: Stage 7D (`EnchantmentScreenMixin`, `TableScreenAccess`, client GameTest, DESIGN §6).
2. Owner, in one `runClient` session: 1.6 + 2B.9 + 2C.7 + 3B.6 HUD check + 4A.7 (synced max level) (bar at level
   ~1,000,000) + 6D: panel look, config screen "Display" toggle, and a vanilla server keeping the vanilla anvil screen +
   6bC: grindstone panel look, and a vanilla server keeping the vanilla grindstone.

## Open questions / blockers
- None. Owner (2026-10-06): a lowered max level never touches existing items (decisions.md).

## Do not forget
- Plugin ID is `net.fabricmc.fabric-loom-remap` for 1.21.11. 1.21.11 Mojang names use `Identifier`, not `ResourceLocation`.
- Never kill java by name. The dev server uses 25566; the owner's other instance may be on 25565.
- 1.21.x enchantments are data-driven: use `EnchantmentEvents.MODIFY`, not override JSON. MODIFY runs
  before the server exists and `/reload` doesn't re-run it (gotchas.md). FAPI 0.141.6 = item-api 11.6.0.
- Config code never rewrites an existing file on load. Only the GUI save and the anvil tab (`client.json5`) write,
  both through `saveWithBackup` (`.bak` first).
- `.env` and MCP configs are tracked with live credentials (owner's call). Rotate the `ghp_` PAT.
- `.claude/skills/` and `.github/skills/` are hand-synced copies.
- `./gradlew build` runs JUnit + server GameTests only. Run `./gradlew runClientGameTest` too for client changes (opens
  a game window, ~2.5 min; screenshots in `build/run/clientGameTest/screenshots/`).
