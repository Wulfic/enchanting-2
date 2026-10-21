package com.wulfnet.enchanting2.platform.forgelike;

import com.wulfnet.enchanting2.platform.ItemHooks.EnchantmentModifier;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.HolderGetter;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import org.jspecify.annotations.Nullable;

/**
 * The {@code ItemHooks.onEnchantmentLoad} modifiers on NeoForge and Forge, which have no enchantment-load event: their
 * {@code ItemHooks} add here, and {@code mixin/forgelike/ResourceManagerRegistryLoadTaskMixin} runs them (Phase 13C C4,
 * 13D D4). No loader API, so both loaders' nodes compile it; Fabric nodes leave it out.
 */
public final class EnchantmentLoad {
	private static final List<EnchantmentModifier> MODIFIERS = new CopyOnWriteArrayList<>();

	private EnchantmentLoad() {
	}

	public static void add(EnchantmentModifier modifier) {
		MODIFIERS.add(modifier);
	}

	/** Every registered modifier on one enchantment while it loads, in registration order. */
	public static void modify(ResourceKey<Enchantment> key, Enchantment.Builder builder, @Nullable HolderGetter<Item> items) {
		for (EnchantmentModifier modifier : MODIFIERS) {
			modifier.modify(key, builder, items);
		}
	}
}
