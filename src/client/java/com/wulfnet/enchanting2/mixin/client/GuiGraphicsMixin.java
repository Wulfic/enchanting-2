package com.wulfnet.enchanting2.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.wulfnet.enchanting2.client.TallTooltips;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import org.joml.Vector2i;
import org.joml.Vector2ic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A tooltip taller than the screen starts at its top and scrolls (owner 2026-10-13, {@code TooltipScroll}). Vanilla's
 * positioner moves it up until its bottom fits, which pushes the item's name off the top; this replaces only that
 * case's y, so every tooltip that fits is placed exactly as vanilla places it.
 */
@Mixin(GuiGraphics.class)
abstract class GuiGraphicsMixin {
	@WrapOperation(method = "renderTooltip", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/"
			+ "inventory/tooltip/ClientTooltipPositioner;positionTooltip(IIIIII)Lorg/joml/Vector2ic;"))
	private Vector2ic enchanting2$scrollTallTooltip(ClientTooltipPositioner positioner, int screenWidth,
			int screenHeight, int mouseX, int mouseY, int width, int height, Operation<Vector2ic> original) {
		Vector2ic placed = original.call(positioner, screenWidth, screenHeight, mouseX, mouseY, width, height);
		int y = TallTooltips.y(placed.y(), width, height, screenHeight);
		return y == placed.y() ? placed : new Vector2i(placed.x(), y);
	}
}
