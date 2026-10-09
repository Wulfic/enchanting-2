package com.wulfnet.enchanting2.table;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.acquisition.AcquisitionRules;
import com.wulfnet.enchanting2.acquisition.Source;
import com.wulfnet.enchanting2.config.TableSection;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * DESIGN §6: which enchantments the table sells. The {@code enchanting2:offered_at_table} tag is vanilla's
 * {@code #in_enchanting_table} (which mods already join) plus Mending, Frost Walker, Binding and Vanishing (owner,
 * 2026-10-07). Datapacks can extend it, and {@code enchantments.json5} takes members out ({@code table: false} or
 * {@code enabled: false}, Phase 9).
 */
public final class TablePool {
	public static final TagKey<Enchantment> OFFERED = TagKey.create(Registries.ENCHANTMENT,
			Identifier.fromNamespaceAndPath(Enchanting2.MOD_ID, "offered_at_table"));

	private TablePool() {
	}

	/** Every offered enchantment the rules allow at the table, in ID order, so rows never depend on tag order. */
	public static List<Holder<Enchantment>> members(HolderLookup.RegistryLookup<Enchantment> enchantments,
			AcquisitionRules rules) {
		return enchantments.get(OFFERED)
				.map(set -> set.stream().filter(h -> rules.allows(h.getRegisteredName(), Source.TABLE))
						.sorted(Comparator.comparing(Holder::getRegisteredName)).toList())
				.orElseGet(() -> {
					// The tag ships in the mod jar, so it can only be missing if a datapack broke tag loading.
					Enchanting2.LOGGER.warn("Enchantment tag {} is missing; the enchanting table offers nothing",
							OFFERED.location());
					return List.of();
				});
	}

	/**
	 * Treasure needs 15 shelves when {@code treasure_requires_full_power} is on. Treasure is {@code #treasure} unless the
	 * enchantment's {@code treasure} override says otherwise. A curse is a discount, not power, so it's never gated, even
	 * though vanilla tags curses as treasure.
	 */
	public static boolean needsFullPower(Holder<Enchantment> enchantment, TableSection table, AcquisitionRules rules) {
		return table.treasureRequiresFullPower()
				&& rules.treasure(enchantment.getRegisteredName(), enchantment.is(EnchantmentTags.TREASURE))
				&& !enchantment.is(EnchantmentTags.CURSE);
	}
}
