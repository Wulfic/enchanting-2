package com.wulfnet.enchanting2.client;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.config.ClientConfig;
import com.wulfnet.enchanting2.tooltip.DescriptionPlan;
import com.wulfnet.enchanting2.tooltip.TooltipFit;
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
 * its enchantments does, on that enchantment's own line as "Name: description"; otherwise one "hold Ctrl" hint
 * follows the last one. Vanilla writes each enchantment as {@link Enchantment#getFullname}, so the line equal to it is
 * the one replaced, and an enchantment the tooltip hides has no line and gets nothing. Off when the Enchantment
 * Descriptions mod is loaded, which does the same from the same lang keys. {@link DescriptionPlan} decides; this only
 * places lines.
 *
 * <p>Descriptions under their names made even a few enchantments a tall tooltip, so the name's own line is the only
 * layout (owner 2026-10-13, Phase 11c), wrapped as wide as fits beside the cursor ({@link TooltipFit#compactWidth}).
 * Any tooltip still taller than the screen, descriptions or not, gets a "scroll" line under the item's name, and
 * {@link TallTooltips} scrolls it.
 */
public final class EnchantmentDescriptions {
	/** Darkhax's Enchantment Descriptions. */
	static final String OTHER_MOD_ID = "enchdesc";
	static final String HINT_KEY = "enchanting2.tooltip.hold_ctrl";
	/** "Name: description", each described enchantment's line. */
	static final String INLINE_KEY = "enchanting2.tooltip.inline_description";
	static final String SCROLL_KEY = "enchanting2.tooltip.scroll_more";
	/** GUI pixels the table and grindstone lists wrap a hover tooltip's description at. */
	static final int WRAP_WIDTH = 200;

	private EnchantmentDescriptions() {
	}

	public static void register() {
		boolean describe = !FabricLoader.getInstance().isModLoaded(OTHER_MOD_ID);
		if (!describe) {
			Enchanting2.LOGGER.info("Enchantment Descriptions ({}) is loaded; leaving tooltip descriptions to it",
					OTHER_MOD_ID);
		}
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			try {
				onTooltip(stack, lines, describe, Minecraft.getInstance().hasControlDown(), ClientSettings.get(),
						Minecraft.getInstance().getWindow().getGuiScaledHeight());
			} catch (RuntimeException e) {
				Enchanting2.LOGGER.error("Adding descriptions or the scroll line to {}'s tooltip failed; left as it was",
						stack.getItem(), e);
			}
		});
	}

	/**
	 * Everything this adds to one tooltip: descriptions unless another mod writes them ({@code describe}), then the
	 * scroll line on any tooltip still too tall, whoever added its lines (owner 2026-10-13: Enchantment Descriptions'
	 * lines made one too tall with no line saying it scrolls).
	 */
	public static void onTooltip(ItemStack stack, List<Component> lines, boolean describe, boolean ctrlDown,
			ClientConfig settings, int screenHeight) {
		if (describe) {
			addTo(stack, lines, ctrlDown, settings);
		}
		addScrollLine(lines, screenHeight);
	}

	/** Under the item's name, the one place that is always on screen, when the tooltip is taller than the screen. */
	static void addScrollLine(List<Component> lines, int screenHeight) {
		if (lines.size() > 1 && !TooltipFit.fits(lines.size() + 1, screenHeight)) {
			lines.add(1, Component.translatable(SCROLL_KEY).withStyle(ChatFormatting.DARK_GRAY));
		}
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
		int width = TooltipFit.compactWidth(Minecraft.getInstance().getWindow().getGuiScaledWidth());
		// Bottom-up, so replacing never moves a line still to be handled.
		int last = ordered.getLast().getValue();
		if (plan.hint()) {
			lines.add(last + 1, Component.translatable(HINT_KEY).withStyle(ChatFormatting.DARK_GRAY));
		}
		for (int i = ordered.size() - 1; i >= 0; i--) {
			int index = ordered.get(i).getValue();
			String key = plan.descriptions().get(ordered.get(i).getKey());
			if (key != null) {
				lines.addAll(index, inline(lines.remove(index), Component.translatable(key), width));
			}
		}
	}

	/**
	 * "Name: description" split at {@code width}: the name keeps its own colour (grey, red for a curse), the description
	 * is dark grey, and each further line is indented by a space.
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
