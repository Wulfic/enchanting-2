package com.wulfnet.enchanting2.mixin.neoforge;

import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * NeoForge only (Phase 13C): lets {@link ResourceManagerRegistryLoadTaskMixin} check that no enchantment modifier
 * touched a builder's effects, which that hook doesn't carry over.
 */
@Mixin(Enchantment.Builder.class)
public interface EnchantmentBuilderEffectsAccessor {
	@Accessor("effectLists")
	Map<DataComponentType<?>, List<?>> enchanting2$getEffectLists();

	@Accessor("effectMapBuilder")
	DataComponentMap.Builder enchanting2$getEffectMapBuilder();
}
