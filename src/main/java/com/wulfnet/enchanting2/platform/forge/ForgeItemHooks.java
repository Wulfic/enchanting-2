package com.wulfnet.enchanting2.platform.forge;

import com.wulfnet.enchanting2.platform.ItemHooks;
import com.wulfnet.enchanting2.platform.forgelike.EnchantmentLoad;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

public final class ForgeItemHooks implements ItemHooks {
	private static final List<DefaultComponents> DEFAULT_COMPONENTS = new CopyOnWriteArrayList<>();

	@Override
	public void modifyDefaultComponents(Collection<Item> items, BiConsumer<DataComponentMap.Builder, Item> modifier) {
		// Not Forge's GatherComponentsEvent.Item: on 26.3 it only feeds Item.components(), while an ItemStack takes its
		// defaults from the registry holder, which vanilla binds from DataComponentInitializers. The Forge-only
		// mixin/forge/DataComponentInitializersMixin adds ours there (gotchas.md "Forge 26.3 default components").
		DEFAULT_COMPONENTS.add(new DefaultComponents(List.copyOf(items), modifier));
	}

	/** Adds every registered modifier's components to the items' builders. Called by the mixin, on every bind. */
	public static void addDefaultComponents(Map<ResourceKey<?>, DataComponentMap.Builder> builders) {
		for (DefaultComponents entry : DEFAULT_COMPONENTS) {
			for (Item item : entry.items()) {
				ResourceKey<Item> key = BuiltInRegistries.ITEM.getResourceKey(item).orElseThrow(
						() -> new IllegalStateException("default components for an unregistered item " + item));
				entry.modifier().accept(builders.computeIfAbsent(key, k -> DataComponentMap.builder()), item);
			}
		}
	}

	@Override
	public void onEnchantmentLoad(EnchantmentModifier modifier) {
		// Forge has no event for it either: the registry-load mixin NeoForge uses runs these (TODO.md 13D D4).
		EnchantmentLoad.add(modifier);
	}

	@Override
	public boolean acceptsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
		// Forge's anvil asks vanilla's canEnchant (supported_items) per enchantment; it adds no item hook to that, only
		// a whole-book veto (IForgeItem.isBookEnchantable) that takes no enchantment.
		return enchantment.value().canEnchant(stack);
	}

	private record DefaultComponents(List<Item> items, BiConsumer<DataComponentMap.Builder, Item> modifier) {
	}
}
