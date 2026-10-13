package com.wulfnet.enchanting2.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.wulfnet.enchanting2.client.TallTooltips;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * While a tooltip taller than the screen is shown, the wheel scrolls it instead of the screen (owner 2026-10-13). Ahead
 * of every screen's own {@code mouseScrolled}, so it works the same in the inventory, the creative tabs and every
 * container. The amount is vanilla's, after its sensitivity and discrete-scrolling options.
 */
@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
	@WrapOperation(method = "onScroll", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/Screen;mouseScrolled(DDDD)Z"))
	private boolean enchanting2$scrollTallTooltip(Screen screen, double mouseX, double mouseY, double scrollX,
			double scrollY, Operation<Boolean> original) {
		if (scrollY != 0 && TallTooltips.scroll(scrollY)) {
			return true;
		}
		return original.call(screen, mouseX, mouseY, scrollX, scrollY);
	}
}
