# Changelog

All notable changes to Enchanting 2. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [1.0.0] - 2026-10-14

First release, for Minecraft 1.21.11 (Fabric). Needed on both the server and the client.

### Added
- **Experience curve:** from level 30 on, every level costs 112 XP points. No level cap.
- **Enchanting table:** lists every enchantment that fits the item. Pick several, each starting at level I with
  arrows to raise it, with the XP and lapis totals shown before Enchant (Mending's row shows its full price with
  everything else picked). Bookshelves cap levels (15 for every top level). Mending and Frost Walker need all 15.
  Enchanted items and books can be added to. The player's armor and offhand slots are in the window.
- **Anvil:** one cost model shared with the table, no 40-level cap (configurable), no prior-work penalty, a cost
  breakdown in the window, and a choice of what happens to a book's enchantments the item can't take. Anvils never
  wear out by default (`anvil.vanilla_durability` and `anvil.durability_multiplier` bring wear back).
- **Grindstone:** Extract one enchantment onto a book, Cleanse one curse, or Disenchant / Repair, only after the
  player picks.
- **Stacking:** an item takes every enchantment that fits it. Vanilla's exclusive groups are allowed together by
  default, each one switchable.
- **Max levels:** most vanilla enchantments go to V. Depth Strider stays III, Quick Charge goes to IV. Any max can be
  set from 1 to 255, with the levels that break a mechanic capped (Lure 5, Quick Charge 4, Riptide 7).
- **Curses as a discount:** each curse on the item takes a share off its enchant cost (capped at 45%). Cleansing it
  pays the discount back.
- **Fortune** on every natural block in its tool's tags, with placed blocks tracked so they never get the bonus.
- **49 new enchantments** for armor, elytra, shields, weapons, tools and any durable item, plus five curses
  (Fragility, Weight, Greed, Fumbling, Hunger). See [Enchantments](https://github.com/Wulfic/enchanting-2/wiki/Enchantments).
- **Vanilla extensions:** axes take Knockback, Looting and Fire Aspect, crossbows take Flame and Infinity, Silk Touch
  collects spawners, and Infinity makes water and lava buckets last 256 uses.
- **Acquisition control:** each enchantment can be turned off or kept out of the table, loot or trades, with its
  loot and trade weights and treasure status set per enchantment.
- **Tooltips:** hold Ctrl to see what each enchantment on an item does ("Efficiency V: ..."). A tooltip taller than
  the screen starts at its top and scrolls with the mouse wheel.
- **Mob health multiplier**, 1x to 100x (default 1x).
- **Config:** `common.json5`, `enchantments.json5` and `client.json5` in `config/enchanting2/`, an in-game screen
  through Mod Menu and YACL (optional), server settings synced to players, `/reload` support and a `.bak` on every
  in-game save. See [Configuration](https://github.com/Wulfic/enchanting-2/wiki/Configuration).

[Unreleased]: https://github.com/Wulfic/enchanting-2/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/Wulfic/enchanting-2/releases/tag/v1.0.0
