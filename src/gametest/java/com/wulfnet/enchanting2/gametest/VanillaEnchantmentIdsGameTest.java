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
 * DESIGN §8 against the live registry: every vanilla enchantment exists with its v1 max level (the default config,
 * no overrides), and no vanilla enchantment exists that §8 doesn't classify. Depth Strider stays 3, Quick Charge goes
 * to 4, and Lure and Riptide stay 3 until Stage 4D.
 */
public class VanillaEnchantmentIdsGameTest {
	private static final Map<ResourceKey<Enchantment>, Integer> V1_MAX = Map.ofEntries(
			Map.entry(Enchantments.PROTECTION, 5),
			Map.entry(Enchantments.FIRE_PROTECTION, 5),
			Map.entry(Enchantments.BLAST_PROTECTION, 5),
			Map.entry(Enchantments.PROJECTILE_PROTECTION, 5),
			Map.entry(Enchantments.FEATHER_FALLING, 5),
			Map.entry(Enchantments.RESPIRATION, 5),
			Map.entry(Enchantments.AQUA_AFFINITY, 1),
			Map.entry(Enchantments.THORNS, 5),
			Map.entry(Enchantments.DEPTH_STRIDER, 3),
			Map.entry(Enchantments.FROST_WALKER, 5),
			Map.entry(Enchantments.BINDING_CURSE, 1),
			Map.entry(Enchantments.SOUL_SPEED, 5),
			Map.entry(Enchantments.SWIFT_SNEAK, 5),
			Map.entry(Enchantments.SHARPNESS, 5),
			Map.entry(Enchantments.SMITE, 5),
			Map.entry(Enchantments.BANE_OF_ARTHROPODS, 5),
			Map.entry(Enchantments.KNOCKBACK, 5),
			Map.entry(Enchantments.FIRE_ASPECT, 5),
			Map.entry(Enchantments.LOOTING, 5),
			Map.entry(Enchantments.SWEEPING_EDGE, 5),
			Map.entry(Enchantments.EFFICIENCY, 5),
			Map.entry(Enchantments.SILK_TOUCH, 1),
			Map.entry(Enchantments.UNBREAKING, 5),
			Map.entry(Enchantments.FORTUNE, 5),
			Map.entry(Enchantments.POWER, 5),
			Map.entry(Enchantments.PUNCH, 5),
			Map.entry(Enchantments.FLAME, 1),
			Map.entry(Enchantments.INFINITY, 1),
			Map.entry(Enchantments.LUCK_OF_THE_SEA, 5),
			Map.entry(Enchantments.LURE, 3),
			Map.entry(Enchantments.LOYALTY, 5),
			Map.entry(Enchantments.IMPALING, 5),
			Map.entry(Enchantments.RIPTIDE, 3),
			Map.entry(Enchantments.CHANNELING, 1),
			Map.entry(Enchantments.MULTISHOT, 1),
			Map.entry(Enchantments.QUICK_CHARGE, 4),
			Map.entry(Enchantments.PIERCING, 5),
			Map.entry(Enchantments.DENSITY, 5),
			Map.entry(Enchantments.BREACH, 5),
			Map.entry(Enchantments.WIND_BURST, 5),
			Map.entry(Enchantments.MENDING, 1),
			Map.entry(Enchantments.VANISHING_CURSE, 1),
			Map.entry(Enchantments.LUNGE, 5));

	@GameTest
	public void everyDesignedEnchantmentExistsAtItsV1Max(GameTestHelper helper) {
		HolderLookup.RegistryLookup<Enchantment> registry =
				helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);

		V1_MAX.forEach((key, expectedMax) -> {
			Enchantment enchantment = registry.get(key)
					.orElseThrow(() -> helper.assertionException(
							Component.literal("missing from the registry: " + key)))
					.value();
			// GameTestHelper#assertValueEqual takes (expected, actual), despite the usual JUnit order.
			helper.assertValueEqual(expectedMax, enchantment.getMaxLevel(),
					Component.literal("v1 max level of " + key));
		});
		helper.succeed();
	}

	@GameTest
	public void noVanillaEnchantmentIsMissingFromTheDesign(GameTestHelper helper) {
		Set<ResourceKey<Enchantment>> unclassified = new HashSet<>();
		helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElementIds()
				.filter(key -> key.identifier().getNamespace().equals("minecraft"))
				.filter(key -> !V1_MAX.containsKey(key))
				.forEach(unclassified::add);

		helper.assertTrue(unclassified.isEmpty(),
				Component.literal("vanilla enchantments not classified in DESIGN §8: " + unclassified));
		helper.succeed();
	}
}
