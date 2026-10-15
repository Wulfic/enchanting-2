package com.wulfnet.enchanting2.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.wulfnet.enchanting2.tool.Tiller;
import net.minecraft.world.InteractionResult;
//? if >=26.3 {
/*import net.minecraft.core.component.BlockTransformer;
*///?} else
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Phase 10cEC, Tiller: once vanilla has tilled the clicked block, the square around it follows (server only). 26.3 made
 * tools data: a hoe is a plain item whose {@code block_transformer} component does the tilling, so the wrap moves to
 * {@code BlockTransformer.transformBlock} there; {@link Tiller#afterUse} still needs the Tiller effect on the tool and
 * farmland at the centre, so an axe's or shovel's transform passes through untouched (Phase 12E).
 */
//? if >=26.3 {
/*@Mixin(BlockTransformer.class)
abstract class HoeItemMixin {
	@WrapMethod(method = "transformBlock")
*///?} else {
@Mixin(HoeItem.class)
abstract class HoeItemMixin {
	@WrapMethod(method = "useOn")
//?}
	private InteractionResult enchanting2$tiller(UseOnContext context, Operation<InteractionResult> original) {
		InteractionResult result = original.call(context);
		Tiller.afterUse(context, result);
		return result;
	}
}
