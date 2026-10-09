package com.wulfnet.enchanting2.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A registry's holders are created with the registry as their owner ({@code MappedRegistry.createStandAlone(this, key)}). */
@Mixin(Holder.Reference.class)
public interface HolderReferenceAccessor {
	@Accessor("owner")
	HolderOwner<?> enchanting2$owner();
}
