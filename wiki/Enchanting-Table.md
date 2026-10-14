# Enchanting Table

No random offers. The table lists **every enchantment that fits the item**, and you pick what you want.

## Using it

1. Put the item (or a book) in the left slot and lapis in the right one.
2. Click a row to pick it. It starts at level I (or one above the level the item already has). Click `<` / `>` to
   change the level, or the row again to unpick it.
3. Pick as many as you like. The XP and lapis totals under the slots update as you go: green if you can afford it,
   red if not, or the reason there's no price.
4. Click **Enchant**. Its tooltip lists the Mending surcharge, any curse discount, the totals and the status.

Hover a row for why it can't be picked ("Needs 8 bookshelves", "Conflicts with Fortune", "Already at maximum level").
Hold **Ctrl** while hovering to see what the enchantment does.

Your armor and offhand slots are in the window, so worn gear can go straight onto the table (shift-click works).

## Bookshelves

Placement is vanilla's: up to 15 shelves, one block of air between them and the table. Shelves cap the level you can
pick, not what's offered:

| Shelves | Highest level you can pick |
|---|---|
| 0–3 | 1/5 of the enchantment's max, rounded up |
| 4–7 | 2/5 |
| 8–11 | 3/5 |
| 12–14 | 4/5 |
| 15 | the max |

So with 15 shelves every enchantment goes to its max. **Treasure enchantments** (Mending, Frost Walker, Health, Last
Stand) need all 15 (`table.treasure_requires_full_power`). Curses are never gated.

## What's offered

Every enchantment in the `enchanting2:offered_at_table` tag that fits the item: everything vanilla's table offers,
plus Mending, Frost Walker, Curse of Binding and Curse of Vanishing, plus the new enchantments. Soul Speed, Swift Sneak
and Wind Burst stay loot-only. Any enchantment can be kept off the table in the config, and datapacks can add to the
tag.

**Already-enchanted items and books are accepted.** Add more enchantments or raise levels; nothing is ever lowered
or removed. A book can take any table enchantment, several at once.

**Curses** appear as optional picks with a negative price: each one takes a share off the cost. See
[Costs](Costs#curses).

## Price

1 XP level per enchantment level by default, and 1 lapis per level, at most 64 lapis. The full rules (upgrades,
Mending, single-level enchantments) are on [Costs](Costs).

The server checks everything again when you click Enchant. If the price changed (someone took a shelf away), it
refuses rather than charging you something you didn't see.
