# Current State

> Rewritten at every phase boundary — not appended to. Keep it under ~30 lines so it is
> cheap to read at the start of every session. History belongs in `decisions.md` and
> `gotchas.md`; this file only answers "where am I right now?".

**Last updated:** 2026-10-12
**Branch:** main (commit straight to main and push; no CI; see decisions.md)

## Where we are
Phases 1–9, 10a, 10b, 10f, 10g, 10h, 10i, 10j and 10k done (TODO.md has each stage's notes; owner `runClient` checks still open, listed below).
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
- 10f (2026-10-12): `PricingTable.BUILT_IN` single-level rows (flat 10) and Frost Walker 2/level.
- 10k (2026-10-12): Fortune soil/bamboo/leaves tags; table panel rows by translated name (`TablePanelView.sortedByName`).
- 10gA (2026-10-12): `ItemWidening` + `ItemWideningHook` + `RegistryDataLoaderMixin` (lent item lookup); axes take
  Knockback/Looting/Fire Aspect, crossbows Flame/Infinity/TrueShot.
- 10gB (2026-10-12): `silk_touch.collect_spawners` (`SilkTouchSection`), `spawner/CollectedSpawners` (mob type only +
  `enchanting2:collected_spawner` stamp, owner), `SpawnerBlockMixin` (no XP), `BlockItemMixin` restore after the op gate.
- 10gC (2026-10-12): `bucket/InfiniteBuckets` (256 uses, `afterUse`), `ItemUtilsMixin` (hand + cauldron),
  `BucketDispenseBehaviorMixin` (`$4`), fuel/ingredient guards, `EnchantRules` bucket gate + `APPLY_ORDER`.
- 10i (2026-10-12): Step Assist, Anchored (hits only), Sure Footing (level / 3, soul sand + honey; powder snow → 10c).
  `everySingleLevelEnchantmentCostsTen` guards the flat-10 rows registry-wide.
- 10h (2026-10-12): Ctrl descriptions (`tooltip/DescriptionPlan`, `client/EnchantmentDescriptions`), `client.json5`
  `tooltips.*`, 62 `.desc` lines guarded by `EnchantmentDescriptionLangTest` (new enchantments must add one).
- 10jA (2026-10-12): `enchantment/LevelCeilings` (every vanilla + own ID: none/soft/hard + note), replaces
  `MaxLevels.SAFE_MAX`; hard: Lure 5, Riptide 7, Quick Charge 4, Piercing 127, Lunge 13, Wind Burst 14.
- 10jB (2026-10-12): max level / base cost / cost per level per enchantment in the config screen
  (`EnchantmentNumbersDraft`, defaults from `DefaultMaxLevels` + `PricingTable`); `ConfigScreen.build()` is public.
- 10eA (2026-10-12): `Pricing.discountAt(level)`, `discount_per_level` key, curse discount fields on the screen
  (`DefaultCurses` off the title screen).
- 10eB (2026-10-12): Fragility, Greed, Fumbling as data, `ModEnchantments.CURSES`, curse tags like vanilla's,
  durability damage rounds at random (`EnchantmentHelperMixin`).
- Tests: 348 JUnit + 265 GameTests (`./gradlew build`) + 5 client GameTests (`./gradlew runClientGameTest`).

## What's next
0. NOW: 10e curses: 10eA, 10eB done; next 10eC (Weight, Hunger: components + mixins + `weight_max_slowdown_percent`).
   Plan in TODO "Phase 10e"; owner answers in decisions.md "10e curse scaling".
1. Then: 10c (M route, see TODO; each enchantment needs its own written sub-plan with owner questions first: edge
   cases from DESIGN §9; each adds a `LevelCeilings` row, a `.desc` line, a screen entry comes free), 10e (curses;
   scaling answered 2026-10-12), 10d balance pass.
2. Owner, in one `runClient` session: 1.6 + 2B.9 + 2C.7 + 3B.6 HUD check + 4A.7 (synced max level) (bar at level
   ~1,000,000) + 6D: panel look, config screen "Display" toggle, and a vanilla server keeping the vanilla anvil screen +
   6bC: grindstone panel look, and a vanilla server keeping the vanilla grindstone + 7D: table panel and window look
   (does the ready Enchant button, vanilla's beige offer sprite, read as clickable?), and a vanilla server keeping the
   vanilla table + 8: the config screen's "Fortune" category and its warning tooltip, and a quick feel check (Fortune V
   axe on a tree; place a log and mine it) + 9C: the config screen's "Enchantments" category (tick boxes, names,
   the remote "kept on the server" label) + 10a: new names in tooltips, a Health V set's hearts + 10b: the five new names, a Vision helmet at night
   (no flicker, gone when taken off), a TrueShot arrow's flight, the "Enchantment effects" config category + 10gB:
   the "Silk Touch collects spawners" toggle, a collected spawner's tooltip (vanilla's "Spawns: Zombie" line) + 10gC:
   an Infinity bucket's durability bar, pouring by hand feels right (no flicker to an empty bucket) + 10i: Step Assist
   up a block, Sure Footing III on soul sand feels like normal ground + 10h: description wording, the two "Display"
   toggles, and with Enchantment Descriptions installed that only one set of lines shows (confirms `enchdesc`) +
   10j: an enchantment's max level / cost fields and their notes, a raised max after a restart (Efficiency 10).

## Open questions / blockers
- Owner (2026-10-06): a lowered max level never touches existing items (decisions.md).

## Do not forget
- `docs/ENCHANTMENTS.md` is the owner's quick reference (by armor/item type). Update it with every new enchantment,
  max level or price change.
- Owner principle (DESIGN §1): every option goes in the config screen too, and every enchantment needs a `.desc` lang
  line (10h) and a level-ceiling row (10j).
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
