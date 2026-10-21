package com.wulfnet.enchanting2.platform.neoforge;

import com.wulfnet.enchanting2.platform.ItemHooks;
import com.wulfnet.enchanting2.platform.forgelike.EnchantmentLoad;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;

public final class NeoForgeItemHooks implements ItemHooks {
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
		// NeoForge has no event for it (TODO.md 13C C4): the shared registry-load mixin runs these.
		EnchantmentLoad.add(modifier);
	}

	@Override
	public boolean acceptsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
		// NeoForge's anvil rule: supported_items, plus whatever IItemExtension.supportsEnchantment adds.
		return stack.supportsEnchantment(enchantment);
	}
}
