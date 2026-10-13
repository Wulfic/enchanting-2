# Current State

> Rewritten at every phase boundary — not appended to. Keep it under ~30 lines so it is
> cheap to read at the start of every session. History belongs in `decisions.md` and
> `gotchas.md`; this file only answers "where am I right now?".

**Last updated:** 2026-10-12
**Branch:** main (commit straight to main and push; no CI; see decisions.md)

## Where we are
Phases 1–9, 10a, 10b, 10e, 10f, 10g, 10h, 10i, 10j, 10k, 10cA, 10cB, 10cC (CA, CB, CC), 10cD (DA, DB), 10cE (EA, EB, EC), 10cF (FA, FB) and 10cG (GA, GB, GC) done: Phase 10c complete (TODO.md has each stage's notes; owner `runClient` checks still open, listed below).
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
- 10eC (2026-10-12): Weight (`movement_penalty` component, one capped modifier set at `handleEquipmentChanges`,
  `curses.weight_max_slowdown_percent` 50) and Hunger (`exhaustion` component, `causeFoodExhaustion`), `curse/*`.
- 10cA (2026-10-12): Calm Gaze (`EnderManMixin`), Phantom Ward (`PhantomMixin.canAttack`, transient provocation),
  Clarity (`LivingEntityMixin` wraps `addEffect`/`forceAddEffect`, server only, rounded), Sustenance (`PlayerMixin`),
  Endurance (`ServerPlayerMixin`, sliced after `isSprinting`), Sure Footing on powder snow (`PowderSnowBlockMixin`);
  components in `ModEffects`, shared `effect/WornEffects` + `WornMath`.
- 10cB (2026-10-12): Lava Walking (`LivingEntity.canStandOnFluid`, flows via `LiquidBlockMixin`, not sneaking or sunk)
  and Snow Walker (`canEntityWalkOnPowderSnow`, `canFreeze`), rules in `effect/Footing`; `BootsGameTest` on husks.
- 10cCA (2026-10-12): Steadfast (`BlocksAttacksMixin.disable`), Spiked/Bulwark/Riposte (`shield/ShieldEffects` from a
  `@WrapOperation` on `hurtBlockingItem` in `applyItemBlocking`; Riposte's hit at `hurtServer` HEAD); hidden
  `enchanting2:bulwark` mob effect; timers on `LivingEntity` (`shield/ShieldState`). Shields are book + anvil only.
- 10cCB (2026-10-12): Deflect through `Entity.deflection` (`EntityMixin`), `shield/ShieldDeflect` (`AT_SHOOTER`), owner
  set at `Projectile.deflect` RETURN (`ProjectileMixin`, which also holds the once-per-projectile flag); entity type
  tag `enchanting2:deflectable`.
- 10cCC (2026-10-12): Steady Stance (client `LocalPlayerMixin.itemUseSpeedMultiplier`), Shield Bash (client
  `MinecraftMixin` lets the attack click through; server `PlayerMixin` `@WrapMethod` on `attack`), `shield/ShieldBash`;
  `ShieldClientGameTest` drives real input.
- 10cDA (2026-10-12): Magnetic and Ender Link via `drops/DropRedirect` (context around `Block.dropResources` and
  `LivingEntity.dropAllDeathLoot`; `BlockMixin`, `EntityMixin.spawnAtLocation`, `ExperienceOrbMixin.award`).
- 10cDB (2026-10-12): Keep on Death (`InventoryMixin.dropAll` keeps them in place; `drops/KeepOnDeath` on Fabric
  `COPY_FROM`); items from tag `enchanting2:enchantable/keep_on_death`.
- 10cEA (2026-10-13): Area Mining (`tool/AreaMining`; `ServerPlayerGameModeMixin` remembers the client's face and
  wraps `destroyBlock`).
- 10cEB (2026-10-13): Replant (`tool/Replant` inside `BlockMixin`'s drop wrapper, ahead of Magnetic; block tag
  `enchanting2:replantable`).
- 10cEC (2026-10-13): Tiller (`tool/Tiller` from `HoeItemMixin.useOn`; radius capped at 8).
- 10cFA (2026-10-13): Quick Strike (`combat/QuickStrike` after the damage call in `PlayerMixin.attack` and
  `MobMixin.doHurtTarget`; `Lifesteal` skips the repeat).
- 10cFB (2026-10-13): Last Stand (`combat/LastStand` after `checkTotemDeathProtection`; attachment
  `last_stand_ready_at`; `config/LastStandSection` `pvp`; client `LastStandHud`). `StochasticRounding` snaps float error.
- 10cGA (2026-10-13): Speed (`elytra/GlideSpeed`, `GlideMath`; glide drag in `LivingEntityMixin`,
  `FireworkRocketEntityMixin` boost; capped at X).
- 10cGB (2026-10-13): Flight (`elytra/Flight`: a share of the rocket step in the glide wrap, durability at
  `updateFallFlying` TAIL; jump from `isJumping()` / `getLastClientInput()`).
- 10cGC (2026-10-13): Double Jump (`movement/DoubleJump`, client `LocalPlayerMixin` wraps `tryToStartFallFlying`,
  `PlayerMixin` air-jump flag; no packet).
- Tests: 366 JUnit + 323 GameTests (`./gradlew build`) + 7 client GameTests (`./gradlew runClientGameTest`).

## What's next
1. Next: 10d balance pass (TODO "10d": every candidate REVIEW → INCLUDE/DEFER/REMOVE), best after the owner's
   `runClient` checks below and answers to the open questions; a new enchantment still adds a `LevelCeilings` row, a
   `.desc` line, tags, a price row if single-level, docs rows.
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
   10j: an enchantment's max level / cost fields and their notes, a raised max after a restart (Efficiency 10) +
   10e: the five curse names in red with their Ctrl descriptions, a Weight set's walking feel (I vs V, the 50% cap),
   a curse's two discount fields and the Weight cap field, a curse picked at the table showing its discount line. + 10cA: the five new names and descriptions, staring at an
   enderman in a Calm Gaze helmet, a sleepless night in Phantom Ward, a Warden's Darkness with Clarity I vs III,
   sprinting through powder snow in Sure Footing III. + 10cB: a lava lake and its flowing edge in Lava Walking boots
   (speed, burn, sneak to sink, getting out after sinking), a fall onto lava, Snow Walker iron boots on powder snow.
   + 10cCA: the four shield names/descriptions, Spiked V against a zombie, Bulwark III's yellow hearts (gone after
   5 s), Steadfast III against an axe vindicator. + 10cCB: a skeleton against Deflect III (arrows come back, none to
   pick up), blaze fireballs, a ghast fireball. + 10cCC: walking with a Steady Stance shield up; Shield Bash III on a
   zombie (knockback, slow, shield down 3 s); a plain shield still can't attack while raised. + 10cDA: mining with
   Magnetic (inventory, XP to you, a full inventory spilling), Ender Link, a Magnetic sword kill. + 10cDB: dying with
   Keep on Death gear (back in place), with keepInventory on (no doubles), with Vanishing on it (gone). + 10cEA: Area
   Mining through stone with ores (ores stay), a wall vs a floor, sneaking, a shovel in dirt. + 10cEB: Replant on a
   wheat field and on cocoa. + 10cEC: Tiller III on grass (7×7), blocks under tall grass staying. + 10cFA: Quick
   Strike V on zombies (double hits, no extra knockback), with Vampirism. + 10cFB: Last Stand in a fight (save,
   sound, HUD timer), with a totem in hand, the "Last Stand in PvP" toggle. + 10cGA: Speed V vs plain elytra over a
   long flat, a rocket boost, no rubber-banding. + 10cGB: Flight I / III / V held while gliding and climbing (is I
   too close to V?), the durability drain. + 10cGC: double jumps (land, cliff, none in water or on a ladder, elytra
   takes off on the second press).

## Open questions / blockers
- Owner: Magnetic and Ender Link share a tool (DESIGN §4: ours declare no exclusive set; ender chest first). DESIGN §9
  had said they conflict. Keep, or make them exclusive?
- Owner: Flight's model. A share of a rocket per level means every level cruises near a rocket's speed once it gets
  there; levels differ in how fast they get there. The alternative (a level-scaled target) makes I barely help and
  V climb 2.5× harder than a rocket. decisions.md 2026-10-13 has the numbers.
- Owner: Keep on Death's items are an item tag (`enchanting2:enchantable/keep_on_death`, durables by default). Does
  "configurable" need a config-screen list too?
- Owner: after a pick near the bottom of a scrolling table list, the picked row can scroll out of view (its footer
  grows). Keep the picked row in view?
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
- A new GameTest class must be listed in `src/gametest/resources/fabric.mod.json` or it silently never runs.
- `./gradlew build` runs JUnit + server GameTests only. Run `./gradlew runClientGameTest` too for client changes (opens
  a game window, ~2.5 min; screenshots in `build/run/clientGameTest/screenshots/`).
