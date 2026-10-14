# Grindstone

Pick what to do. **Nothing happens until you pick a row**, so a shift-click can't strip an item by accident.

| Put in | Pick | Result | Cost |
|---|---|---|---|
| an enchanted item (or a book with several enchantments) + plain books | **Extract** one enchantment | the enchantment moves onto a book; the item keeps everything else | XP levels, as if adding it (Efficiency V = 5, Mending = 2) |
| a cursed item | **Cleanse** one curse | that curse is removed | at least 30 levels (see below) |
| an enchanted item | **Disenchant** | vanilla: non-curse enchantments removed, XP back | gives XP |
| two of the same item | **Repair** | vanilla: combined and disenchanted, XP back | gives XP |

The list beside the slots shows each action with its price. Click a row to pick it, again to clear it; putting in a
different item clears it too. The price of the pick is shown where the anvil's cost label is.

## Extract

- The enchantment is **moved, not copied**. It can never end up on both the item and the book, or on neither.
- One plain book per extraction. The books can go in either slot.
- Curses can't be extracted (they're listed but refused). Remove them with Cleanse.
- A book holding only the picked enchantment is refused (there's nothing to split off). A book with that enchantment
  and a curse can be split, which separates the curse.

## Cleanse

Removing a curse repays the discount it gave: the cost is the curse's own discount percentage of what the item's
other enchantments would cost to add, with a minimum of 30 levels (`curses.removal_min_cost`). Without that, "add a
curse, enchant cheaply, remove the curse" would be a free discount. See [Costs](Costs#curses).

Extract and Cleanse respect the server's "Too Expensive!" cap. Creative players bypass it.
