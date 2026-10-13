package com.wulfnet.enchanting2.client;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.config.ClientConfig;
import com.mojang.blaze3d.platform.Window;
import com.wulfnet.enchanting2.tooltip.DescriptionPlan;
import com.wulfnet.enchanting2.tooltip.TooltipFit;
import com.wulfnet.enchanting2.tooltip.TooltipFit.Layout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * DESIGN §6 "Enchantment descriptions in tooltips" (owner, 2026-10-10): holding Ctrl over an item shows what each of
 * its enchantments does, under that enchantment's line; otherwise one "hold Ctrl" hint follows the last one. Vanilla
 * writes each enchantment as {@link Enchantment#getFullname}, so a description goes right after the line equal to it,
 * and an enchantment the tooltip hides has no line and gets nothing. Off when the Enchantment Descriptions mod is
 * loaded, which does the same from the same lang keys. {@link DescriptionPlan} decides; this only places lines.
 *
 * <p>When descriptions under their names would make the tooltip taller than the screen, each goes on its name's line
 * instead, wrapped wider ({@link TooltipFit}); if even that is too tall, a "scroll" line goes under the item's name
 * and {@link TallTooltips} scrolls it (owner 2026-10-13).
 */
public final class EnchantmentDescriptions {
	/** Darkhax's Enchantment Descriptions. */
	static final String OTHER_MOD_ID = "enchdesc";
	static final String HINT_KEY = "enchanting2.tooltip.hold_ctrl";
	/** "Name: description", the compact layout's line. */
	static final String INLINE_KEY = "enchanting2.tooltip.inline_description";
	static final String SCROLL_KEY = "enchanting2.tooltip.scroll_more";
	/** GUI pixels a description line wraps at, so a long one doesn't stretch the tooltip across the screen. */
	static final int WRAP_WIDTH = 200;

	private EnchantmentDescriptions() {
	}

	public static void register() {
		if (FabricLoader.getInstance().isModLoaded(OTHER_MOD_ID)) {
			Enchanting2.LOGGER.info("Enchantment Descriptions ({}) is loaded; leaving tooltip descriptions to it",
					OTHER_MOD_ID);
			return;
		}
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			try {
				addTo(stack, lines, Minecraft.getInstance().hasControlDown(), ClientSettings.get());
			} catch (RuntimeException e) {
				Enchanting2.LOGGER.error("Adding enchantment descriptions to {}'s tooltip failed; left as it was",
						stack.getItem(), e);
			}
		});
	}

	/** Inserts the planned lines into {@code lines}. */
	static void addTo(ItemStack stack, List<Component> lines, boolean ctrlDown, ClientConfig settings) {
		// The tooltip line index of each shown enchantment, in tooltip order.
		Map<String, Integer> shown = new LinkedHashMap<>();
		for (ItemEnchantments enchantments : List.of(stack.getEnchantments(),
				stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY))) {
			for (var entry : enchantments.entrySet()) {
				Holder<Enchantment> holder = entry.getKey();
				int index = lines.indexOf(Enchantment.getFullname(holder, entry.getIntValue()));
				if (index >= 0) {
					holder.unwrapKey().ifPresent(key -> shown.putIfAbsent(key.identifier().toString(), index));
				}
			}
		}
		if (shown.isEmpty()) {
			return;
		}
		List<Map.Entry<String, Integer>> ordered = new ArrayList<>(shown.entrySet());
		ordered.sort(Map.Entry.comparingByValue());
		List<String> ids = ordered.stream().map(Map.Entry::getKey).toList();
		DescriptionPlan plan = DescriptionPlan.of(ids, Language.getInstance()::has, ctrlDown,
				settings.enchantmentDescriptions(), settings.showDescriptionHint());
		Window window = Minecraft.getInstance().getWindow();
		int screenHeight = window.getGuiScaledHeight();
		// Each described line's replacement in both layouts: full puts the wrapped description under the name, compact
		// on the name's own line, wrapped wider (owner 2026-10-13: many enchantments overflowed the screen).
		int compactWidth = TooltipFit.compactWidth(window.getGuiScaledWidth());
		Map<Integer, List<Component>> full = new LinkedHashMap<>();
		Map<Integer, List<Component>> compact = new LinkedHashMap<>();
		int fullLines = lines.size();
		int compactLines = lines.size();
		for (Map.Entry<String, Integer> shownLine : ordered) {
			String key = plan.descriptions().get(shownLine.getKey());
			if (key == null) {
				continue;
			}
			Component name = lines.get(shownLine.getValue());
			List<Component> under = new ArrayList<>(List.of(name));
			under.addAll(wrapped(Component.translatable(key)));
			List<Component> inline = inline(name, Component.translatable(key), compactWidth);
			full.put(shownLine.getValue(), under);
			compact.put(shownLine.getValue(), inline);
			fullLines += under.size() - 1;
			compactLines += inline.size() - 1;
		}
		Map<Integer, List<Component>> chosen = TooltipFit.choose(fullLines, compactLines, screenHeight) == Layout.FULL
				? full : compact;
		// Bottom-up, so replacing never moves a line still to be handled.
		int last = ordered.getLast().getValue();
		if (plan.hint()) {
			lines.add(last + 1, Component.translatable(HINT_KEY).withStyle(ChatFormatting.DARK_GRAY));
		}
		for (int i = ordered.size() - 1; i >= 0; i--) {
			int index = ordered.get(i).getValue();
			List<Component> replacement = chosen.get(index);
			if (replacement != null) {
				lines.remove(index);
				lines.addAll(index, replacement);
			}
		}
		// Still too tall: say so under the item's name, the one place that is always on screen.
		if (lines.size() > 1 && !TooltipFit.fits(lines.size() + 1, screenHeight)) {
			lines.add(1, Component.translatable(SCROLL_KEY).withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	/** {@code text} split at {@link #WRAP_WIDTH}, each line indented by a space and dark grey. */
	private static List<Component> wrapped(Component text) {
		return Minecraft.getInstance().font.getSplitter().splitLines(text, WRAP_WIDTH, Style.EMPTY).stream()
				.map(part -> (Component) Component.literal(" " + part.getString()).withStyle(ChatFormatting.DARK_GRAY))
				.toList();
	}

	/**
	 * "Name: description" split at {@code width}: the name keeps its own colour (grey, red for a curse), the description
	 * is dark grey, and each further line is indented by a space as in the full layout.
	 */
	private static List<Component> inline(Component name, Component description, int width) {
		Component joined = Component.translatable(INLINE_KEY, name, description.copy().withStyle(ChatFormatting.DARK_GRAY))
				.withStyle(ChatFormatting.GRAY);
		List<FormattedText> parts = Minecraft.getInstance().font.getSplitter().splitLines(joined, width, Style.EMPTY);
		List<Component> out = new ArrayList<>(parts.size());
		for (int i = 0; i < parts.size(); i++) {
			MutableComponent line = Component.literal(i == 0 ? "" : " ");
			parts.get(i).visit((style, text) -> {
				line.append(Component.literal(text).withStyle(style));
				return Optional.empty();
			}, Style.EMPTY);
			out.add(line);
		}
		return out;
	}
}
