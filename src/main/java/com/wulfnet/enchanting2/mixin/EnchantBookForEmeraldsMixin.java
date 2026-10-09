package com.wulfnet.enchanting2.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.wulfnet.enchanting2.acquisition.Acquisition;
import com.wulfnet.enchanting2.acquisition.Source;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.villager.VillagerTrades;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Phase 9 librarian books. Vanilla picks uniformly from the trade's tag ({@code #tradeable}, or a trade-rebalance
 * biome tag); it now drops what trades don't allow and weighs the rest. The price doubles for {@code treasure}, which
 * an {@code enchantments.json5} entry can override in either direction (owner, 2026-10-09: our rules only, the tag
 * itself is untouched).
 */
@Mixin(VillagerTrades.EnchantBookForEmeralds.class)
abstract class EnchantBookForEmeraldsMixin {
	@WrapOperation(method = "getOffer", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/core/Registry;getRandomElementOf(Lnet/minecraft/tags/TagKey;Lnet/minecraft/util/RandomSource;)Ljava/util/Optional;"))
	private Optional<Holder<Enchantment>> enchanting2$weighted(Registry<Enchantment> registry, TagKey<Enchantment> tag,
			RandomSource random, Operation<Optional<Holder<Enchantment>>> original) {
		List<Holder<Enchantment>> members = registry.get(tag).map(set -> set.stream().toList()).orElse(List.of());
		return Acquisition.pick(members, Source.TRADE, random, () -> original.call(registry, tag, random));
	}

	@WrapOperation(method = "getOffer", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/core/Holder;is(Lnet/minecraft/tags/TagKey;)Z"))
	private boolean enchanting2$treasurePrice(Holder<Enchantment> enchantment, TagKey<Enchantment> doubleTradePrice,
			Operation<Boolean> original) {
		return Acquisition.treasure(enchantment, original.call(enchantment, doubleTradePrice));
	}
}
