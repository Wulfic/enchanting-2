package com.wulfnet.enchanting2.mixin;

import net.minecraft.core.HolderSet;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * {@code Enchantment.Builder} has no setter for its {@code final} definition, which holds the max level. Fabric has its
 * own accessor on this class, so these names carry our prefix to avoid clashing with it.
 */
@Mixin(Enchantment.Builder.class)
public interface EnchantmentBuilderAccessor {
	@Accessor("definition")
	Enchantment.EnchantmentDefinition enchanting2$getDefinition();

	@Mutable
	@Accessor("definition")
	void enchanting2$setDefinition(Enchantment.EnchantmentDefinition definition);

	@Accessor("exclusiveSet")
	HolderSet<Enchantment> enchanting2$getExclusiveSet();
}
