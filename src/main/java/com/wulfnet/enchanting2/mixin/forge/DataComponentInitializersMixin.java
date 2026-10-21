package com.wulfnet.enchanting2.mixin.forge;

import com.wulfnet.enchanting2.platform.forge.ForgeItemHooks;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Forge only (Phase 13D): adds {@code ItemHooks.modifyDefaultComponents}' components where vanilla gathers every
 * element's default components before binding them to its registry holder (at startup and on each reload), after
 * vanilla's own. Forge's {@code GatherComponentsEvent.Item} never reaches an ItemStack on 26.3 (ForgeItemHooks).
 */
@Mixin(DataComponentInitializers.class)
abstract class DataComponentInitializersMixin {
	@Inject(method = "runInitializers", at = @At("RETURN"))
	private void enchanting2$addDefaultComponents(HolderLookup.Provider context,
			CallbackInfoReturnable<Map<ResourceKey<?>, DataComponentMap.Builder>> cir) {
		ForgeItemHooks.addDefaultComponents(cir.getReturnValue());
	}
}
