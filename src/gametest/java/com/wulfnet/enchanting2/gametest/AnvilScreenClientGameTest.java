package com.wulfnet.enchanting2.gametest;

import com.wulfnet.enchanting2.client.AnvilPanelTab;
import com.wulfnet.enchanting2.client.AnvilPreviewState;
import com.wulfnet.enchanting2.client.AnvilScreenAccess;
import com.wulfnet.enchanting2.client.ClientSettings;
import com.wulfnet.enchanting2.config.ClientConfig;
import com.wulfnet.enchanting2.config.CommonConfig;
import com.wulfnet.enchanting2.config.ConfigFiles;
import com.wulfnet.enchanting2.config.CostsSection;
import com.wulfnet.enchanting2.config.ServerConfigs;
import com.wulfnet.enchanting2.screen.SidePanelLayout.Placement;
import com.wulfnet.enchanting2.screen.SidePanelLayout;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.lwjgl.glfw.GLFW;

/**
 * Stage 6D on the real client: the anvil screen's label and panel come from the server's preview, the tab saves
 * {@code client.json5}, the window shifts when the screen is narrow, and a click on the panel never throws the carried
 * item. Screenshots land in the client GameTest run dir for the owner to look at.
 */
public class AnvilScreenClientGameTest implements FabricClientGameTest {
	/** 854x480 at GUI scale auto is 427 wide (scale 2): the window must shift. 1920x1080 at scale 4 is 480: left. */
	private static final int NARROW_WIDTH = 854;
	private static final int NARROW_HEIGHT = 480;
	private static final int WIDE_WIDTH = 1920;
	private static final int WIDE_HEIGHT = 1080;
	/** Past the 32,767 the vanilla cost slot syncs as. */
	private static final int HUGE_RENAME_COST = 40_000;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getClientWorld().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("xp set @a 30 levels");
			context.getInput().resizeWindow(NARROW_WIDTH, NARROW_HEIGHT);
			world.getServer().runOnServer(AnvilScreenClientGameTest::openAnvil);
			context.waitForScreen(AnvilScreen.class);
			context.waitTick();

			narrowScreenShiftsTheWindow(context);
			bookShowsPriceAndSkippedEntry(context, world);
			unaffordableShowsCompactLabelAndFullReason(context, world);
			panelClickKeepsTheCarriedItem(context, world);
			tabHidesPanelAndSavesClientConfig(context);
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

	private static void narrowScreenShiftsTheWindow(ClientGameTestContext context) {
		SidePanelLayout layout = screen(context, AnvilScreenAccess::enchanting2$layout);
		check(layout != null, "our server, but the screen has no layout (mixin not applied?)");
		check(layout.placement() == Placement.SHIFTED, "427 wide should shift, got " + layout);
		int width = context.computeOnClient(client -> client.getWindow().getGuiScaledWidth());
		check(layout.leftPos() > (width - 176) / 2, "window not moved right: " + layout);
		check(layout.panelX() >= 0, "panel off screen: " + layout);
		int leftPos = screen(context, AnvilScreenAccess::enchanting2$leftPos);
		int nameX = screen(context, AnvilScreenAccess::enchanting2$nameBoxX);
		check(leftPos == layout.leftPos(), "leftPos " + leftPos + " vs layout " + layout.leftPos());
		check(nameX == leftPos + 62, "rename box at " + nameX + ", window at " + leftPos);
		check(screen(context, AnvilScreenAccess::enchanting2$panelText)
				.equals(List.of(Component.translatable("enchanting2.anvil.panel.empty").getString())),
				"empty anvil panel: " + screen(context, AnvilScreenAccess::enchanting2$panelText));
		context.takeScreenshot("enchanting2-anvil-shifted-empty");
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
		context.waitTicks(2);
		SidePanelLayout layout = screen(context, AnvilScreenAccess::enchanting2$layout);
		check(layout != null && layout.placement() == Placement.LEFT, "1920x1080 should place the panel left: " + layout);
		int total = total(context);
		check(total > 0, "a priced preview arrived");
		String label = screen(context, AnvilScreenAccess::enchanting2$labelText);
		check(Component.translatable("container.repair.cost", total).getString().equals(label),
				"label '" + label + "' for total " + total);
		labelClearsTheInventoryLabel(context);
		List<String> panel = screen(context, AnvilScreenAccess::enchanting2$panelText);
		check(panel.stream().anyMatch(t -> t.contains("Silk Touch") && t.contains("Diamond Sword")),
				"Silk Touch's reason names it and the item: " + panel);
		check(panel.contains(Component.translatable("enchanting2.anvil.fate.keep_on_book").getString()),
				"Silk Touch stays on the book: " + panel);
		check(panel.stream().anyMatch(t -> t.startsWith("Sharpness")), "Sharpness line: " + panel);
		context.takeScreenshot("enchanting2-anvil-panel-left");
	}

	private static int total(ClientGameTestContext context) {
		return context.computeOnClient(client -> AnvilPreviewState
				.forContainer(client.player.containerMenu.containerId)
				.flatMap(p -> p.breakdown()).map(b -> b.total()).orElse(-1));
	}

	/** The full sentence doesn't fit the label, so the label falls back to vanilla's red cost; the panel has it all. */
	private static void unaffordableShowsCompactLabelAndFullReason(ClientGameTestContext context,
			TestSingleplayerContext world) {
		world.getServer().runCommand("xp set @a 0 levels");
		context.waitTicks(2);
		int total = total(context);
		String label = screen(context, AnvilScreenAccess::enchanting2$labelText);
		check(Component.translatable("container.repair.cost", total).getString().equals(label),
				"compact label '" + label + "'");
		String insufficient = Component.translatable("enchanting2.anvil.label.insufficient", total, 0).getString();
		List<String> panel = screen(context, AnvilScreenAccess::enchanting2$panelText);
		check(panel.contains(insufficient), "panel says '" + insufficient + "': " + panel);
		world.getServer().runCommand("xp set @a 30 levels");
		context.waitTicks(2);
	}

	/** Vanilla would count a click beside the window as outside and throw the carried item; the panel isn't outside. */
	private static void panelClickKeepsTheCarriedItem(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			AnvilMenu menu = serverMenu(server);
			menu.setCarried(new ItemStack(Items.DIAMOND, 3));
			menu.broadcastChanges();
		});
		context.waitTicks(2);
		SidePanelLayout layout = screen(context, AnvilScreenAccess::enchanting2$layout);
		int top = context.computeOnClient(client -> (client.getWindow().getGuiScaledHeight() - 166) / 2);
		// Inside the panel, below the title line.
		click(context, layout.panelX() + 10, top + 30);
		context.waitTicks(2);
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

	private static void tabHidesPanelAndSavesClientConfig(ClientGameTestContext context) {
		check(ClientSettings.get().showAnvilPreviewPanel(), "panel shown by default");
		clickTab(context);
		SidePanelLayout layout = screen(context, AnvilScreenAccess::enchanting2$layout);
		int width = context.computeOnClient(client -> client.getWindow().getGuiScaledWidth());
		check(layout.placement() == Placement.HIDDEN, "tab click hides the panel: " + layout);
		check(layout.leftPos() == (width - 176) / 2, "hidden panel centres the window: " + layout);
		check(screen(context, AnvilScreenAccess::enchanting2$nameBoxX) == layout.leftPos() + 62, "rename box moved back");
		check(screen(context, AnvilScreenAccess::enchanting2$panelText).isEmpty(), "hidden panel draws nothing");
		check(!ClientSettings.get().showAnvilPreviewPanel(), "setting is off");
		ClientConfig onDisk = ConfigFiles.load(ClientSettings.file(), ClientConfig.CODEC).config();
		check(!onDisk.showAnvilPreviewPanel(), "client.json5 says false");
		AnvilPanelTab tab = screen(context, AnvilScreenAccess::enchanting2$tab);
		check(tab != null && !tab.open(), "tab shows closed");
		context.takeScreenshot("enchanting2-anvil-panel-hidden");

		clickTab(context);
		check(screen(context, AnvilScreenAccess::enchanting2$layout).panelShown(), "second click shows it again");
		check(ConfigFiles.load(ClientSettings.file(), ClientConfig.CODEC).config().showAnvilPreviewPanel(),
				"client.json5 says true again");
	}

	private static void clickTab(ClientGameTestContext context) {
		AnvilPanelTab tab = screen(context, AnvilScreenAccess::enchanting2$tab);
		check(tab != null, "our server, but no tab");
		click(context, tab.getX() + tab.getWidth() / 2, tab.getY() + tab.getHeight() / 2);
		context.waitTick();
	}

	/** {@code x, y} in GUI coordinates; the input API takes window pixels. */
	private static void click(ClientGameTestContext context, int x, int y) {
		double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
		context.getInput().setCursorPos((x + 0.5) * scale, (y + 0.5) * scale);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
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
						c.maximumOperationCost(), c.creativeFree()), defaults.curses(), defaults.anvil());
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
			context.waitTicks(3);
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
