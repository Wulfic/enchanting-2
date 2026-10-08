package com.wulfnet.enchanting2.gametest;

import com.wulfnet.enchanting2.client.TablePreviewState;
import com.wulfnet.enchanting2.client.TableScreenAccess;
import com.wulfnet.enchanting2.network.TablePreviewPayload;
import com.wulfnet.enchanting2.screen.PanelText.Zone;
import com.wulfnet.enchanting2.screen.SidePanelLayout;
import com.wulfnet.enchanting2.screen.SidePanelLayout.Placement;
import com.wulfnet.enchanting2.table.TableMenuAccess;
import com.wulfnet.enchanting2.table.TableRow;
import com.wulfnet.enchanting2.table.TableSelection;
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
import org.lwjgl.glfw.GLFW;

/**
 * Stage 7D on the real client: a real table with 15 bookshelves. The panel lists the server's rows, clicks pick and
 * step levels on the server, the window shows the price, Enchant charges exactly that, a plain book takes several
 * enchantments, a book's long list scrolls to its last row, and a click on the panel never throws the carried item.
 * Screenshots land in the client GameTest run dir for the owner to look at.
 */
public class TableScreenClientGameTest implements FabricClientGameTest {
	/** 854x480 at GUI scale auto is 427 wide: neither side fits the panel, so the window shifts. */
	private static final int NARROW_WIDTH = 854;
	private static final int NARROW_HEIGHT = 480;
	private static final int WIDE_WIDTH = 1920;
	private static final int WIDE_HEIGHT = 1080;
	private static final int LEVELS = 40;
	private static final String SHARPNESS = "minecraft:sharpness";
	private static final String MENDING = "minecraft:mending";
	private static final String EFFICIENCY = "minecraft:efficiency";
	/** First in a book's list, so it's on screen before any scrolling. */
	private static final String AQUA_AFFINITY = "minecraft:aqua_affinity";
	/** Vanilla's table window and its third offer row, where Enchant is. */
	private static final int IMAGE_HEIGHT = 166;
	private static final int BUTTON_X = 60 + 54;
	private static final int BUTTON_Y = 14 + 2 * 19 + 9;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getClientWorld().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("xp set @a " + LEVELS + " levels");
			context.getInput().resizeWindow(NARROW_WIDTH, NARROW_HEIGHT);
			world.getServer().runOnServer(TableScreenClientGameTest::openTable);
			context.waitForScreen(EnchantmentScreen.class);
			context.waitTick();

			narrowScreenShiftsTheWindow(context);
			context.getInput().resizeWindow(WIDE_WIDTH, WIDE_HEIGHT);
			context.waitTick();
			enchantThroughThePanel(context, world);
			stepperChangesTheLevel(context, world);
			bookTakesSeveralAndItsListScrolls(context, world);
			panelClickKeepsTheCarriedItem(context, world);
			closingClearsThePanel(context);
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
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
	}

	private static int topPos(ClientGameTestContext context) {
		int height = context.computeOnClient(client -> client.getWindow().getGuiScaledHeight());
		return (height - IMAGE_HEIGHT) / 2;
	}

	private static void clickRow(ClientGameTestContext context, String id) {
		SidePanelLayout layout = screen(context, TableScreenAccess::enchanting2$layout);
		OptionalInt rowY = screen(context, access -> access.enchanting2$rowY(id));
		check(rowY.isPresent(), id + " is drawn: " + screen(context, TableScreenAccess::enchanting2$panelText));
		click(context, layout.panelX() + 10, rowY.getAsInt());
		context.waitTicks(3);
	}

	private static void clickEnchant(ClientGameTestContext context) {
		int leftPos = screen(context, TableScreenAccess::enchanting2$leftPos);
		click(context, leftPos + BUTTON_X, topPos(context) + BUTTON_Y);
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

	private static void narrowScreenShiftsTheWindow(ClientGameTestContext context) {
		SidePanelLayout layout = screen(context, TableScreenAccess::enchanting2$layout);
		check(layout != null, "our server, but the screen has no layout (mixin not applied?)");
		check(layout.placement() == Placement.SHIFTED, "427 wide should shift, got " + layout);
		int width = context.computeOnClient(client -> client.getWindow().getGuiScaledWidth());
		int leftPos = screen(context, TableScreenAccess::enchanting2$leftPos);
		check(leftPos == layout.leftPos() && leftPos > (width - 176) / 2, "window not moved right: " + layout);
		check(layout.panelX() >= 0 && layout.panelX() + layout.panelWidth() + SidePanelLayout.GAP <= leftPos,
				"panel on screen, left of the window: " + layout);
		List<String> window = screen(context, TableScreenAccess::enchanting2$windowText);
		check(window.equals(List.of(text("enchanting2.table.status.empty"), text("enchanting2.table.window.enchant"))),
				"empty table window: " + window);
		context.takeScreenshot("enchanting2-table-shifted-empty");
	}

	/** Sword + 64 lapis at 15 shelves: pick Sharpness V and Mending on the panel, Enchant for 17 and 17. */
	private static void enchantThroughThePanel(ClientGameTestContext context, TestSingleplayerContext world) {
		setSlots(world, new ItemStack(Items.DIAMOND_SWORD), 64);
		context.waitTicks(3);
		SidePanelLayout layout = screen(context, TableScreenAccess::enchanting2$layout);
		check(layout != null && layout.panelShown(), "panel shown at 1920x1080: " + layout);
		int shelves = context.computeOnClient(client -> TablePreviewState
				.forContainer(client.player.containerMenu.containerId).map(TablePreviewPayload::shelves).orElse(-1));
		check(shelves == 15, "15 shelves counted, got " + shelves);
		List<String> panel = screen(context, TableScreenAccess::enchanting2$panelText);
		check(panel.contains("Sharpness") && panel.contains("Mending") && !panel.contains("Efficiency"),
				"the sword's rows: " + panel);
		check(panel.contains(text("enchanting2.table.status.pick")), "asks for a pick: " + panel);
		context.takeScreenshot("enchanting2-table-rows");

		clickRow(context, SHARPNESS);
		check(held(world).equals(TableSelection.EMPTY.with(SHARPNESS, 5)), "the click picked Sharpness V on the server: "
				+ held(world));
		clickRow(context, MENDING);
		TableSelection both = TableSelection.EMPTY.with(SHARPNESS, 5).with(MENDING, 1);
		check(held(world).equals(both), "and Mending: " + held(world));
		List<String> window = screen(context, TableScreenAccess::enchanting2$windowText);
		check(window.contains(text("enchanting2.table.window.levels", 17))
				&& window.contains(text("enchanting2.table.window.lapis", 17)), "17 levels and 17 lapis: " + window);
		check(screen(context, TableScreenAccess::enchanting2$panelText)
				.contains(text("enchanting2.table.status.ready")), "ready");
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

	private static void stepperChangesTheLevel(ClientGameTestContext context, TestSingleplayerContext world) {
		setSlots(world, new ItemStack(Items.IRON_SWORD), 64);
		context.waitTicks(3);
		clickRow(context, SHARPNESS);
		check(held(world).equals(TableSelection.EMPTY.with(SHARPNESS, 5)), "picked at V: " + held(world));
		OptionalInt down = screen(context, access -> access.enchanting2$arrowX(SHARPNESS, Zone.DECREASE));
		OptionalInt rowY = screen(context, access -> access.enchanting2$rowY(SHARPNESS));
		check(down.isPresent() && rowY.isPresent(), "the picked row has a stepper");
		click(context, down.getAsInt(), rowY.getAsInt());
		context.waitTicks(3);
		check(held(world).equals(TableSelection.EMPTY.with(SHARPNESS, 4)), "stepped down to IV: " + held(world));
		OptionalInt up = screen(context, access -> access.enchanting2$arrowX(SHARPNESS, Zone.INCREASE));
		click(context, up.orElseThrow(), screen(context, access -> access.enchanting2$rowY(SHARPNESS)).orElseThrow());
		context.waitTicks(3);
		check(held(world).equals(TableSelection.EMPTY.with(SHARPNESS, 5)), "and back up to V: " + held(world));
		clickRow(context, SHARPNESS);
		check(held(world).equals(TableSelection.EMPTY), "clicking a picked row unpicks it: " + held(world));
	}

	private static void bookTakesSeveralAndItsListScrolls(ClientGameTestContext context,
			TestSingleplayerContext world) {
		setSlots(world, new ItemStack(Items.BOOK), 64);
		context.waitTicks(3);
		clickRow(context, AQUA_AFFINITY);
		clickRow(context, EFFICIENCY);
		check(held(world).equals(TableSelection.EMPTY.with(AQUA_AFFINITY, 1).with(EFFICIENCY, 5)),
				"two picks on a book: " + held(world));

		int max = screen(context, TableScreenAccess::enchanting2$maxScroll);
		check(max > 0, "a book's whole list doesn't fit, so it scrolls");
		List<TableRow> rows = context.computeOnClient(client -> TablePreviewState
				.forContainer(client.player.containerMenu.containerId).map(TablePreviewPayload::rows).orElseThrow());
		String last = rows.getLast().id();
		check(screen(context, access -> access.enchanting2$rowY(last)).isEmpty(), "the last row starts out of view");
		SidePanelLayout layout = screen(context, TableScreenAccess::enchanting2$layout);
		moveTo(context, layout.panelX() + 20, topPos(context) + 40);
		for (int i = 0; i < max && screen(context, TableScreenAccess::enchanting2$scrollOffset) < max; i++) {
			context.getInput().scroll(-1);
			context.waitTick();
		}
		check(screen(context, TableScreenAccess::enchanting2$scrollOffset) == max, "scrolled to the end");
		check(screen(context, access -> access.enchanting2$rowY(last)).isPresent(), last + " is now drawn");
		context.takeScreenshot("enchanting2-table-book-scrolled");

		clickEnchant(context);
		world.getServer().runOnServer(server -> {
			ItemStack book = serverMenu(server).getSlot(0).getItem();
			check(book.is(Items.ENCHANTED_BOOK) && level(server, book, Enchantments.EFFICIENCY) == 5
					&& level(server, book, Enchantments.AQUA_AFFINITY) == 1, "an enchanted book with both: " + book);
		});
	}

	/** Vanilla would count a click beside the window as outside and throw the carried item; the panel isn't outside. */
	private static void panelClickKeepsTheCarriedItem(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			EnchantmentMenu menu = serverMenu(server);
			menu.setCarried(new ItemStack(Items.DIAMOND, 7));
			menu.broadcastChanges();
		});
		context.waitTicks(2);
		SidePanelLayout layout = screen(context, TableScreenAccess::enchanting2$layout);
		// The title line: on the panel, not a row.
		click(context, layout.panelX() + 10, topPos(context) + 6);
		context.waitTicks(3);
		ItemStack carried = world.getServer().computeOnServer(server -> serverMenu(server).getCarried().copy());
		check(carried.is(Items.DIAMOND) && carried.getCount() == 7, "the carried diamonds were thrown: " + carried);
		world.getServer().runOnServer(server -> {
			EnchantmentMenu menu = serverMenu(server);
			player(server).getInventory().add(menu.getCarried().copy());
			menu.setCarried(ItemStack.EMPTY);
			menu.broadcastChanges();
		});
		context.waitTicks(2);
	}

	private static void closingClearsThePanel(ClientGameTestContext context) {
		int containerId = context.computeOnClient(client -> client.player.containerMenu.containerId);
		check(context.computeOnClient(client -> TablePreviewState.forContainer(containerId).isPresent()),
				"a panel is held while open");
		context.runOnClient(client -> client.player.closeContainer());
		context.waitForScreen(null);
		check(context.computeOnClient(client -> TablePreviewState.forContainer(containerId).isEmpty()),
				"closing the table cleared its panel");
	}
}
