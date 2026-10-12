package com.wulfnet.enchanting2.client;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.config.ClientConfig;
import com.wulfnet.enchanting2.tooltip.DescriptionPlan;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
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
 */
public final class EnchantmentDescriptions {
	/** Darkhax's Enchantment Descriptions. */
	static final String OTHER_MOD_ID = "enchdesc";
	static final String HINT_KEY = "enchanting2.tooltip.hold_ctrl";
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
		// Bottom-up, so inserting never moves a line still to be handled.
		int last = ordered.getLast().getValue();
		if (plan.hint()) {
			lines.add(last + 1, Component.translatable(HINT_KEY).withStyle(ChatFormatting.DARK_GRAY));
		}
		for (int i = ordered.size() - 1; i >= 0; i--) {
			String key = plan.descriptions().get(ordered.get(i).getKey());
			if (key != null) {
				lines.addAll(ordered.get(i).getValue() + 1, wrapped(Component.translatable(key)));
			}
		}
	}

	/** {@code text} split at {@link #WRAP_WIDTH}, each line indented by a space and dark grey. */
	private static List<Component> wrapped(Component text) {
		return Minecraft.getInstance().font.getSplitter().splitLines(text, WRAP_WIDTH, Style.EMPTY).stream()
				.map(part -> (Component) Component.literal(" " + part.getString()).withStyle(ChatFormatting.DARK_GRAY))
				.toList();
	}
}
