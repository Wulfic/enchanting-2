package com.wulfnet.enchanting2.mixin;

import com.wulfnet.enchanting2.config.ServerConfigs;
import com.wulfnet.enchanting2.spawner.CollectedSpawners;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SpawnerBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//? if neoforge {
/*import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
*///?} else {
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
//?}

/**
 * DESIGN §6: a collected spawner gives no XP. Vanilla pops its 15–43 XP straight from {@code spawnAfterBreak}, not
 * through {@code block_experience}, so Silk Touch's own XP-to-zero effect never reaches it. Same condition as the
 * drop ({@link CollectedSpawners#collects}), read from the same server config. NeoForge (Phase 13C) moves that XP into
 * its {@code getExpDrop} override instead (its fix for MC-273642), so there the same rule zeroes what that returns.
 */
@Mixin(SpawnerBlock.class)
abstract class SpawnerBlockMixin {
	//? if neoforge {
	/*@ModifyReturnValue(method = "getExpDrop", at = @At("RETURN"))
	private int enchanting2$noXpWhenCollected(int xp, BlockState state, LevelAccessor level, BlockPos pos,
			@Nullable BlockEntity blockEntity, @Nullable Entity breaker, ItemStack tool) {
		return level instanceof ServerLevel serverLevel && collects(serverLevel, tool) ? 0 : xp;
	}
	*///?} else {
	@WrapWithCondition(method = "spawnAfterBreak", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/SpawnerBlock;popExperience(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;I)V"))
	private boolean enchanting2$noXpWhenCollected(SpawnerBlock self, ServerLevel level, BlockPos pos, int amount,
			@Local(argsOnly = true) ItemStack tool) {
		return !collects(level, tool);
	}
	//?}

	private static boolean collects(ServerLevel level, ItemStack tool) {
		return CollectedSpawners.collects(
				ServerConfigs.get(level.getServer()).silkTouch().collectSpawners(), tool, level.registryAccess());
	}
}
