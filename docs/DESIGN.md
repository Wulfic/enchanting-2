# Enchanting 2 — Design Spec

Living spec. `TODO.md` holds the work list; this file holds the rules the work must satisfy.
Anything marked **OPEN** is unresolved and blocks the phase named next to it. When an OPEN
item is answered, replace it with the rule and log the choice in `.agent/memory/decisions.md`.

Source: rough plan drafted 2026-10-06, cleaned up for contradictions and checked against
how 1.21.x enchantments actually work.

---

## 1. Summary

| | |
|---|---|
| Name | Enchanting 2 |
| Loader | Fabric (Loader + Fabric API) |
| Minecraft | Java Edition 1.21.11 |
| Goal | Overhaul XP progression and enchanting while keeping every vanilla enchantment |

### Principles
- XP cost per level is flat after level 30. There is no intentional level cap.
- An item takes every enchantment that is **applicable** to it. There is no count limit.
- Applicability is mandatory. Mutual exclusion is a configurable rule, not a law.
- Costs are additive, transparent, configurable, and shown before the player commits.
- Vanilla enchantments are kept under their vanilla IDs. They are modified, never duplicated.
- The server is authoritative. A client can never override gameplay config.

### Terminology (enforced in code and config names)
- **XP level** / **experience level**: the player-facing number. A cost of 17 deducts 17 levels.
- **XP points** / **raw XP**: the underlying points. Levels and points are separate types in code.
- Never use "XP bar" in code or config. It's ambiguous.

---

## 2. Technical foundation

| Item | Value | Status |
|---|---|---|
| Java | 21 (1.21.x toolchain) | verify in Phase 1 |
| Build | Gradle + Fabric Loom | |
| Mappings | **Mojang official** (`loom.officialMojangMappings()`) | recommended, see below |
| Mixins | Only where Fabric API exposes no event or hook | |
| Mod Menu | Optional dependency; config screen entry point lives in client source set | |
| Source sets | Loom `splitEnvironmentSourceSets()`; `main` never imports `client` | |
| Tests | JUnit 5 for pure logic; Fabric GameTest for in-world behavior | |

**Mappings:** 1.21.11 is the last version Yarn ships for. Minecraft 26.x is unobfuscated
and uses Mojang names. Starting on Mojang mappings makes a later port a version bump instead
of a rename across the whole codebase.

**Enchantments in 1.21.x are data-driven.** Each one is a registry entry with
`supported_items`, `primary_items`, `exclusive_set`, `max_level`, `weight`, `min_cost`,
`max_cost`, `anvil_cost`, `slots` and `effects` (effect components). Consequences:
- Changing vanilla max levels goes through Fabric API `EnchantmentEvents.MODIFY`. It edits the
  builder at registry load, so other mods' and datapacks' changes stay intact. We do **not** ship
  override JSONs for vanilla enchantments.
- Conflicts are **not** a registry edit. Registry `exclusive_set`s stay vanilla, and `EnchantRules`
  relaxes configured families pair by pair (§4; decisions.md 2026-10-06).
- The enchantment registry syncs to clients, so modified max levels display correctly
  client-side without our own packet. Conflict toggles aren't in the registry. They reach
  clients in the `common.json5` config sync.
- Registry changes apply at registry load (world open / server start). A max-level change
  needs a server restart or reopening the world. `/reload` does not re-run `MODIFY` (dev-server
  probe, TODO 4A.6). Conflict toggles (`common.json5` → `allow_together`) are plain config and apply on `/reload`.
- Many new enchantments need **no Java code** (see §9 "Route").

### Identity
- Mod ID `enchanting2`, package `com.wulfnet.enchanting2`, display name "Enchanting 2".
- Private repo. No license file and no public distribution in v1.
- Target 1.21.11 only. Porting to 26.x comes after v1 is complete and tested.

### Config
- Format: JSON5, read and written by our own code using **Jankson** (plain-Java, bundled
  jar-in-jar). Config loading must never depend on a client-only or GUI library, so a
  dedicated server needs nothing but Fabric API.
- Screen: **Mod Menu + YACL, both optional client dependencies.** Mod Menu supplies the
  "Config" button and YACL builds the screen. Without them the mod still runs and is
  configured by file.
- Files: `config/enchanting2/common.json5` (gameplay, server-authoritative),
  `config/enchanting2/client.json5` (display only), and per-enchantment overrides keyed
  by enchantment ID.
- Every file has `schema_version`. Loading validates every value. An invalid value falls back
  to its default and logs a WARN naming the key, the bad value and the default.
- Gameplay config syncs to clients on join (S2C packet). On a remote server, the client
  screen shows server values read-only.
- Only the singleplayer/LAN host edits config in-game. On a dedicated server the screen is
  read-only for everyone, ops included. Admins edit the file, and vanilla `/reload` re-reads it
  and re-syncs it (owner, 2026-10-06).
- The title screen (no world open) edits the same file. All singleplayer worlds share one
  `common.json5`; there is no per-world copy in v1 (owner, 2026-10-06). The host's in-game
  save writes the whole file from the values in effect, so a hand edit that was never
  `/reload`ed is replaced (it survives in the `.bak`). A file that doesn't parse or comes from
  a newer schema makes the screen read-only. A server that sent no config shows a "vanilla
  rules" note.
- Our code never rewrites an existing config file on load. An invalid value only produces a WARN.
  The in-game save keeps a `.bak` of the previous file. An unparseable file on `/reload` keeps
  the previous config.
- Each section is added by the phase that implements its behavior, so no key is a dead toggle.
- Packages: `config`, `experience`, `enchanting`, `anvil`, `enchantment`, `compat`,
  `registry`, `network`, `client`, `mixin`.

## 2a. Lifecycle: adding, removing, singleplayer and multiplayer

Owner requirement (2026-10-06): the mod can be added to or removed from an existing world
without losing anything, and it works the same in singleplayer, LAN and on a dedicated server.

**Adding the mod to an existing world**
- XP: the stored level and progress are kept. Only the cost of later levels changes. No conversion.
- Fortune tracker: blocks placed before install have no mark and count as **natural** (owner
  choice). Each one can pay out a bonus once. Once it is mined and placed again it gets marked,
  so it can't loop. A chunk without our attachment means "no marks", not a read error.
- Existing enchanted items are unchanged. Levels above the new maximums can't occur because
  every maximum only goes up.

**Removing the mod**
- Nothing new in the world: no new blocks, block entities or items. The table, anvil and
  grindstone are vanilla blocks with new screens, so nothing turns into missing-block air.
- Vanilla enchantments above vanilla's max level (Protection V and so on) stay on the item and
  keep working at that level. Vanilla accepts levels up to 255.
- Chunk and entity attachments (tracker marks, cooldowns) are orphaned data that vanilla ignores.
- XP levels stay as they are. Only the curve reverts.
- **New `enchanting2:` enchantments are lost, but their items are kept** (measured on 1.21.11,
  Phase 1.10). Vanilla drops the unknown enchantment from the item and logs a WARN. The item and
  its other enchantments stay. A book holding only new enchantments becomes an empty enchanted
  book. This is permanent once the chunk saves, so reinstalling won't restore it. The owner
  accepted this on 2026-10-06: it is documented in the README, with no restore mirror. Player
  inventories are expected to behave the same, but that's unmeasured until the `runClient` check.

**Singleplayer, LAN and dedicated servers**
- No static world state. Config, the tracker and caches belong to a `MinecraftServer`, so an
  integrated server that closes and reopens another world starts clean.
- The client keeps the server's synced config separate from its own file (§2 Config), so a
  LAN host and a remote client agree.
- A modded client on a **vanilla server** works as vanilla: no custom screens, and it never sends
  our C2S payloads unless `ClientPlayNetworking.canSend` says the server accepts them.
- A **vanilla client** joining a modded server is disconnected at join with a clear message
  naming the mod (§10 says the mod is required on both sides), not a crash or a broken screen.
- Every gameplay GameTest runs on the dedicated-server harness. The integrated-server path is
  checked with a `runClient` smoke test plus the dev server (`run/server`, port 25566).

---

## 3. Experience curve

Vanilla points needed to go from level L to L+1:

| Level L | Points to next |
|---|---|
| 0–15 | `2L + 7` |
| 16–30 | `5L − 38` |
| 31+ | `9L − 158` |

**New rule:** for `L ≥ flatten_after_level` (default 30), points to next = `fixed_points_per_level`.
The default is **112**, vanilla's level 30→31 cost.

Total points at level L (L ≥ 30) = `1395 + 112 × (L − 30)`. 1395 is vanilla's total at level 30.

### Implementation
- One service: `experience/ExperienceCurve`. Every conversion between points, total, level
  and progress goes through it. Use `long` math internally and clamp at the `int` boundary.
- Mixin into `Player#getXpNeededForNextLevel` so that all vanilla callers route to the
  service, then audit every other caller that does its own math: `/xp` commands, death drop,
  XP orbs, bottles, enchanting, anvil, grindstone, and `totalExperience` bookkeeping.
- No admin level cap (owner, 2026-10-06). The only limit is `Integer.MAX_VALUE`, and a level never wraps negative.
  The service clamps points-to-next to `[1, Integer.MAX_VALUE]`. That also fixes vanilla's own overflow: `112 + 9(L − 30)`
  goes negative near L ≈ 238.6M, and the client then hides the XP bar.
- Points into a level are exact. Vanilla stores progress as the float `p/n`, and `floor(progress × n)` can read it
  back one short, so 29 + 157 points lands on 30 + 49. `giveExperiencePoints` keeps its vanilla body, then snaps to
  the exact total and stores the smallest float `f` with `f × n ≥ p`, which every vanilla reader floors to `p`. `/xp set points` stores the same
  form. Old `p/n` saves read correctly.
- Score counters never wrap (owner, 2026-10-06). `totalExperience` and the death-screen score (`getScore()`) saturate
  at the `int` limits. Vanilla's `int` adds reset the total to 0 and turn the score negative.
- Death drop stays vanilla (`min(7 × level, 100)` points) unless configured otherwise.

### Config
```json5
experience: {
  enabled: true,              // false = the full vanilla curve
  flatten_after_level: 30,    // 0 = fixed cost at every level
  fixed_points_per_level: 112,
}
```
`post_threshold_mode` and `preserve_vanilla_curve_below_threshold` were dropped (owner, 2026-10-06).
The first had one value and the second duplicated `flatten_after_level: 0`. A missing key reads as its
default, so a later mode can add the key back without breaking existing files.

### Acceptance
- Levels 30→31, 31→32, 49→50, 99→100 and 9999→10000 each cost exactly 112 points.
- Spending N levels from any level, including across the 30 boundary, leaves the correct
  level and progress.
- Add-then-remove of the same points is lossless. Nothing is duplicated or deleted.
- `/xp add|set|query` are correct above 30.
- Death with and without `keepInventory` behaves consistently.
- HUD shows the real level and never a negative one. A save at a very high level reloads
  intact with no overflow.

---

## 4. Applicability, stacking and conflicts

Four **independent** settings. Never conflate them:
1. **Count limit:** none.
2. **Applicability:** mandatory, from `supported_items`. Never widened just to enable a combo.
3. **Compatibility:** `exclusive_set`, relaxed per family via config.
4. **Max level:** per enchantment, see §8.

### Conflict families (each a config toggle; default = allow together)
`common.json5` → `allow_together.<family>`: `true` (default) lets the family share an item, `false`
is vanilla's exclusive set. Keys: `silk_touch_fortune`, `mending_infinity`, `protection`, `damage`,
`riptide_loyalty_channeling`, `multishot_piercing`, `depth_strider_frost_walker`. Membership is the
vanilla IDs below; an enchantment a datapack adds to a vanilla exclusive-set tag is not relaxed.

| Family | Vanilla rule | When combined |
|---|---|---|
| Silk Touch / Fortune | exclusive | `silk_fortune_mode`: `SILK_WINS` (default; Fortune ignored on silk-touched drops) or `FORTUNE_MULTIPLIES_SILK` (Mod Menu toggle; the tooltip warns it enables an ore-duplication loop) |
| Mending / Infinity | exclusive | both work normally |
| Protection family | exclusive | all apply; vanilla 20-EPF cap still limits the total |
| Damage: Sharpness / Smite / Bane / Impaling / Density / Breach (vanilla `exclusive_set/damage`) | exclusive | all applicable bonuses add (owner: all six, 2026-10-06) |
| Riptide / Loyalty / Channeling | exclusive | in water or rain → Riptide launch; otherwise → normal throw with Loyalty/Channeling |
| Multishot / Piercing | exclusive | both apply |
| Depth Strider / Frost Walker | exclusive | both apply |
| Silk Touch + Smelting (new) | n/a | `smelting_vs_silk_priority`, default Silk Touch |

- An item never holds the same enchantment twice. The enchantment component is a map, but
  merge code must normalize to one entry per ID and take the max level (or the upgrade result).
- Unknown and third-party enchantments on an item are preserved through every operation.
- New enchantments declare `supported_items` and `exclusive_set` explicitly.

---

## 5. Cost model

The same model prices the enchanting table and the anvil.

```
enchant_cost = Σ over each enchantment changed:
                 base_cost                        (only if newly added)
               + cost_per_level × levels_charged
             + mending_surcharge
total_cost   = enchant_cost + rename_cost (if renamed) + repair_cost (if repairing)
lapis_cost   = min(enchant_cost, 64)   (enchanting table only)
```
- **Default enchantment:** `base_cost 0`, `cost_per_level 1`, so 1 XP level per enchantment
  level (Efficiency V = 5). Special cases are per-enchantment overrides in data, never code
  branches. Mending is the first. The owner will add others later.
- `levels_charged`: the result level for a new enchantment (`RESULT_LEVEL`); only the added
  levels for an upgrade (`LEVEL_DIFFERENCE`). Efficiency III → V = 2.
- **Mending:** `base_cost 2` plus a surcharge of `2 × levels` of every other non-curse
  enchantment. Let `O_in` / `O_out` be the total other levels before and after:
  - Mending newly added → surcharge = `2 × O_out`
  - Mending already on the item → surcharge = `2 × (O_out − O_in)`
  - No Mending → 0

  So **order doesn't matter**. The finished item costs the same however it was built, and
  Mending can't be dodged by applying it first.

| Operation | Cost |
|---|---|
| Mending alone | 2 |
| Mending + Eff I / II / III / IV / V | 5 / 8 / 11 / 14 / 17 |
| Eff V first (5), then Mending (2 + 10) | 5 + 12 = 17 |
| Mending first (2), then Eff V (5 + 10) | 2 + 15 = 17 |
| Mending + Eff V + Unbreaking III + Fortune III | 2 + 22 + 11 = 35 |
| Eff III → V on an item with Mending | 2 + 4 = 6 |

- **Lapis:** only where vanilla uses it, which means the **enchanting table only**. There it
  costs 1 lapis per XP level of `enchant_cost`, capped at 64. The anvil (enchanting, repair
  and rename alike) never costs lapis.
- `rename_cost` defaults to 1 level. Repair cost is configurable.
- No maximum operation cost by default. "Too Expensive!" appears only if the admin sets
  `maximum_operation_cost ≥ 0`.
- Creative mode: free by default.

Per-enchantment data (config, keyed by ID):
`enabled, base_cost, cost_per_level, upgrade_cost_mode, treasure_multiplier, curse_multiplier,
max_level_override, table/loot/trade flags + weights`.

Upgrade modes: `RESULT_LEVEL`, `LEVEL_DIFFERENCE`, `FIXED`, `FREE`.

### 5a. Curses as a discount
In a system where players choose every enchantment, nobody would pick a curse. So a curse
buys a cheaper enchant.
- Each curse on the **result** item cuts `enchant_cost` by its `discount_percent`. Discounts
  add up to a cap of `curses.max_discount_percent` (default 45%), and the result rounds up.
  Rename and repair are never discounted. At the table, lapis follows the discounted cost.
- Curses are offered at the table as optional picks with a negative cost line, and they
  also come from books, loot and trades.
- **Removal (Cleanse at the grindstone):** cost =
  `max(curses.removal_min_cost (30), ceil(discount_percent × undiscounted cost of the item's
  current non-curse enchantments))`. In other words, you repay the discount the curse earned.
  Otherwise "take a curse, enchant cheaply, cleanse" would be a free discount.

| Curse | Discount | Items | Effect | Route |
|---|---|---|---|---|
| Binding (vanilla) | 10% | wearables | can't remove until death or break | vanilla |
| Vanishing (vanilla) | 10% | any | destroyed on death | vanilla |
| Fragility | 15% | durables | ×2 durability loss | D (`item_damage`) |
| Weight | 15% | armor | −10% movement speed per piece | D (attribute) |
| Greed | 15% | tools, weapons | −50% XP from kills and mining; multiplies with XP Boost | D (`mob_/block_experience`) |
| Fumbling | 15% | tools | −30% block break speed | D (attribute) |
| Hunger | 15% | armor, held items | +50% hunger drain | M (exhaustion mixin) |

Example: Mending + Efficiency V (17) + Curse of Weight → `ceil(17 × 0.85)` = 15. Cleansing it
later costs `max(30, ceil(0.15 × 17))` = 30.

### Messages (never a bare failure)
- `Not applicable to <item>: <enchantment>`
- `Insufficient levels: requires X (you have Y)`
- `Insufficient lapis: requires X`
- `Already at maximum level: <enchantment> <N>`
- `Conflicts with <enchantment>` (only when that family is set to exclusive)

---

## 6. Workflows

### Anvil
- Uses the §5 cost model. No 40-level cap by default. No exponential prior-work penalty:
  the `repair_cost` component is ignored, or flat if configured.
- Repair (material and two-item) and rename keep working. Each has its own configurable cost.
- Books apply every applicable enchantment they hold. Invalid-book mode: `REJECT_ALL |
  APPLY_VALID (default) | STRIP_INVALID`. The preview lists what will and won't apply.
- Never silently deletes an enchantment. A test asserts this.

### Enchanting table
Readable and not random. The vanilla three-random-offers screen is replaced by our own screen.
- Lists **every enabled enchantment applicable to the item, Mending included**. The player
  picks one or more with a target level. The XP and lapis totals and the per-line breakdown
  update live.
- Curses are offered as optional picks with a **negative** cost line (the §5a discount).
- **Bookshelves:** placement rules and the 15-shelf maximum are unchanged from vanilla, so
  existing setups keep working. Power tier = `1 + floor(shelves × 4 / 15)`:
  0–3 → 1, 4–7 → 2, 8–11 → 3, 12–14 → 4, 15 → 5.
  Max selectable level = `ceil(enchantment_max_level × tier / 5)`, so 15 shelves unlocks
  every enchantment's max.
- Treasure enchantments (Mending) need tier 5 (15 shelves) by default
  (`table.treasure_requires_full_power = true`).
- Books can be enchanted with any table enchantment, several at once.
- The server recomputes everything. The client's numbers are display only.

### Grindstone extraction
Moves one chosen enchantment from an item onto a book, which makes enchantments
transferable instead of destroyable.
- Enchanted item (or a multi-enchantment book) goes in slot 1 and a plain book in slot 2. The
  grindstone screen gains an enchantment list, and the player picks one.
- **The enchantment is moved, not copied.** The source item keeps every other enchantment.
  Result and source are updated atomically, so a crash or desync can never leave the
  enchantment on both items or on neither. A test asserts this.
- Cost: XP levels from the §5 model, priced as adding that enchantment at its current level
  to a blank item (Efficiency V = 5, Mending = 2). No lapis.
- Curses can't be *extracted* onto a book. They can be *removed* (see Cleanse below).

### Grindstone modes
The grindstone screen lists actions depending on what's in the slots:

| Slot 1 | Slot 2 | Action |
|---|---|---|
| enchanted item / book | plain book | **Extract** one chosen non-curse enchantment (above) |
| cursed item | empty | **Cleanse** one chosen curse (§5a removal cost) |
| enchanted item | empty | **Disenchant** (vanilla behavior: strip non-curse enchantments, return XP), as an explicit choice |

### Fortune on all mining tools
Fortune already goes on pickaxes, shovels, axes and hoes, but it only does anything when a
block's loot table opts in (ores, gravel→flint, leaves, melons, crops). That's deliberate in
vanilla: if Fortune multiplied a block that drops *itself*, you could mine 1, place 3, mine
them and repeat forever. See `gotchas.md`.

**Our rule: Fortune multiplies the block's own drop, but only for natural blocks.** A
placed-block tracker breaks the loop: a block a player placed (or manufactured) never gets
the Fortune bonus. The design is ported from the mcMMO-Singleplayer
`PlacedBlockTracker`/`BlockRules` pattern.
- Blocks Fortune affects: data tags `enchanting2:fortune_multiplies/<tool>`. Multiplier is
  vanilla's ore formula (`1 + max(0, rand(0, level+2) − 1)`), so Fortune V averages about 2.9×.
- Draft defaults (adjust in Phase 8):

  | Tool | Default tag |
  |---|---|
  | Axe | logs and stems, mushroom blocks |
  | Shovel | dirt, sand, red sand, gravel (the block itself; flint chance stays vanilla), clay, soul sand/soil, mud, snow block |
  | Hoe | vanilla crops (unchanged), moss block, nether/warped wart block, shroomlight |
  | Pickaxe | vanilla only. **Ancient debris is hard-excluded** (multiplying it breaks the netherite economy) |

- **What counts as placed (marked ineligible):**
  - Hand placement (`BlockItem` place)
  - Piston moves: the mark moves with the block. Read all, clear all, write all as three
    separate passes, as mcMMO does, or pushed columns lose marks.
  - **Falling blocks:** sand or gravel placed in the air lands somewhere else. The mark must
    ride the falling-block entity, or the drop launders a placed block into a natural one.
    mcMMO never needed this; we do, because sand and gravel are in the shovel tag.
  - Lava/water generator output and snow-golem snow, when that block is in a Fortune tag
  - Breaking a block clears its mark. Blocks grown from a sapling or bone meal stay
    natural (renewable anyway).
- Storage: per-chunk persistent data via the Fabric Data Attachment API rather than mcMMO's
  single world file. A multiplayer server with many players placing blocks would otherwise
  hold the whole set in memory and rewrite one growing file. Chunk attachments load,
  unload and save with their chunk. (Confirm chunk-attachment persistence on 1.21.11 in
  Phase 1.8.)
- Every failure path fails **closed**: if the tracker can't be read for a chunk, Fortune
  gives no bonus there and logs a WARN. mcMMO fails open (placed blocks pay out again),
  which is fine for XP but not for item duplication.
- Silk Touch + Fortune (`FORTUNE_MULTIPLIES_SILK`, still off by default) uses the same
  tracker, so it can't loop either.
- Tests: place → mine with Fortune V → exactly one drop. The same through a piston push and
  a sand fall. A natural block → multiplied. An unreadable chunk → no bonus.

### Books
Can hold any enabled enchantment, mixed. Applicability is checked when the book is applied.
Tooltip shows cost and applicable item categories.

### Loot and trading
Per-enchantment flags and separate weights for table, loot and librarian trades. Powerful
enchantments are not made equally common.

---

## 7. Vanilla enchantments

All kept under their vanilla IDs. Each can be disabled and has cost, max-level and
compatibility overrides. Curses stay distinguishable in the UI and config. Mending repair
efficiency stays vanilla in v1.

---

## 8. Max levels ("5 where applicable")

Single-level enchantments stay at 1: Aqua Affinity, Silk Touch, Flame, Infinity, Channeling,
Multishot, Mending, both curses. Everything else goes to 5 with two exceptions, plus two that
are conditional on a GameTest. Every row can be overridden with `max_level_override`.

| Enchantment | Vanilla | v1 | Note |
|---|---|---|---|
| Protection, Fire/Blast/Projectile Prot. | 4 | 5 | Protection V ×4 = 20 EPF, which exactly hits the existing cap |
| Feather Falling | 4 | 5 | |
| Respiration, Thorns, Soul Speed, Swift Sneak | 3 | 5 | Thorns V = 75% proc; Swift Sneak clamps at walk speed |
| Frost Walker | 2 | 5 | |
| Knockback, Fire Aspect, Punch | 2 | 5 | |
| Looting, Fortune, Luck of the Sea, Unbreaking | 3 | 5 | |
| Sweeping Edge, Loyalty, Wind Burst | 3 | 5 | |
| Piercing, Breach | 4 | 5 | Breach V = 75% armor ignored |
| Sharpness, Smite, Bane, Efficiency, Power, Impaling, Density | 5 | 5 | unchanged |
| Lunge (1.21.11 spear) | 3 | 5 | confirm ID and effects in Phase 1.8 |
| **Depth Strider** | 3 | **3** | Already at full effect at level 3, so levels 4–5 would do nothing |
| **Quick Charge** | 3 | **4** | Level 5 = zero charge time, which has historically broken crossbows |
| Lure | 3 | 5\* | \*If a GameTest shows fish stop biting, drop to 4 |
| Riptide | 3 | 5\* | \*If a GameTest shows the server rejects the launch ("moved too quickly"), drop to 4 |

---

## 9. New enchantments (all REVIEW)

**Route** = how it gets built:
- **D**: data-only enchantment JSON, using vanilla effect components
- **E**: custom effect-component type (small Java)
- **M**: mixin and/or networking

| Name | Max | Items | Effect | Route | Concerns |
|---|---|---|---|---|---|
| Vampirism | 5 | melee | heal after damage ≥ threshold; threshold drops per level | E | boss, PvP, cooldown |
| Withering | 5 | melee, ranged | Wither on hit, duration per level | D | verify `post_attack` fires for projectiles |
| Poisoning | 5 | melee, ranged | Poison on hit | D | same |
| Freezing | 5 | melee, ranged | Slowness on hit | D | same |
| Attack Speed | 5 | melee | `attack_speed` attribute | D | |
| Critical Strike | 5 | all weapons | 5%/level chance of +50% damage (max 25%) | D | `damage` + `random_chance` |
| Quick Strike | 5 | melee | 5%/level chance of a second hit (max 25%) | M | i-frames, effect/crit duplication, Vampirism, durability, PvP |
| TrueShot Aura | 1 | bows | arrows ignore gravity | E/M | infinite-range arrows; despawn |
| Toughness | 5 | armor | `armor_toughness` attribute | D | |
| Health | 5 | armor | `max_health` attribute | D | health clamping on unequip |
| Swiftness | 5 | boots | `movement_speed` attribute | D | conflicts with Soul Speed? |
| Double Jump | 1 | boots | one extra air jump; resets on ground | M | client input packet; water/lava/ladder/creative/vehicle; server validation |
| Flight | 5 | elytra | powered flight without fireworks | M | activation, durability, speed, takeoff, anti-cheat |
| Smelting | 1 | pickaxe (default) | smelt drops via recipe lookup | E/M | Silk/Fortune priority; no hard-coded ore list |
| Magnetic | 1 | tools, weapons | drops go straight into the inventory; overflow drops normally | M | ownership, pickup events, dupes |
| Reach | 3 | tools, weapons | `block_/entity_interaction_range` attributes | D | vanilla server already validates against attributes |
| XP Boost | 5 | tools, weapons | +5% XP per level | D | `mob_experience` / `block_experience`; Mending loop |
| Vision | 1 | helmet | Night Vision while worn | D/E | `tick` effect; avoid flicker under 10 s |
| Keep on Death | 1 | durables (configurable) | kept through death | M | keepInventory, graves mods, dupes |
| Area Mining | 1 | pickaxe, shovel | breaks 3×3 facing the hit face; sneak to break one block | M | only blocks with hardness ≤ the centre block and mineable by the tool; each extra block fires the break event (protection/claim mods) and costs durability; Fortune/Silk/Smelting/Magnetic apply per block |
| Replant | 1 | hoe | breaking a mature crop replants it, using a seed taken from its own drops | M | never creates a seed; immature crops break normally |
| Tiller | 3 | hoe | tills 3×3 / 5×5 / 7×7 | M | only dirt/grass → farmland; durability per block; respects protection |
| Ender Link | 1 | tools, weapons | drops go to the ender chest; overflow drops normally | M | **conflicts with Magnetic**; dupes; respects pickup events |
| Last Stand | 3 | chestplate | once per cooldown, a lethal hit leaves you at 1 HP with brief absorption; cooldown 10 / 8 / 6 min | M | a Totem of Undying fires first; HUD cooldown indicator; works in PvP (configurable) |
| Deflect | 3 | shield | blocked projectiles reflect at the shooter; chance 33 / 66 / 100% | M | reflected projectile ownership; no infinite ping-pong between two shields |
| Shield Bash | 3 | shield | attacking while blocking knocks the target back and briefly stuns | M | cooldown; balance vs. Knockback |
| Cushion | 3 | elytra, chestplate | reduces wall-collision damage | D | `damage_protection` on `fly_into_wall` |
| Beheading | 5 | melee | chance (per level) to drop the victim's head | E | mobs with a head item only; player heads behind a config option |

**Deferred (not in v1, kept for later):** Vein Miner, Leaping, Cleansing, Executioner,
Seeking, Shockwave.

---

## 10. Out of scope for v1
- Loaders other than Fabric. Minecraft versions other than 1.21.11.
- Vanilla-client compatibility. **The mod is required on both client and server** because
  the new table screen and Double Jump need client code.
- Changing Mending repair efficiency.
- In-GUI editing of every per-enchantment field. The GUI covers globals plus
  enable/cost/max level, and the rest is file-only.

---

## Open questions

None. Resolved 2026-10-06: Q13 (Fortune multiplies natural blocks only, gated by the
placed-block tracker, §6), Q14 (curses as a discount, Cleanse removal, five new curses, §5a),
Q12 (lapis only where vanilla uses it: the table, not the anvil)
and Q1–Q11 (cost formula, Mending surcharge, lapis, table, Silk/Fortune,
max levels, identity, version, config library, leftover lines, Riptide). They're folded
into the sections above and logged in `.agent/memory/decisions.md`.
