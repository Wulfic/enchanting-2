package com.wulfnet.enchanting2.platform.fabric;

import com.wulfnet.enchanting2.platform.ClientHooks;
import java.util.List;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public final class FabricClientHooks implements ClientHooks {
	@Override
	public void onItemTooltip(BiConsumer<ItemStack, List<Component>> listener) {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> listener.accept(stack, lines));
	}

	@Override
	public void addHudLayerAfterHotbar(Identifier id, BiConsumer<GuiGraphics, DeltaTracker> renderer) {
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, id, renderer::accept);
	}
}
