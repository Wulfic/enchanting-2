# FAQ

**I joined a server and got disconnected with a message about Enchanting 2.**
The server runs Enchanting 2 and your client doesn't, or runs a different version. Install the same release the server
uses. See [Installation](Installation#servers-and-lan).

**The table / anvil / grindstone looks like vanilla.**
The server you're on doesn't run Enchanting 2. A modded client keeps vanilla screens and rules there.

**There's no config button.**
On Fabric the button comes from [Mod Menu](https://modrinth.com/mod/modmenu); [YACL](https://modrinth.com/mod/yacl) is
optional (with it you get YACL's screen, without it the mod's own). On NeoForge and Forge it's in the mod list. You can
always edit the files in `config/enchanting2/` instead ([Configuration](Configuration)).

**The config screen won't let me change anything.**
On a dedicated server the screen shows the server's settings read-only, for everyone, operators included. Edit the
files on the server and run `/reload`. It's also read-only when a config file has a syntax error: fix the file (the
log names the problem).

**I raised a max level but nothing changed.**
Max levels apply when the server starts or the world opens. `/reload` doesn't apply them. Restart, or close and
reopen the world.

**I lowered a max level, but my items still have the higher level.**
By design: a lower max never touches existing items. It only limits new enchanting.

**Why is Mending so expensive on my pickaxe?**
Mending costs 2 plus 2 for every level of every other (non-curse) enchantment on the item, whichever order you add
them in. The
table shows Mending's full price on its row. See [Costs](Costs#mending).

**Why can't I pick Mending (or Frost Walker) at the table?**
Treasure enchantments need all 15 bookshelves. Hover the row for the reason.

**Fortune didn't multiply the logs I placed.**
On purpose: blocks a player placed never get the Fortune bonus, so there's no place-and-mine loop. Natural blocks do.
See [Vanilla Changes](Vanilla-Changes#fortune).

**A tooltip runs off the screen.**
A tooltip taller than the screen starts at its top, says "Scroll to see the rest", and scrolls with the mouse wheel.
Holding Ctrl shows the short "Name: description" form.

**Ctrl doesn't show descriptions.**
Check Display → enchantment descriptions in the config screen (`client.json5` `tooltips.enchantment_descriptions`).
If Enchantment Descriptions (`enchdesc`) is installed, its descriptions are used instead of ours.

**Where do I report a bug?**
[Open an issue](https://github.com/Wulfic/enchanting-2/issues/new/choose) with your Minecraft, Fabric Loader, Fabric
API and Enchanting 2 versions, the other mods installed, and `logs/latest.log` (or the crash report).
