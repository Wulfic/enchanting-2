# Enchanting 2

**A Fabric mod for Minecraft 1.21.11 that reworks XP and enchanting.** You choose your enchantments instead of rolling
for them, you see every price before you pay, and an item can hold every enchantment that fits it. Every vanilla
enchantment is kept, 49 new ones are added, and almost everything can be configured in-game.

| | |
|---|---|
| **Minecraft** | Java Edition 1.21.11 |
| **Loader** | Fabric Loader 0.19.5 or newer |
| **Requires** | Fabric API |
| **Optional** | Mod Menu + YACL, for the in-game config screen |
| **Sides** | Server **and** every player's client |
| **License** | MIT |

**[Download the latest release](https://github.com/Wulfic/enchanting-2/releases/latest)**, then see
[Installation](Installation).

---

## What changes

| | Vanilla | Enchanting 2 |
|---|---|---|
| **XP past level 30** | each level costs more than the last | every level costs 112 points, no cap ([Experience](Experience)) |
| **Enchanting table** | three random offers | every enchantment that fits, pick as many as you like at the level you want ([Enchanting Table](Enchanting-Table)) |
| **Anvil** | "Too Expensive!" at 40, cost grows every use | no cap, no prior-work penalty, a full price breakdown ([Anvil](Anvil)) |
| **Grindstone** | strips everything | move one enchantment onto a book, or remove one curse ([Grindstone](Grindstone)) |
| **Prices** | opaque | 1 level per enchantment level, shown before you pay ([Costs](Costs)) |
| **Combining** | Sharpness XOR Smite, Silk Touch XOR Fortune... | all allowed by default, each switchable ([Vanilla Changes](Vanilla-Changes)) |
| **Max levels** | mostly III or IV | mostly V, any max from 1 to 255 in the config |
| **Curses** | pure downside | each one makes enchanting the item cheaper |
| **New enchantments** | — | 49, from Vision to Double Jump to Area Mining ([Enchantments](Enchantments)) |

---

## Pages

**Getting started**
- [Installation](Installation): requirements, servers, compatibility
- [Adding and Removing](Adding-and-Removing): what happens to an existing world
- [FAQ](FAQ): common questions and fixes

**Playing**
- [Experience](Experience) · [Enchanting Table](Enchanting-Table) · [Anvil](Anvil) · [Grindstone](Grindstone)
- [Costs](Costs): how every price is worked out, Mending, curse discounts
- [Enchantments](Enchantments): every enchantment, its max level, price and what it fits
- [Vanilla Changes](Vanilla-Changes): conflicts, Fortune, Silk Touch spawners, infinite buckets, tooltips

**Reference**
- [Configuration](Configuration): every option and its default
- [Building from Source](Building-from-Source): Gradle, tests, how releases are made

Found a bug or have an idea? [Open an issue](https://github.com/Wulfic/enchanting-2/issues/new/choose).
