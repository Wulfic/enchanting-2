package com.wulfnet.enchanting2.platform.neoforge;

import com.wulfnet.enchanting2.platform.ClientHooks;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

public final class NeoForgeClientHooks implements ClientHooks {
	@Override
	public void onItemTooltip(BiConsumer<ItemStack, List<Component>> listener) {
		NeoForge.EVENT_BUS.addListener(ItemTooltipEvent.class,
				event -> listener.accept(event.getItemStack(), event.getToolTip()));
	}

	@Override
	public void addHudLayerAfterHotbar(Identifier id, BiConsumer<GuiGraphics, DeltaTracker> renderer) {
		NeoForgeBus.mod().addListener(RegisterGuiLayersEvent.class,
				event -> event.registerAbove(VanillaGuiLayers.HOTBAR, id, renderer::accept));
	}
}
