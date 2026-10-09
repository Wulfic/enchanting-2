package com.wulfnet.enchanting2.enchantment;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Every vanilla enchantment, for code that runs with no world and so no registry: the config screen on the title
 * screen. {@code VanillaEnchantmentIdsGameTest} pins it to the live registry, so a version bump that adds one fails
 * there.
 */
public final class VanillaEnchantments {
	public static final List<ResourceKey<Enchantment>> KEYS = List.of(
			Enchantments.PROTECTION, Enchantments.FIRE_PROTECTION, Enchantments.BLAST_PROTECTION,
			Enchantments.PROJECTILE_PROTECTION, Enchantments.FEATHER_FALLING, Enchantments.RESPIRATION,
			Enchantments.AQUA_AFFINITY, Enchantments.THORNS, Enchantments.DEPTH_STRIDER, Enchantments.FROST_WALKER,
			Enchantments.BINDING_CURSE, Enchantments.SOUL_SPEED, Enchantments.SWIFT_SNEAK, Enchantments.SHARPNESS,
			Enchantments.SMITE, Enchantments.BANE_OF_ARTHROPODS, Enchantments.KNOCKBACK, Enchantments.FIRE_ASPECT,
			Enchantments.LOOTING, Enchantments.SWEEPING_EDGE, Enchantments.EFFICIENCY, Enchantments.SILK_TOUCH,
			Enchantments.UNBREAKING, Enchantments.FORTUNE, Enchantments.POWER, Enchantments.PUNCH, Enchantments.FLAME,
			Enchantments.INFINITY, Enchantments.LUCK_OF_THE_SEA, Enchantments.LURE, Enchantments.LOYALTY,
			Enchantments.IMPALING, Enchantments.RIPTIDE, Enchantments.CHANNELING, Enchantments.MULTISHOT,
			Enchantments.QUICK_CHARGE, Enchantments.PIERCING, Enchantments.DENSITY, Enchantments.BREACH,
			Enchantments.WIND_BURST, Enchantments.MENDING, Enchantments.VANISHING_CURSE, Enchantments.LUNGE);

	private VanillaEnchantments() {
	}

	/** {@link #KEYS} as {@code namespace:path} strings. */
	public static Set<String> ids() {
		return KEYS.stream().map(key -> key.identifier().toString()).collect(Collectors.toUnmodifiableSet());
	}
}
