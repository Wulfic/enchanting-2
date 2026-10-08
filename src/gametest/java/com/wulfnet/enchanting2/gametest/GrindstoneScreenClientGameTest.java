package com.wulfnet.enchanting2.gametest;

import com.wulfnet.enchanting2.client.GrindstoneOptionsState;
import com.wulfnet.enchanting2.client.GrindstoneScreenAccess;
import com.wulfnet.enchanting2.grindstone.GrindstoneAction;
import com.wulfnet.enchanting2.grindstone.GrindstoneChoice;
import com.wulfnet.enchanting2.grindstone.GrindstoneMenuAccess;
import com.wulfnet.enchanting2.grindstone.GrindstoneOption;
import com.wulfnet.enchanting2.screen.SidePanelLayout.Placement;
import com.wulfnet.enchanting2.screen.SidePanelLayout;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.GrindstoneScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.lwjgl.glfw.GLFW;

/**
 * Stage 6bC on the real client: the grindstone's picker panel lists the server's rows, a click on a row picks it, the
 * take moves the enchantment, a curse gets a Cleanse row with its price, and a click on the panel never throws the
 * carried item. Screenshots land in the client GameTest run dir for the owner to look at.
 */
public class GrindstoneScreenClientGameTest implements FabricClientGameTest {
	/** 854x480 at GUI scale auto is 427 wide: neither side fits the panel, so the window shifts. */
	private static final int NARROW_WIDTH = 854;
	private static final int NARROW_HEIGHT = 480;
	private static final int WIDE_WIDTH = 1920;
	private static final int WIDE_HEIGHT = 1080;
	private static final int LEVELS = 30;
	private static final GrindstoneChoice EXTRACT_SHARPNESS = GrindstoneChoice.of(GrindstoneAction.EXTRACT,
			"minecraft:sharpness");
	/** Vanilla's grindstone window and result slot. */
	private static final int IMAGE_HEIGHT = 166;
	private static final int RESULT_X = 129;
	private static final int RESULT_Y = 34;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getClientWorld().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("xp set @a " + LEVELS + " levels");
			context.getInput().resizeWindow(NARROW_WIDTH, NARROW_HEIGHT);
			world.getServer().runOnServer(GrindstoneScreenClientGameTest::openGrindstone);
			context.waitForScreen(GrindstoneScreen.class);
			context.waitTick();

			narrowScreenShiftsTheWindow(context);
			context.getInput().resizeWindow(WIDE_WIDTH, WIDE_HEIGHT);
			context.waitTick();
			extractThroughThePanel(context, world);
			cursedItemGetsACleanseRow(context, world);
			panelClickKeepsTheCarriedItem(context, world);
			closingClearsThePanel(context);
		}
	}

	private static void openGrindstone(MinecraftServer server) {
		player(server).openMenu(new SimpleMenuProvider((id, inventory, p) -> new GrindstoneMenu(id, inventory,
				ContainerLevelAccess.NULL), Component.translatable("container.grindstone_title")));
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static GrindstoneMenu serverMenu(MinecraftServer server) {
		if (player(server).containerMenu instanceof GrindstoneMenu menu) {
			return menu;
		}
		throw new AssertionError("the server player has no grindstone open");
	}

	private static ItemStack with(MinecraftServer server, Item item, Map<ResourceKey<Enchantment>, Integer> levels) {
		var registry = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		ItemStack stack = new ItemStack(item);
		ItemEnchantments.Mutable m = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		levels.forEach((key, level) -> m.set(registry.getOrThrow(key), level));
		EnchantmentHelper.setEnchantments(stack, m.toImmutable());
		return stack;
	}

	private static int level(MinecraftServer server, ItemStack stack, ResourceKey<Enchantment> key) {
		return EnchantmentHelper.getEnchantmentsForCrafting(stack)
				.getLevel(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key));
	}

	private static <T> T screen(ClientGameTestContext context, Function<GrindstoneScreenAccess, T> read) {
		return context.computeOnClient(client -> {
			if (client.screen instanceof GrindstoneScreenAccess access) {
				return read.apply(access);
			}
			throw new AssertionError("expected the grindstone screen, found " + client.screen);
		});
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError(what);
		}
	}

	/** {@code x, y} in GUI coordinates; the input API takes window pixels. */
	private static void click(ClientGameTestContext context, int x, int y) {
		double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
		context.getInput().setCursorPos((x + 0.5) * scale, (y + 0.5) * scale);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
	}

	private static void narrowScreenShiftsTheWindow(ClientGameTestContext context) {
		SidePanelLayout layout = screen(context, GrindstoneScreenAccess::enchanting2$layout);
		check(layout != null, "our server, but the screen has no layout (mixin not applied?)");
		check(layout.placement() == Placement.SHIFTED, "427 wide should shift, got " + layout);
		int width = context.computeOnClient(client -> client.getWindow().getGuiScaledWidth());
		int leftPos = screen(context, GrindstoneScreenAccess::enchanting2$leftPos);
		check(leftPos == layout.leftPos() && leftPos > (width - 176) / 2, "window not moved right: " + layout);
		check(layout.panelX() >= 0 && layout.panelX() + layout.panelWidth() + SidePanelLayout.GAP <= leftPos,
				"panel on screen, left of the window: " + layout);
		List<String> panel = screen(context, GrindstoneScreenAccess::enchanting2$panelText);
		check(panel.equals(List.of(Component.translatable("enchanting2.grindstone.status.empty").getString())),
				"empty grindstone panel: " + panel);
		context.takeScreenshot("enchanting2-grindstone-shifted-empty");
	}

	/** Sword (Sharpness V, Unbreaking III) + 3 books: pick Sharpness on the panel, take the book. */
	private static void extractThroughThePanel(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			GrindstoneMenu menu = serverMenu(server);
			menu.getSlot(GrindstoneMenu.INPUT_SLOT).set(with(server, Items.DIAMOND_SWORD,
					Map.of(Enchantments.SHARPNESS, 5, Enchantments.UNBREAKING, 3)));
			menu.getSlot(GrindstoneMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.BOOK, 3));
		});
		context.waitTicks(2);
		SidePanelLayout layout = screen(context, GrindstoneScreenAccess::enchanting2$layout);
		check(layout != null && layout.placement() == Placement.LEFT, "1920x1080 should place the panel left: "
				+ layout);
		List<String> panel = screen(context, GrindstoneScreenAccess::enchanting2$panelText);
		check(panel.contains("Sharpness V") && panel.contains("Unbreaking III"), "a row per enchantment: " + panel);
		check(panel.contains(Component.translatable("enchanting2.grindstone.status.pick").getString()),
				"asks for a pick: " + panel);
		check(resultOnClient(context).isEmpty(), "no result before a pick");
		context.takeScreenshot("enchanting2-grindstone-rows");

		OptionalInt rowY = screen(context, access -> access.enchanting2$rowY(EXTRACT_SHARPNESS));
		check(rowY.isPresent(), "the Sharpness row is drawn");
		click(context, layout.panelX() + 10, rowY.getAsInt());
		context.waitTicks(3);
		Optional<GrindstoneChoice> held = world.getServer().computeOnServer(server ->
				((GrindstoneMenuAccess) serverMenu(server)).enchanting2$choice());
		check(held.equals(Optional.of(EXTRACT_SHARPNESS)), "the click picked Sharpness on the server: " + held);
		check(resultOnClient(context).is(Items.ENCHANTED_BOOK), "the result arrived: " + resultOnClient(context));
		panel = screen(context, GrindstoneScreenAccess::enchanting2$panelText);
		check(panel.contains(Component.translatable("container.repair.cost", 5).getString()), "costs 5: " + panel);
		context.takeScreenshot("enchanting2-grindstone-picked");

		int leftPos = screen(context, GrindstoneScreenAccess::enchanting2$leftPos);
		int height = context.computeOnClient(client -> client.getWindow().getGuiScaledHeight());
		click(context, leftPos + RESULT_X + 8, (height - IMAGE_HEIGHT) / 2 + RESULT_Y + 8);
		context.waitTicks(3);
		world.getServer().runOnServer(server -> {
			GrindstoneMenu menu = serverMenu(server);
			ItemStack taken = menu.getCarried();
			check(taken.is(Items.ENCHANTED_BOOK) && level(server, taken, Enchantments.SHARPNESS) == 5,
					"carrying the Sharpness V book: " + taken);
			ItemStack sword = menu.getSlot(GrindstoneMenu.INPUT_SLOT).getItem();
			check(level(server, sword, Enchantments.SHARPNESS) == 0 && level(server, sword, Enchantments.UNBREAKING) == 3,
					"the sword kept only Unbreaking: " + sword);
			check(menu.getSlot(GrindstoneMenu.ADDITIONAL_SLOT).getItem().getCount() == 2, "one book used");
			check(player(server).experienceLevel == LEVELS - 5, "5 levels paid: " + player(server).experienceLevel);
			player(server).getInventory().add(taken.copy());
			menu.setCarried(ItemStack.EMPTY);
			menu.broadcastChanges();
		});
		context.waitTicks(2);
	}

	private static ItemStack resultOnClient(ClientGameTestContext context) {
		return context.computeOnClient(client -> client.player.containerMenu.getSlot(GrindstoneMenu.RESULT_SLOT)
				.getItem().copy());
	}

	private static void cursedItemGetsACleanseRow(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			GrindstoneMenu menu = serverMenu(server);
			menu.getSlot(GrindstoneMenu.ADDITIONAL_SLOT).set(ItemStack.EMPTY);
			menu.getSlot(GrindstoneMenu.INPUT_SLOT).set(with(server, Items.DIAMOND_SWORD,
					Map.of(Enchantments.SHARPNESS, 5, Enchantments.VANISHING_CURSE, 1)));
		});
		context.waitTicks(2);
		List<String> panel = screen(context, GrindstoneScreenAccess::enchanting2$panelText);
		check(panel.contains(Component.translatable("enchanting2.grindstone.heading.cleanse").getString())
				&& panel.contains("Curse of Vanishing"), "a Cleanse row for the curse: " + panel);
		int cost = context.computeOnClient(client -> GrindstoneOptionsState
				.forContainer(client.player.containerMenu.containerId)
				.flatMap(p -> p.options().stream().filter(o -> o.choice().action() == GrindstoneAction.CLEANSE)
						.findFirst())
				.map(GrindstoneOption::cost).orElse(-1));
		check(cost == 30, "Cleanse costs removal_min_cost 30, got " + cost);
		context.takeScreenshot("enchanting2-grindstone-cleanse");
	}

	/** Vanilla would count a click beside the window as outside and throw the carried item; the panel isn't outside. */
	private static void panelClickKeepsTheCarriedItem(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			GrindstoneMenu menu = serverMenu(server);
			menu.setCarried(new ItemStack(Items.DIAMOND, 7));
			menu.broadcastChanges();
		});
		context.waitTicks(2);
		SidePanelLayout layout = screen(context, GrindstoneScreenAccess::enchanting2$layout);
		int height = context.computeOnClient(client -> client.getWindow().getGuiScaledHeight());
		// The title line: on the panel, not a row.
		click(context, layout.panelX() + 10, (height - IMAGE_HEIGHT) / 2 + 6);
		context.waitTicks(3);
		ItemStack carried = world.getServer().computeOnServer(server -> serverMenu(server).getCarried().copy());
		check(carried.is(Items.DIAMOND) && carried.getCount() == 7, "the carried diamonds were thrown: " + carried);
		world.getServer().runOnServer(server -> {
			GrindstoneMenu menu = serverMenu(server);
			player(server).getInventory().add(menu.getCarried().copy());
			menu.setCarried(ItemStack.EMPTY);
			menu.broadcastChanges();
		});
		context.waitTicks(2);
	}

	private static void closingClearsThePanel(ClientGameTestContext context) {
		int containerId = context.computeOnClient(client -> client.player.containerMenu.containerId);
		check(context.computeOnClient(client -> GrindstoneOptionsState.forContainer(containerId).isPresent()),
				"a panel is held while open");
		context.runOnClient(client -> client.player.closeContainer());
		context.waitForScreen(null);
		check(context.computeOnClient(client -> GrindstoneOptionsState.forContainer(containerId).isEmpty()),
				"closing the grindstone cleared its panel");
	}
}
