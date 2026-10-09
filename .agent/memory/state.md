# Current State

> Rewritten at every phase boundary — not appended to. Keep it under ~30 lines so it is
> cheap to read at the start of every session. History belongs in `decisions.md` and
> `gotchas.md`; this file only answers "where am I right now?".

**Last updated:** 2026-10-09
**Branch:** main (commit straight to main and push; no CI; see decisions.md)

## Where we are
Phases 1–9 and 10a done (TODO.md has each stage's notes; owner `runClient` checks still open, listed below).
- 3: `ExperienceCurve`, no level cap, exact XP points, counters saturate. 4: `MaxLevels` via `MODIFY` (restart to
  apply), `ConflictFamily` + `allow_together`, `EnchantRules`, Lure/Riptide 5 with safe caps.
- 5: `cost/` (`CostModel`, `PricingTable`, curse discount, `cleanseCost`, `extractCost`).
- 6: anvil on `AnvilCalculator` (book modes, no 40 cap, `repair_cost` never touched), S2C preview, side panel + tab,
  `client.json5`.
- 6b (2026-10-07): grindstone Extract / Cleanse / Disenchant / Repair, nothing until a pick (server-held, set by C2S
  `GrindstoneChoicePayload`), picker panel. `NetworkProtocol.VERSION` 3. Shared panel code in `screen/` and
  `client/SidePanel`.
- 7 (2026-10-07): table on `TableCalculator` (pool = tag `offered_at_table`, `TablePower` tiers, `table` config),
  `EnchantmentMenuMixin` (vanilla offers off, server-held selection, priced Enchant payload, protocol v4), panel
  (`TablePanelView`, scrolling `SidePanel.drawList`) and `EnchantmentScreenMixin` (price + Enchant in the offer rows).
- 8 (2026-10-08): `fortune.silk_fortune_mode`, `TridentItemMixin` (Riptide throws on land), conflict-effect GameTests;
  placed-block tracker (`fortune/PlacedMarks` per-chunk attachment, fail-closed codec, `MarkMoves`, 8 marking mixins,
  clear at `destroyBlock` RETURN); `FortuneDrops` on `MODIFY_DROPS` with `fortune_multiplies/<kind>` tags.
- 9 (2026-10-09): `enchantments.json5` acquisition keys (`config/AcquisitionSettings`), `acquisition/*`
  (`AcquisitionRules`, `WeightedPick`, registry stamp + holder-owner lookup, TRADE scope), mixins on `selectEnchantment`,
  `enchant_randomly`, librarian books, villager `getOffer`s; table pool filter + treasure override; config screen
  "Enchantments" category (`ConfigView<T>`, `EnchantmentFlagsDraft`, `VanillaEnchantments`).
- Tests: 301 JUnit + 210 GameTests (`./gradlew build`) + 4 client GameTests (`./gradlew runClientGameTest`).

## What's next
1. Phase 10b in progress (plan + stages in TODO.md; owner answers in decisions.md "Phase 10b", "round 2", "dragon").
   10bA done (d5ea75d): `effect/ModEffects` components, Vampirism (`hurtServer` wrap), Beheading (`HeadDrops`,
   `loot_table/beheading/heads`), `beheading` + `smelting` config sections and the screen's "Enchantment effects"
   category. 10bB done: Smelting (`SmeltingDrops`, inside `FortuneDrops`' listener after Fortune), TrueShot Aura
   (`SuspendGravity`, `AbstractArrowMixin`). Next: 10bC Vision (`NightVisionTicker`), then DESIGN §9 update.
2. Owner, in one `runClient` session: 1.6 + 2B.9 + 2C.7 + 3B.6 HUD check + 4A.7 (synced max level) (bar at level
   ~1,000,000) + 6D: panel look, config screen "Display" toggle, and a vanilla server keeping the vanilla anvil screen +
   6bC: grindstone panel look, and a vanilla server keeping the vanilla grindstone + 7D: table panel and window look
   (does the ready Enchant button, vanilla's beige offer sprite, read as clickable?), and a vanilla server keeping the
   vanilla table + 8: the config screen's "Fortune" category and its warning tooltip, and a quick feel check (Fortune V
   axe on a tree; place a log and mine it) + 9C: the config screen's "Enchantments" category (tick boxes, names,
   the remote "kept on the server" label) + 10a: new names in tooltips, a Health V set's hearts.
3. Owner, optional: the table panel lists rows in ID order, so all `enchanting2:` rows come before `minecraft:` ones.
   Sorting by translated name would interleave them (a client-side change to the panel's order).
4. Owner, optional: the Fortune default tags are DESIGN §6's draft list. Grass, podzol, coarse/rooted dirt, mycelium,
   bamboo blocks and leaves aren't in them.

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
