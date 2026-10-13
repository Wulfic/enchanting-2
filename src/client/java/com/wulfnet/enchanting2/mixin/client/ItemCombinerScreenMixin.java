package com.wulfnet.enchanting2.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.wulfnet.enchanting2.client.AnvilScreenAccess;
import com.wulfnet.enchanting2.client.WindowBackground;
import com.wulfnet.enchanting2.screen.ColumnWindow;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.ItemCombinerScreen;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The anvil's wide background (TODO 10o): {@code ItemCombinerScreen} draws it for the anvil and the smithing table, so
 * the wrap acts only on an anvil screen that is ours ({@link AnvilScreenAccess#enchanting2$wide}). Vanilla's texture
 * in {@link ColumnWindow#BACKGROUND}'s pieces, full height.
 */
@Mixin(ItemCombinerScreen.class)
abstract class ItemCombinerScreenMixin {
	@WrapOperation(method = "renderBg", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;"
			+ "blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"))
	private void enchanting2$background(GuiGraphics graphics, RenderPipeline pipeline, Identifier texture, int x, int y,
			float u, float v, int width, int height, int textureWidth, int textureHeight, Operation<Void> original) {
		if (!((Object) this instanceof AnvilScreenAccess anvil) || !anvil.enchanting2$wide()) {
			original.call(graphics, pipeline, texture, x, y, u, v, width, height, textureWidth, textureHeight);
			return;
		}
		WindowBackground.band(graphics, pipeline, texture, ColumnWindow.BACKGROUND, x, y, 0, ColumnWindow.HEIGHT,
				ColumnWindow.HEIGHT, textureWidth, textureHeight);
	}
}
