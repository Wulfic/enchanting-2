package com.wulfnet.enchanting2.platform;

import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Client rendering hooks. */
public interface ClientHooks {
	/** Every item tooltip, after vanilla's lines; the listener may add to {@code lines}. */
	void onItemTooltip(BiConsumer<ItemStack, List<Component>> listener);

	/** A HUD layer drawn right after vanilla's hotbar. */
	void addHudLayerAfterHotbar(Identifier id, BiConsumer<GuiGraphics, DeltaTracker> renderer);
}
