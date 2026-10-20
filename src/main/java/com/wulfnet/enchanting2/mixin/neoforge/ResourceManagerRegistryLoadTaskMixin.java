package com.wulfnet.enchanting2.mixin.neoforge;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Lifecycle;
import com.wulfnet.enchanting2.mixin.EnchantmentBuilderAccessor;
import com.wulfnet.enchanting2.platform.neoforge.NeoForgeItemHooks;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.RegistryLoadTask;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceManagerRegistryLoadTask;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge only (Phase 13C C4): its counterpart of Fabric API's {@code EnchantmentEvents.MODIFY_WITH_LOOKUP}, which
 * NeoForge lacks. Copied from Fabric item-api 14.1.0's own {@code ResourceManagerRegistryLoadTaskMixin}: each
 * enchantment decoded from a datapack goes through {@link NeoForgeItemHooks#modifyEnchantment} with the item lookup it
 * was parsed with. The lambda name was checked with javap on NeoForge 26.3.0.95's patched Minecraft.
 *
 * <p>Our modifiers change only the definition and the exclusive set, so those two come from the builder and the
 * effects stay the JSON's; a modifier that touches effects fails the load rather than losing them. A changed
 * enchantment drops its known-pack info, as Fabric does, so the server sends it to clients instead of trusting theirs.
 * Extends the target's superclass only to see its protected {@code PendingRegistration} (Fabric uses a class tweaker).
 */
@Mixin(ResourceManagerRegistryLoadTask.class)
abstract class ResourceManagerRegistryLoadTaskMixin<T> extends RegistryLoadTask<T> {
	private ResourceManagerRegistryLoadTaskMixin(RegistryDataLoader.RegistryData<T> data, Lifecycle lifecycle,
			Map<ResourceKey<?>, Exception> loadingErrors) {
		super(data, lifecycle, loadingErrors);
	}

	@WrapOperation(method = "lambda$load$2",
			at = @At(value = "NEW", target = "net/minecraft/resources/RegistryLoadTask$PendingRegistration"))
	private <E> RegistryLoadTask.PendingRegistration<?> enchanting2$modifyEnchantment(ResourceKey<E> key,
			Either<E, Exception> value, RegistrationInfo info, Operation<RegistryLoadTask.PendingRegistration<E>> original,
			@Local(argsOnly = true) RegistryOps<?> ops) {
		Optional<E> loaded = value.left();
		if (loaded.isPresent() && loaded.get() instanceof Enchantment enchantment) {
			Enchantment modified = enchanting2$modify(ResourceKey.create(Registries.ENCHANTMENT, key.identifier()),
					enchantment, ops.getter(Registries.ITEM).orElse(null));
			if (modified != null) {
				return original.call(key, Either.left(modified), new RegistrationInfo(Optional.empty(), info.lifecycle()));
			}
		}
		return original.call(key, value, info);
	}

	/** The enchantment with every modifier applied, or {@code null} when none changed it. */
	private static @Nullable Enchantment enchanting2$modify(ResourceKey<Enchantment> key, Enchantment original,
			@Nullable HolderGetter<Item> items) {
		Enchantment.Builder builder = Enchantment.enchantment(original.definition());
		builder.exclusiveWith(original.exclusiveSet());
		NeoForgeItemHooks.modifyEnchantment(key, builder, items);
		EnchantmentBuilderEffectsAccessor effects = (EnchantmentBuilderEffectsAccessor) builder;
		if (!effects.enchanting2$getEffectLists().isEmpty() || !effects.enchanting2$getEffectMapBuilder().build().isEmpty()) {
			throw new IllegalStateException("An Enchanting 2 modifier changed " + key.identifier() + "'s effects, which "
					+ "the NeoForge enchantment hook doesn't carry over (ResourceManagerRegistryLoadTaskMixin)");
		}
		EnchantmentBuilderAccessor accessor = (EnchantmentBuilderAccessor) builder;
		Enchantment.EnchantmentDefinition definition = accessor.enchanting2$getDefinition();
		HolderSet<Enchantment> exclusive = accessor.enchanting2$getExclusiveSet();
		if (definition.equals(original.definition()) && exclusive.equals(original.exclusiveSet())) {
			return null;
		}
		return new Enchantment(original.description(), definition, exclusive, original.effects());
	}
}
