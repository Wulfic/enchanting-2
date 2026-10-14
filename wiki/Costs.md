# Costs

One rule prices the [Enchanting Table](Enchanting-Table) and the [Anvil](Anvil), and the
[Grindstone](Grindstone)'s Extract and Cleanse build on it. Every price is shown before you pay.

## The rule

For each enchantment added or raised:

```
price = base cost (only if it's new to the item)
      + cost per level × levels charged
```

**Default: base 0, 1 per level**, so Sharpness V costs 5. **Upgrades pay only for the added levels**: Efficiency
III → V costs 2.

| Enchantment | Price |
|---|---|
| most | 1 per level |
| any single-level enchantment (Silk Touch, Infinity, Multishot, Smelting, Double Jump, ...) | 10 |
| Frost Walker | 2 per level (V = 10) |
| Speed and Flight (elytra) | 6 per level (V = 30) |
| Mending | 2, plus a surcharge (below) |
| curses | 0, and a discount (below) |

At the table, lapis = the XP price, at most 64.

Every number here can be changed per enchantment in `enchantments.json5` or the config screen, and so can how
upgrades are charged (`upgrade_cost_mode`: only the added levels, the whole result level again, a fixed amount, or
free). Other mods' enchantments use the default 1 per level.

## Mending

Mending costs 2, **plus 2 for every level of every other (non-curse) enchantment on the finished item**. It's priced
on the finished item, so order doesn't matter:

| Build | Price |
|---|---|
| Mending alone | 2 |
| Mending + Efficiency V | 2 + 5 + 10 = 17 |
| Efficiency V first (5), Mending later (2 + 10) | 17 |
| Mending first (2), Efficiency V later (5 + 10) | 17 |
| Mending + Silk Touch | 2 + 10 + 2 = 14 |
| Efficiency III → V on an item that has Mending | 2 + 4 = 6 |

At the table, Mending's row shows its full price with everything else you've picked.

## Curses

A curse makes enchanting **cheaper**. Each curse on the finished item takes its discount off the enchanting price:
10% for Binding and Vanishing, 15% at level I and 5% more per level for the five new curses (V = 35%). Discounts add up
to at most 45% (`curses.max_discount_percent`), and the discounted price rounds up. Rename and repair are
never discounted; at the table, lapis follows the discounted price.

Example: Mending + Efficiency V (17) with Curse of Weight I (15%) → `ceil(17 × 0.85)` = **15**.

**Removing a curse** (Cleanse at the grindstone) repays the discount it earned: its own percentage of what the item's
enchantments would cost, at least 30 levels (`curses.removal_min_cost`). Cleansing that Curse of Weight later costs
`max(30, ceil(0.15 × 17))` = 30.

The curses and what they do: [Enchantments](Enchantments#curses).

## Limits

- No maximum by default. A server can set `costs.maximum_operation_cost`; totals above it show "Too Expensive!".
  Creative players are never refused.
- Creative players pay nothing by default (`costs.creative_free`).
