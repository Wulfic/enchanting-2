package com.wulfnet.enchanting2.enchantment;

import com.wulfnet.enchanting2.Enchanting2;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Our own enchantments (DESIGN §9), defined as data in {@code data/enchanting2/enchantment/}. Like
 * {@link VanillaEnchantments}, this is for code with no registry (the title-screen config list).
 * {@code NewEnchantmentsGameTest} pins it to the registry's {@code enchanting2} namespace, so a JSON added without a key
 * here (or the reverse) fails there.
 */
public final class ModEnchantments {
	public static final ResourceKey<Enchantment> WITHERING = key("withering");
	public static final ResourceKey<Enchantment> POISONING = key("poisoning");
	public static final ResourceKey<Enchantment> FREEZING = key("freezing");
	public static final ResourceKey<Enchantment> ATTACK_SPEED = key("attack_speed");
	public static final ResourceKey<Enchantment> CRITICAL_STRIKE = key("critical_strike");
	public static final ResourceKey<Enchantment> REACH = key("reach");
	public static final ResourceKey<Enchantment> XP_BOOST = key("xp_boost");
	public static final ResourceKey<Enchantment> TOUGHNESS = key("toughness");
	public static final ResourceKey<Enchantment> HEALTH = key("health");
	public static final ResourceKey<Enchantment> SWIFTNESS = key("swiftness");
	public static final ResourceKey<Enchantment> CUSHION = key("cushion");
	public static final ResourceKey<Enchantment> VAMPIRISM = key("vampirism");
	public static final ResourceKey<Enchantment> BEHEADING = key("beheading");
	public static final ResourceKey<Enchantment> SMELTING = key("smelting");
	public static final ResourceKey<Enchantment> TRUE_SHOT_AURA = key("true_shot_aura");
	public static final ResourceKey<Enchantment> VISION = key("vision");

	public static final List<ResourceKey<Enchantment>> KEYS = List.of(
			WITHERING, POISONING, FREEZING, ATTACK_SPEED, CRITICAL_STRIKE, REACH, XP_BOOST, TOUGHNESS, HEALTH,
			SWIFTNESS, CUSHION, VAMPIRISM, BEHEADING, SMELTING, TRUE_SHOT_AURA, VISION);

	private ModEnchantments() {
	}

	private static ResourceKey<Enchantment> key(String path) {
		return ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath(Enchanting2.MOD_ID, path));
	}

	/** {@link #KEYS} as {@code namespace:path} strings. */
	public static Set<String> ids() {
		return KEYS.stream().map(key -> key.identifier().toString()).collect(Collectors.toUnmodifiableSet());
	}

	/** Every enchantment known without a registry: vanilla's and ours. */
	public static Set<String> knownWithoutRegistry() {
		return Stream.concat(VanillaEnchantments.ids().stream(), ids().stream())
				.collect(Collectors.toUnmodifiableSet());
	}
}
