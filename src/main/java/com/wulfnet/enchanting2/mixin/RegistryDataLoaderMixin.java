package com.wulfnet.enchanting2.mixin;

import com.google.gson.JsonElement;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.serialization.Decoder;
import com.wulfnet.enchanting2.enchantment.ItemWideningHook;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Lends {@link ItemWideningHook} the item lookup an enchantment's JSON is parsed with, for exactly the load of that
 * element: Fabric fires {@code EnchantmentEvents.MODIFY} inside this method. Wrapping the whole method clears it even
 * when the load throws, so a later element never sees a stale lookup.
 */
@Mixin(RegistryDataLoader.class)
abstract class RegistryDataLoaderMixin {
	@WrapMethod(method = "loadElementFromResource")
	private static <E> void enchanting2$lendItems(WritableRegistry<E> registry, Decoder<E> decoder,
			RegistryOps<JsonElement> ops, ResourceKey<E> key, Resource resource, RegistrationInfo info,
			Operation<Void> original) {
		if (!registry.key().equals(Registries.ENCHANTMENT)) {
			original.call(registry, decoder, ops, key, resource, info);
			return;
		}
		ItemWideningHook.lend(ops.getter(Registries.ITEM).orElse(null));
		try {
			original.call(registry, decoder, ops, key, resource, info);
		} finally {
			ItemWideningHook.lend(null);
		}
	}
}
