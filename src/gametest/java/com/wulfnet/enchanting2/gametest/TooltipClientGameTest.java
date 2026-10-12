package com.wulfnet.enchanting2.gametest;

import com.wulfnet.enchanting2.enchantment.ModEnchantments;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Holder;
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
 * Phase 10h on the real client: holding Ctrl puts each enchantment's description under its tooltip line (a stored
 * enchantment on a book and one on worn gear), letting go shows the single hint instead. The tooltip is built the way
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
			assertBelow(book, "Sharpness V", sharpness);
			check(!book.contains(hint), "the hint shows while Ctrl is held: " + book);
			List<String> helmet = tooltip(context, enchanted(context, Items.DIAMOND_HELMET, ModEnchantments.VISION));
			assertBelow(helmet, "Vision", vision);
			// Silk Touch's is the longest: it wraps into several lines, each starting under the name.
			List<String> pickaxe = tooltip(context, enchanted(context, Items.DIAMOND_PICKAXE, Enchantments.SILK_TOUCH));
			int silk = pickaxe.indexOf("Silk Touch");
			String joined = String.join(" ", pickaxe.subList(silk + 1, pickaxe.size()).stream().map(String::trim).toList());
			check(joined.startsWith(I18n.get("enchantment.minecraft.silk_touch.desc")) && pickaxe.size() > silk + 2,
					"Silk Touch's description isn't wrapped under it: " + pickaxe);

			screenshotHoveredBook(context, world);
			context.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_CONTROL);
			context.waitTick();

			List<String> released = tooltip(context, sharpnessBook(context));
			check(released.stream().noneMatch(line -> line.contains(sharpness)),
					"a description shows without Ctrl: " + released);
			assertBelow(released, "Sharpness V", hint);
			context.runOnClient(client -> client.setScreen(null));
		}
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
