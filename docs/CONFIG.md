# Configuration

Enchanting 2 is configured in three JSON5 files (JSON with comments and trailing commas) in
`config/enchanting2/`. Each file is written with its defaults and a comment on every key the first time the
game or server starts. Almost everything can also be changed in-game.

| File | What it holds | Whose it is |
|---|---|---|
| `common.json5` | Gameplay: the XP curve, costs, conflicts, curses, the anvil, table and grindstone, Fortune, mobs | The server's. Sent to every player on join |
| `enchantments.json5` | Per-enchantment settings: on/off, where it can be found, max level, price | The server's. Sent to every player on join |
| `client.json5` | Display only: the enchantment description tooltips | Each player's own. Never sent anywhere |

## Editing in-game

With [Mod Menu](https://modrinth.com/mod/modmenu) and [YACL](https://modrinth.com/mod/yacl) installed, open
**Mods → Enchanting 2 → Config**. Both are optional; without them, edit the files.

The screen has a category for each section of `common.json5` (Experience, Conflicts, Costs, Curses, Anvil,
Enchanting Table, Fortune, Enchantment effects, Mobs), an **Enchantments** category for `enchantments.json5`, and
**Display** for `client.json5`.

- **Singleplayer and LAN:** the host edits everything, from the title screen or in a world. All singleplayer worlds
  share one `common.json5` and one `enchantments.json5`.
- **Dedicated server:** the screen shows the server's values read-only, for everyone, operators included. Edit the
  files on the server instead (below).
- **Vanilla server:** the screen says the server runs vanilla rules. `client.json5` is still yours to edit.
- **Display** (`client.json5`) is always editable, on any server.

Saving from the screen keeps the previous file as `<name>.json5.bak`. The save writes the whole file from the values
in effect, so a hand edit you never applied with `/reload` is replaced (it survives in the `.bak`).

## Editing the files

1. Edit the file on the server (or in your own `config/enchanting2/` for singleplayer).
2. Run `/reload`. The server re-reads both gameplay files and sends them to every player.

**Exception:** `max_level_override` in `enchantments.json5` applies only when the server starts or the world is
opened. `/reload` doesn't apply it.

Enchanting 2 never rewrites a file when it loads it. A value it can't use (wrong type, out of range, unknown option)
falls back to its default and logs a warning naming the key, the bad value and the default. A file that doesn't
parse at all keeps the previous settings on `/reload` and makes the in-game screen read-only until it's fixed.
Leave `schema_version` as it is; it lets newer versions read older files.

## `common.json5`

| Section | Controls |
|---|---|
| `experience` | The XP curve: from level 30 on, every level costs the same 112 points instead of more each time |
| `allow_together` | Which of vanilla's mutually exclusive groups may share an item (all allowed by default) |
| `costs` | Rename and repair costs, the "Too Expensive!" cap (off by default), free creative mode |
| `curses` | The discount curses give, what removing one at the grindstone costs, Curse of Weight's slowdown cap |
| `anvil` | What happens to a book's enchantments the item can't take; whether anvils wear out (never, by default) |
| `table` | Whether treasure enchantments need a full 15 bookshelves |
| `fortune` | Silk Touch and Fortune on the same tool |
| `beheading`, `smelting`, `silk_touch`, `last_stand` | Options for those enchantments (player heads, Silk Touch vs Smelting, collecting spawners, Last Stand in PvP) |
| `mobs` | A health multiplier for every mob (1x by default) |

<!-- defaults: common.json5 -->
```json5
{
	// File format version. Leave it as it is: Enchanting 2 uses it to read older files correctly.
	"schema_version": 1,
	// Experience curve (levels past 30).
	"experience": {
		// false = the full vanilla curve at every level.
		"enabled": true,
		// From this level on, each level costs fixed_points_per_level. 0 = fixed cost at every level. Range 0..1000000.
		"flatten_after_level": 30,
		// XP points per level past the threshold. 112 is vanilla's 30->31 cost. Range 1..1000000.
		"fixed_points_per_level": 112,
	},
	// Vanilla exclusive sets. true = these may share an item; false = vanilla (only one of them).
	"allow_together": {
		// fortune, silk_touch
		"silk_touch_fortune": true,
		// infinity, mending
		"mending_infinity": true,
		// blast_protection, fire_protection, projectile_protection, protection
		"protection": true,
		// bane_of_arthropods, breach, density, impaling, sharpness, smite
		"damage": true,
		// channeling, loyalty, riptide
		"riptide_loyalty_channeling": true,
		// multishot, piercing
		"multishot_piercing": true,
		// depth_strider, frost_walker
		"depth_strider_frost_walker": true,
	},
	// XP level costs that aren't per enchantment (see enchantments.json5).
	"costs": {
		// XP levels to rename at the anvil.
		"rename_cost": 1,
		// XP levels per material item used to repair (e.g. one per iron ingot).
		"repair_cost_per_material": 1,
		// XP levels to repair by combining two of the same item. Enchantments moved across are priced on top.
		"repair_combine_cost": 2,
		// Totals above this are refused ("Too Expensive!"). -1 = no limit. Creative players are never refused.
		"maximum_operation_cost": -1,
		// true = creative players pay nothing.
		"creative_free": true,
	},
	// Each curse on the result makes enchanting cheaper.
	"curses": {
		// Cap on the total discount from every curse on the item. Range 0..95.
		"max_discount_percent": 45,
		// Least XP levels to cleanse a curse at the grindstone. Otherwise it costs the curse's percent of the item's enchantments, i.e. the discount it earned.
		"removal_min_cost": 30,
		// Curse of Weight slows by 10% per level on each armor piece; the pieces add up to at most this much. Range 0..100 (100 can stop the wearer completely). Applies the next time the wearer's gear changes.
		"weight_max_slowdown_percent": 50,
	},
	// Anvil behavior.
	"anvil": {
		// What a book's entries that the item can't take do. One of reject_all, apply_valid, strip_invalid. reject_all = no result; apply_valid = they stay on the book; strip_invalid = they are destroyed (the anvil shows which first).
		"invalid_book_mode": "apply_valid",
		// Anvils get chipped and damaged, and finally break, as in vanilla (a 12% chance per use, and when they fall). false = anvils never wear out.
		"vanilla_durability": false,
		// With vanilla_durability on: each chip is this many times rarer (1 to 100; 1 = vanilla, 2 = anvils last about twice as long).
		"durability_multiplier": 1,
	},
	// Enchanting table behavior.
	"table": {
		// true = treasure enchantments offered at the table (Mending, Frost Walker) need 15 bookshelves. Curses are never gated.
		"treasure_requires_full_power": true,
	},
	// Fortune and Silk Touch on mining tools.
	"fortune": {
		// A tool with Silk Touch and Fortune. One of silk_wins, fortune_multiplies_silk. silk_wins = Fortune never multiplies a silk-touched drop; fortune_multiplies_silk = it does, on natural blocks only. WARNING: on ores that multiplies the ore blocks themselves, which are then mined again with Fortune (about 3.1x the ore at Fortune V, more if placed-block tracking is lost).
		"silk_fortune_mode": "silk_wins",
	},
	// The Beheading enchantment.
	"beheading": {
		// Beheading can drop a killed player's head (true) or only mob heads (false).
		"player_heads": false,
	},
	// The Smelting enchantment.
	"smelting": {
		// A tool with Silk Touch and Smelting. One of silk_touch, smelting. silk_touch = nothing is smelted; smelting = the silk-touched drop is smelted.
		"silk_priority": "silk_touch",
	},
	// Silk Touch on mob spawners.
	"silk_touch": {
		// A Silk Touch pickaxe drops a mob spawner as an item that keeps its mob, and gives no XP (true), or spawners never drop, as in vanilla (false). Only changes new breaks.
		"collect_spawners": true,
	},
	// The Last Stand enchantment.
	"last_stand": {
		// Last Stand also saves a player from another player's lethal hit (true), or only from everything else (false).
		"pvp": true,
	},
	// Mobs.
	"mobs": {
		// Every mob's max health times this (1 to 100; 1 = vanilla). Bosses too, never players. Mobs already in the world change on the next /reload and keep their share of health.
		"health_multiplier": 1,
	},
}
```

## `enchantments.json5`

Empty by default: every enchantment uses its built-in settings. Add an entry under `overrides`, keyed by the
enchantment's ID, to change one. Every key in an entry is optional.

| Key | Values | Default |
|---|---|---|
| `enabled` | `false` = never from the table, loot or trades. Items that already have it keep it | `true` |
| `table`, `loot`, `trade` | `false` = never from that source (loot also covers mob gear; trade covers librarian books and villager gear) | `true` |
| `loot_weight`, `trade_weight` | 1–1024. Higher is more common | The enchantment's own weight (Sharpness 10, Mending 2) |
| `treasure` | `true` = needs 15 bookshelves at the table and costs double from librarians | Vanilla's tags |
| `max_level_override` | 1–255. Restart or reopen the world to apply | See [ENCHANTMENTS.md](ENCHANTMENTS.md) |
| `base_cost` | XP levels charged once when the enchantment is newly added | Built-in |
| `cost_per_level` | XP levels per level | Built-in (usually 1) |
| `upgrade_cost_mode` | `level_difference` (only added levels), `result_level`, `fixed` (`cost_per_level` once), `free` | `level_difference` |
| `other_levels_surcharge` | XP levels per level of every other non-curse enchantment on the item | 0 (Mending 2) |
| `discount_percent`, `discount_per_level` | Curses only: the percent taken off at level I, and added for each level above I | Built-in |

The `table`, `loot` and `trade` flags only take an enchantment out of a source. Tags such as
`#minecraft:tradeable` decide what's in a source to begin with, so a datapack still controls that.

Some max levels stop working past a point: Lure above 5, Quick Charge above 4 and Riptide above 7 are lowered to
those levels with a warning. Lowering a max level never changes items that already carry a higher one.

Example:

```json5
"overrides": {
	"minecraft:efficiency": { max_level_override: 10 },
	"minecraft:mending": { table: false, trade_weight: 4 },
	"enchanting2:area_mining": { enabled: false },
	"enchanting2:speed": { cost_per_level: 4 },
},
```

<!-- defaults: enchantments.json5 -->
```json5
{
	// File format version. Leave it as it is: Enchanting 2 uses it to read older files correctly.
	"schema_version": 1,
	// Per-enchantment settings keyed by ID, e.g. "minecraft:fortune": { max_level_override: 3 }. max_level_override (1-255) replaces the max level from DESIGN §8. It applies when the server starts or the world is opened (/reload does not apply it), and it never changes items that already carry a higher level. Lure above 5, Quick Charge above 4 and Riptide above 7 stop working, so they are lowered to those levels (logged as a warning). Costs, in XP levels (each optional; the built-in value otherwise): base_cost (charged once when newly added), cost_per_level, upgrade_cost_mode (level_difference = only added levels, result_level, fixed = cost_per_level once, free), other_levels_surcharge (per level of every other non-curse enchantment; Mending's is 2), discount_percent (curses: cut to the enchant cost at level I), discount_per_level (curses: added for each level above I). Where it can be obtained (each optional, true by default; applies on /reload): enabled (false = never from the table, loot or trades; items that already have it keep it), table, loot (loot tables and mob gear), trade (librarian books and villager gear). These only take an enchantment out of a source; tags such as #minecraft:tradeable decide what is in it. loot_weight and trade_weight (1-1024; the enchantment's own weight by default, e.g. Sharpness 10, Mending 2): higher is more common. treasure (true/false): needs 15 bookshelves at the table and costs double from librarians; the vanilla tags by default.
	"overrides": { },
}
```

## `client.json5`

<!-- defaults: client.json5 -->
```json5
{
	// File format version. Leave it as it is: Enchanting 2 uses it to read older files correctly.
	"schema_version": 1,
	// Item tooltips (on any server).
	"tooltips": {
		// Hold Ctrl over an item to see what each of its enchantments does.
		"enchantment_descriptions": true,
		// Show a "hold Ctrl" line under the enchantments while Ctrl isn't held.
		"show_hint": true,
	},
}
```
