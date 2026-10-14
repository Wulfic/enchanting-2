# Anvil

- **No "Too Expensive!"** at 40 levels. A server can set its own cap (`costs.maximum_operation_cost`).
- **No prior-work penalty.** An item costs the same to work on the tenth time as the first.
- **A price breakdown** in a box beside the slots: every price line, the total, and every book enchantment that won't
  apply with the reason and what happens to it. It scrolls when it's long.
- **Anvils never wear out** by default. `anvil.vanilla_durability` brings back vanilla's chipping and breaking, and
  `anvil.durability_multiplier` (1 to 100) makes each chip that many times rarer.

## Books

A book applies **every** enchantment it holds that the item can take, in one go. The ones it can't take follow
`anvil.invalid_book_mode`:

| Mode | Result |
|---|---|
| `apply_valid` (default) | the rest stay on the book, which goes back to the right slot |
| `reject_all` | no result unless every entry applies |
| `strip_invalid` | the book is used up and the rest are destroyed; the breakdown says "Will be lost" first |

Combining two items works as in vanilla, except the anvil refuses (in `apply_valid` and `reject_all`) when an
enchantment on the sacrifice would be lost, since there's no book to return it on.

## Prices

| Action | Default cost |
|---|---|
| Enchanting (book or item) | per [Costs](Costs): 1 level per enchantment level, upgrades pay only for added levels |
| Rename | 1 level (`costs.rename_cost`) |
| Repair with material | 1 level per material item (`costs.repair_cost_per_material`) |
| Repair by combining two items | 2 levels (`costs.repair_combine_cost`) plus any enchantments moved across |

The anvil never costs lapis. Creative players pay nothing (`costs.creative_free`).

The cost label shows the exact total, "Too Expensive!", "Insufficient levels", or what's blocking the result.
