package com.wulfnet.enchanting2.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.wulfnet.enchanting2.acquisition.Acquisition;
import com.wulfnet.enchanting2.acquisition.Source;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.functions.EnchantRandomlyFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Phase 9: the {@code enchant_randomly} loot function picks uniformly from its options (the whole registry when it has
 * none). It now drops what loot doesn't allow and weighs the rest (owner, 2026-10-09: weighted by default).
 */
@Mixin(EnchantRandomlyFunction.class)
abstract class EnchantRandomlyFunctionMixin {
	@WrapOperation(method = "run", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/util/Util;getRandomSafe(Ljava/util/List;Lnet/minecraft/util/RandomSource;)Ljava/util/Optional;"))
	private Optional<Holder<Enchantment>> enchanting2$weighted(List<Holder<Enchantment>> candidates, RandomSource random,
			Operation<Optional<Holder<Enchantment>>> original) {
		return Acquisition.pick(candidates, Source.LOOT, random, () -> original.call(candidates, random));
	}
}
