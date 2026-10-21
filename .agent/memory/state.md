# Current State

> Rewritten at every phase boundary — not appended to. Keep it under ~30 lines so it is
> cheap to read at the start of every session. History belongs in `decisions.md` and
> `gotchas.md`; this file only answers "where am I right now?".

**Last updated:** 2026-10-21
**Branch:** `main` only (owner 2026-10-21: as few branches as possible). `port/loaders` was fast-forwarded into main and deleted; commit straight to main and push. CI on every push (`ci.yml`: verifyAll + gitleaks + client GameTests headless per Fabric node); a release is a pushed `v<mod_version>` tag (`release.yml`); `wiki/` syncs to the GitHub wiki (`wiki.yml`). Agent skills/AGENTS.md come from `Wulfic/Minecraft-Template` (decisions.md "One branch (main)").

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
- 10d (2026-10-13): balance pass done; retunes in decisions.md "10d rulings".
- 10l (2026-10-13): table list in the window (`table/TableWindow`, `TableListModel`, client `TableList`; side panel
  gone, `SidePanel` list mode deleted). 10n: amounts carry units (`PanelText.amount`, `Row.unit`). 10m:
  `mobs.health_multiplier` (`mobs/MobHealth`, permanent modifier, `AttributesMixin` ceiling 1,000,000).
- 10o (2026-10-13): anvil and grindstone in the window (296 × 166, `screen/ColumnWindow`, one full-height box on the
  right at x 173): client `AnvilBox`, `GrindstoneList`, `CostLabel`; shared `ScrollModel`, `PickerStyle`, `ScrollBar`,
  `WindowBackground`; `ItemCombinerScreenMixin` draws the anvil background. `SidePanel`, `SidePanelLayout`,
  `AnvilPanelTab` and client.json5 `anvil.show_preview_panel` gone. Table 10l v2 accepted by the owner.
- 10oD (2026-10-13): owner keeps the anvil's wrap; grindstone picks wrap onto more lines instead of "…".
- 11.1 (2026-10-13): `README.md` (players/modpack makers), `CHANGELOG.md` (1.0.0), `docs/CONFIG.md` with each file's
  defaults verbatim, guarded by JUnit `ConfigDocsTest`.
- 11b (2026-10-13, owner's table report): armor + offhand slots in the table window (`table/TableEquipmentSlot`,
  menu indices 38–42, protocol 5), picks start at level I, row descriptions need Ctrl, tall tooltips go compact with
  Ctrl and scroll (`tooltip/TooltipFit`, `TooltipScroll`, client `GuiGraphicsMixin`, `MouseHandlerMixin`). CHANGELOG
  has an [Unreleased] section; the local `v1.0.0` tag predates it (protocol 4).
- 11c (2026-10-13, owner's second report): Ctrl descriptions always "Name: description" (`TooltipFit.choose` gone);
  a picked table row's price includes its own surcharge (Mending's true cost; footer line only for one already on the
  item); anvils unbreakable by default, `anvil.vanilla_durability` + `durability_multiplier` (`anvil/AnvilWear`,
  hooks at `AnvilBlock.damage` in `AnvilMenuMixin` lambda `method_24922` and `FallingBlockEntityMixin`).
- 11d (2026-10-13, owner's third report): the "always-on descriptions" were the owner's `enchdesc` (removed from the
  Prism instance, now in `minecraft/mods-disabled/`); the scroll line shows on any too-tall tooltip
  (`EnchantmentDescriptions.onTooltip`); config tabs in rows (`client/TabbedConfigScreen`, `screen/TabRows`).
- 11e (2026-10-14): public-release prep. History scrubbed of `.env` + MCP configs (new GitHub repo, public, wiki live; the
  old one is the private `enchanting-2-archive`), MIT, CI/release/wiki workflows, `wiki/`, `ReleaseDocsTest`, Gradle on JDK 25
  (`gradle_java_version`) while the mod targets 21.
- 11f (2026-10-14, owner report "Flight never thrusts"): Elytra Slot compat (`compat/ElytraSlot` by reflection,
  `elytra/WornElytra`); Flight's wear hook wraps the `updateFallFlying` call in `aiStep`. Gametest stub of the mod.
- 12A (2026-10-14, branch `port/26x`): Stonecutter 0.9.8 + loom-back-compat 0.4.3, one node `versions/1.21.11/`
  (per-node gradle.properties, `run_root`), Groovy; jar = v1.0.1 entry for entry. Run tasks are node-qualified.
- 12B (2026-10-14): node `versions/26.1.2/` (Java 25, Loom 1.18.4); renames as Stonecutter replacements in
  build.gradle, 25 `//?` conditions, `port/LootTool`; band-only mixins via `<% if (band == 'A') %>` in mixins.json.
- 12C (2026-10-15): runtime matrix `matrix/<version>` (plain subprojects, `matrix/matrix.gradle`: the 26.1.2 node's
  jar + `gametestJar` on another Minecraft; `:matrix-<v>:linkCheck`, `runGameTest`, `runClientGameTest`) and the static
  link check (`matrix/linkcheck/LinkCheck.java`). Verdict: one band per 26.x line (decisions.md 2026-10-15).
  B1 = 26.1–26.1.2 proven (range `>=26.1 <26.2-`). CI's `build` configures the matrix projects (empty jars) but
  doesn't run their GameTests yet (12F).
- Tests per node: 445 JUnit + 340 GameTests (2026-10-20) (`./gradlew build`, all four nodes, also in CI) + 10 client GameTests
  (`./gradlew :<node>:runClientGameTest`, one node at a time).
- 12E (2026-10-15/19): node `versions/26.3/` (B3, range `>=26.3 <26.4-`): renderer/loot/input renames as
  replacements, moved targets (`Enderman`, hoe `BlockTransformer`, fuel component mixins), loot JSON upgraded per node
  in processResources (decisions.md "12E").

## What's next
00. **Phase 13 (on `main`), 2026-10-20/21.** Owner: 1.21.11 stays Fabric-only *and current* (not frozen, unlike the
    template's policy); 26.x bands get Quilt, NeoForge, Forge. Done: 13A spikes, 13B seam, 13C NeoForge ×3, 13D Forge
    ×3, 13F-names, **13E config screen** (E1 9ed8c91 model, E2 26905e7 `client/config/FormScreen` + Mod Menu
    fallback, E3 894f632 NeoForge `IConfigScreenFactory` / Forge `ConfigScreenFactory`). verifyAll 2026-10-21: 463
    JUnit + 340 GameTests on all 10 nodes + matrix 26.1 / 26.1.1. e4838d4: GameTest servers capped at 4 at once
    (`gametest_max_parallel`; unbounded, the box ran out of commit memory, gotchas.md 2026-10-21) and the dev-server
    smoke works on NeoForge/Forge (EULA + stdin); smoke green on 26.3, 26.3-neoforge, 26.3-forge.
    **13F (2026-10-21, TODO.md "F1–F6 results"):** `scripts/server_boot_check.py` (exit 0/1/2, `--self-test`),
    `scripts/checks/boot.txt` (fingerprint, mutation-checked), `scripts/boot-matrix.txt` + `scripts/boot-all.sh
    [shipped|full|all] [loader]`, `BootMatrixTest`, a boot row = a claim (decisions.md 2026-10-21). Shipped 10/10
    green; Forge B1 widened to 26.1–26.1.2; NeoForge 26.1.0/26.1.1 betas red (LootModifier API, gotchas) so NeoForge
    B1 stays 26.1.2; Quilt green on 26.x (340/340 production GameTests). ci.yml: build per loader (`-Ploaders`),
    `boot` job (shipped on push, all on dispatch). Committed 1826c34 + ed03d51, CI green on push (10 boot
    rows) and on dispatch (20 rows).
    **13G (2026-10-21): every gate green on the 1.2.0 tree and recorded (TODO.md 13G session notes); committed in
    four units. Resume: push main → CI green → push `v1.2.0` → release.yml 10 jars → mod-page text.** Owner rulings (decisions.md "13G owner rulings", "Forge mod packs get a
    KnownPack"): rung 5 on NeoForge/Forge = dev-client hook (`scripts/client_boot_check.py`, `:<node>:runClientBootCheck`);
    tag push pre-authorized once every gate is green and recorded; mod pages by the owner by hand (hand over text);
    fix Forge's experimental-settings warning. Rung 5 found 3 defects, all fixed in the tree: NeoForge client crash
    (`GuiGraphicsMixin` → `tooltip*`), NeoForge logoFile warning screen (`iconFile`), Forge experimental warning
    (`ForgeKnownPacks` + `PackLocationInfoMixin`). **Evening:** whitelist fixed (vanilla 26.3 defaults
    `white-list=true`); `--join` PASS on 26.3-forge and 26.3-neoforge; new GameTest
    `BootGameTest.everyEntryOfOursLoadsStable`; G5 text done, `mod_version=1.2.0` in the tree. Gates on the 1.2.0 tree: verifyAll
    exit 0 (341/341 ×10), client boot ×6 PASS, Fabric client GameTests ×4 exit 0, boot-all all 20/20, release dry run
    10 jars.
    Open owner checks: `runClient` on NeoForge + Forge (config screen, tooltips, HUD, panels, joins). Owner: they do
    **not** block 1.2.0; findings go into 1.2.1.
    **Only one session works this tree** (owner 2026-10-20); a second session's builds lock GameTest worlds (gotchas).
0. **v1.1.0 released 2026-10-19** (https://github.com/Wulfic/enchanting-2/releases/tag/v1.1.0): four jars,
   `enchanting2-1.1.0+{1.21.11,26.1-26.1.2,26.2,26.3}.jar`, from main 9246bc4. Phase 12 (12A–12G) done.
1. **12H is process, not code** (TODO.md): a new Minecraft version gets `matrix/<v>/` on the nearest band's jar plus
   a settings.gradle entry; `linkCheck` first, then GameTests (`verifyAll` runs them). Green widens that band's name
   by itself (build.gradle derives it); update README + wiki Installation tables (ReleaseDocsTest). Red = a new node.
   2026-10-19 session: the client GameTest flake is fixed (e016372; Fabric 4.3.5's synchronizer, off on 1.21.11;
   cross-side waits go through `Delivery`). `port/26x` deleted (restore from `refs/pull/1/head`); the stale worktree
   is unregistered, its leftover folder (147c41c6 scratchpad `base/`, 530 MB) is the owner's to delete.
2. Owner checks and housekeeping: TODO.md "Owner checks (open)" and "Owner housekeeping" (the full list moved there
   from this file on 2026-10-14; TODO.md was trimmed to open work, old notes at `git show fd3504c:TODO.md`).
   A new enchantment still adds a `LevelCeilings` row, a `.desc` line, tags, a price row if single-level, docs rows.

## Open questions / blockers
- Owner (2026-10-06): a lowered max level never touches existing items (decisions.md).

## Do not forget
- `docs/ENCHANTMENTS.md` is the owner's quick reference (by armor/item type). Update it with every new enchantment,
  max level or price change.
- `docs/CONFIG.md` quotes the default config files verbatim; `ConfigDocsTest` fails until it's updated after any
  config key, default or comment change. README and CHANGELOG are player-facing now.
- Owner principle (DESIGN §1): every option goes in the config screen too, and every enchantment needs a `.desc` lang
  line (10h) and a level-ceiling row (10j).
- Loom's plugin is picked by loom-back-compat (remap on 1.21.11, plain on 26.x); never name it in build.gradle. 1.21.11 Mojang names use `Identifier`, not `ResourceLocation`.
- Never kill java by name. The dev server uses 25566; the owner's other instance may be on 25565.
- 1.21.x enchantments are data-driven: use `EnchantmentEvents.MODIFY`, not override JSON. MODIFY runs
  before the server exists and `/reload` doesn't re-run it (gotchas.md). FAPI 0.141.6 = item-api 11.6.0.
- Config code never rewrites an existing file on load. Only the GUI save and the anvil tab (`client.json5`) write,
  both through `saveWithBackup` (`.bak` first).
- `.env`, `.mcp.json` and `.claude/mcp.vscode-reference.json` are git-ignored (Phase 11e); the repo is public.
  `.mcp.example.json` is the tracked template. CI's gitleaks job fails on any committed secret.
- `.claude/skills/` and `.github/skills/` are hand-synced copies.
- A new GameTest class must be listed in `src/gametest/resources/fabric.mod.json` or it silently never runs.
- Client GameTests: read the other side only after `Delivery.toServer` / `Delivery.toClient`, never after a fixed
  `waitTicks` (gotchas.md 2026-10-19); time-based client state gets a test clock, never the wall clock.
- **Done = `scripts/verify-all.sh` green** (`./gradlew verifyAll`, 12F: every node's build, every matrix `linkCheck`,
  GameTests on claimed matrix versions 26.1/26.1.1; CI runs the same task). `./gradlew build` runs JUnit + server GameTests on every node. Run `./gradlew :<node>:runClientGameTest` too for
  client changes, one node at a time (opens a game window, ~3 min; screenshots in
  `versions/<node>/build/run/clientGameTest/screenshots/`); CI's `client` jobs run them on every node too (Xvfb +
  Mesa; 26.3 needs the Vulkan driver). A change is done when every node is green.
- B1's jar claims 26.1 and 26.1.1 too: `verifyAll` (and CI) runs their link checks and GameTests. A matrix node's role
  is derived (control iff `minecraft_version == matrix_jar_node`); a new `matrix/<v>/` also goes in settings.gradle.
- `src/` is written for 1.21.11 (`vcsVersion`); 26.x code sits in `//? if >=26.1` comments and in build.gradle's
  `stonecutter { replacements }`. Read `versions/26.1.2/build/generated/stonecutter/` to see what 26.1.2 compiles.
  Never commit with `stonecutter.active` switched away from 1.21.11. Pick anonymous-class / lambda targets by javap.
