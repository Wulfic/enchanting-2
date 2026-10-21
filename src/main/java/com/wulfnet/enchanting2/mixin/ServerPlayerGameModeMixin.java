package com.wulfnet.enchanting2.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.wulfnet.enchanting2.fortune.PlacedBlocks;
//? if forge {
/*import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.wulfnet.enchanting2.drops.DropRedirect;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
*///?}
import com.wulfnet.enchanting2.tool.AreaMining;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * DESIGN §6: a player break clears the position's mark, at RETURN, after {@code playerDestroy} has rolled the loot that
 * reads it. Fabric's {@code PlayerBlockBreakEvents.AFTER} fires at {@code Block.destroy}, before the loot, so clearing
 * there would make every placed block pay out (gotchas.md).
 * <p>
 * Phase 10cEA, Area Mining: the face the client last reported hitting, and the area broken after the centre.
 */
@Mixin(ServerPlayerGameMode.class)
abstract class ServerPlayerGameModeMixin {
	@Shadow
	protected ServerLevel level;

	@Shadow
	@Final
	protected ServerPlayer player;

	/** The block and face of the client's last break action (Area Mining's plane). */
	@Unique
	private BlockPos enchanting2$actionPos;
	@Unique
	private Direction enchanting2$actionFace;

	@Inject(method = "handleBlockBreakAction", at = @At("HEAD"))
	private void enchanting2$rememberFace(BlockPos pos, ServerboundPlayerActionPacket.Action action, Direction face,
			int maxBuildHeight, int sequence, CallbackInfo ci) {
		enchanting2$actionPos = pos.immutable();
		enchanting2$actionFace = face;
	}

	@WrapMethod(method = "destroyBlock")
	private boolean enchanting2$areaMining(BlockPos pos, Operation<Boolean> original) {
		Direction face = pos.equals(enchanting2$actionPos) ? enchanting2$actionFace : null;
		return AreaMining.destroy(player, level, pos, face, original::call);
	}

	@Inject(method = "destroyBlock", at = @At("RETURN"))
	private void enchanting2$clearMark(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			PlacedBlocks.clear(level, pos);
		}
	}

	//? if forge {
	/*// Forge (13D) pops a broken block's XP here, after playerDestroy's drops (BlockMixin), so Magnetic and Ender Link
	// get the same context around it. destroyedWith (the second ItemStack local) is the tool as it was, even if the
	// break used it up.
	@WrapOperation(method = "destroyBlock", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/Block;popExperience(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;I)V"))
	private void enchanting2$redirectBlockXp(Block block, ServerLevel xpLevel, BlockPos pos, int amount,
			Operation<Void> original, @Local(ordinal = 1) ItemStack destroyedWith) {
		DropRedirect.within(DropRedirect.forTool(player, destroyedWith), () -> {
			original.call(block, xpLevel, pos, amount);
			return null;
		});
	}
	*///?}
}
