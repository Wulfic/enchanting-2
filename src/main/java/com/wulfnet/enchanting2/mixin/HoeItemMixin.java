package com.wulfnet.enchanting2.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.wulfnet.enchanting2.tool.Tiller;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;

/** Phase 10cEC, Tiller: once vanilla has tilled the clicked block, the square around it follows (server only). */
@Mixin(HoeItem.class)
abstract class HoeItemMixin {
	@WrapMethod(method = "useOn")
	private InteractionResult enchanting2$tiller(UseOnContext context, Operation<InteractionResult> original) {
		InteractionResult result = original.call(context);
		Tiller.afterUse(context, result);
		return result;
	}
}
