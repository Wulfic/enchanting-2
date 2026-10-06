package com.wulfnet.enchanting2.gametest;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * DESIGN §8 is written against the 1.21.11 vanilla registry. This pins that assumption: every enchantment
 * the max-level table names must exist with the vanilla max level the table states, and no vanilla
 * enchantment may exist that §8 doesn't classify. Phase 4 changes the expected levels to the v1 column.
 */
public class VanillaEnchantmentIdsGameTest {
	private static final Map<ResourceKey<Enchantment>, Integer> VANILLA_MAX = Map.ofEntries(
			Map.entry(Enchantments.PROTECTION, 4),
			Map.entry(Enchantments.FIRE_PROTECTION, 4),
			Map.entry(Enchantments.BLAST_PROTECTION, 4),
			Map.entry(Enchantments.PROJECTILE_PROTECTION, 4),
			Map.entry(Enchantments.FEATHER_FALLING, 4),
			Map.entry(Enchantments.RESPIRATION, 3),
			Map.entry(Enchantments.AQUA_AFFINITY, 1),
			Map.entry(Enchantments.THORNS, 3),
			Map.entry(Enchantments.DEPTH_STRIDER, 3),
			Map.entry(Enchantments.FROST_WALKER, 2),
			Map.entry(Enchantments.BINDING_CURSE, 1),
			Map.entry(Enchantments.SOUL_SPEED, 3),
			Map.entry(Enchantments.SWIFT_SNEAK, 3),
			Map.entry(Enchantments.SHARPNESS, 5),
			Map.entry(Enchantments.SMITE, 5),
			Map.entry(Enchantments.BANE_OF_ARTHROPODS, 5),
			Map.entry(Enchantments.KNOCKBACK, 2),
			Map.entry(Enchantments.FIRE_ASPECT, 2),
			Map.entry(Enchantments.LOOTING, 3),
			Map.entry(Enchantments.SWEEPING_EDGE, 3),
			Map.entry(Enchantments.EFFICIENCY, 5),
			Map.entry(Enchantments.SILK_TOUCH, 1),
			Map.entry(Enchantments.UNBREAKING, 3),
			Map.entry(Enchantments.FORTUNE, 3),
			Map.entry(Enchantments.POWER, 5),
			Map.entry(Enchantments.PUNCH, 2),
			Map.entry(Enchantments.FLAME, 1),
			Map.entry(Enchantments.INFINITY, 1),
			Map.entry(Enchantments.LUCK_OF_THE_SEA, 3),
			Map.entry(Enchantments.LURE, 3),
			Map.entry(Enchantments.LOYALTY, 3),
			Map.entry(Enchantments.IMPALING, 5),
			Map.entry(Enchantments.RIPTIDE, 3),
			Map.entry(Enchantments.CHANNELING, 1),
			Map.entry(Enchantments.MULTISHOT, 1),
			Map.entry(Enchantments.QUICK_CHARGE, 3),
			Map.entry(Enchantments.PIERCING, 4),
			Map.entry(Enchantments.DENSITY, 5),
			Map.entry(Enchantments.BREACH, 4),
			Map.entry(Enchantments.WIND_BURST, 3),
			Map.entry(Enchantments.MENDING, 1),
			Map.entry(Enchantments.VANISHING_CURSE, 1),
			Map.entry(Enchantments.LUNGE, 3));

	@GameTest
	public void everyDesignedEnchantmentExistsAtItsVanillaMax(GameTestHelper helper) {
		HolderLookup.RegistryLookup<Enchantment> registry =
				helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);

		VANILLA_MAX.forEach((key, expectedMax) -> {
			Enchantment enchantment = registry.get(key)
					.orElseThrow(() -> helper.assertionException(
							Component.literal("missing from the registry: " + key)))
					.value();
			// GameTestHelper#assertValueEqual takes (expected, actual), despite the usual JUnit order.
			helper.assertValueEqual(expectedMax, enchantment.getMaxLevel(),
					Component.literal("vanilla max level of " + key));
		});
		helper.succeed();
	}

	@GameTest
	public void noVanillaEnchantmentIsMissingFromTheDesign(GameTestHelper helper) {
		Set<ResourceKey<Enchantment>> unclassified = new HashSet<>();
		helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElementIds()
				.filter(key -> key.identifier().getNamespace().equals("minecraft"))
				.filter(key -> !VANILLA_MAX.containsKey(key))
				.forEach(unclassified::add);

		helper.assertTrue(unclassified.isEmpty(),
				Component.literal("vanilla enchantments not classified in DESIGN §8: " + unclassified));
		helper.succeed();
	}
}
