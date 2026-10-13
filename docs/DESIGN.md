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
- **Highly configurable** (owner, 2026-10-10). Every enchantment, ours and vanilla, can be enabled or disabled,
  have its max level raised or lowered (§8, up to 255 where it still works, with a note where more stops helping),
  and be priced. Every gameplay option, global or per enchantment, is editable in the in-game config screen (Mod Menu
  + YACL) as well as the file. A new option isn't done until it's in the screen.

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
- `client.json5` (Stage 6D) is this player's own: never synced, never read by a server, always
  editable (config screen "Display" category). Today it holds `anvil.show_preview_panel`
  (default `true`). The anvil's panel tab saves it on click through the same backed-up writer
  as the screen; a refused or failed save keeps the new value for the session and shows a toast.
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
| Silk Touch / Fortune | exclusive | `fortune.silk_fortune_mode`: `SILK_WINS` (default; Fortune never multiplies a drop Silk Touch changed, §6) or `FORTUNE_MULTIPLIES_SILK` (config screen; the tooltip warns it multiplies ore blocks) |
| Mending / Infinity | exclusive | both work normally |
| Protection family | exclusive | all apply; vanilla 20-EPF cap still limits the total |
| Damage: Sharpness / Smite / Bane / Impaling / Density / Breach (vanilla `exclusive_set/damage`) | exclusive | all applicable bonuses add (owner: all six, 2026-10-06). No vanilla item takes Impaling with another member (the trident supports only Impaling) |
| Riptide / Loyalty / Channeling | exclusive | in water or rain → Riptide launch; otherwise → normal throw with Loyalty/Channeling. Applies to **any** Riptide trident, Riptide alone included (owner, 2026-10-08) |
| Multishot / Piercing | exclusive | both apply |
| Depth Strider / Frost Walker | exclusive | both apply |
| Silk Touch + Smelting (new) | n/a | `smelting.silk_priority`: `silk_touch` (default; nothing is smelted) or `smelting` (the silk drop is smelted) |

- An item never holds the same enchantment twice. The enchantment component is a map, but
  merge code must normalize to one entry per ID and take the max level (or the upgrade result).
- Unknown and third-party enchantments on an item are preserved through every operation.
- New enchantments declare `supported_items` and `exclusive_set` explicitly.

### Widened applicability (owner, 2026-10-10; Phase 10g)
Rule 2 still holds: these are owner decisions about what an item takes, not a way to enable a combo.

| Enchantment | Vanilla fits | v1 adds | Notes |
|---|---|---|---|
| Knockback, Looting | swords, spears (`#enchantable/melee_weapon`) | axes | |
| Fire Aspect | swords, spears, mace | axes | |
| Flame | bow | crossbow | arrows only; a firework rocket isn't lit |
| Infinity | bow | crossbow; water bucket, lava bucket (§6 "Infinite buckets") | on a crossbow, arrows only (vanilla's `ammo_use` matches `minecraft:arrow`), so rockets are still used up |
| TrueShot Aura (ours) | bow | crossbow | arrows only; Multishot's three arrows each get it |

- Axes already take Sharpness, Smite and Bane (vanilla `#enchantable/sharp_weapon`, `#enchantable/weapon`) and every
  `enchanting2:` melee enchantment (our `melee` tag is `#enchantable/weapon` + trident). **Not Sweeping Edge**: the
  sweep attack is hard-coded to `#minecraft:swords` (`Player`), so it would do nothing on an axe (owner,
  2026-10-10: swords only). Not Lunge (a spear mechanic).
- Shovels already take Reach (`tool_or_melee`), XP Boost, and the planned Magnetic and Ender Link (`tool_or_weapon`),
  and no weapon enchantment. Nothing to change; a GameTest pins it.
- **Mending** already fits every vanilla item with durability (`#enchantable/durability`: armor, elytra, shield, every
  tool and weapon, bow, crossbow, trident, mace, spears, flint and steel, shears, brush, fishing rod, both on-a-stick
  items), and the water and lava buckets join it once they have durability (§6 "Infinite buckets"). The other
  enchantable items (compass, heads, carved pumpkin) have no durability, so Mending would do nothing on them and is
  not added. A modded durable item that a mod didn't put in that tag doesn't take it (owner, 2026-10-10: no).
- **How:** an `enchanting2:enchantable/<id>` item tag per widened vanilla enchantment (vanilla tag + the new items), put
  into that enchantment's `supported_items` and `primary_items` in `EnchantmentEvents.MODIFY` (the `MaxLevelHook`
  pattern), so the shared vanilla tags (`#enchantable/bow` also drives Power and Punch) are never edited. TrueShot Aura
  is ours, so its JSON points at a new `bow_or_crossbow` tag. Built in 10gA (`ItemWidening`, `ItemWideningHook`):
  `primary_items` is replaced only where vanilla has one (Fire Aspect → `fire_aspect_primary`, melee weapons + axes);
  elsewhere it's absent and follows `supported_items`. `MODIFY` has no registry lookup, so `RegistryDataLoaderMixin`
  lends the hook the item lookup the JSON was parsed with. A definition whose items aren't vanilla's tags (a datapack
  or mod changed them) is left alone and logged at INFO.

---

## 5. Cost model

The same model prices the enchanting table and the anvil.

```
enchant_cost = Σ over each enchantment changed:
                 base_cost                        (only if newly added)
               + cost_per_level × levels_charged
               + other_levels_surcharge × other levels added   (Mending's surcharge)
total_cost   = enchant_cost + rename_cost (if renamed) + repair_cost (if repairing)
lapis_cost   = min(enchant_cost, 64)   (enchanting table only)
```
- **Default enchantment:** `base_cost 0`, `cost_per_level 1`, so 1 XP level per enchantment
  level (Efficiency V = 5). Special cases are per-enchantment overrides in data, never code
  branches. Mending is the first. The owner will add others later.
- `levels_charged`: a newly added enchantment always pays for its result level. An upgrade
  pays by `upgrade_cost_mode`: `LEVEL_DIFFERENCE` (default; only the added levels, so
  Efficiency III → V = 2), `RESULT_LEVEL` (the whole result level again), `FIXED`
  (`cost_per_level` once, however many levels are added) or `FREE` (0).
- A curse's own line costs 0. Its value is the §5a discount, and curses never count as
  "other levels".
- **Mending:** data, not a code branch: `base_cost 2`, `cost_per_level 0` (so Mending alone =
  2), and `other_levels_surcharge 2`, i.e. `2 × levels` of every other non-curse
  enchantment. Let `O_in` / `O_out` be the total other levels before and after:
  - Mending newly added → surcharge = `2 × O_out`
  - Mending already on the item → surcharge = `2 × (O_out − O_in)`
  - No Mending → 0

  So **order doesn't matter**. The finished item costs the same however it was built, and
  Mending can't be dodged by applying it first.
- **Frost Walker costs 2 per level** (owner, 2026-10-10): `cost_per_level 2`, so Frost Walker V
  = 10 and an upgrade pays 2 per added level (III → V = 4).
- **Single-level enchantments cost 10** (owner, 2026-10-10): data rows like Mending's,
  `base_cost 10`, `cost_per_level 0`. Covered: Aqua Affinity, Silk Touch, Flame, Infinity,
  Channeling, Multishot (vanilla); Smelting, TrueShot Aura, Vision (built); Lava Walking, Snow
  Walker, Magnetic, Ender Link, Keep on Death, Double Jump, Area Mining, Replant, Steady Stance, Calm Gaze,
  Phantom Ward, Step Assist, and any later
  single-level one (planned, §9; owner: every level-1 enchantment, built or not).
  Not Mending (its own row above) and not curses (their line is 0, §5a). The rows are by ID, so
  a third-party single-level enchantment keeps the default 1. They still count as 1 "other
  level" for Mending's surcharge. Everything priced "as adding" follows: the table charges 10
  lapis, Extract costs 10, and Cleanse's undiscounted item cost includes the 10.
- **Speed (elytra) costs 6 per level** (owner, 2026-10-10): `cost_per_level 6`, so Speed V = 30
  and an upgrade pays 6 per added level (II → V = 18). It counts as its level for Mending's
  surcharge like any other enchantment. The row lands with Speed's 10c stage.

| Operation | Cost |
|---|---|
| Silk Touch / Infinity / Vision alone | 10 |
| Silk Touch + Eff V | 10 + 5 = 15 |
| Mending + Silk Touch | 2 + 10 + 2 = 14 |
| Mending alone | 2 |
| Mending + Eff I / II / III / IV / V | 5 / 8 / 11 / 14 / 17 |
| Eff V first (5), then Mending (2 + 10) | 5 + 12 = 17 |
| Mending first (2), then Eff V (5 + 10) | 2 + 15 = 17 |
| Mending + Eff V + Unbreaking III + Fortune III | 2 + 22 + 11 = 35 |
| Eff III → V on an item with Mending | 2 + 4 = 6 |

- **Lapis:** only where vanilla uses it, which means the **enchanting table only**. There it
  costs 1 lapis per XP level of `enchant_cost`, capped at 64. The anvil (enchanting, repair
  and rename alike) never costs lapis.
- `common.json5` `costs`: `rename_cost` 1 level. Repair is vanilla without the prior-work
  penalty: `repair_cost_per_material` 1 level per material item used, `repair_combine_cost` 2
  levels to combine two damaged items.
- No maximum operation cost by default (`maximum_operation_cost −1`). If the admin sets
  `N ≥ 0`, a total above N shows "Too Expensive!". Creative players bypass it, as in vanilla.
- Creative mode: free by default (`creative_free`).

Per-enchantment data (`enchantments.json5`, keyed by ID):
`enabled, base_cost, cost_per_level, upgrade_cost_mode, other_levels_surcharge,
discount_percent (curses), max_level_override, table/loot/trade flags + weights`.
(`treasure_multiplier` and `curse_multiplier` were dropped, owner 2026-10-07: they duplicate
these fields.)

### 5a. Curses as a discount
In a system where players choose every enchantment, nobody would pick a curse. So a curse
buys a cheaper enchant.
- Each curse on the **result** item cuts `enchant_cost` by its `discount_percent`. Discounts
  add up to a cap of `curses.max_discount_percent` (default 45%), and the result rounds up.
  A curse with no `discount_percent` entry (e.g. a third-party curse) discounts 10%.
  Rename and repair are never discounted. At the table, lapis follows the discounted cost.
- Curses are offered at the table as optional picks with a negative cost line, and they
  also come from books, loot and trades.
- **Removal (Cleanse at the grindstone):** cost =
  `max(curses.removal_min_cost (30), ceil(discount_percent × undiscounted cost of the item's
  current non-curse enchantments))`, where `discount_percent` is **the removed curse's own**
  (even if the cap meant it earned less). In other words, you repay the discount the curse
  earned. Otherwise "take a curse, enchant cheaply, cleanse" would be a free discount.

| Curse | Discount | Items | Effect | Route |
|---|---|---|---|---|
| Binding (vanilla) | 10% | wearables | can't remove until death or break | vanilla |
| Vanishing (vanilla) | 10% | any | destroyed on death | vanilla |
| Fragility | 15% +5%/lvl | durables | ×2 durability loss at I, +0.5 per level (V ×4) | D (`item_damage`) |
| Weight | 15% +5%/lvl | armor | −10% movement speed per level per piece; pieces add, total capped at 50% (`curses.weight_max_slowdown_percent`) | E (component + equipment hook) |
| Greed | 15% +5%/lvl | tools, weapons | −10% XP from kills and mining per level (V −50%); multiplies with XP Boost | D (`mob_/block_experience`) |
| Fumbling | 15% +5%/lvl | mining tools | −15% block break speed per level (V −75%) | D (attribute) |
| Hunger | 15% +5%/lvl | armor | +10% hunger drain per level per piece (V +50%); pieces add, no cap | E (component + exhaustion mixin) |

The five new curses go to max level 5 (owner, 2026-10-10). Binding and Vanishing stay at 1. The discount is 15% at
I, +5% per level (V = 35%), still capped by `curses.max_discount_percent`, and a curse's Cleanse repays its discount
at the level it has (`discount_percent` + `discount_per_level` × (level − 1)). Effect numbers per level are the
owner's of 2026-10-12 (decisions.md "10e curse scaling").

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
- Uses the §5 cost model, computed on the server only. No 40-level cap by default
  (`maximum_operation_cost` sets one). No prior-work penalty: the `repair_cost` component is
  **never read and never written**, so removing the mod hands items back their pre-mod penalty.
- Repair (material and two-item) and rename keep working. Each has its own configurable cost.
  Free results (creative with `creative_free`, curse-only books) can be taken.
- Books apply every applicable enchantment they hold, checked in ID order. Invalid-book mode
  (`common.json5` `anvil.invalid_book_mode`):
  - `reject_all`: no result unless every entry applies.
  - `apply_valid` (default): valid entries apply; the book goes back to the right slot holding
    only what was skipped.
  - `strip_invalid`: the book is consumed; skipped entries are destroyed, and the preview says
    "Will be lost" first.
- Item + item: under `reject_all` and `apply_valid` the anvil refuses when a sacrifice
  enchantment would be lost (there is no leftover to return); `strip_invalid` allows it,
  previewed. A lower level of an ID the target already has higher is never a loss.
- Enchanting needs a single item in the left slot; renaming a stack still works.
- Never silently deletes an enchantment. A seeded conservation property test asserts this.
- **Screen** (only when the server runs Enchanting 2; on a vanilla server the screen is vanilla):
  the server sends a preview (the whole cost breakdown and every right-slot entry's verdict and
  fate). The cost label shows the exact total (vanilla syncs it as a short), "Too Expensive!",
  "Insufficient levels", or the blocking reason, shortened to fit beside "Inventory". A cost
  panel shows each price line, the total, and every entry that won't apply with its reason
  (§5 Messages) and fate: stays on the book, already higher on the item, will be lost, or
  blocks the result. The panel opens **left** of the window from a tab on the window's left
  edge (recipe viewers use the right side); right if only that side has room; otherwise the
  window shifts right to make room. Its width is fixed, so the window never moves as items
  change. A click on the panel or tab never throws the carried item.

### Enchanting table
Readable and not random. The vanilla three-random-offers screen is replaced by our own screen.
- Lists **every offered enchantment applicable to the item** (`supported_items`, as at the
  anvil), Mending included. The player picks one or more with a target level. The XP and lapis
  totals and the per-line breakdown update live. A selection with any invalid entry enchants
  nothing; the panel names the entry and why.
- **Offered** = the `enchanting2:offered_at_table` enchantment tag: `#minecraft:in_enchanting_table`
  plus Mending, Frost Walker, Binding and Vanishing (owner, 2026-10-07). Soul Speed, Swift Sneak
  and Wind Burst stay loot-only. Datapacks can extend the tag.
- **Enchanted items and enchanted books are accepted** (owner, 2026-10-07). The player can add
  enchantments and raise levels. Upgrades are priced by `upgrade_cost_mode`. Nothing is ever
  lowered or removed.
- Curses (Binding and Vanishing for now) are offered as optional picks with a **negative** cost
  line (the §5a discount).
- **Bookshelves:** placement rules and the 15-shelf maximum are unchanged from vanilla, so
  existing setups keep working. Power tier = `1 + floor(shelves × 4 / 15)`:
  0–3 → 1, 4–7 → 2, 8–11 → 3, 12–14 → 4, 15 → 5.
  Max selectable level = `ceil(enchantment_max_level × tier / 5)`, so 15 shelves unlocks
  every enchantment's max.
- Offered treasure enchantments (Mending, Frost Walker) need tier 5 (15 shelves) by default
  (`table.treasure_requires_full_power = true`). Curses are never gated: they're a discount, not
  power.
- **Screen** (only when the server runs Enchanting 2; on a vanilla server the screen is vanilla):
  the vanilla window stays (slots, book). Its first two offer rows show the XP levels and lapis
  (green if affordable, red if not) or why there is no price, and the third is the Enchant
  button. The list is a scrollable side panel (160 wide) placed like the grindstone's, rows sorted
  by translated name so mod and vanilla enchantments interleave (owner, 2026-10-12). A row
  click picks at the highest level the table sells, or unpicks; `<` / `>` step the level. Rows
  that can't be picked say why ("Needs 8 bookshelves", "Conflicts with Fortune", "Already at
  maximum level"). The footer shows the Mending surcharge, the curse discount, the totals and
  the status. Enchant carries the numbers the player saw, and the server refuses it if they no
  longer match. A click on the panel never throws the carried item.
- Books can be enchanted with any table enchantment, several at once.
- The server recomputes everything. The client's numbers are display only.

### Grindstone extraction
Moves one chosen enchantment from an item onto a book, which makes enchantments
transferable instead of destroyable.
- An enchanted item (or a multi-enchantment book) goes in one input slot and plain books in the
  other, either way round. Each take uses one book. The grindstone's slots accept plain books
  only on a server running Enchanting 2.
- **The enchantment is moved, not copied.** The source keeps every other enchantment, at the
  level it has (a level above the current maximum moves as it is). The server writes the result,
  the source and the book stack in one step on the take, so it can never end up on both or on
  neither. A 300-case property test asserts this.
- A book holding nothing but the picked enchantment is refused (nothing to split). A book with
  that enchantment plus a curse can be split, which separates the curse.
- Cost: XP levels from the §5 model, priced as adding that enchantment at its current level
  to a blank item (Efficiency V = 5, Mending = 2). No lapis, no XP orbs.
- Curses can't be *extracted* onto a book. They can be *removed* (see Cleanse below).

### Grindstone modes
The panel beside the grindstone lists the actions for what's in the slots, one row per pick with
its price:

| Slots | Action |
|---|---|
| enchanted item / book + plain books | **Extract** one chosen non-curse enchantment (above). Curses are listed but refused |
| cursed item alone | **Cleanse** one chosen curse (§5a removal cost). No XP orbs |
| enchanted item alone | **Disenchant**: vanilla (strip non-curse enchantments, XP orbs back) |
| two of the same item | **Repair**: vanilla's combine (also strips non-curse enchantments, XP orbs back) |

- **Nothing happens until the player picks a row**, vanilla Disenchant included (owner,
  2026-10-07). So a shift-click can't strip an item the player meant to extract from. Clicking
  the picked row again clears the pick. Putting in a different item clears it too; changing the
  number of books doesn't.
- The pick is server state, set by a client message the server checks against what's in the
  grindstone. A pick that isn't a row for the current inputs gives no result.
- `maximum_operation_cost` caps Extract and Cleanse like the anvil (Cleanse's minimum of 30
  means a cap below 30 blocks it). Creative players bypass the cap, and `creative_free` makes it
  free.
- The panel is placed like the anvil's (left, else right, else the window shifts) and has no
  hide tab: it's the control. Vanilla's red X shows only when the server refuses something,
  not while it waits for a pick.
- On a vanilla server, a modded client keeps the vanilla grindstone.

### Fortune on all mining tools
Fortune already goes on pickaxes, shovels, axes and hoes, but it only does anything when a
block's loot table opts in (ores, gravel→flint, leaves, melons, crops). That's deliberate in
vanilla: if Fortune multiplied a block that drops *itself*, you could mine 1, place 3, mine
them and repeat forever. See `gotchas.md`.

**Our rule: Fortune multiplies every drop of a natural block in its tool's tag** (owner,
2026-10-08: gravel's flint, clay's clay balls, a snow block's snowballs, a mushroom block's
mushrooms). A placed-block tracker breaks the loop: a block a player placed (or manufactured)
never gets the Fortune bonus. The design is ported from the mcMMO-Singleplayer
`PlacedBlockTracker`/`BlockRules` pattern. Implemented in Phase 8 (`fortune/*`).
- Blocks Fortune affects: block tags `enchanting2:fortune_multiplies/<kind>` for the kinds
  axes, shovels, hoes and pickaxes. A tool's kinds are the vanilla item tags it's in
  (`#minecraft:axes` etc.), so a modded paxel gets the union. Multiplier is vanilla's ore
  formula (`1 + max(0, rand(0, level+2) − 1)`), so Fortune V averages 22/7 ≈ 3.1× (outcomes
  1, 1, 2, 3, 4, 5, 6). Stacks are split at the max stack size.
- Applied in Fabric's `LootTableEvents.MODIFY_DROPS`, after the block's own loot table, and
  only for that table (never chests, mobs or nested tables). Any error leaves vanilla's drops.
- Defaults (datapacks can extend any of them):

  | Tool | Default tag |
  |---|---|
  | Axe | `#minecraft:logs` (logs and stems), mushroom blocks and stems, bamboo (owner, 2026-10-12) |
  | Shovel | dirt, sand, red sand, gravel, clay, soul sand/soil, mud, snow block; grass block, podzol, coarse dirt, rooted dirt, mycelium (owner, 2026-10-12) |
  | Hoe | moss block, nether/warped wart block, shroomlight, `#minecraft:leaves` (owner, 2026-10-12; on top of vanilla's sapling/stick odds) (vanilla crops keep vanilla Fortune) |
  | Pickaxe | empty: vanilla only. **Ancient debris is hard-excluded in code** whatever the tags say (multiplying it breaks the netherite economy) |

- **Silk Touch + Fortune** (§4): `SILK_WINS` (default) still multiplies a tagged block
  **unless Silk Touch changed its drop** (owner, 2026-10-08). Logs, sand, dirt and moss
  multiply. Clay, snow blocks, mushroom blocks and gravel at Fortune III+ give one silk-touched
  block. "Changed" means a second roll of the block's table with a Silk-free copy of the tool
  drops different item types. `FORTUNE_MULTIPLIES_SILK` multiplies the silk-touched drop of a
  block in the tool's tag or in `enchanting2:fortune_multiplies_silk` (default `#c:ores`),
  natural blocks only, so it can't loop either.
- **What counts as placed (marked ineligible):**
  - **Every** block placed from an item (`BlockItem.place`), by a player or a dispenser,
    whatever the block (owner, 2026-10-08), so a later tag change can't pay out on it.
  - Piston moves: the mark moves with the block. Read all, clear all (sources, destinations and
    destroyed blocks), then write, as separate passes, or pushed columns lose marks. Works
    across chunk borders.
  - **Falling blocks:** sand or gravel placed in the air lands somewhere else. The mark rides
    the falling-block entity (a persistent entity attachment), or the drop would launder a
    placed block into a natural one. Landing sets the landing position either way.
  - Endermen setting a block down (it may be a placed block they picked up).
  - Lava/water generator output and snow-golem snow, when that block is in a Fortune tag.
  - A player breaking a block clears its mark, **after** its loot is rolled
    (`ServerPlayerGameMode.destroyBlock` RETURN; Fabric's `PlayerBlockBreakEvents.AFTER` fires
    before the loot). Other removals (explosions, fire) leave a stale mark, which fails closed.
  - A mark belongs to a position, so in-place conversions (dirt → path, log → stripped) keep
    it. Blocks grown from a sapling or bone meal stay natural (renewable anyway).
- Storage: a Fabric Data Attachment on each chunk (`enchanting2:placed_blocks`), a 4096-bit set
  per 16-high section (≤ 512 bytes per section), rather than mcMMO's single world file. A
  multiplayer server with many players placing blocks would otherwise hold the whole set in
  memory and rewrite one growing file. Chunk attachments load, unload and save with their chunk.
- Every failure path fails **closed**. Fabric drops an attachment whose codec fails, which
  would make the chunk's placed blocks natural, so our codec never fails. Data it can't read
  becomes "unreadable" (no bonus anywhere in that chunk, WARN at load) and is written back
  unchanged. mcMMO fails open (placed blocks pay out again), which is fine for XP but not for
  item duplication. Removing the mod drops the attachment on the next save (Fabric WARNs about
  an unknown type), so re-adding it treats those chunks' placed blocks as natural once (§2a).
- Tests: place → mine with Fortune V → exactly one drop, ten times over (`FortuneDropsGameTest`).
  Marks through piston push and pull, sand falls, endermen, generators and golems
  (`PlacedBlocksGameTest`). A natural block → multiplied. An unreadable chunk → no bonus.

### Silk Touch and spawners (owner, 2026-10-10; Phase 10g)
`common.json5` → `silk_touch.collect_spawners` (config screen toggle). Off: vanilla, a spawner never drops. On: a
Silk Touch tool drops the spawner as an item.
- **Default on** (owner, 2026-10-10). An admin who wants vanilla's spawner economy turns it off.
- **The spawner keeps its mob type, and only that** (owner, 2026-10-10 and 2026-10-12). The item carries a minimal
  `block_entity_data` (`SpawnData.entity.id`) plus our `enchanting2:collected_spawner` stamp. Vanilla only copies a
  spawner's block-entity data for an operator (`onlyOpCanSetNbt`, checked in `BlockItem.updateCustomBlockEntityTag`),
  so for anyone else a stamped item gets its mob back through `setEntityId`, the path a spawn egg uses (which any
  player may do), and only for a type that has a spawn egg. Other fields are ignored, and an unstamped item stays
  op-only. A forged stamp therefore grants nothing a spawn egg doesn't. Cost: a map-maker's custom spawner (gear,
  timings, ranges) comes back as a plain spawner of its mob.
- **No XP** when silk-touched (vanilla gives 15–43 XP for breaking one; like ores, Silk Touch forfeits it). Vanilla pops
  that XP straight from `SpawnerBlock.spawnAfterBreak`, outside Silk Touch's `block_experience` effect, so a mixin
  skips it on the same condition as the drop.
- Vanilla's own break rules hold: a pickaxe (the spawner's correct tool), survival, no drop in creative.
- An empty spawner (no mob) drops a plain spawner item.
- **Mob spawners only.** A trial spawner still drops nothing (its loot and ominous state don't survive an item).
- Fortune never multiplies a spawner (hard-excluded in code like ancient debris, whatever the tags or
  `FORTUNE_MULTIPLIES_SILK` say). Smelting does nothing (no recipe). Area Mining's extra blocks never include one.
- Off or on, the setting changes only new breaks; a spawner item already dropped stays an item.

### Infinite buckets (owner, 2026-10-10; Phase 10g)
Vanilla **Infinity** also fits the water bucket and the lava bucket (§4), costing 10 like every single-level
enchantment (§5). Reusing Infinity rather than a new enchantment is the owner's call (2026-10-10).
- **Effect:** emptying an Infinity bucket places the fluid and the bucket stays full, enchanted, same item, minus
  one durability. It never becomes an empty bucket, so it can't scoop.
- **Durability** (owner, 2026-10-10: so Mending and the rest are useful on it). The water and lava buckets get a
  `max_damage` component (Fabric `DefaultItemComponentEvents.MODIFY`; they already stack to 1) and join
  `#minecraft:enchantable/durability`, so Unbreaking, Mending and Curse of Vanishing fit them, and so do our Keep on
  Death and Fragility. This is the one place we add to a vanilla tag: every enchantment keyed on that tag should apply.
  - **256 uses** for both buckets (owner, 2026-10-10), a fixed number (not config: the bar is drawn from the item's
    component on each side, and a server/client mismatch would show the wrong bar). Every placement costs 1, whatever
    the path; Unbreaking can skip it as on a tool.
  - **The last use destroys it**, like any tool breaking (owner, 2026-10-10).
  - **Only a water bucket or a lava bucket takes enchantments**, never the empty bucket (owner, 2026-10-10).
  - **Durability enchantments need Infinity on the bucket** (owner, 2026-10-10). A water bucket without
    Infinity turns into a new empty bucket the first time it's poured, so Mending or Unbreaking on it would be lost
    with the levels paid. `EnchantRules` refuses them without Infinity, at the anvil and in the table's pool; a book
    with Infinity and Mending together applies in one go.
  - Un-enchanted buckets carry the component too but never take damage, and a bucket that scoops is always undamaged.
  - Two damaged Infinity buckets combine at the anvil like any damaged tool. No iron-ingot repair.
- **Every emptying path** keeps it: a player placing it, a dispenser, and a cauldron (filling a cauldron returns the
  bucket full). Water in the Nether evaporates as normal and the bucket stays full. Built (10gC): the hand and the
  cauldron both get their remainder from `ItemUtils.createFilledResult(held, player, new bucket)`, so one hook
  (`ItemUtilsMixin`) swaps that remainder for the spent bucket; the dispenser's `DispenseItemBehavior$4` gets the same
  through `consumeWithRemainder`. Creative is untouched (vanilla keeps the held bucket there).
- **Getting it on a bucket:** the anvil with a book, and the enchanting table (owner, 2026-10-10). Our table never
  asks `ItemStack.isEnchantable()`, so the buckets need no `enchantable` component: Infinity's tag is enough (built
  10gC, GameTest). A plain bucket is offered Infinity only; Unbreaking and the rest appear once Infinity is on it.
  At the anvil a book's Infinity is applied before its other entries (`EnchantRules.APPLY_ORDER`).
- **The grindstone may take Infinity off** a bucket that keeps Mending or Unbreaking (owner, 2026-10-12): the panel
  shows what stays, and the plain bucket then loses the rest on its first pour. No rule against it.
- **Lava is an infinite fuel source.** Place, scoop with a plain bucket, burn: that already works with any lava
  source, so an Infinity lava bucket only makes it portable. The bucket itself must not be furnace fuel, or it would
  burn and come back as a plain empty bucket (lost enchantment) or, if kept, burn forever. An Infinity lava bucket
  isn't accepted as fuel (owner, 2026-10-10: not to be abused as fuel).
- **Crafting:** a recipe that takes a water or lava bucket (none in vanilla, datapacks may add some) refuses an
  Infinity bucket, so it's never consumed into a plain bucket.
- Milk, powder snow and fish buckets aren't covered unless the owner adds them (milk would be an infinite cure for
  every effect).
- Tests: place water and lava ten times from one bucket (still one enchanted full bucket, 10 durability used), a
  dispenser and a cauldron keep it and use durability, the last use destroys it, Unbreaking skips some uses, Mending
  repairs it from XP, Mending alone is refused on a water bucket without Infinity, an empty bucket takes nothing, the
  table offers Infinity on a water bucket, a furnace refuses it, an un-enchanted bucket still empties and is never
  damaged.

### Enchantment descriptions in tooltips (owner, 2026-10-10; Phase 10h)
Holding Ctrl while hovering an item shows what each of its enchantments does, under that enchantment's line.
Client-only (no packet, no server rule).
- **Where:** every item tooltip with enchantments, and enchanted books (`stored_enchantments`), in any screen.
  Enchantments hidden by the item's `tooltip_display` stay hidden, description included.
- **Text:** lang key `enchantment.<namespace>.<path>.desc`, one grey line per enchantment, wrapped to the tooltip's
  width. We write one for every vanilla enchantment and every one of ours (taken from docs/ENCHANTMENTS.md "What it
  does", with this mod's numbers, e.g. Thorns V = 75%). A third-party enchantment shows its own `.desc` if its lang
  has one (the same key convention as the Enchantment Descriptions mod), otherwise nothing.
- **Hint** (owner, 2026-10-10): without Ctrl, an enchanted item shows one dark-grey line "Hold Ctrl for
  enchantment descriptions". macOS uses vanilla's control check, which reads Cmd there.
- **Config:** `client.json5` `tooltips.enchantment_descriptions` (default on) and `tooltips.show_hint` (default on),
  in the config screen's "Display" category.
- **Another mod doing the same:** if the Enchantment Descriptions mod (`enchdesc`, the name of its config file in
  Darkhax's docs) is loaded, ours stays off so lines aren't doubled.
- **Built (10h):** `tooltip/DescriptionPlan` decides (MC-free), `client/EnchantmentDescriptions` places lines after the
  line equal to `Enchantment.getFullname`, wrapped at 200 GUI pixels. Ctrl is `Minecraft.hasControlDown()` (still
  there in 1.21.11).
- Tests: JUnit for the line builder (key per ID, missing key → no line, hidden → no line, Ctrl off → hint only); a
  lang-completeness test that fails when any `minecraft:` or `enchanting2:` enchantment lacks a `.desc` key, so every
  later enchantment stage has to write its description; a client GameTest screenshot with Ctrl held.

### Books
Can hold any enabled enchantment, mixed. Applicability is checked when the book is applied.
Tooltip shows cost and applicable item categories.

### Loot and trading
Per-enchantment flags and separate weights for table, loot and librarian trades. Powerful
enchantments are not made equally common. (Phase 9; owner 2026-10-09; decisions.md "Phase 9".)
- `enchantments.json5` keys, all optional: `enabled`, `table`, `loot`, `trade` (true by default),
  `loot_weight` and `trade_weight` (1–1024, the registry weight by default), `treasure`.
- **Sources.** Table = the `offered_at_table` pool. Loot = loot tables (`enchant_randomly`,
  `enchant_with_levels`: chests, fishing, bartering) and mob spawn gear. Trade = librarian books
  and villager-sold enchanted gear. Flags only take an enchantment out of a source; tags decide
  what's in it.
- **Disabled = acquisition only.** It disappears from every source. Items that already have it keep
  it, it keeps working, and the anvil and grindstone still move it. Fixed grants that name it in
  data (`set_enchantments`, single-enchantment providers) and `/enchant` are not filtered.
- **Weighted by default.** Librarian books and `enchant_randomly`, uniform in vanilla, pick by
  weight. Levels still roll up to the (raised) max, as in vanilla.
- **`treasure`** overrides `#treasure` for the table's 15-shelf gate and `#double_trade_price` for
  the librarian's price. The vanilla tags are untouched.
- **Config screen:** an Enchantments category with Enabled / table / loot / trade per enchantment
  (host or title screen only; a remote server keeps `enchantments.json5` to itself). Weights and
  `treasure` are file-only.

---

## 7. Vanilla enchantments

All kept under their vanilla IDs. Each can be disabled and has cost, max-level and
compatibility overrides. Curses stay distinguishable in the UI and config. Mending repair
efficiency stays vanilla in v1.

---

## 8. Max levels ("5 where applicable")

Single-level enchantments stay at 1: Aqua Affinity, Silk Touch, Flame, Infinity, Channeling,
Multishot, Mending, both curses. Everything else goes to 5 with two exceptions. Lure and Riptide
reach 5 only because a GameTest proved it (Stage 4D). Every row can be overridden with
`max_level_override`, within the safe caps below.

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
| Lure | 3 | 5 | GameTest: a Lure V hook still gets bites; Lure VI never does (the 100–600 tick roll minus 600 is always ≤ 0) |
| Riptide | 3 | 5 | GameTest: a Riptide V launch (4.5 b/t) stays under the moved-too-quickly check even along a terminal fall |

**Safe caps on `max_level_override`** (owner, 2026-10-07): an override past a level that breaks the mechanic is
clamped to the last working level, with a WARN: Lure 5, Quick Charge 4, Riptide 7 (VIII can trip moved-too-quickly
when launched along a fall). A datapack-set max is not clamped. Arithmetic in TODO.md 4D.1.

### Raising max levels past the default (owner, 2026-10-10; Phase 10j)
Any enchantment's max level can be set from 1 to 255 (vanilla's hard limit for a stored level), in
`enchantments.json5` or the config screen. Efficiency 100 is allowed: it just mines instantly long before 100. Each
enchantment falls in one of three groups, shown in the config screen next to its max-level field:
- **No ceiling:** higher keeps adding (Sharpness, Power, Unbreaking, Fortune, Looting, Knockback, Multishot...).
  A note says where it stops mattering in practice, e.g. Fortune and Looting dropping hundreds of items, or Multishot
  firing 2 × level + 1 arrows (owner, 2026-10-12: no cap).
- **Soft ceiling (allowed, with a note):** levels past a point do nothing more. The field still accepts them and
  the note says so. Built in 10jA (`LevelCeilings`), each number checked by arithmetic (TODO 10jA) or
  `LevelCeilingsGameTest`:

  | Enchantment | Stops adding at | Why |
  |---|---|---|
  | Protection / Fire, Blast, Projectile Protection / Feather Falling / Cushion | 20 / 10 / 7 / 5 | vanilla caps all enchantment protection at 20 points; 1, 2, 3, 4 points per level per piece |
  | Thorns, Breach | 7 | 15% per level reaches 100% |
  | Depth Strider, Sure Footing | 3 | the efficiency attributes cap at 1 |
  | Swift Sneak | 5 | sneaking speed caps at 1 (walking) |
  | Frost Walker | 14 | the freezing radius is clamped at 16 |
  | Efficiency | 32 | mining efficiency caps at 1024 (level² + 1); stone is instant from VI, obsidian never |
  | Aqua Affinity | 25 | submerged mining speed caps at 20; past I you mine faster underwater than on land (found in 10jA: it scales) |
  | Critical Strike, Vampirism, Beheading, Anchored | 20, 25, 50, 10 | the chance or share reaches 100% |
  | Reach, Toughness | 122, 40 | interaction range caps at 64, armor toughness at 20 |
  | Clarity, Sustenance, Endurance | 3, 25, 20 | the reduction reaches 100% (immune, no hunger drain, free sprinting) |
  | Single-level ones whose effect doesn't scale (Silk Touch, Infinity, Mending, Vision...) | 1 | extra levels do nothing (a GameTest checks their effects have no level-based value) |

- **Hard ceiling (clamped, WARN, the field won't go higher):** a level that breaks the mechanic: Lure 5, Quick
  Charge 4, Riptide 7 (4D); Piercing 127 (an arrow's pierce level is a byte, 128 wraps negative); Lunge 13 and Wind
  Burst 14 (owner, 2026-10-12: Riptide's moved-too-quickly rule). Swiftness needs none (255 sprinting ≈ 4 blocks a
  tick, far under the check's 10).
- A raised max costs what the pricing row says at that level (Efficiency 100 at 1/level = 100 XP levels; Speed
  at 6/level). A single-level enchantment raised past 1 keeps its flat 10 (§5).
- Changing a max level needs a restart (gotchas.md: `/reload` doesn't re-run `MODIFY`). The screen says "applies on
  restart" next to the field. Lowering a max never touches existing items (owner, 2026-10-06).
- The table, loot and trades roll up to the new max as for any max (§6 "Loot and trading").

---

## 9. New enchantments (all REVIEW)

**Route** = how it gets built:
- **D**: data-only enchantment JSON, using vanilla effect components
- **E**: custom effect-component type (small Java)
- **M**: mixin and/or networking

| Name | Max | Items | Effect | Route | Concerns |
|---|---|---|---|---|---|
| Vampirism | 5 | melee | heal 4% × level of the health the target lost (overkill never counts), direct melee hits only, every hit, no cooldown (owner, 2026-10-09) | E | works on players and bosses; a thrown trident doesn't heal |
| Withering | 5 | melee, ranged | Wither on hit, duration per level | D | verify `post_attack` fires for projectiles |
| Poisoning | 5 | melee, ranged | Poison on hit | D | same |
| Freezing | 5 | melee, ranged | Slowness on hit | D | same |
| Attack Speed | 5 | melee | `attack_speed` attribute | D | |
| Critical Strike | 5 | all weapons | 5%/level chance of +50% damage (max 25%) | D | `damage` + `random_chance` |
| Quick Strike | 5 | melee | 5%/level chance of a second hit (max 25%); the second hit repeats the damage only, ignoring i-frames: no on-hit effects, Vampirism, Critical Strike roll or durability, never chains (owner, 2026-10-12) | M | i-frames, effect/crit duplication, Vampirism, durability, PvP |
| TrueShot Aura | 1 | bow, crossbow (crossbow: owner, 2026-10-10, Phase 10g) | arrows ignore gravity for 5 s, then fall normally (owner, 2026-10-09) | E | despawn as normal arrows; the countdown survives a save |
| Toughness | 5 | armor | `armor_toughness` attribute | D | |
| Health | 5 | armor | `max_health` attribute | D | health clamping on unequip |
| Swiftness | 5 | boots | `movement_speed` attribute | D | conflicts with Soul Speed? |
| Double Jump | 1 | boots | one extra air jump; resets on ground | M | client input packet; water/lava/ladder/creative/vehicle; server validation |
| Lava Walking | 1 | boots | stand and walk on the surface of lava; sneak to sink; standing on lava still burns, as touching lava does; mobs too (owner, 2026-10-10); the surface is a floor: normal walking speed, and a fall onto it does normal fall damage (owner, 2026-10-12); flowing lava too (agent, 10cB) | M | built 10cB: `LivingEntity.canStandOnFluid` on both sides (the strider's hook: `LiquidBlock`'s half-block stable shape and `shouldTravelInFluid`), false when sneaking or sunk deeper than 0.5; the feet stand inside the lava block, so vanilla burns them unaided; flowing lava reads as a source in `LiquidBlock.getCollisionShape` for a walker |
| Snow Walker | 1 | boots | exactly the leather-boots effect on any boots: walk on powder snow instead of sinking, sneak to sink, and no freezing; mobs too (owner, 2026-10-10) | M | built 10cB: `PowderSnowBlock.canEntityWalkOnPowderSnow` (collision and climbing) and `LivingEntity.canFreeze` |
| Flight | 5 | elytra | powered flight without fireworks: while gliding, holding jump gives steady forward thrust growing per level (V ≈ one rocket), 1 elytra durability per second of thrust, Speed scales it, takeoff is vanilla (owner, 2026-10-12) | M | activation, durability, speed, takeoff, anti-cheat |
| Speed | 5 | elytra | +10% elytra flight speed per level (V = +50%); costs 6 per level; scales both gliding and firework-rocket boosts (owner, 2026-10-10) | M | glide physics run on the client for players (`LivingEntity.updateFallFlyingMovement`), so the hook runs on both sides; the rocket boost is applied in `FireworkRocketEntity`'s tick, also client-side for the player; server's fall-flying moved-too-quickly allowance (GameTest at V with a rocket along a dive, like Riptide's); stacks with Flight |
| Smelting | 1 | pickaxe, shovel, axe, hoe | smelts every drop with a smelting recipe (logs → charcoal), after Fortune; grants the recipe's XP (owner, 2026-10-09) | E | Silk Touch wins by default (§4); no ore list, `enchanting2:smelting_excluded` item tag for datapacks |
| Magnetic | 1 | tools, weapons | drops go straight into the inventory; overflow drops normally; drops from blocks broken and mobs killed with the item, and XP goes straight to the player (Mending applies) (owner, 2026-10-12) | M | built 10cDA: a redirect context around `Block.dropResources` and `LivingEntity.dropAllDeathLoot`, read by `Block.popResource`, `Entity.spawnAtLocation` and `ExperienceOrb.award` (orbs spawn at the player); only the remainder spawns; not chest contents or a player victim's inventory; ownership, pickup events, dupes |
| Reach | 3 | tools, weapons | `block_/entity_interaction_range` attributes | D | vanilla server already validates against attributes |
| XP Boost | 5 | tools, weapons | +5% XP per level | D | `mob_experience` / `block_experience`; Mending loop |
| Vision | 1 | helmet | Night Vision while worn, players only | E | tick handler refreshes under 11 s (no flicker), removes on unequip, never touches a longer potion (owner, 2026-10-09) |
| Keep on Death | 1 | durables (configurable) | kept through death, back in its slot on respawn, enchantment kept, no cost (owner, 2026-10-12) | M | built 10cDB: `Inventory.dropAll` leaves kept stacks in their slots (Vanishing is destroyed before it); Fabric `COPY_FROM` moves them to the same slots on respawn, skipping what keepInventory/end portal already copied; a taken slot sends it elsewhere, never lost; items: tag `enchanting2:enchantable/keep_on_death` (default `#minecraft:enchantable/durability`); graves mods that empty the inventory first win |
| Area Mining | 1 | pickaxe, shovel | breaks 3×3 facing the hit face; sneak to break one block | M | built 10cEA: `ServerPlayerGameMode.destroyBlock` breaks the centre, then each neighbour in the client's last-reported face plane (else the look axis) through `destroyBlock` again; only blocks with hardness ≤ the centre block and mineable by the tool; each extra block fires the break event (protection/claim mods) and costs durability; Fortune/Silk/Smelting/Magnetic apply per block |
| Replant | 1 | hoe | breaking a mature crop replants it, using a seed taken from its own drops | M | built 10cEB: inside the tool's `Block.dropResources`, ahead of Magnetic, one of the crop's clone item is taken from the drops and the crop goes back at age 0 (block tag `enchanting2:replantable`, mature = `age` at its max); never creates a seed; immature crops break normally |
| Tiller | 3 | hoe | tills 3×3 / 5×5 / 7×7 | M | only dirt/grass → farmland; durability per block; respects protection |
| Ender Link | 1 | tools, weapons | drops go to the ender chest; overflow drops normally; blocks broken and mobs killed with the item, items only (XP as normal) (owner, 2026-10-12) | M | built 10cDA with Magnetic's redirect; no `exclusive_set` (§4: ours declare none), so with both on one tool the ender chest fills first, then the inventory (agent; owner to confirm); was "conflicts with Magnetic"; dupes; respects pickup events |
| Last Stand | 3 | chestplate | once per cooldown, a lethal hit leaves you at 1 HP with brief absorption; cooldown 10 / 8 / 6 min | M | a Totem of Undying fires first and doesn't use the cooldown; HUD cooldown indicator; works in PvP by default, a config toggle turns it off (owner, 2026-10-12) |
| Deflect | 3 | shield | blocked projectiles reflect at the shooter; chance 33 / 66 / 100%; same speed and damage, owned by the shield holder (kill credit, arrows not picked up), at most once per projectile (owner, 2026-10-12) | M | built 10cCB: `Entity.deflection` (vanilla's pre-hit path, the breeze's), server only; entity type tag `enchanting2:deflectable` (arrows, spectral arrows, blaze/ghast fireballs, wither skulls, llama spit; not tridents: owning one would steal a Loyalty trident); a deflection counts as a block (sound, 1 durability) but not for Bulwark or Riposte; reflected projectile ownership; no infinite ping-pong between two shields |
| Shield Bash | 3 | shield | attacking while blocking knocks the target back and briefly stuns: Knockback I per level and Slowness IV for 0.5 s per level, then a 3 s shield cooldown (owner, 2026-10-12) | M | built 10cCC: the client lets an attack click through while a Shield Bash shield is raised and the crosshair is on an entity (`Minecraft.handleKeybinds`); the server adds the bash after `Player.attack` once the hit landed; the hit itself is the normal attack; balance vs. Knockback |
| Steadfast | 3 | shield | an axe disables the shield for less time: −33% per level, III = never (vanilla 5 s) (owner, 2026-10-10) | M | built 10cCA: `BlocksAttacks.disable`'s seconds × (1 − level / 3) for that shield; vanilla rounds the ticks (I 67, II 33) and skips 0; PvP axe play |
| Spiked | 5 | shield | a melee attacker whose hit your raised shield blocks takes damage, Thorns for shields (owner, 2026-10-10) | E | built 10cCA: 1 damage per level (thorns type, so Spiked never answers Spiked) on every blocked hit whose direct attacker is a mob or player, not a projectile; no extra shield durability |
| Bulwark | 3 | shield | a blocked hit gives brief absorption, 1 heart per level, refreshed not stacked (owner, 2026-10-10); lasts 5 s, granted again only 10 s after the last grant (owner, 2026-10-12) | E | built 10cCA: a hidden `enchanting2:bulwark` mob effect, vanilla's Absorption at 1 health per amplifier step; the cooldown is per entity, not saved |
| Riposte | 5 | shield | after a block, your next melee hit within 2 s deals +3% per level (V = +15%) (owner, 2026-10-10) | M | built 10cCA: a per-entity timer (not saved), used up by the next player/mob/spear/mace attack; multiplies the hit's final amount, so it stacks with Critical Strike and Quick Strike |
| Steady Stance | 1 | shield | no movement slowdown while blocking; priced 10 (§5) (owner, 2026-10-10) | M | built 10cCC: `LocalPlayer.itemUseSpeedMultiplier` (the item's `use_effects.speed_multiplier`, read only by the client) is 1; blocking still stops sprinting (agent); PvP balance |
| Calm Gaze | 1 | helmet | endermen don't get angry when you look at them, the carved-pumpkin effect (owner, 2026-10-10) | M | the enderman stare check reads `#minecraft:gaze_disguise_equipment`; add an enchantment check beside it |
| Clarity | 3 | helmet | Blindness, Darkness and Nausea last 33% shorter per level, III = immune (owner, 2026-10-10); any wearer, mobs too; I/II shorten at application and leave an infinite effect infinite, III blocks infinite ones too (owner, 2026-10-12) | M | effect-applied hook; the Warden's Darkness pulse is shortened each time it lands; only these three effects |
| Phantom Ward | 1 | helmet | phantoms don't target you, even with insomnia (owner, 2026-10-10); insomnia spawning unchanged, phantoms still attack other players (owner, 2026-10-12) | M | phantom target selection; a phantom you hurt targets you until vanilla drops the target |
| Sustenance | 5 | helmet | 4% less hunger drain per level, V = 20% (owner, 2026-10-10) | M | exhaustion hook, every source; multiplies with Endurance and the Hunger curse (10e) |
| Step Assist | 1 | leggings | step up full blocks without jumping (owner, 2026-10-10) | D | `step_height` attribute +0.4 (0.6 → 1.0) |
| Anchored | 5 | leggings | 10% knockback resistance per level, hits only (owner, 2026-10-10 and 2026-10-12) | D | `knockback_resistance` attribute (melee and projectiles), not `explosion_knockback_resistance` (Blast Protection's job); with netherite armor it reaches the 100% clamp |
| Endurance | 5 | leggings | sprinting costs 5% less hunger per level, V = 25% (owner, 2026-10-10) | M | exhaustion hook, sprint only: sprint distance and sprint-jumps (owner, 2026-10-12) |
| Sure Footing | 3 | leggings | a third of the slowdown on soul sand, honey and powder snow gone per level, III = none (owner, 2026-10-12); in powder snow only the sideways slowdown, so sinking and freezing stay vanilla (10cA) | D (+M for powder snow) | `movement_efficiency` attribute (Soul Speed's), `fraction` level / 3. It's read only by `LivingEntity.getBlockSpeedFactor` (javap 1.21.11), so it covers blocks with a speed factor (soul sand, honey 0.4) and never powder snow, which slows through `makeStuckInBlock`: a mixin there applies the same `lerp` to its x/z multiplier on both sides |
| Cushion | 3 | elytra, chestplate | reduces wall-collision damage | D | `damage_protection` on `fly_into_wall` |
| Beheading | 5 | melee | 2% × level to drop the victim's head on a player's direct melee kill, on top of vanilla's wither skull roll (owner, 2026-10-09) | E | zombie, skeleton, wither skeleton, creeper, piglin (loot table `enchanting2:beheading/heads`); no ender dragon (it never rolls death loot); player heads behind `beheading.player_heads` (default off) |

**Phase 10a (owner, 2026-10-09):** every new non-curse enchantment joins `#minecraft:non_treasure`, so it comes from
every source (table, books, loot, mob and villager gear) and Phase 9's flags remove it per source. No `exclusive_set` on
any (§4). Health keeps the saved health through a relog (vanilla clamps it to 20 before armor applies, MC-17876). XP
Boost's fraction rounds up at random, because vanilla truncates (5 × 1.15 → 5). Starting numbers are in TODO 10a.

**Phase 10b (owner, 2026-10-09):** the E-route enchantments run on our own effect components
(`enchanting2:lifesteal`, `head_drop_chance`, `smelt_drops`, `night_vision`, and the entity effect `suspend_gravity` in
`projectile_spawned`), so a datapack can put the behaviour on any enchantment. They come from every source like 10a.
Config: `beheading.player_heads` (off) and `smelting.silk_priority` (`silk_touch`), in the config screen's
"Enchantment effects" category.

**Shield ideas not picked** (owner, 2026-10-10): Quick Guard (blocks sooner after raising), Parry (a well-timed
block stuns), Arrow Catcher (blocked arrows go to your inventory). Kept here, not planned.

**Helmet and leggings ideas not picked** (owner, 2026-10-10): Hard Hat (less falling-block damage), Leaping (back
on the deferred list), Shadow Step (mobs notice a sneaking player later). Kept here, not planned.

**Deferred (not in v1, kept for later):** Vein Miner, Leaping, Cleansing, Executioner,
Seeking, Shockwave.

---

## 10. Out of scope for v1
- Loaders other than Fabric. Minecraft versions other than 1.21.11.
- Vanilla-client compatibility. **The mod is required on both client and server** because
  the new table screen and Double Jump need client code.
- Changing Mending repair efficiency.
- In-GUI editing of the per-enchantment weights (`loot_weight`, `trade_weight`) and `treasure`. They stay
  file-only. Everything else is in the screen: enable and the table/loot/trade flags (Phase 9), max level and
  cost (Phase 10j; owner, 2026-10-10), and every global section.

---

## Open questions

None. Resolved 2026-10-06: Q13 (Fortune multiplies natural blocks only, gated by the
placed-block tracker, §6), Q14 (curses as a discount, Cleanse removal, five new curses, §5a),
Q12 (lapis only where vanilla uses it: the table, not the anvil)
and Q1–Q11 (cost formula, Mending surcharge, lapis, table, Silk/Fortune,
max levels, identity, version, config library, leftover lines, Riptide). They're folded
into the sections above and logged in `.agent/memory/decisions.md`.
