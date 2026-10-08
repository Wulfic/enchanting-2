package com.wulfnet.enchanting2.mixin;

import com.wulfnet.enchanting2.fortune.PlacedBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * DESIGN §6: a player break clears the position's mark, at RETURN, after {@code playerDestroy} has rolled the loot that
 * reads it. Fabric's {@code PlayerBlockBreakEvents.AFTER} fires at {@code Block.destroy}, before the loot, so clearing
 * there would make every placed block pay out (gotchas.md).
 */
@Mixin(ServerPlayerGameMode.class)
abstract class ServerPlayerGameModeMixin {
	@Shadow
	protected ServerLevel level;

	@Inject(method = "destroyBlock", at = @At("RETURN"))
	private void enchanting2$clearMark(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			PlacedBlocks.clear(level, pos);
		}
	}
}
