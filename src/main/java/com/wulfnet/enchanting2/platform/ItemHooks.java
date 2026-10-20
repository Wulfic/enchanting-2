package com.wulfnet.enchanting2.platform;

import java.util.Collection;
import java.util.function.BiConsumer;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.jspecify.annotations.Nullable;

/** Item components and enchantment definitions as they load, and the loader's own enchantability rule. */
public interface ItemHooks {
	/** Changes these items' default components. Call once, during mod init. */
	void modifyDefaultComponents(Collection<Item> items, BiConsumer<DataComponentMap.Builder, Item> modifier);

	/**
	 * Each enchantment while it loads from datapacks, before any server exists. {@code /reload} doesn't re-run it.
	 * Modifiers run in registration order.
	 */
	void onEnchantmentLoad(EnchantmentModifier modifier);

	/**
	 * Whether the item accepts the enchantment at an anvil, by vanilla's {@code supported_items} plus whatever the
	 * loader adds to it (Fabric: {@code EnchantingContext.ACCEPTABLE}).
	 */
	boolean acceptsEnchantment(ItemStack stack, Holder<Enchantment> enchantment);

	@FunctionalInterface
	interface EnchantmentModifier {
		/**
		 * @param items the item registry loading alongside, or {@code null} where the loader can't lend it (Fabric on
		 *              1.21.11, where {@code ItemWideningHook} takes it from its own loader mixin)
		 */
		void modify(ResourceKey<Enchantment> key, Enchantment.Builder builder, @Nullable HolderGetter<Item> items);
	}
}
