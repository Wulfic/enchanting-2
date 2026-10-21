package com.wulfnet.enchanting2.platform.forge;

import com.wulfnet.enchanting2.platform.ClientHooks;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

public final class ForgeClientHooks implements ClientHooks {
	@Override
	public void onItemTooltip(BiConsumer<ItemStack, List<Component>> listener) {
		ItemTooltipEvent.BUS.addListener(event -> listener.accept(event.getItemStack(), event.getToolTip()));
	}

	@Override
	public void addHudLayerAfterHotbar(Identifier id, BiConsumer<GuiGraphics, DeltaTracker> renderer) {
		// Inside the hotbar cluster, right above the item hotbar: where Fabric's VanillaHudElements.HOTBAR and NeoForge's
		// VanillaGuiLayers.HOTBAR put it.
		AddGuiOverlayLayersEvent.BUS.addListener(event -> event.getLayeredDraw().addAbove(
				ForgeLayeredDraw.HOTBAR_AND_DECOS, id, ForgeLayeredDraw.ITEM_HOTBAR, renderer::accept));
	}
}
