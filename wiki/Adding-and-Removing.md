# Adding and Removing

## Adding Enchanting 2 to an existing world

Safe. Nothing is converted.
- XP levels and progress are kept as they are. Only the cost of later levels changes.
- Enchanted items are unchanged. Every new maximum is at least vanilla's, so nothing is over its cap.
- Blocks placed before the mod was installed count as natural for Fortune (each can pay a bonus once; once mined and
  placed again, it's tracked as placed).

## Removing it

**Back up the world first.** The mod adds no blocks or items (the table, anvil and grindstone are vanilla blocks with
new screens), so nothing turns into air, but:

- **The 49 new enchantments are deleted from items** when a chunk or player loads without the mod. The items and their
  vanilla enchantments stay. A book holding only new enchantments becomes an empty enchanted book. Reinstalling won't
  bring them back.
- Vanilla enchantments above vanilla's max (Protection V, or higher if you raised a max) stay on items and keep working at that level.
- Items get their vanilla anvil prior-work penalty back: Enchanting 2 never reads or writes it.
- Mobs multiplied by the health multiplier stay multiplied (vanilla caps them at 1024 health). Set the multiplier to
  1x and load the areas before removing the mod to undo it.
- XP levels stay as they are. Only the curve goes back to vanilla.
- The placed-block marks for Fortune are dropped (Minecraft logs a warning about an unknown attachment). If you add the
  mod back later, blocks placed meanwhile count as natural once.
