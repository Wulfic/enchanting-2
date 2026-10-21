# Installation

## Requirements

| | |
|---|---|
| Minecraft | Java Edition **1.21.11**, **26.1 to 26.1.2**, **26.2** or **26.3** (one jar per version line and loader, below) |
| Loader | [Fabric Loader](https://fabricmc.net/use/) 0.19.5 or newer, [Quilt Loader](https://quiltmc.org/) 0.31.0-beta.4 or newer (26.x only), [NeoForge](https://neoforged.net/) (26.1.2, 26.2, 26.3) or [Forge](https://files.minecraftforge.net/) (26.1 to 26.3) |
| Required | Fabric and Quilt: [Fabric API](https://modrinth.com/mod/fabric-api) (the plain one: Quilted Fabric API stopped at 1.21). NeoForge and Forge: nothing else |
| Optional | Fabric: [Mod Menu](https://modrinth.com/mod/modmenu) for the in-game config screen, and [YACL](https://modrinth.com/mod/yacl) if you'd rather have YACL's. NeoForge and Forge: nothing, the screen is in the mod list |
| Java | 21 or newer for 1.21.11, 25 or newer for 26.x |

## Singleplayer

1. Install Fabric Loader, NeoForge or Forge for your Minecraft version.
2. Download the jar for that version and loader from the
   [latest release](https://github.com/Wulfic/enchanting-2/releases/latest) (not a `-sources` jar):

   | Your Minecraft | Fabric / Quilt | NeoForge | Forge | Java |
   |---|---|---|---|---|
   | 1.21.11 | `enchanting2-<version>+1.21.11-fabric.jar` (Fabric only) | — | — | 21 |
   | 26.1, 26.1.1 | `enchanting2-<version>+26.1-26.1.2-fabric.jar` | — | `enchanting2-<version>+26.1-26.1.2-forge.jar` | 25 |
   | 26.1.2 | `enchanting2-<version>+26.1-26.1.2-fabric.jar` | `enchanting2-<version>+26.1.2-neoforge.jar` | `enchanting2-<version>+26.1-26.1.2-forge.jar` | 25 |
   | 26.2 | `enchanting2-<version>+26.2-fabric.jar` | `enchanting2-<version>+26.2-neoforge.jar` | `enchanting2-<version>+26.2-forge.jar` | 25 |
   | 26.3 | `enchanting2-<version>+26.3-fabric.jar` | `enchanting2-<version>+26.3-neoforge.jar` | `enchanting2-<version>+26.3-forge.jar` | 25 |

3. Put it in your `mods` folder. On Fabric, add Fabric API too, and Mod Menu if you want the config screen.

Your first launch writes the config files to `config/enchanting2/` (see [Configuration](Configuration)).

## Servers and LAN

**Install it on the server and on every player's client.** The new table, anvil and grindstone screens and Double
Jump need client code.

| Server | Client | Result |
|---|---|---|
| Enchanting 2 | Enchanting 2 | everything works |
| Enchanting 2 | vanilla (or without the mod) | the player is turned away at join, with a message naming the mod |
| vanilla | Enchanting 2 | vanilla screens and rules; the mod stays out of the way |

Both sides must run the same release: when the network protocol changes between versions, a mismatched client is
turned away at join the same way.

The server's gameplay settings apply to everyone and are sent to each player when they join. On a dedicated server
the in-game config screen shows the server's values read-only; edit the files on the server and run `/reload`. Only
the Display settings (`client.json5`) are each player's own. Details: [Configuration](Configuration).

## Compatibility

- **Other mods' enchantments** are kept on items through every operation. They're priced like the default (1 level
  per enchantment level) unless `enchantments.json5` says otherwise, and they show their own Ctrl description if
  their mod provides one.
- **Enchantment Descriptions** (`enchdesc`): when it's installed, Enchanting 2's own Ctrl descriptions turn off so
  lines aren't doubled.
- **Recipe viewers** (JEI, REI, EMI): the wider anvil and grindstone windows draw nothing outside the window, so
  viewers lay out around them.
- **Datapacks** can extend the tags the mod reads: which enchantments the table offers
  (`enchanting2:offered_at_table`), which blocks Fortune multiplies (`enchanting2:fortune_multiplies/<tool>`), and
  which items take Keep on Death and the widened vanilla enchantments.

Next: [Adding and Removing](Adding-and-Removing) if you're installing into an existing world.
