package com.wulfnet.enchanting2.platform.fabric;

import com.wulfnet.enchanting2.platform.ItemHooks;
import java.util.Collection;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.fabricmc.fabric.api.item.v1.EnchantingContext;
import net.fabricmc.fabric.api.item.v1.EnchantmentEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
//? if >=26.1
/*import net.minecraft.core.registries.Registries;*/
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

public final class FabricItemHooks implements ItemHooks {
	@Override
	public void modifyDefaultComponents(Collection<Item> items, BiConsumer<DataComponentMap.Builder, Item> modifier) {
		DefaultItemComponentEvents.MODIFY.register(context -> context.modify(items, modifier));
	}

	@Override
	public void onEnchantmentLoad(EnchantmentModifier modifier) {
		// Fabric API 26.1 deprecated MODIFY for MODIFY_WITH_LOOKUP (the same call plus the registries loading
		// alongside). 26.3's lookup returns the getter itself instead of a RegistryInfo holding it.
		//? if >=26.3 {
		/*EnchantmentEvents.MODIFY_WITH_LOOKUP.register((key, builder, source, registries) -> modifier.modify(key,
				builder, registries.<Item>lookup(Registries.ITEM).orElse(null)));
		*///?} else if >=26.1 {
		/*EnchantmentEvents.MODIFY_WITH_LOOKUP.register((key, builder, source, registries) -> modifier.modify(key,
				builder, registries.<Item>lookup(Registries.ITEM)
						.map(net.minecraft.resources.RegistryOps.RegistryInfo::getter).orElse(null)));
		*///?} else
		EnchantmentEvents.MODIFY.register((key, builder, source) -> modifier.modify(key, builder, null));
	}

	@Override
	public boolean acceptsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
		return stack.canBeEnchantedWith(enchantment, EnchantingContext.ACCEPTABLE);
	}
}
