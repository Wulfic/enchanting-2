# Enchantment Reference

Every enchantment in Enchanting 2, grouped by what it goes on: armor first (head to feet),
then elytra, shield, weapons, tools, and anything durable. Each enchantment is listed once,
under the narrowest group that covers it. The **Fits** column says exactly which items take it.

In game, holding Ctrl over an item shows these descriptions under each enchantment.

The design lives in [DESIGN.md](DESIGN.md) (§5 prices, §8 max levels, §9 new enchantments). **Update this file
whenever an enchantment, a max level or a price changes.**

**Status**
- **Vanilla**: a Minecraft 1.21.11 enchantment, kept under its vanilla ID (max levels per DESIGN §8)
- **Built**: ours, in the mod now
- **Planned**: ours, not built yet (Phase 10c)

**Cost** is in XP levels, for adding the enchantment at the table or anvil (DESIGN §5):
- **1/lvl**: the default, 1 level per enchantment level (V = 5). An upgrade pays only the added levels.
- **10**: every single-level enchantment except Mending and the curses (owner, 2026-10-10).
- **2/lvl**: Frost Walker only (V = 10; owner, 2026-10-10).
- **6/lvl**: Speed (elytra) only (V = 30; owner, 2026-10-10).
- **Mending**: 2, plus 2 for every level of every other non-curse enchantment on the item.
- **Curses**: 0, and each one takes a percentage off the item's enchant cost (DESIGN §5a).
- **†**: a planned enchantment's price, decided now; its stage adds the price row when it is built (10f set the rest).

Unknown or third-party enchantments use the default 1/lvl unless `enchantments.json5` says otherwise.

**Everything here is configurable** (owner, 2026-10-10), in `enchantments.json5` or the in-game config screen (Mod
Menu): each enchantment can be turned off or kept out of the table, loot or trades, priced, and given a different max
level, up to 255 (Efficiency 100 works). Where extra levels stop doing anything (Thorns past VII, Depth Strider past
III), the screen says so. Where they'd break the game (Lure past V, Quick Charge past IV, Riptide past VII), they're
capped. The screen's max-level and price fields come in Phase 10j; until then those are file-only, and a max level
change needs a restart. See DESIGN §8.

---

## Armor

### Helmet
| Enchantment | Max | Cost | Fits | What it does | Status |
|---|---|---|---|---|---|
| Respiration | 5 | 1/lvl | helmets | longer underwater breathing | Vanilla |
| Aqua Affinity | 1 | 10 | helmets | normal mining speed underwater | Vanilla |
| Vision | 1 | 10 | helmets | Night Vision while worn (players only), no flicker, removed when taken off | Built |
| Calm Gaze | 1 | 10 | helmets | endermen don't get angry when you look at them (like wearing a carved pumpkin); hitting one still does | Built |
| Clarity | 3 | 1/lvl | helmets | Blindness, Darkness and Nausea last a third shorter per level (III = immune, even to endless ones); works on mobs too | Built |
| Phantom Ward | 1 | 10 | helmets | phantoms don't target you, even when you haven't slept (they still spawn and still go for other players); one you hurt hunts you until it loses you | Built |
| Sustenance | 5 | 1/lvl | helmets | 4% less hunger drain per level from everything (V = 20%); multiplies with Endurance and Curse of Hunger | Built |

### Chestplate
| Enchantment | Max | Cost | Fits | What it does | Status |
|---|---|---|---|---|---|
| Cushion | 3 | 1/lvl | chestplates, elytra | less damage from flying into walls | Built |
| Last Stand | 3 | 1/lvl | chestplates | once per cooldown (10 / 8 / 6 min), a lethal hit leaves you at 1 HP with brief absorption; a Totem fires first | Planned |

### Leggings
| Enchantment | Max | Cost | Fits | What it does | Status |
|---|---|---|---|---|---|
| Swift Sneak | 5 | 1/lvl | leggings | faster sneaking (capped at walking speed) | Vanilla |
| Step Assist | 1 | 10 | leggings | step up full blocks without jumping | Built |
| Anchored | 5 | 1/lvl | leggings | 10% knockback resistance per level against hits (not explosions) | Built |
| Endurance | 5 | 1/lvl | leggings | sprinting and sprint-jumping cost 5% less hunger per level (V = 25%) | Built |
| Sure Footing | 3 | 1/lvl | leggings | a third less slowdown per level on soul sand, honey and powder snow (III = none); in powder snow you still sink and freeze | Built |

### Boots
| Enchantment | Max | Cost | Fits | What it does | Status |
|---|---|---|---|---|---|
| Feather Falling | 5 | 1/lvl | boots | less fall damage | Vanilla |
| Depth Strider | 3 | 1/lvl | boots | faster underwater movement (full effect at III, so it stays at 3) | Vanilla |
| Frost Walker | 5 | 2/lvl | boots | freezes water under you as you walk; no magma or campfire damage | Vanilla |
| Soul Speed | 5 | 1/lvl | boots | faster on soul sand and soul soil | Vanilla |
| Swiftness | 5 | 1/lvl | boots | more movement speed | Built |
| Double Jump | 1 | 10 † | boots | one extra jump in the air, reset on landing | Planned |
| Lava Walking | 1 | 10 | boots | stand and walk on lava (still or flowing) at normal speed; sneak to sink, and once under you swim as usual; standing on lava still burns, so bring Fire Resistance; a fall onto lava hurts like a fall onto ground; works on mobs too | Built |
| Snow Walker | 1 | 10 | boots | the leather-boots effect on any boots: walk on and climb powder snow, sneak to sink, no freezing; works on mobs too | Built |

Frost Walker and Depth Strider can share boots in this mod (vanilla forbids it). Water Walking was dropped,
because Frost Walker already crosses water (owner, 2026-10-10).

### Any armor piece
| Enchantment | Max | Cost | Fits | What it does | Status |
|---|---|---|---|---|---|
| Protection | 5 | 1/lvl | all armor | less damage from most sources | Vanilla |
| Fire Protection | 5 | 1/lvl | all armor | less fire damage, shorter burning | Vanilla |
| Blast Protection | 5 | 1/lvl | all armor | less explosion damage and knockback | Vanilla |
| Projectile Protection | 5 | 1/lvl | all armor | less projectile damage | Vanilla |
| Thorns | 5 | 1/lvl | all armor | chance to hurt attackers (V = 75%) | Vanilla |
| Toughness | 5 | 1/lvl | all armor | more armor toughness | Built |
| Health | 5 | 1/lvl | all armor | more max health; kept through a relog | Built |

The four protections can all share one piece in this mod (vanilla allows only one).

---

## Elytra

| Enchantment | Max | Cost | Fits | What it does | Status |
|---|---|---|---|---|---|
| Flight | 5 | 1/lvl | elytra | powered flight without fireworks | Planned |
| Speed | 5 | 6/lvl | elytra | +10% elytra flight speed per level (V = +50%), gliding and firework boosts | Planned |

Cushion (chestplate table above) also fits elytra.

---

## Shield

| Enchantment | Max | Cost | Fits | What it does | Status |
|---|---|---|---|---|---|
| Deflect | 3 | 1/lvl | shields | blocked projectiles bounce back at the shooter (33 / 66 / 100%) | Planned |
| Shield Bash | 3 | 1/lvl | shields | attacking while blocking knocks the target back and briefly stuns it | Planned |
| Steadfast | 3 | 1/lvl | shields | an axe disables your shield for less time (−33% per level, III = never) | Planned |
| Spiked | 5 | 1/lvl | shields | melee attackers whose hit you block take damage (Thorns for shields) | Planned |
| Bulwark | 3 | 1/lvl | shields | a blocked hit gives brief absorption, 1 heart per level | Planned |
| Riposte | 5 | 1/lvl | shields | after a block, your next melee hit within 2 s deals +3% per level (V = +15%) | Planned |
| Steady Stance | 1 | 10 † | shields | no slowdown while blocking | Planned |

---

## Melee weapons

"Melee" in our own enchantments means swords, spears, axes, the mace and the trident.

### Swords, spears and axes
| Enchantment | Max | Cost | Fits | What it does | Status |
|---|---|---|---|---|---|
| Sharpness | 5 | 1/lvl | swords, spears, axes | more damage | Vanilla |
| Smite | 5 | 1/lvl | swords, spears, axes, mace | more damage to undead | Vanilla |
| Bane of Arthropods | 5 | 1/lvl | swords, spears, axes, mace | more damage to arthropods, plus Slowness | Vanilla |
| Knockback | 5 | 1/lvl | swords, spears, axes | more knockback | Vanilla |
| Fire Aspect | 5 | 1/lvl | swords, spears, mace, axes | sets the target on fire | Vanilla |
| Looting | 5 | 1/lvl | swords, spears, axes | more mob drops | Vanilla |
| Sweeping Edge | 5 | 1/lvl | swords | more sweep-attack damage (only swords sweep, so not axes) | Vanilla |
| Lunge | 5 | 1/lvl | spears | lunge forward on a piercing attack (costs hunger) | Vanilla |

The damage enchantments (Sharpness, Smite, Bane, Impaling, Density, Breach) can all share one weapon in this mod.

### Mace
| Enchantment | Max | Cost | Fits | What it does | Status |
|---|---|---|---|---|---|
| Density | 5 | 1/lvl | mace | more smash damage per block fallen | Vanilla |
| Breach | 5 | 1/lvl | mace | ignores armor (V = 75%) | Vanilla |
| Wind Burst | 5 | 1/lvl | mace | launches you up after a smash | Vanilla |

### Trident
| Enchantment | Max | Cost | Fits | What it does | Status |
|---|---|---|---|---|---|
| Impaling | 5 | 1/lvl | trident | more damage to water mobs | Vanilla |
| Loyalty | 5 | 1/lvl | trident | returns after a throw | Vanilla |
| Riptide | 5 | 1/lvl | trident | launches you in water or rain | Vanilla |
| Channeling | 1 | 10 | trident | summons lightning in a thunderstorm | Vanilla |

Riptide, Loyalty and Channeling can share a trident in this mod: Riptide launches in water or rain, otherwise it
throws normally.

### Any melee weapon
| Enchantment | Max | Cost | Fits | What it does | Status |
|---|---|---|---|---|---|
| Vampirism | 5 | 1/lvl | melee | heals 4%/level of the health the target lost, direct hits only | Built |
| Beheading | 5 | 1/lvl | melee | 2%/level chance of a mob head on a player's melee kill | Built |
| Attack Speed | 5 | 1/lvl | melee | faster attacks | Built |
| Quick Strike | 5 | 1/lvl | melee | 5%/level chance of a second hit | Planned |

### Melee or ranged
| Enchantment | Max | Cost | Fits | What it does | Status |
|---|---|---|---|---|---|
| Withering | 5 | 1/lvl | melee, bow, crossbow | Wither on hit | Built |
| Poisoning | 5 | 1/lvl | melee, bow, crossbow | Poison on hit | Built |
| Freezing | 5 | 1/lvl | melee, bow, crossbow | Slowness on hit | Built |
| Critical Strike | 5 | 1/lvl | melee, bow, crossbow | 5%/level chance of +50% damage | Built |

---

## Ranged weapons

### Bow
| Enchantment | Max | Cost | Fits | What it does | Status |
|---|---|---|---|---|---|
| Power | 5 | 1/lvl | bow | more arrow damage | Vanilla |
| Punch | 5 | 1/lvl | bow | more arrow knockback | Vanilla |

### Crossbow
| Enchantment | Max | Cost | Fits | What it does | Status |
|---|---|---|---|---|---|
| Quick Charge | 4 | 1/lvl | crossbow | faster reload (V would be zero charge time, so it stops at 4) | Vanilla |
| Piercing | 5 | 1/lvl | crossbow | arrows pass through more targets | Vanilla |
| Multishot | 1 | 10 | crossbow | fires three arrows | Vanilla |

Multishot and Piercing can share a crossbow in this mod.

### Bow or crossbow
| Enchantment | Max | Cost | Fits | What it does | Status |
|---|---|---|---|---|---|
| Flame | 1 | 10 | bow, crossbow | arrows set targets on fire (not firework rockets) | Vanilla |
| Infinity | 1 | 10 | bow, crossbow, water bucket, lava bucket | bow/crossbow: one arrow in the inventory is never used up (rockets still are); bucket: never empties (see below) | Vanilla |
| TrueShot Aura | 1 | 10 | bow, crossbow | arrows ignore gravity for 5 s, then fall normally | Built |

Infinity and Mending can share a bow or crossbow in this mod.

---

## Tools

### Mining tools
| Enchantment | Max | Cost | Fits | What it does | Status |
|---|---|---|---|---|---|
| Efficiency | 5 | 1/lvl | pickaxes, shovels, axes, hoes, shears | faster mining | Vanilla |
| Fortune | 5 | 1/lvl | pickaxes, shovels, axes, hoes | more block drops; only natural blocks, never placed ones (shovels: dirt, grass, podzol, sand, gravel, clay...; axes: logs, mushroom blocks, bamboo; hoes: moss, wart blocks, leaves) | Vanilla |
| Silk Touch | 1 | 10 | pickaxes, shovels, axes, hoes | blocks drop themselves; a Silk Touch pickaxe also drops mob spawners (keeping their mob type, no XP) while `silk_touch.collect_spawners` is on (default on) | Vanilla |
| Smelting | 1 | 10 | pickaxes, shovels, axes, hoes | drops come out smelted, after Fortune, with furnace XP; Silk Touch wins by default | Built |
| Area Mining | 1 | 10 † | pickaxes, shovels | breaks 3×3 on the face you hit; sneak to break one block | Planned |

Silk Touch and Fortune can share a tool in this mod. By default Fortune never multiplies a Silk Touch drop.

### Hoe
| Enchantment | Max | Cost | Fits | What it does | Status |
|---|---|---|---|---|---|
| Replant | 1 | 10 † | hoes | breaking a grown crop replants it with a seed from its own drops | Planned |
| Tiller | 3 | 1/lvl | hoes | tills 3×3 / 5×5 / 7×7 | Planned |

### What an axe and a shovel take
- **Axe:** every mining-tool enchantment above (Efficiency, Fortune, Silk Touch, Smelting), Reach, XP Boost,
  Magnetic, Ender Link, Unbreaking, Mending, and the melee weapon enchantments: Sharpness, Smite, Bane of Arthropods,
  Knockback, Fire Aspect, Looting, and all of ours under "Any melee weapon" and "Melee or ranged". Not Sweeping
  Edge or Lunge (sword and spear mechanics), and not Area Mining (pickaxes and shovels only).
- **Shovel:** Efficiency, Fortune, Silk Touch, Smelting, Area Mining, Reach, XP Boost, Magnetic, Ender Link,
  Unbreaking, Mending. No weapon enchantments.

### Fishing rod
| Enchantment | Max | Cost | Fits | What it does | Status |
|---|---|---|---|---|---|
| Luck of the Sea | 5 | 1/lvl | fishing rod | better catches | Vanilla |
| Lure | 5 | 1/lvl | fishing rod | faster bites | Vanilla |

---

## Tools and weapons

| Enchantment | Max | Cost | Fits | What it does | Status |
|---|---|---|---|---|---|
| Reach | 3 | 1/lvl | mining tools, melee | longer block and entity reach | Built |
| XP Boost | 5 | 1/lvl | mining tools, melee, bow, crossbow | +5%/level XP from kills and mining | Built |
| Magnetic | 1 | 10 † | tools, weapons | drops go straight into your inventory; the rest drops normally | Planned |
| Ender Link | 1 | 10 † | tools, weapons | drops go to your ender chest; conflicts with Magnetic | Planned |

---

## Any durable item

| Enchantment | Max | Cost | Fits | What it does | Status |
|---|---|---|---|---|---|
| Unbreaking | 5 | 1/lvl | anything with durability | uses durability more slowly | Vanilla |
| Mending | 1 | 2 + 2 per other level | anything with durability (every item it can repair) | XP repairs the item | Vanilla |
| Keep on Death | 1 | 10 † | durables (configurable) | kept through death | Planned |

"Anything with durability" means armor, elytra, shield, swords, axes, pickaxes, shovels, hoes, spears, the mace, bow,
crossbow, trident, flint and steel, shears, brush, fishing rod and the two on-a-stick items: every vanilla item that
takes enchantments and has durability, so Mending already fits all of them. The water and lava buckets join them
(256 uses; see below). The other enchantable items (compass, heads, carved pumpkin) have no durability,
so Mending would do nothing there.

### Infinite buckets
Infinity on a water or lava bucket (table, or anvil + book; owner, 2026-10-10, DESIGN §6): placing the fluid leaves
the bucket full, by hand, from a dispenser, or into a cauldron. Each placement uses 1 durability (256 uses), so
Unbreaking and Mending are worth putting on it; they need Infinity on the bucket first. The last use destroys it like
any tool. Only a water or lava bucket takes enchantments, never the empty bucket. It can't scoop, isn't furnace fuel,
and isn't used up by a crafting recipe. Water still evaporates in the Nether. Costs 10 like any single-level
enchantment.

---

## Curses

A curse costs 0 and takes its discount off the item's enchant cost. Discounts add up to 45%. Removing one at the
grindstone (Cleanse) costs at least 30 levels. See DESIGN §5a.

| Curse | Max | Discount | Fits | What it does | Status |
|---|---|---|---|---|---|
| Curse of Binding | 1 | 10% | anything worn (armor, elytra, heads, carved pumpkin) | can't be taken off until death or it breaks | Vanilla |
| Curse of Vanishing | 1 | 10% | durables, compass, heads, carved pumpkin | destroyed on death | Vanilla |
| Curse of Fragility | 5 | 15% +5%/lvl | durables | 2× durability loss at I, +0.5× per level (V = 4×) | Built |
| Curse of Weight | 5 | 15% +5%/lvl | armor | −10% movement speed per level per piece; pieces add, at most −50% in total (config) | Built |
| Curse of Greed | 5 | 15% +5%/lvl | tools, weapons | −10% XP from kills and mining per level (V = −50%); multiplies with XP Boost | Built |
| Curse of Fumbling | 5 | 15% +5%/lvl | mining tools | −15% block break speed per level (V = −75%) | Built |
| Curse of Hunger | 5 | 15% +5%/lvl | armor | +10% hunger drain per level per piece (V = +50%; four pieces at V = +200%) | Built |

The five new curses go to level 5 (owner, 2026-10-10). Their discount is 15% at I and 5% more per level (V = 35%),
so one curse alone never reaches the 45% cap. Like vanilla's curses, ours come from loot, librarian trades and the
table (as a discount), never from mob gear. Cleansing one repays its discount at the level it has. Effect numbers
per level: owner, 2026-10-12. Both discount numbers can be changed per curse in the config screen.

---

**Totals:** 43 vanilla (2 of them curses), 24 built (5 of them curses), 25 planned in 10c. Phase 10g
widened Knockback, Fire Aspect, Looting, Flame, Infinity and TrueShot Aura, gave water and lava buckets durability, and
added the Silk Touch spawner option.
