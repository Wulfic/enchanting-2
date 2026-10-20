package com.wulfnet.enchanting2.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import com.wulfnet.enchanting2.client.AnvilPreviewState;
import com.wulfnet.enchanting2.client.AnvilScreenAccess;
import com.wulfnet.enchanting2.config.CommonConfig;
import com.wulfnet.enchanting2.config.ConfigFiles;
import com.wulfnet.enchanting2.config.CostsSection;
import com.wulfnet.enchanting2.config.LastStandSection;
import com.wulfnet.enchanting2.config.ServerConfigs;
import com.wulfnet.enchanting2.screen.ColumnWindow;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * The anvil screen on the real client (Stage 6D; in the window since TODO 10o): the window is wide and centred even on
 * a small screen, the label and the breakdown box come from the server's preview, a long breakdown scrolls by wheel and
 * by thumb, and a click in the box never throws the carried item. Screenshots land in the client GameTest run dir for
 * the owner to look at.
 */
public class AnvilScreenClientGameTest implements FabricClientGameTest {
	/** 854x480 at GUI scale auto is 427 x 240 (scale 2): the 296 wide window fits. 1920x1080 at scale 4 is 480. */
	private static final int NARROW_WIDTH = 854;
	private static final int NARROW_HEIGHT = 480;
	private static final int WIDE_WIDTH = 1920;
	private static final int WIDE_HEIGHT = 1080;
	/** Past the 32,767 the vanilla cost slot syncs as. */
	private static final int HUGE_RENAME_COST = 40_000;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			ClientWorlds.waitForChunksRender(world);
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("xp set @a 30 levels");
			context.getInput().resizeWindow(NARROW_WIDTH, NARROW_HEIGHT);
			world.getServer().runOnServer(AnvilScreenClientGameTest::openAnvil);
			context.waitForScreen(AnvilScreen.class);
			context.waitTick();

			wideWindowFitsTheSmallScreenCentred(context);
			bookShowsPriceAndSkippedEntry(context, world);
			unaffordableShowsCompactLabelAndFullReason(context, world);
			longBreakdownScrolls(context, world);
			boxClickKeepsTheCarriedItem(context, world);
			totalPastShortRangeIsExact(context, world);
			closingClearsThePreview(context);
		}
	}

	private static void openAnvil(MinecraftServer server) {
		ServerPlayer player = player(server);
		player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new AnvilMenu(id, inventory,
				ContainerLevelAccess.NULL), Component.translatable("container.repair")));
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static AnvilMenu serverMenu(MinecraftServer server) {
		if (player(server).containerMenu instanceof AnvilMenu menu) {
			return menu;
		}
		throw new AssertionError("the server player has no anvil open");
	}

	private static <T> T screen(ClientGameTestContext context, Function<AnvilScreenAccess, T> read) {
		return context.computeOnClient(client -> {
			if (client.screen instanceof AnvilScreenAccess access) {
				return read.apply(access);
			}
			throw new AssertionError("expected the anvil screen, found " + client.screen);
		});
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError(what);
		}
	}

	private static void wideWindowFitsTheSmallScreenCentred(ClientGameTestContext context) {
		check(screen(context, AnvilScreenAccess::enchanting2$wide), "our server, but the window isn't wide (mixin?)");
		check(screen(context, AnvilScreenAccess::enchanting2$imageWidth) == ColumnWindow.WIDTH, "296 wide");
		int width = context.computeOnClient(client -> client.getWindow().getGuiScaledWidth());
		int height = context.computeOnClient(client -> client.getWindow().getGuiScaledHeight());
		int leftPos = screen(context, AnvilScreenAccess::enchanting2$leftPos);
		check(leftPos == (width - ColumnWindow.WIDTH) / 2 && leftPos >= 0, "centred at " + leftPos + " on " + width);
		int nameX = screen(context, AnvilScreenAccess::enchanting2$nameBoxX);
		check(nameX == leftPos + 62, "rename box at " + nameX + ", window at " + leftPos);
		int topPos = (height - ColumnWindow.HEIGHT) / 2;
		check(screen(context, AnvilScreenAccess::enchanting2$boxLeft) == leftPos + ColumnWindow.BOX.left()
				&& screen(context, AnvilScreenAccess::enchanting2$boxTop) == topPos + ColumnWindow.BOX_TOP,
				"the box sits in the right column");
		check(screen(context, AnvilScreenAccess::enchanting2$boxText)
				.equals(List.of(Component.translatable("enchanting2.anvil.panel.empty").getString())),
				"empty anvil box: " + screen(context, AnvilScreenAccess::enchanting2$boxText));
		context.takeScreenshot("enchanting2-anvil-empty");
	}

	/** Sword + book (Sharpness V, Silk Touch): Sharpness is priced, Silk Touch isn't applicable and stays on the book. */
	private static void bookShowsPriceAndSkippedEntry(ClientGameTestContext context, TestSingleplayerContext world) {
		context.getInput().resizeWindow(WIDE_WIDTH, WIDE_HEIGHT);
		world.getServer().runOnServer(server -> {
			AnvilMenu menu = serverMenu(server);
			var registry = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
			ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
			ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
			stored.set(registry.getOrThrow(Enchantments.SHARPNESS), 5);
			stored.set(registry.getOrThrow(Enchantments.SILK_TOUCH), 1);
			EnchantmentHelper.setEnchantments(book, stored.toImmutable());
			menu.getSlot(AnvilMenu.INPUT_SLOT).set(new ItemStack(Items.DIAMOND_SWORD));
			menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(book);
		});
		Delivery.toClient(context);
		int total = total(context);
		check(total > 0, "a priced preview arrived");
		String label = screen(context, AnvilScreenAccess::enchanting2$labelText);
		check(Component.translatable("container.repair.cost", total).getString().equals(label),
				"label '" + label + "' for total " + total);
		labelClearsTheInventoryLabel(context);
		List<String> panel = screen(context, AnvilScreenAccess::enchanting2$boxText);
		check(panel.stream().anyMatch(t -> t.contains("Silk Touch") && t.contains("Diamond Sword")),
				"Silk Touch's reason names it and the item: " + panel);
		check(panel.contains(Component.translatable("enchanting2.anvil.fate.keep_on_book").getString()),
				"Silk Touch stays on the book: " + panel);
		check(panel.stream().anyMatch(t -> t.startsWith("Sharpness")), "Sharpness line: " + panel);
		// Owner, 2026-10-13: a bare number read as confusing; the total says "levels".
		List<String> amounts = screen(context, AnvilScreenAccess::enchanting2$boxAmounts);
		check(amounts.contains(Component.translatable("enchanting2.amount.levels", total).getString()),
				"the total carries its unit: " + amounts);
		check(screen(context, AnvilScreenAccess::enchanting2$maxScroll) == 0, "a short breakdown fits");
		context.takeScreenshot("enchanting2-anvil-breakdown");
	}

	private static int total(ClientGameTestContext context) {
		return context.computeOnClient(client -> AnvilPreviewState
				.forContainer(client.player.containerMenu.containerId)
				.flatMap(p -> p.breakdown()).map(b -> b.total()).orElse(-1));
	}

	/** The full sentence doesn't fit the label, so the label falls back to vanilla's red cost; the box has it all. */
	private static void unaffordableShowsCompactLabelAndFullReason(ClientGameTestContext context,
			TestSingleplayerContext world) {
		world.getServer().runCommand("xp set @a 0 levels");
		Delivery.toClient(context);
		int total = total(context);
		String label = screen(context, AnvilScreenAccess::enchanting2$labelText);
		check(Component.translatable("container.repair.cost", total).getString().equals(label),
				"compact label '" + label + "'");
		String insufficient = Component.translatable("enchanting2.anvil.label.insufficient", total, 0).getString();
		List<String> panel = screen(context, AnvilScreenAccess::enchanting2$boxText);
		check(panel.contains(insufficient), "box says '" + insufficient + "': " + panel);
		world.getServer().runCommand("xp set @a 30 levels");
		Delivery.toClient(context);
	}

	/**
	 * A book of armour and tool enchantments on a sword: Sharpness applies, and each of the rest gets a reason and a
	 * fate in the box, far more lines than it holds. The wheel scrolls to the end, the thumb drags back to the top.
	 */
	private static void longBreakdownScrolls(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			AnvilMenu menu = serverMenu(server);
			Registry<Enchantment> registry = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
			ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
			stored.set(registry.getOrThrow(Enchantments.SHARPNESS), 5);
			for (ResourceKey<Enchantment> key : List.of(Enchantments.PROTECTION, Enchantments.FEATHER_FALLING,
					Enchantments.RESPIRATION, Enchantments.AQUA_AFFINITY, Enchantments.DEPTH_STRIDER,
					Enchantments.EFFICIENCY, Enchantments.SILK_TOUCH, Enchantments.POWER, Enchantments.LURE)) {
				stored.set(registry.getOrThrow(key), 1);
			}
			ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
			EnchantmentHelper.setEnchantments(book, stored.toImmutable());
			player(server).getInventory().add(menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem().copy());
			menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(book);
		});
		Delivery.toClient(context);
		int max = screen(context, AnvilScreenAccess::enchanting2$maxScroll);
		check(max > 0, "nine skipped entries don't fit: " + screen(context, AnvilScreenAccess::enchanting2$boxText));
		check(screen(context, AnvilScreenAccess::enchanting2$scrollOffset) == 0, "starts at the top");

		int boxLeft = screen(context, AnvilScreenAccess::enchanting2$boxLeft);
		int boxTop = screen(context, AnvilScreenAccess::enchanting2$boxTop);
		moveTo(context, boxLeft + 20, boxTop + 40);
		for (int i = 0; i <= max / 10 && screen(context, AnvilScreenAccess::enchanting2$scrollOffset) < max; i++) {
			context.getInput().scroll(-1);
			context.waitTick();
		}
		check(screen(context, AnvilScreenAccess::enchanting2$scrollOffset) == max, "the wheel scrolled to the end");
		context.takeScreenshot("enchanting2-anvil-scrolled");

		int barX = screen(context, AnvilScreenAccess::enchanting2$scrollBarX) + ColumnWindow.SCROLLBAR_WIDTH / 2;
		moveTo(context, barX, screen(context, AnvilScreenAccess::enchanting2$thumbCenterY));
		context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTick();
		moveTo(context, barX, boxTop - 20);
		context.waitTicks(2);
		context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTick();
		check(screen(context, AnvilScreenAccess::enchanting2$scrollOffset) == 0,
				"the thumb dragged up to the top: " + screen(context, AnvilScreenAccess::enchanting2$scrollOffset));
	}

	/** The box is inside the window, so vanilla never counts a click on it as outside and throws the carried item. */
	private static void boxClickKeepsTheCarriedItem(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			AnvilMenu menu = serverMenu(server);
			menu.setCarried(new ItemStack(Items.DIAMOND, 3));
			menu.broadcastChanges();
		});
		Delivery.toClient(context);
		click(context, screen(context, AnvilScreenAccess::enchanting2$boxLeft) + 10,
				screen(context, AnvilScreenAccess::enchanting2$boxTop) + 30);
		Delivery.toServer(context);
		int carried = world.getServer().computeOnServer(server -> serverMenu(server).getCarried().getCount());
		check(carried == 3, "the carried diamonds were thrown: " + carried + " left");
		world.getServer().runOnServer(server -> {
			AnvilMenu menu = serverMenu(server);
			player(server).getInventory().add(menu.getCarried());
			menu.setCarried(ItemStack.EMPTY);
			menu.broadcastChanges();
		});
		context.waitTicks(2);
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

	/**
	 * Rename only, at a cost past the short: the label shows the exact total, while the vanilla cost slot (a short)
	 * arrived wrapped. The client's take prediction uses the exact total too.
	 */
	private static void totalPastShortRangeIsExact(ClientGameTestContext context, TestSingleplayerContext world) {
		CommonConfig defaults = CommonConfig.defaults();
		CostsSection c = defaults.costs();
		CommonConfig huge = new CommonConfig(defaults.experience(), defaults.allowTogether(),
				new CostsSection(HUGE_RENAME_COST, c.repairCostPerMaterial(), c.repairCombineCost(),
						c.maximumOperationCost(), c.creativeFree()), defaults.curses(), defaults.anvil(),
				defaults.table(), defaults.fortune(), defaults.beheading(), defaults.smelting(), defaults.silkTouch(), LastStandSection.DEFAULTS);
		check(ConfigFiles.saveWithBackup(ServerConfigs.commonFile(), CommonConfig.CODEC, huge)
				== ConfigFiles.SaveOutcome.SAVED, "test config written");
		try {
			world.getServer().runCommand("reload");
			world.getServer().runCommand("xp set @a 30000 levels");
			world.getServer().runOnServer(server -> {
				AnvilMenu menu = serverMenu(server);
				player(server).getInventory().add(menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem().copy());
				menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(ItemStack.EMPTY);
				menu.setItemName("Renamed");
			});
			Delivery.toClient(context);
			check(total(context) == HUGE_RENAME_COST, "preview total " + total(context));
			int syncedCost = context.computeOnClient(client -> ((AnvilMenu) client.player.containerMenu).getCost());
			check(syncedCost != HUGE_RENAME_COST, "the short cost slot wrapped (" + syncedCost + "), as expected");
			String label = screen(context, AnvilScreenAccess::enchanting2$labelText);
			check(Component.translatable("enchanting2.anvil.label.cost_short", HUGE_RENAME_COST).getString()
					.equals(label), "label shows the exact total in its short form: " + label);
			labelClearsTheInventoryLabel(context);
			boolean mayTake = context.computeOnClient(client -> client.player.containerMenu
					.getSlot(AnvilMenu.RESULT_SLOT).mayPickup(client.player));
			check(!mayTake, "30,000 levels can't pay 40,000 (the wrapped short would have said yes)");
			context.takeScreenshot("enchanting2-anvil-40000");
		} finally {
			check(ConfigFiles.saveWithBackup(ServerConfigs.commonFile(), CommonConfig.CODEC, defaults)
					== ConfigFiles.SaveOutcome.SAVED, "default config restored");
			world.getServer().runCommand("reload");
		}
	}

	/** The label shares a row with vanilla's "Inventory" label (x 8); a long one must not run over it. */
	private static void labelClearsTheInventoryLabel(ClientGameTestContext context) {
		int labelX = screen(context, AnvilScreenAccess::enchanting2$labelX);
		int inventoryEnd = context.computeOnClient(
				client -> 8 + client.font.width(Component.translatable("container.inventory")));
		check(labelX - 2 > inventoryEnd, "label background at " + (labelX - 2) + " overlaps Inventory ending at "
				+ inventoryEnd);
	}

	private static void closingClearsThePreview(ClientGameTestContext context) {
		int containerId = context.computeOnClient(client -> client.player.containerMenu.containerId);
		check(context.computeOnClient(client -> AnvilPreviewState.forContainer(containerId).isPresent()),
				"a preview is held while open");
		context.runOnClient(client -> client.player.closeContainer());
		context.waitForScreen(null);
		check(context.computeOnClient(client -> AnvilPreviewState.forContainer(containerId).isEmpty()),
				"closing the anvil cleared its preview");
	}
}
