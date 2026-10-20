package com.wulfnet.enchanting2.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import com.wulfnet.enchanting2.client.GrindstoneOptionsState;
import com.wulfnet.enchanting2.client.GrindstoneScreenAccess;
import com.wulfnet.enchanting2.grindstone.GrindstoneAction;
import com.wulfnet.enchanting2.grindstone.GrindstoneChoice;
import com.wulfnet.enchanting2.grindstone.GrindstoneMenuAccess;
import com.wulfnet.enchanting2.grindstone.GrindstoneOption;
import com.wulfnet.enchanting2.screen.ColumnWindow;
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

/**
 * The grindstone on the real client (Stage 6bC; in the window since TODO 10o): the window is wide and centred even on a
 * small screen, the picker lists the server's rows with hover descriptions, a click on a row picks it and the price
 * shows in vanilla's cost-label place, the take moves the enchantment, a curse gets a Cleanse row with its price, and a
 * click in the list never throws the carried item. Screenshots land in the client GameTest run dir for the owner.
 */
public class GrindstoneScreenClientGameTest implements FabricClientGameTest {
	/** 854x480 at GUI scale auto is 427 x 240: the 296 wide window still fits, centred. */
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
			ClientWorlds.waitForChunksRender(world);
			world.getServer().runCommand("gamemode survival @a");
			world.getServer().runCommand("xp set @a " + LEVELS + " levels");
			context.getInput().resizeWindow(NARROW_WIDTH, NARROW_HEIGHT);
			world.getServer().runOnServer(GrindstoneScreenClientGameTest::openGrindstone);
			context.waitForScreen(GrindstoneScreen.class);
			context.waitTick();

			wideWindowFitsTheSmallScreenCentred(context);
			context.getInput().resizeWindow(WIDE_WIDTH, WIDE_HEIGHT);
			context.waitTick();
			extractThroughTheList(context, world);
			cursedItemGetsACleanseRow(context, world);
			longPicksWrap(context, world);
			listClickKeepsTheCarriedItem(context, world);
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
	private static void moveTo(ClientGameTestContext context, int x, int y) {
		double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
		context.getInput().setCursorPos((x + 0.5) * scale, (y + 0.5) * scale);
	}

	private static void click(ClientGameTestContext context, int x, int y) {
		moveTo(context, x, y);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
	}

	private static void wideWindowFitsTheSmallScreenCentred(ClientGameTestContext context) {
		check(screen(context, GrindstoneScreenAccess::enchanting2$wide), "our server, but not wide (mixin?)");
		check(screen(context, GrindstoneScreenAccess::enchanting2$imageWidth) == ColumnWindow.WIDTH, "296 wide");
		int width = context.computeOnClient(client -> client.getWindow().getGuiScaledWidth());
		int height = context.computeOnClient(client -> client.getWindow().getGuiScaledHeight());
		int leftPos = screen(context, GrindstoneScreenAccess::enchanting2$leftPos);
		check(leftPos == (width - ColumnWindow.WIDTH) / 2 && leftPos >= 0, "centred at " + leftPos + " on " + width);
		check(screen(context, GrindstoneScreenAccess::enchanting2$listLeft) == leftPos + ColumnWindow.BOX.left()
				&& screen(context, GrindstoneScreenAccess::enchanting2$listTop)
						== (height - IMAGE_HEIGHT) / 2 + ColumnWindow.BOX_TOP, "the list sits in the right column");
		List<String> list = screen(context, GrindstoneScreenAccess::enchanting2$listText);
		check(list.equals(List.of(Component.translatable("enchanting2.grindstone.status.empty").getString())),
				"empty grindstone list: " + list);
		check(screen(context, GrindstoneScreenAccess::enchanting2$labelText) == null, "no price without an item");
		context.takeScreenshot("enchanting2-grindstone-empty");
	}

	/** Sword (Sharpness V, Unbreaking III) + 3 books: hover and pick Sharpness in the list, take the book. */
	private static void extractThroughTheList(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			GrindstoneMenu menu = serverMenu(server);
			menu.getSlot(GrindstoneMenu.INPUT_SLOT).set(with(server, Items.DIAMOND_SWORD,
					Map.of(Enchantments.SHARPNESS, 5, Enchantments.UNBREAKING, 3)));
			menu.getSlot(GrindstoneMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.BOOK, 3));
		});
		Delivery.toClient(context);
		List<String> panel = screen(context, GrindstoneScreenAccess::enchanting2$listText);
		check(panel.contains("Sharpness V") && panel.contains("Unbreaking III"), "a row per enchantment: " + panel);
		check(panel.contains(Component.translatable("enchanting2.grindstone.status.pick").getString()),
				"asks for a pick: " + panel);
		check(screen(context, GrindstoneScreenAccess::enchanting2$labelText) == null, "no price before a pick");
		check(resultOnClient(context).isEmpty(), "no result before a pick");

		OptionalInt rowY = screen(context, access -> access.enchanting2$rowY(EXTRACT_SHARPNESS));
		check(rowY.isPresent(), "the Sharpness row is drawn");
		int rowX = screen(context, GrindstoneScreenAccess::enchanting2$listLeft) + 10;
		moveTo(context, rowX, rowY.getAsInt());
		context.waitTicks(2);
		List<String> tooltip = screen(context, GrindstoneScreenAccess::enchanting2$rowTooltip);
		check(tooltip.size() == 3 && tooltip.get(0).equals("Sharpness V")
				&& tooltip.get(1).equals(Component.translatable("enchantment.minecraft.sharpness.desc").getString())
				&& tooltip.get(2).contains("5"), "name, description and price on hover: " + tooltip);
		context.takeScreenshot("enchanting2-grindstone-rows");
		click(context, rowX, rowY.getAsInt());
		Delivery.toServer(context);
		Delivery.toClient(context);
		Optional<GrindstoneChoice> held = world.getServer().computeOnServer(server ->
				((GrindstoneMenuAccess) serverMenu(server)).enchanting2$choice());
		check(held.equals(Optional.of(EXTRACT_SHARPNESS)), "the click picked Sharpness on the server: " + held);
		check(resultOnClient(context).is(Items.ENCHANTED_BOOK), "the result arrived: " + resultOnClient(context));
		String label = screen(context, GrindstoneScreenAccess::enchanting2$labelText);
		check(Component.translatable("container.repair.cost", 5).getString().equals(label), "costs 5: " + label);
		panel = screen(context, GrindstoneScreenAccess::enchanting2$listText);
		check(!panel.contains(label), "the price isn't repeated in the list: " + panel);
		context.takeScreenshot("enchanting2-grindstone-picked");

		int leftPos = screen(context, GrindstoneScreenAccess::enchanting2$leftPos);
		int height = context.computeOnClient(client -> client.getWindow().getGuiScaledHeight());
		click(context, leftPos + RESULT_X + 8, (height - IMAGE_HEIGHT) / 2 + RESULT_Y + 8);
		Delivery.toServer(context);
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
		Delivery.toClient(context);
		List<String> panel = screen(context, GrindstoneScreenAccess::enchanting2$listText);
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

	/**
	 * The cursed sword from {@link #cursedItemGetsACleanseRow}: "Disenchant (XP back)" and, with a book in, the curse's
	 * "(a curse: can't be extracted)" note don't fit one 108 px line, so they wrap whole instead of ending in "…" (10oD).
	 */
	private static void longPicksWrap(ClientGameTestContext context, TestSingleplayerContext world) {
		checkWraps(context, GrindstoneChoice.of(GrindstoneAction.DISENCHANT),
				Component.translatable("enchanting2.grindstone.row.disenchant").getString());
		context.takeScreenshot("enchanting2-grindstone-wrapped");

		world.getServer().runOnServer(server -> serverMenu(server).getSlot(GrindstoneMenu.ADDITIONAL_SLOT)
				.set(new ItemStack(Items.BOOK)));
		Delivery.toClient(context);
		checkWraps(context, GrindstoneChoice.of(GrindstoneAction.EXTRACT, "minecraft:vanishing_curse"),
				Component.translatable("enchanting2.grindstone.row.curse", "Curse of Vanishing").getString());
		List<String> sharpness = screen(context, access -> access.enchanting2$pickLines(EXTRACT_SHARPNESS));
		check(sharpness.equals(List.of("Sharpness V")), "a short pick keeps one line: " + sharpness);
		world.getServer().runOnServer(server -> serverMenu(server).getSlot(GrindstoneMenu.ADDITIONAL_SLOT)
				.set(ItemStack.EMPTY));
		context.waitTicks(2);
	}

	private static void checkWraps(ClientGameTestContext context, GrindstoneChoice choice, String full) {
		List<String> lines = screen(context, access -> access.enchanting2$pickLines(choice));
		check(lines.size() >= 2, "'" + full + "' wraps: " + lines);
		check(lines.stream().noneMatch(line -> line.endsWith("…")), "no line is cut: " + lines);
		check(String.join("", lines).replace(" ", "").equals(full.replace(" ", "")),
				"the lines hold all of '" + full + "': " + lines);
		check(screen(context, access -> access.enchanting2$rowY(choice)).isPresent(), "'" + full + "' is in view");
	}

	/** The list is inside the window, so vanilla never counts a click on it as outside and throws the carried item. */
	private static void listClickKeepsTheCarriedItem(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			GrindstoneMenu menu = serverMenu(server);
			menu.setCarried(new ItemStack(Items.DIAMOND, 7));
			menu.broadcastChanges();
		});
		Delivery.toClient(context);
		// Near the list's bottom, below the rows: inside the box, not a pick.
		click(context, screen(context, GrindstoneScreenAccess::enchanting2$listLeft) + 10,
				screen(context, GrindstoneScreenAccess::enchanting2$listTop) + ColumnWindow.BOX_HEIGHT - 5);
		Delivery.toServer(context);
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
