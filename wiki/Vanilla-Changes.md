# Vanilla Changes

What Enchanting 2 changes about vanilla's own enchantments. The new ones are on [Enchantments](Enchantments).

## No limit on how many

An item takes every enchantment that fits it. "Fits" is still vanilla's (or the mod's widened list below): a pickaxe
never takes Sharpness.

## Vanilla's "can't be combined" groups

All allowed together by default. Each group can be switched back to vanilla in `common.json5` → `allow_together`.

| Group | When combined |
|---|---|
| Sharpness / Smite / Bane of Arthropods / Impaling / Density / Breach | every bonus that applies adds up |
| Protection / Fire / Blast / Projectile Protection | all apply; vanilla's protection cap still limits the total |
| Silk Touch / Fortune | Silk Touch wins where it changes the drop (below) |
| Mending / Infinity | both work |
| Riptide / Loyalty / Channeling | in water or rain, Riptide launches you; otherwise a normal throw with Loyalty and Channeling |
| Multishot / Piercing | both apply |
| Depth Strider / Frost Walker | both apply |

## Max levels

Most vanilla enchantments go to **V**: Protection V, Unbreaking V, Fortune V, Looting V, Feather Falling V and more.
Depth Strider stays III and Quick Charge goes to IV. Each one's max is on [Enchantments](Enchantments).

Any max can be set from 1 to 255 per enchantment (restart to apply). Where extra levels stop doing anything (Thorns
past VII, Depth Strider past III) the config screen says so. Where they'd break a mechanic, they're capped: Lure 5,
Quick Charge 4, Riptide 7, Piercing 127, Lunge 13, Wind Burst 14.

Lowering a max never touches items that already have a higher level.

## More items

| Enchantment | Vanilla | Also fits |
|---|---|---|
| Knockback, Looting | swords, spears | axes |
| Fire Aspect | swords, spears, mace | axes |
| Flame, Infinity | bow | crossbow (arrows only; rockets are still used up) |
| Infinity | bow | water and lava buckets (below) |

Sweeping Edge stays on swords: the sweep attack only exists for swords.

## Fortune

**Fortune multiplies every drop of a natural block in its tool's list**, not just ores:

| Tool | Blocks |
|---|---|
| Axe | logs and stems, mushroom blocks, bamboo |
| Shovel | dirt, grass, podzol, mycelium, sand, red sand, gravel (flint), clay (clay balls), soul sand and soil, mud, snow blocks (snowballs) |
| Hoe | moss, nether and warped wart blocks, shroomlight, leaves |
| Pickaxe | vanilla's ores only. Ancient debris is never multiplied |

**Blocks you placed never get the bonus**, so there's no place-and-mine loop. The mod tracks placed blocks per chunk,
through pistons, falling sand and endermen. Blocks that were in the world before you installed the mod count as
natural. Fortune V averages about 3.1× (vanilla's ore formula).

**Silk Touch + Fortune** (`fortune.silk_fortune_mode`): by default Silk Touch wins wherever it changes the drop (clay,
snow, mushroom blocks and gravel give one silk-touched block), and logs, sand, dirt and moss still multiply.
`fortune_multiplies_silk` multiplies silk-touched drops too, including ore blocks; the config screen warns about it.

Datapacks can change the lists: block tags `enchanting2:fortune_multiplies/<axes|shovels|hoes|pickaxes>`.

## Silk Touch picks up spawners

A Silk Touch pickaxe drops a mob spawner as an item that **keeps its mob** (only the mob: a map-maker's custom spawner
comes back as a plain spawner of that mob). No XP from it, as with silk-touched ores. Trial spawners still drop
nothing. Turn it off with `silk_touch.collect_spawners`.

## Infinite buckets

**Infinity on a water or lava bucket**: pouring leaves the bucket full. It has 256 uses, so Unbreaking and Mending are
worth adding (Infinity has to be on it first). It works by hand, from a dispenser and into a cauldron. It can't scoop,
isn't furnace fuel, and isn't used up by crafting. The last use breaks it like a tool.

## Descriptions in tooltips

Hold **Ctrl** over any enchanted item or book to see what each enchantment does, as "Efficiency V: ..." on its own
line. Without Ctrl, a grey hint line says so. Other mods' enchantments show a description if their mod provides one.
A tooltip taller than the screen scrolls with the mouse wheel. Both lines can be turned off in Display
(`client.json5`).

## Mob health

An optional multiplier for every mob's health, bosses included, 1x to 100x (`mobs.health_multiplier`, default 1x =
vanilla).
