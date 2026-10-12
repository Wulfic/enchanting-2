package com.wulfnet.enchanting2.enchantment;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.mixin.EnchantmentBuilderAccessor;
import java.util.Optional;
import net.fabricmc.fabric.api.item.v1.EnchantmentEvents;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import org.jspecify.annotations.Nullable;

/**
 * Points the {@link ItemWidening} enchantments at our item tags while enchantments load (Phase 10gA). {@code MODIFY}
 * gets no registry lookup, and the static item registry's tags aren't bound yet at this point, so
 * {@code RegistryDataLoaderMixin} lends us the item lookup the JSON itself was parsed with, for the length of one
 * element's load. Like {@link MaxLevelHook}, {@code /reload} doesn't re-run this; the tags' contents do reload.
 */
public final class ItemWideningHook {
	/** The item lookup of the element being loaded, set only around {@code loadElementFromResource}. */
	private static final ThreadLocal<HolderGetter<Item>> ITEMS = new ThreadLocal<>();

	private ItemWideningHook() {
	}

	public static void register() {
		EnchantmentEvents.MODIFY.register((key, builder, source) -> apply(key, builder));
	}

	/** Called by the loader mixin before an element loads; {@code null} clears it afterwards. */
	public static void lend(@Nullable HolderGetter<Item> items) {
		if (items == null) {
			ITEMS.remove();
		} else {
			ITEMS.set(items);
		}
	}

	private static void apply(ResourceKey<Enchantment> key, Enchantment.Builder builder) {
		String id = key.identifier().toString();
		ItemWidening.Row row = ItemWidening.ROWS.get(id);
		if (row == null) {
			return;
		}
		EnchantmentBuilderAccessor accessor = (EnchantmentBuilderAccessor) builder;
		Enchantment.EnchantmentDefinition d = accessor.enchanting2$getDefinition();
		ItemWidening.Why why = ItemWidening.decide(id, tagId(d.supportedItems()),
				d.primaryItems().flatMap(ItemWideningHook::tagId), d.primaryItems().isPresent());
		if (why == ItemWidening.Why.CHANGED_ELSEWHERE) {
			Enchanting2.LOGGER.info("{} loaded with items {} (primary {}), not vanilla's; something else changed them, so "
					+ "DESIGN §4's widening to {} is not applied.", id, describe(d.supportedItems()),
					d.primaryItems().map(ItemWideningHook::describe).orElse("none"), row.supported());
			return;
		}
		HolderGetter<Item> items = ITEMS.get();
		if (items == null) {
			Enchanting2.LOGGER.warn("{} was not loaded from a data file, so there's no item lookup to widen it to {}; it "
					+ "keeps vanilla's items.", id, row.supported());
			return;
		}
		Optional<HolderSet<Item>> supported = tag(items, row.supported());
		Optional<Optional<HolderSet<Item>>> primary = row.primary().isEmpty() ? Optional.of(d.primaryItems())
				: tag(items, row.primary().get()).map(Optional::of);
		if (supported.isEmpty() || primary.isEmpty()) {
			Enchanting2.LOGGER.warn("{}: item tag {} or {} is missing from the loaded data, so it keeps vanilla's items.",
					id, row.supported(), row.primary().orElse("-"));
			return;
		}
		accessor.enchanting2$setDefinition(new Enchantment.EnchantmentDefinition(supported.get(), primary.get(),
				d.weight(), d.maxLevel(), d.minCost(), d.maxCost(), d.anvilCost(), d.slots()));
		// Fabric keeps the edit only if a builder method ran (gotchas.md 2026-10-07); this one is a no-op.
		builder.exclusiveWith(accessor.enchanting2$getExclusiveSet());
		Enchanting2.LOGGER.debug("{} items {} -> {} (DESIGN §4)", id, row.vanillaSupported(), row.supported());
	}

	private static Optional<HolderSet<Item>> tag(HolderGetter<Item> items, String id) {
		return items.get(TagKey.create(Registries.ITEM, Identifier.parse(id))).map(set -> set);
	}

	private static Optional<String> tagId(HolderSet<Item> set) {
		return set.unwrapKey().map(tag -> tag.location().toString());
	}

	private static String describe(HolderSet<Item> set) {
		return tagId(set).map(tag -> "#" + tag).orElse("a list of " + set.size());
	}
}
