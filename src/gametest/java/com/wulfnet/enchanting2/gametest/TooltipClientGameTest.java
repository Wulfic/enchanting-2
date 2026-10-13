package com.wulfnet.enchanting2.gametest;

import com.wulfnet.enchanting2.client.TallTooltips;
import com.wulfnet.enchanting2.enchantment.ModEnchantments;
import com.wulfnet.enchanting2.tooltip.DescriptionPlan;
import com.wulfnet.enchanting2.tooltip.TooltipFit;
import com.wulfnet.enchanting2.tooltip.TooltipScroll;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import org.lwjgl.glfw.GLFW;

/**
 * Phase 10h on the real client: holding Ctrl puts each enchantment's description on its own tooltip line as "Name:
 * description" (a stored enchantment on a book and one on worn gear; Phase 11c, owner 2026-10-13: under the name was
 * too tall), letting go shows the single hint instead. The tooltip is built the way
 * the inventory builds it, and a screenshot of a hovered book is kept for the owner's look.
 */
public class TooltipClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getClientWorld().waitForChunksRender();
			world.getServer().runCommand("gamemode survival @a");
			context.waitTicks(2);

			String sharpness = I18n.get("enchantment.minecraft.sharpness.desc");
			String vision = I18n.get("enchantment.enchanting2.vision.desc");
			String hint = I18n.get("enchanting2.tooltip.hold_ctrl");

			context.getInput().holdKey(GLFW.GLFW_KEY_LEFT_CONTROL);
			context.waitTick();
			List<String> book = tooltip(context, sharpnessBook(context));
			assertInline(book, "Sharpness V", sharpness);
			check(!book.contains(hint), "the hint shows while Ctrl is held: " + book);
			List<String> helmet = tooltip(context, enchanted(context, Items.DIAMOND_HELMET, ModEnchantments.VISION));
			assertInline(helmet, "Vision", vision);
			// Silk Touch's is the longest: it starts on the name's line and wraps onto indented lines.
			List<String> pickaxe = tooltip(context, enchanted(context, Items.DIAMOND_PICKAXE, Enchantments.SILK_TOUCH));
			assertInline(pickaxe, "Silk Touch", I18n.get("enchantment.minecraft.silk_touch.desc"));

			screenshotHoveredBook(context, world);
			context.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_CONTROL);
			context.waitTick();

			List<String> released = tooltip(context, sharpnessBook(context));
			check(released.stream().noneMatch(line -> line.contains(sharpness)),
					"a description shows without Ctrl: " + released);
			assertBelow(released, "Sharpness V", hint);

			// Owner, 2026-10-13: many enchantments must not run off the screen. 1080p at GUI scale 3 is 640 x 360.
			context.runOnClient(client -> client.setScreen(null));
			context.getInput().resizeWindow(1920, 1080);
			context.runOnClient(client -> {
				client.options.guiScale().set(3);
				client.resizeDisplay();
			});
			context.waitTick();
			manyEnchantmentsGoOnTheirNamesLinesWithCtrl(context);
			everyEnchantmentIsTooTallAndTheWheelScrollsIt(context, world);
			context.runOnClient(client -> {
				client.options.guiScale().set(0);
				client.resizeDisplay();
				client.setScreen(null);
			});
		}
	}

	/**
	 * Sixteen described enchantments: each description is on its name's line, and the scroll line shows only if that is
	 * still taller than 360. (The GameTest-only enchantments have no description, so they're left out.)
	 */
	private static void manyEnchantmentsGoOnTheirNamesLinesWithCtrl(ClientGameTestContext context) {
		int height = context.computeOnClient(client -> client.getWindow().getGuiScaledHeight());
		check(height == 360, "GUI scale 3 at 1080p is 360 tall, got " + height);
		ItemStack pickaxe = context.computeOnClient(client -> withEnchantments(client.level.registryAccess()
				.lookupOrThrow(Registries.ENCHANTMENT), Items.DIAMOND_PICKAXE, 16, true));
		List<String> names = fullNames(context, pickaxe);
		context.getInput().holdKey(GLFW.GLFW_KEY_LEFT_CONTROL);
		context.waitTick();
		List<String> lines = tooltip(context, pickaxe);
		context.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_CONTROL);
		context.waitTick();
		for (String name : names) {
			check(lines.stream().anyMatch(line -> line.startsWith(name + ": ")),
					"\"" + name + ": <description>\" on one line: " + lines);
			check(!lines.contains(name), "no bare \"" + name + "\" line left: " + lines);
		}
		String scroll = I18n.get("enchanting2.tooltip.scroll_more");
		check(TooltipFit.fits(lines.size(), height) != lines.contains(scroll),
				"the scroll line shows exactly when it's still too tall (" + lines.size() + " lines): " + lines);
	}

	/**
	 * Every enchantment at once is too tall even compact: the scroll line sits under the item's name, the tooltip starts
	 * at its top in the real inventory, and the wheel moves it.
	 */
	private static void everyEnchantmentIsTooTallAndTheWheelScrollsIt(ClientGameTestContext context,
			TestSingleplayerContext world) {
		int height = context.computeOnClient(client -> client.getWindow().getGuiScaledHeight());
		ItemStack all = context.computeOnClient(client -> withEnchantments(client.level.registryAccess()
				.lookupOrThrow(Registries.ENCHANTMENT), Items.DIAMOND_PICKAXE, Integer.MAX_VALUE, false));
		List<String> plain = tooltip(context, all);
		check(!TooltipFit.fits(plain.size(), height), "every enchantment is taller than the screen: " + plain.size());
		check(plain.get(1).equals(I18n.get("enchanting2.tooltip.scroll_more")),
				"the scroll line is under the name: " + plain.subList(0, 3));

		world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst().getInventory().setItem(0,
				withEnchantments(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT), Items.DIAMOND_PICKAXE,
						Integer.MAX_VALUE, false)));
		context.waitTicks(2);
		context.runOnClient(client -> client.setScreen(new InventoryScreen(client.player)));
		context.waitTick();
		hoverHotbarSlot(context);
		context.waitTicks(2);
		check(TallTooltips.offset() == 0, "starts at the top: " + TallTooltips.offset());
		context.takeScreenshot("enchanting2-tooltip-tall-top");
		context.getInput().scroll(-1);
		context.waitTick();
		check(TallTooltips.offset() > 0, "one notch down scrolled it: " + TallTooltips.offset());
		// A notch a frame until it stops moving: the client only draws on a tick here, and a tooltip not drawn for
		// TooltipScroll.STALE_NANOS starts over, so a hundred notches with no frame between them could reset it on a
		// loaded machine (failed that way 2026-10-13).
		int bottom = TallTooltips.offset();
		for (int i = 0; i < 100; i++) {
			context.getInput().scroll(-1);
			context.waitTick();
			if (TallTooltips.offset() == bottom) {
				break;
			}
			bottom = TallTooltips.offset();
		}
		check(bottom > TooltipScroll.STEP, "scrolled down to its bottom: " + bottom);
		context.takeScreenshot("enchanting2-tooltip-tall-bottom");
		context.getInput().scroll(1);
		context.waitTick();
		check(TallTooltips.offset() < bottom, "and back up: " + TallTooltips.offset() + " from " + bottom);
	}

	/** The first {@code count} enchantments by ID (only ones with a description, if {@code described}), each at I. */
	private static ItemStack withEnchantments(HolderLookup.RegistryLookup<Enchantment> registry, Item item, int count,
			boolean described) {
		ItemStack stack = new ItemStack(item);
		ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		registry.listElements().sorted(Comparator.comparing(holder -> holder.key().identifier().toString()))
				.filter(holder -> !described || I18n.exists(DescriptionPlan.key(holder.key().identifier().toString())))
				.limit(count).forEach(holder -> enchantments.set(holder, 1));
		EnchantmentHelper.setEnchantments(stack, enchantments.toImmutable());
		return stack;
	}

	private static List<String> fullNames(ClientGameTestContext context, ItemStack stack) {
		return context.computeOnClient(client -> stack.getEnchantments().entrySet().stream()
				.map(e -> Enchantment.getFullname(e.getKey(), e.getIntValue()).getString()).toList());
	}

	private static void hoverHotbarSlot(ClientGameTestContext context) {
		int[] slot = context.computeOnClient(client -> new int[] {
				(client.getWindow().getGuiScaledWidth() - 176) / 2 + 8 + 8,
				(client.getWindow().getGuiScaledHeight() - 166) / 2 + 142 + 8});
		double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
		context.getInput().setCursorPos(slot[0] * scale, slot[1] * scale);
	}

	private static ItemStack enchanted(ClientGameTestContext context, Item item, ResourceKey<Enchantment> key) {
		return context.computeOnClient(client -> {
			ItemStack stack = new ItemStack(item);
			stack.enchant(holder(client, key), 1);
			return stack;
		});
	}

	private static ItemStack sharpnessBook(ClientGameTestContext context) {
		return context.computeOnClient(client -> {
			ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
			ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
			stored.set(holder(client, Enchantments.SHARPNESS), 5);
			EnchantmentHelper.setEnchantments(book, stored.toImmutable());
			return book;
		});
	}

	private static Holder<Enchantment> holder(Minecraft client, ResourceKey<Enchantment> key) {
		return client.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	/** The tooltip as the inventory builds it, as plain strings. */
	private static List<String> tooltip(ClientGameTestContext context, ItemStack stack) {
		return context.computeOnClient(client -> stack
				.getTooltipLines(Item.TooltipContext.of(client.level), client.player, TooltipFlag.NORMAL).stream()
				.map(Component::getString).toList());
	}

	/**
	 * {@code name}'s line is "name: description", its wrapped rest on the indented lines after it, and no bare
	 * {@code name} line is left.
	 */
	private static void assertInline(List<String> lines, String name, String description) {
		check(!lines.contains(name), "a bare \"" + name + "\" line is left: " + lines);
		int index = -1;
		for (int i = 0; i < lines.size() && index < 0; i++) {
			if (lines.get(i).startsWith(name + ": ")) {
				index = i;
			}
		}
		check(index >= 0, "no \"" + name + ": \" line: " + lines);
		StringBuilder joined = new StringBuilder(lines.get(index));
		for (int i = index + 1; i < lines.size() && lines.get(i).startsWith(" "); i++) {
			joined.append(' ').append(lines.get(i).trim());
		}
		check(joined.toString().replaceAll("\\s+", " ").equals((name + ": " + description).replaceAll("\\s+", " ")),
				"\"" + name + ": " + description + "\" isn't on its line: " + lines);
	}

	/** {@code below} is the line right after {@code line} (the description is indented by one space). */
	private static void assertBelow(List<String> lines, String line, String below) {
		int index = lines.indexOf(line);
		check(index >= 0, "no \"" + line + "\" line: " + lines);
		check(index + 1 < lines.size() && lines.get(index + 1).trim().equals(below),
				"\"" + below + "\" isn't right under \"" + line + "\": " + lines);
	}

	/** The book in hotbar slot 1, the inventory open and the cursor on it, with Ctrl still held. */
	private static void screenshotHoveredBook(ClientGameTestContext context, TestSingleplayerContext world) {
		// Built from the server's registry: a client-side holder can't be encoded by the integrated server.
		world.getServer().runOnServer(server -> {
			ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
			ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
			stored.set(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 5);
			EnchantmentHelper.setEnchantments(book, stored.toImmutable());
			server.getPlayerList().getPlayers().getFirst().getInventory().setItem(0, book);
		});
		context.waitTicks(2);
		context.runOnClient(client -> client.setScreen(new InventoryScreen(client.player)));
		context.waitTick();
		int[] slot = context.computeOnClient(client -> new int[] {
				(client.getWindow().getGuiScaledWidth() - 176) / 2 + 8 + 8,
				(client.getWindow().getGuiScaledHeight() - 166) / 2 + 142 + 8});
		double scale = context.computeOnClient(client -> client.getWindow().getGuiScale());
		context.getInput().setCursorPos(slot[0] * scale, slot[1] * scale);
		context.waitTick();
		context.takeScreenshot("enchanting2-tooltip-ctrl");
		check(context.computeOnClient(client -> client.gameMode.getPlayerMode() == GameType.SURVIVAL),
				"the player isn't in survival");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
