# Changelog

All notable changes to Enchanting 2. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and versions follow [Semantic Versioning](https://semver.org/).

## [1.0.0] - 2026-10-13

First release, for Minecraft 1.21.11 (Fabric). Needed on both the server and the client.

### Added
- **Experience curve:** from level 30 on, every level costs 112 XP points. No level cap.
- **Enchanting table:** lists every enchantment that fits the item. Pick several, each at a chosen level, with the
  XP and lapis totals shown before Enchant. Bookshelves cap levels (15 for every top level). Mending and Frost Walker
  need all 15. Enchanted items and books can be added to.
- **Anvil:** one cost model shared with the table, no 40-level cap (configurable), no prior-work penalty, a cost
  breakdown in the window, and a choice of what happens to a book's enchantments the item can't take.
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
  (Fragility, Weight, Greed, Fumbling, Hunger). See [docs/ENCHANTMENTS.md](docs/ENCHANTMENTS.md).
- **Vanilla extensions:** axes take Knockback, Looting and Fire Aspect, crossbows take Flame and Infinity, Silk Touch
  collects spawners, and Infinity makes water and lava buckets last 256 uses.
- **Acquisition control:** each enchantment can be turned off or kept out of the table, loot or trades, with its
  loot and trade weights and treasure status set per enchantment.
- **Tooltips:** hold Ctrl to see what each enchantment on an item does.
- **Mob health multiplier**, 1x to 100x (default 1x).
- **Config:** `common.json5`, `enchantments.json5` and `client.json5` in `config/enchanting2/`, an in-game screen
  through Mod Menu and YACL (optional), server settings synced to players, `/reload` support and a `.bak` on every
  in-game save. See [docs/CONFIG.md](docs/CONFIG.md).

[1.0.0]: https://github.com/Wulfic/enchanting-2/tree/v1.0.0
