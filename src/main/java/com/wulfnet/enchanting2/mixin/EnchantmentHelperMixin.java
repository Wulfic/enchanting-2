package com.wulfnet.enchanting2.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.acquisition.Acquisition;
import com.wulfnet.enchanting2.acquisition.AcquisitionScope;
import com.wulfnet.enchanting2.acquisition.Source;
import com.wulfnet.enchanting2.enchantment.StochasticRounding;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.ToIntFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Phase 9: every weighted enchantment roll goes through {@code selectEnchantment}: loot {@code enchant_with_levels},
 * mob spawn gear and villager-sold gear. Candidates their source doesn't allow are dropped before the first pick, and
 * both picks weigh by the source's weight. The source is {@link AcquisitionScope#current()}.
 *
 * <p>Phase 10a: mob and block experience after {@code mob_experience} / {@code block_experience} effects (XP Boost)
 * round at random instead of truncating, so the average is the multiplied value ({@link StochasticRounding}).
 */
@Mixin(EnchantmentHelper.class)
abstract class EnchantmentHelperMixin {
	@ModifyExpressionValue(method = "selectEnchantment", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;getAvailableEnchantmentResults(ILnet/minecraft/world/item/ItemStack;Ljava/util/stream/Stream;)Ljava/util/List;"))
	private static List<EnchantmentInstance> enchanting2$onlyAllowed(List<EnchantmentInstance> available) {
		Source source = AcquisitionScope.current();
		// A fresh mutable list: vanilla removes from it while picking more enchantments.
		List<EnchantmentInstance> allowed = new ArrayList<>(available.size());
		for (EnchantmentInstance instance : available) {
			if (Acquisition.allows(instance.enchantment(), source)) {
				allowed.add(instance);
			}
		}
		return allowed;
	}

	@WrapOperation(method = "selectEnchantment", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/util/random/WeightedRandom;getRandomItem(Lnet/minecraft/util/RandomSource;Ljava/util/List;Ljava/util/function/ToIntFunction;)Ljava/util/Optional;"),
			require = 2)
	private static Optional<EnchantmentInstance> enchanting2$weigh(RandomSource random, List<EnchantmentInstance> list,
			ToIntFunction<EnchantmentInstance> weight, Operation<Optional<EnchantmentInstance>> original) {
		Source source = AcquisitionScope.current();
		ToIntFunction<EnchantmentInstance> sourceWeight = instance -> Acquisition.weight(instance.enchantment(), source);
		return original.call(random, list, sourceWeight);
	}

	@WrapOperation(method = {"processMobExperience", "processBlockExperience"}, at = @At(value = "INVOKE",
			target = "Lorg/apache/commons/lang3/mutable/MutableFloat;intValue()I"), require = 2)
	private static int enchanting2$roundExperience(MutableFloat experience, Operation<Integer> original,
			@Local(argsOnly = true) ServerLevel level) {
		float value = experience.floatValue();
		if (!Float.isFinite(value)) {
			// Only a broken effect from another mod or datapack gets here: keep vanilla's truncation.
			int vanilla = original.call(experience);
			Enchanting2.LOGGER.warn("Experience after enchantment effects is {}; using vanilla's {}", value, vanilla);
			return vanilla;
		}
		return StochasticRounding.round(value, () -> level.getRandom().nextDouble());
	}
}
