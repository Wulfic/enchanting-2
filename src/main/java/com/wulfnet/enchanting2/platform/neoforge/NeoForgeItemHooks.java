package com.wulfnet.enchanting2.platform.neoforge;

import com.wulfnet.enchanting2.platform.ItemHooks;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import org.jspecify.annotations.Nullable;

public final class NeoForgeItemHooks implements ItemHooks {
	/** Run, in order, by the enchantment registry-load mixin (NeoForge has no event for it; TODO.md 13C C4). */
	private static final List<EnchantmentModifier> ENCHANTMENT_MODIFIERS = new CopyOnWriteArrayList<>();

	@Override
	public void modifyDefaultComponents(Collection<Item> items, BiConsumer<DataComponentMap.Builder, Item> modifier) {
		List<Item> copy = List.copyOf(items);
		NeoForgeBus.mod().addListener(ModifyDefaultComponentsEvent.class, event -> {
			for (Item item : copy) {
				event.modify(item, (builder, lookup, key) -> modifier.accept(builder, item));
			}
		});
	}

	@Override
	public void onEnchantmentLoad(EnchantmentModifier modifier) {
		ENCHANTMENT_MODIFIERS.add(modifier);
	}

	/** Every registered modifier on one enchantment while it loads. Called by the registry-load mixin. */
	public static void modifyEnchantment(ResourceKey<Enchantment> key, Enchantment.Builder builder,
			@Nullable HolderGetter<Item> items) {
		for (EnchantmentModifier modifier : ENCHANTMENT_MODIFIERS) {
			modifier.modify(key, builder, items);
		}
	}

	@Override
	public boolean acceptsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
		// NeoForge's anvil rule: supported_items, plus whatever IItemExtension.supportsEnchantment adds.
		return stack.supportsEnchantment(enchantment);
	}
}
