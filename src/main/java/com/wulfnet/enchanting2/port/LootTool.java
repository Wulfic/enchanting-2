package com.wulfnet.enchanting2.port;

import com.wulfnet.enchanting2.Enchanting2;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jspecify.annotations.Nullable;

/**
 * The loot context's {@code tool} as an {@link ItemStack} on every band (Phase 12B). 1.21.11 stores an
 * {@code ItemStack}. 26.1 stores an {@code ItemInstance}: an {@code ItemStack} from block breaking, fishing and kills, or
 * an immutable {@code ItemStackTemplate} from {@code /loot ... mine}, which is copied into a stack here. Any other kind
 * reads as no tool (one warning), so the enchantment effect is skipped rather than guessed.
 */
public final class LootTool {
	private static volatile boolean warnedUnknown;

	private LootTool() {
	}

	public static @Nullable ItemStack of(LootContext context) {
		//? if >=26.1 {
		/*net.minecraft.world.item.ItemInstance tool = context.getOptionalParameter(LootContextParams.TOOL);
		if (tool == null || tool instanceof ItemStack) {
			return (ItemStack) tool;
		}
		if (tool instanceof net.minecraft.world.item.ItemStackTemplate template) {
			return template.create();
		}
		if (!warnedUnknown) {
			warnedUnknown = true;
			Enchanting2.LOGGER.warn("Loot tool of unknown kind {}; tool enchantment effects skip these drops",
					tool.getClass().getName());
		}
		return null;
		*///?} else
		return context.getOptionalParameter(LootContextParams.TOOL);
	}
}
