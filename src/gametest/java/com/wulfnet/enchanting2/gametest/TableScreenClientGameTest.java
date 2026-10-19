package com.wulfnet.enchanting2.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import com.wulfnet.enchanting2.client.TablePreviewState;
import com.wulfnet.enchanting2.client.TableScreenAccess;
import com.wulfnet.enchanting2.network.TablePreviewPayload;
import com.wulfnet.enchanting2.screen.PanelText.Zone;
import com.wulfnet.enchanting2.table.TableEquipmentSlot;
import com.wulfnet.enchanting2.table.TableMenuAccess;
import com.wulfnet.enchanting2.table.TableRow;
import com.wulfnet.enchanting2.table.TableSelection;
import com.wulfnet.enchanting2.table.TableWindow;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EnchantingTableBlock;

/**
 * The table in the window on the real client (TODO 10l; Stage 7D's flows kept): a real table with 15 bookshelves. The
 * window is wider and taller with the inventory moved, the list in the offer box shows the server's rows, clicks pick and
 * step levels on the server, the column under the slots shows the price, the Enchant button charges exactly that, a
 * row's tooltip carries its description, a book's long list scrolls by wheel and by dragging the thumb, a pick on the
 * last visible row stays in place, and a click on the list never drops the carried item. Screenshots land in the client
 * GameTest run dir for the owner to look at.
 */
public class TableScreenClientGameTest implements FabricClientGameTest {
	/** 854x480 at GUI scale auto is 240 tall: the taller window still fits. */
	private static final int SMALL_WIDTH = 854;
	private static final int SMALL_HEIGHT = 480;
	private static final int WIDE_WIDTH = 1920;
	private static final int WIDE_HEIGHT = 1080;
	private static final int LEVELS = 40;
	private static final String SHARPNESS = "minecraft:sharpness";
	private static final String MENDING = "minecraft:mending";
	private static final String EFFICIENCY = "minecraft:efficiency";
	/** First in a book's list, so it's on screen before any scrolling. */
	private static final String AQUA_AFFINITY = "minecraft:aqua_affinity";
	/** The first player-inventory slot: vanilla puts it at y 84. */
	private static final int FIRST_INVENTORY_SLOT = 2;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			ClientWorlds.waitForChunksRender(world);
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("xp set @a " + LEVELS + " levels");
			context.getInput().resizeWindow(SMALL_WIDTH, SMALL_HEIGHT);
			world.getServer().runOnServer(TableScreenClientGameTest::openTable);
			context.waitForScreen(EnchantmentScreen.class);
			context.waitTick();

			theWindowIsBiggerWithTheInventoryMoved(context);
			context.getInput().resizeWindow(WIDE_WIDTH, WIDE_HEIGHT);
			context.waitTick();
			enchantThroughTheList(context, world);
			stepperChangesTheLevel(context, world);
			aPickOnTheLastVisibleRowStaysInView(context, world);
			bookTakesSeveralAndItsListScrolls(context, world);
			listClickKeepsTheCarriedItem(context, world);
			closingClearsTheList(context);
		}
	}

	/** A table three blocks in front of the player, 15 shelves around it, nothing between them. */
	private static BlockPos tablePos(ServerPlayer player) {
		return player.blockPosition().offset(0, 0, 3);
	}

	private static void openTable(MinecraftServer server) {
		ServerPlayer player = player(server);
		ServerLevel level = player.level();
		BlockPos table = tablePos(player);
		level.setBlockAndUpdate(table, Blocks.ENCHANTING_TABLE.defaultBlockState());
		List<BlockPos> offsets = EnchantingTableBlock.BOOKSHELF_OFFSETS;
		for (int i = 0; i < offsets.size(); i++) {
			BlockPos offset = offsets.get(i);
			level.setBlockAndUpdate(table.offset(offset.getX() / 2, offset.getY(), offset.getZ() / 2),
					Blocks.AIR.defaultBlockState());
		}
		for (int i = 0; i < offsets.size(); i++) {
			level.setBlockAndUpdate(table.offset(offsets.get(i)), i < 15 ? Blocks.BOOKSHELF.defaultBlockState()
					: Blocks.AIR.defaultBlockState());
		}
		player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new EnchantmentMenu(id, inventory,
				ContainerLevelAccess.create(level, table)), Component.translatable("container.enchant")));
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static EnchantmentMenu serverMenu(MinecraftServer server) {
		if (player(server).containerMenu instanceof EnchantmentMenu menu) {
			return menu;
		}
		throw new AssertionError("the server player has no table open");
	}

	private static TableSelection held(TestSingleplayerContext world) {
		return world.getServer().computeOnServer(server -> ((TableMenuAccess) serverMenu(server))
				.enchanting2$selection());
	}

	private static int level(MinecraftServer server, ItemStack stack, ResourceKey<Enchantment> key) {
		return EnchantmentHelper.getEnchantmentsForCrafting(stack)
				.getLevel(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key));
	}

	private static <T> T screen(ClientGameTestContext context, Function<TableScreenAccess, T> read) {
		return context.computeOnClient(client -> {
			if (client.screen instanceof TableScreenAccess access) {
				return read.apply(access);
			}
			throw new AssertionError("expected the table screen, found " + client.screen);
		});
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError(what);
		}
	}

	/** {@code x, y} in GUI coordinates; the input API takes window pixels. */
	private static void moveTo(ClientGameTestContext context, int x, int y) {
		double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
		context.getInput().setCursorPos((x + 0.5) * scale, (y + 0.5) * scale);
	}

	private static void click(ClientGameTestContext context, int x, int y) {
		moveTo(context, x, y);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
	}

	private static int listTop(ClientGameTestContext context) {
		return screen(context, TableScreenAccess::enchanting2$topPos) + TableWindow.LIST_Y;
	}

	private static int rowsX(ClientGameTestContext context) {
		return screen(context, TableScreenAccess::enchanting2$rowsX);
	}

	/** Scrolls the list one notch at a time with the mouse over it; negative {@code steps} scroll down. */
	private static void scrollList(ClientGameTestContext context, int steps) {
		moveTo(context, rowsX(context) + 20, listTop(context) + 40);
		for (int i = 0; i < Math.abs(steps); i++) {
			context.getInput().scroll(Integer.signum(steps));
			context.waitTick();
		}
	}

	private static int notches(ClientGameTestContext context) {
		return screen(context, TableScreenAccess::enchanting2$maxScroll) / TableWindow.LINE + 1;
	}

	private static void scrollToTop(ClientGameTestContext context) {
		scrollList(context, notches(context));
		check(screen(context, TableScreenAccess::enchanting2$scrollOffset) == 0, "scrolled to the top");
	}

	/** From the top, scrolls down until {@code id}'s first line is drawn. */
	private static void scrollIntoView(ClientGameTestContext context, String id) {
		scrollToTop(context);
		int notches = notches(context);
		for (int i = 0; i < notches && screen(context, access -> access.enchanting2$rowY(id)).isEmpty(); i++) {
			scrollList(context, -1);
		}
	}

	private static void clickRow(ClientGameTestContext context, String id) {
		scrollIntoView(context, id);
		OptionalInt rowY = screen(context, access -> access.enchanting2$rowY(id));
		check(rowY.isPresent(), id + " is drawn: " + screen(context, TableScreenAccess::enchanting2$listText));
		click(context, rowsX(context) + 30, rowY.getAsInt());
		context.waitTicks(3);
	}

	private static void clickEnchant(ClientGameTestContext context) {
		click(context, screen(context, TableScreenAccess::enchanting2$leftPos) + TableWindow.BUTTON_X
				+ TableWindow.BUTTON_WIDTH / 2, screen(context, TableScreenAccess::enchanting2$topPos)
						+ TableWindow.BUTTON_Y + TableWindow.BUTTON_HEIGHT / 2);
		context.waitTicks(3);
	}

	private static void setSlots(TestSingleplayerContext world, ItemStack item, int lapis) {
		world.getServer().runOnServer(server -> {
			EnchantmentMenu menu = serverMenu(server);
			menu.getSlot(1).set(lapis == 0 ? ItemStack.EMPTY : new ItemStack(Items.LAPIS_LAZULI, lapis));
			menu.getSlot(0).set(item);
		});
	}

	private static String text(String key, Object... args) {
		return Component.translatable(key, args).getString();
	}

	private static void theWindowIsBiggerWithTheInventoryMoved(ClientGameTestContext context) {
		check(screen(context, TableScreenAccess::enchanting2$ours), "our server, but the screen is vanilla's");
		check(screen(context, TableScreenAccess::enchanting2$imageHeight) == TableWindow.HEIGHT,
				"the window is " + TableWindow.HEIGHT + " tall");
		int slotX = context.computeOnClient(client -> client.player.containerMenu.getSlot(FIRST_INVENTORY_SLOT).x);
		int slotY = context.computeOnClient(client -> client.player.containerMenu.getSlot(FIRST_INVENTORY_SLOT).y);
		check(slotX == TableWindow.inventorySlotX(8) && slotY == TableWindow.inventorySlotY(84),
				"the inventory moved with the grid: " + slotX + ", " + slotY);
		// Owner, 2026-10-13: the client's menu has the same five equipment slots as the server's, where the layout says.
		List<int[]> equipment = context.computeOnClient(client -> client.player.containerMenu.slots.stream()
				.skip(TableEquipmentSlot.FIRST_MENU_INDEX).map(s -> new int[] {s.x, s.y}).toList());
		check(equipment.size() == 5, "armor and offhand slots on the client: " + equipment.size());
		for (int i = 0; i < 4; i++) {
			check(equipment.get(i)[0] == TableWindow.ARMOR_X && equipment.get(i)[1] == TableWindow.armorSlotY(i),
					"armor slot " + i + " at " + equipment.get(i)[0] + ", " + equipment.get(i)[1]);
		}
		check(equipment.get(4)[0] == TableWindow.OFFHAND_X && equipment.get(4)[1] == TableWindow.OFFHAND_Y,
				"offhand at " + equipment.get(4)[0] + ", " + equipment.get(4)[1]);
		int width = context.computeOnClient(client -> client.getWindow().getGuiScaledWidth());
		int left = screen(context, TableScreenAccess::enchanting2$leftPos);
		check(left >= 0 && left + TableWindow.WIDTH <= width, "the window fits " + width + " wide: left " + left);
		int height = context.computeOnClient(client -> client.getWindow().getGuiScaledHeight());
		int top = screen(context, TableScreenAccess::enchanting2$topPos);
		check(top >= 0 && top + TableWindow.HEIGHT <= height, "the window fits " + height + " high: top " + top);
		List<String> window = screen(context, TableScreenAccess::enchanting2$windowText);
		check(window.equals(List.of(text("enchanting2.table.status.empty"), text("enchanting2.table.window.enchant"))),
				"empty table window: " + window);
		check(!screen(context, TableScreenAccess::enchanting2$buttonActive), "Enchant is off with nothing picked");
		context.takeScreenshot("enchanting2-table-empty");
	}

	/** Sword + 64 lapis at 15 shelves: pick Sharpness (at I), step it to V, pick Mending, Enchant for 17 and 17. */
	private static void enchantThroughTheList(ClientGameTestContext context, TestSingleplayerContext world) {
		setSlots(world, new ItemStack(Items.DIAMOND_SWORD), 64);
		context.waitTicks(3);
		int shelves = context.computeOnClient(client -> TablePreviewState
				.forContainer(client.player.containerMenu.containerId).map(TablePreviewPayload::shelves).orElse(-1));
		check(shelves == 15, "15 shelves counted, got " + shelves);
		List<String> list = screen(context, TableScreenAccess::enchanting2$listText);
		check(list.contains("Sharpness") && list.contains("Mending") && !list.contains("Efficiency"),
				"the sword's rows: " + list);
		check(screen(context, TableScreenAccess::enchanting2$windowText).contains(text("enchanting2.table.status.pick")),
				"asks for a pick");
		// Off the list, so the shot doesn't show a hover left by an earlier test (26.3 drew one over Enchant).
		moveTo(context, 0, 0);
		context.waitTick();
		context.takeScreenshot("enchanting2-table-rows");

		scrollIntoView(context, SHARPNESS);
		moveTo(context, rowsX(context) + 30, screen(context, access -> access.enchanting2$rowY(SHARPNESS)).orElseThrow());
		context.waitTicks(2);
		// Owner, 2026-10-13: the description needs Ctrl here too; without it, the hint.
		List<String> plain = screen(context, TableScreenAccess::enchanting2$rowTooltip);
		check(!plain.contains(text("enchantment.minecraft.sharpness.desc"))
				&& plain.contains(text("enchanting2.tooltip.hold_ctrl")), "no description without Ctrl: " + plain);
		context.getInput().holdKey(InputConstants.KEY_LCONTROL);
		context.waitTicks(2);
		List<String> tooltip = screen(context, TableScreenAccess::enchanting2$rowTooltip);
		check(tooltip.contains(text("enchantment.minecraft.sharpness.desc")),
				"the row's tooltip has its description with Ctrl: " + tooltip);
		context.takeScreenshot("enchanting2-table-tooltip");
		context.getInput().releaseKey(InputConstants.KEY_LCONTROL);
		context.waitTick();

		clickRow(context, SHARPNESS);
		check(held(world).equals(TableSelection.EMPTY.with(SHARPNESS, 1)),
				"the click picked Sharpness I on the server (owner 2026-10-13): " + held(world));
		for (int level = 2; level <= 5; level++) {
			clickArrow(context, SHARPNESS, Zone.INCREASE);
		}
		check(held(world).equals(TableSelection.EMPTY.with(SHARPNESS, 5)), "stepped up to V: " + held(world));
		clickRow(context, MENDING);
		TableSelection both = TableSelection.EMPTY.with(SHARPNESS, 5).with(MENDING, 1);
		check(held(world).equals(both), "and Mending: " + held(world));
		List<String> window = screen(context, TableScreenAccess::enchanting2$windowText);
		check(window.contains(text("enchanting2.table.window.levels", 17))
				&& window.contains(text("enchanting2.table.window.lapis", 17)), "17 levels and 17 lapis: " + window);
		check(screen(context, TableScreenAccess::enchanting2$buttonActive), "Enchant is on");
		List<String> breakdown = screen(context, TableScreenAccess::enchanting2$buttonTooltip);
		check(breakdown.contains(text("enchanting2.table.status.ready")), "the button's breakdown says ready: "
				+ breakdown);
		context.takeScreenshot("enchanting2-table-picked");

		clickEnchant(context);
		world.getServer().runOnServer(server -> {
			EnchantmentMenu menu = serverMenu(server);
			ItemStack sword = menu.getSlot(0).getItem();
			check(level(server, sword, Enchantments.SHARPNESS) == 5 && level(server, sword, Enchantments.MENDING) == 1,
					"the sword has Sharpness V and Mending: " + sword);
			check(player(server).experienceLevel == LEVELS - 17, "17 levels paid: " + player(server).experienceLevel);
			check(menu.getSlot(1).getItem().getCount() == 64 - 17, "17 lapis used: " + menu.getSlot(1).getItem());
		});
		check(held(world).equals(TableSelection.EMPTY), "the selection is cleared after enchanting");
		context.takeScreenshot("enchanting2-table-enchanted");
	}

	/** The arrows are on the picked entry's own line, right of its name. */
	private static void clickArrow(ClientGameTestContext context, String id, Zone zone) {
		OptionalInt arrow = screen(context, access -> access.enchanting2$arrowX(id, zone));
		OptionalInt rowY = screen(context, access -> access.enchanting2$rowY(id));
		check(arrow.isPresent() && rowY.isPresent(), "the picked row has a stepper: row " + rowY + ", arrow " + arrow
				+ ", scroll " + screen(context, TableScreenAccess::enchanting2$scrollOffset));
		click(context, arrow.getAsInt(), rowY.getAsInt());
		context.waitTicks(3);
	}

	private static void stepperChangesTheLevel(ClientGameTestContext context, TestSingleplayerContext world) {
		setSlots(world, new ItemStack(Items.IRON_SWORD), 64);
		context.waitTicks(3);
		clickRow(context, SHARPNESS);
		check(held(world).equals(TableSelection.EMPTY.with(SHARPNESS, 1)), "picked at I: " + held(world));
		clickArrow(context, SHARPNESS, Zone.DECREASE);
		check(held(world).equals(TableSelection.EMPTY.with(SHARPNESS, 1)), "never below I: " + held(world));
		clickArrow(context, SHARPNESS, Zone.INCREASE);
		check(held(world).equals(TableSelection.EMPTY.with(SHARPNESS, 2)), "stepped up to II: " + held(world));
		clickArrow(context, SHARPNESS, Zone.DECREASE);
		check(held(world).equals(TableSelection.EMPTY.with(SHARPNESS, 1)), "and back down to I: " + held(world));
		clickRow(context, SHARPNESS);
		check(held(world).equals(TableSelection.EMPTY), "clicking a picked row unpicks it: " + held(world));
	}

	/** Owner, 2026-10-13: the stepper sits on the row, so picking the last visible row leaves it where it was. */
	private static void aPickOnTheLastVisibleRowStaysInView(ClientGameTestContext context,
			TestSingleplayerContext world) {
		setSlots(world, new ItemStack(Items.BOOK), 64);
		context.waitTicks(3);
		scrollToTop(context);
		int listBottom = listTop(context) + TableWindow.LIST_HEIGHT;
		String last = screen(context, access -> access.enchanting2$rowIds().stream()
				.filter(id -> access.enchanting2$rowY(id).isPresent())
				.reduce((first, second) -> second).orElseThrow());
		int lastY = screen(context, access -> access.enchanting2$rowY(last)).orElseThrow();
		check(lastY + TableWindow.LINE > listBottom, last + " is the last line in view: " + lastY);
		click(context, rowsX(context) + 30, lastY);
		context.waitTicks(3);
		check(held(world).levels().containsKey(last), last + " picked: " + held(world));
		OptionalInt after = screen(context, access -> access.enchanting2$rowY(last));
		check(after.equals(OptionalInt.of(lastY)), "it stays in place: " + after + ", was " + lastY);
		check(screen(context, TableScreenAccess::enchanting2$scrollOffset) == 0, "nothing scrolled");
		check(screen(context, access -> access.enchanting2$arrowX(last, Zone.INCREASE)).isPresent(),
				"its stepper is on its line");
		context.takeScreenshot("enchanting2-table-picked-last-row");
		clickRow(context, last);
		check(held(world).equals(TableSelection.EMPTY), "unpicked again: " + held(world));
	}

	private static void bookTakesSeveralAndItsListScrolls(ClientGameTestContext context,
			TestSingleplayerContext world) {
		setSlots(world, new ItemStack(Items.BOOK), 64);
		context.waitTicks(3);
		clickRow(context, AQUA_AFFINITY);
		clickRow(context, EFFICIENCY);
		check(held(world).equals(TableSelection.EMPTY.with(AQUA_AFFINITY, 1).with(EFFICIENCY, 1)),
				"two picks on a book: " + held(world));

		int max = screen(context, TableScreenAccess::enchanting2$maxScroll);
		check(max > 0, "a book's whole list doesn't fit, so it scrolls");
		// Owner, 2026-10-12: rows are drawn by translated name, not in the server's ID order, so the namespaces mix.
		List<String> drawn = screen(context, TableScreenAccess::enchanting2$rowIds);
		List<String> served = context.computeOnClient(client -> TablePreviewState
				.forContainer(client.player.containerMenu.containerId).map(TablePreviewPayload::rows).orElseThrow())
				.stream().map(TableRow::id).toList();
		check(drawn.size() == served.size() && drawn.containsAll(served), "every served row is drawn: " + drawn);
		int lastMod = drawn.stream().filter(id -> id.startsWith("enchanting2:")).mapToInt(drawn::indexOf).max()
				.orElseThrow();
		check(drawn.stream().filter(id -> id.startsWith("minecraft:")).anyMatch(id -> drawn.indexOf(id) < lastMod),
				"mod and vanilla rows interleave by name: " + drawn);
		String last = drawn.getLast();
		scrollToTop(context);
		check(screen(context, access -> access.enchanting2$rowY(last)).isEmpty(), "the last row starts out of view");
		scrollList(context, -notches(context));
		check(screen(context, TableScreenAccess::enchanting2$scrollOffset) == max, "the wheel scrolled to the end");
		check(screen(context, access -> access.enchanting2$rowY(last)).isPresent(), last + " is now drawn");
		context.takeScreenshot("enchanting2-table-book-scrolled");

		// Drag the thumb back up, past the top of the track.
		int barX = screen(context, TableScreenAccess::enchanting2$scrollBarX) + TableWindow.SCROLLBAR_WIDTH / 2;
		moveTo(context, barX, screen(context, TableScreenAccess::enchanting2$thumbCenterY));
		context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTick();
		moveTo(context, barX, listTop(context) - 20);
		context.waitTicks(2);
		context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTick();
		check(screen(context, TableScreenAccess::enchanting2$scrollOffset) == 0,
				"dragging the thumb up scrolled to the top: " + screen(context, TableScreenAccess::enchanting2$scrollOffset));

		clickEnchant(context);
		world.getServer().runOnServer(server -> {
			ItemStack book = serverMenu(server).getSlot(0).getItem();
			check(book.is(Items.ENCHANTED_BOOK) && level(server, book, Enchantments.EFFICIENCY) == 1
					&& level(server, book, Enchantments.AQUA_AFFINITY) == 1, "an enchanted book with both: " + book);
		});
	}

	/** A click on the list's frame is the list's: it does nothing, and the carried item stays carried. */
	private static void listClickKeepsTheCarriedItem(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			EnchantmentMenu menu = serverMenu(server);
			menu.setCarried(new ItemStack(Items.DIAMOND, 7));
			menu.broadcastChanges();
		});
		context.waitTicks(2);
		click(context, rowsX(context) - 1, listTop(context) + 40);
		context.waitTicks(3);
		ItemStack carried = world.getServer().computeOnServer(server -> serverMenu(server).getCarried().copy());
		check(carried.is(Items.DIAMOND) && carried.getCount() == 7, "the carried diamonds were dropped: " + carried);
		world.getServer().runOnServer(server -> {
			EnchantmentMenu menu = serverMenu(server);
			player(server).getInventory().add(menu.getCarried().copy());
			menu.setCarried(ItemStack.EMPTY);
			menu.broadcastChanges();
		});
		context.waitTicks(2);
	}

	private static void closingClearsTheList(ClientGameTestContext context) {
		int containerId = context.computeOnClient(client -> client.player.containerMenu.containerId);
		check(context.computeOnClient(client -> TablePreviewState.forContainer(containerId).isPresent()),
				"a preview is held while open");
		context.runOnClient(client -> client.player.closeContainer());
		context.waitForScreen(null);
		check(context.computeOnClient(client -> TablePreviewState.forContainer(containerId).isEmpty()),
				"closing the table cleared its preview");
	}
}
