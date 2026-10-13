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
	public static final ResourceKey<Enchantment> STEP_ASSIST = key("step_assist");
	public static final ResourceKey<Enchantment> ANCHORED = key("anchored");
	public static final ResourceKey<Enchantment> SURE_FOOTING = key("sure_footing");
	public static final ResourceKey<Enchantment> CALM_GAZE = key("calm_gaze");
	public static final ResourceKey<Enchantment> PHANTOM_WARD = key("phantom_ward");
	public static final ResourceKey<Enchantment> CLARITY = key("clarity");
	public static final ResourceKey<Enchantment> SUSTENANCE = key("sustenance");
	public static final ResourceKey<Enchantment> ENDURANCE = key("endurance");
	public static final ResourceKey<Enchantment> FRAGILITY = key("fragility");
	public static final ResourceKey<Enchantment> GREED = key("greed");
	public static final ResourceKey<Enchantment> FUMBLING = key("fumbling");
	public static final ResourceKey<Enchantment> WEIGHT = key("weight");
	public static final ResourceKey<Enchantment> HUNGER = key("hunger");
	public static final ResourceKey<Enchantment> LAVA_WALKING = key("lava_walking");
	public static final ResourceKey<Enchantment> SNOW_WALKER = key("snow_walker");
	public static final ResourceKey<Enchantment> STEADFAST = key("steadfast");
	public static final ResourceKey<Enchantment> SPIKED = key("spiked");
	public static final ResourceKey<Enchantment> BULWARK = key("bulwark");
	public static final ResourceKey<Enchantment> RIPOSTE = key("riposte");
	public static final ResourceKey<Enchantment> DEFLECT = key("deflect");
	public static final ResourceKey<Enchantment> STEADY_STANCE = key("steady_stance");
	public static final ResourceKey<Enchantment> SHIELD_BASH = key("shield_bash");
	public static final ResourceKey<Enchantment> MAGNETIC = key("magnetic");
	public static final ResourceKey<Enchantment> ENDER_LINK = key("ender_link");
	public static final ResourceKey<Enchantment> KEEP_ON_DEATH = key("keep_on_death");
	public static final ResourceKey<Enchantment> AREA_MINING = key("area_mining");
	public static final ResourceKey<Enchantment> REPLANT = key("replant");

	public static final List<ResourceKey<Enchantment>> KEYS = List.of(
			WITHERING, POISONING, FREEZING, ATTACK_SPEED, CRITICAL_STRIKE, REACH, XP_BOOST, TOUGHNESS, HEALTH,
			SWIFTNESS, CUSHION, VAMPIRISM, BEHEADING, SMELTING, TRUE_SHOT_AURA, VISION, STEP_ASSIST, ANCHORED,
			SURE_FOOTING, CALM_GAZE, PHANTOM_WARD, CLARITY, SUSTENANCE, ENDURANCE, FRAGILITY, GREED, FUMBLING, WEIGHT,
			HUNGER, LAVA_WALKING, SNOW_WALKER, STEADFAST, SPIKED, BULWARK, RIPOSTE,
			DEFLECT, STEADY_STANCE, SHIELD_BASH, MAGNETIC, ENDER_LINK,
			KEEP_ON_DEATH, AREA_MINING, REPLANT);

	/**
	 * Our curses (DESIGN §5a, Phase 10e), a subset of {@link #KEYS}. They're tagged like vanilla's Binding and
	 * Vanishing ({@code #curse}, {@code #treasure}, {@code #on_random_loot}, {@code #tradeable}) instead of
	 * {@code #non_treasure}, and the table offers them as a discount.
	 */
	public static final List<ResourceKey<Enchantment>> CURSES = List.of(FRAGILITY, GREED, FUMBLING, WEIGHT, HUNGER);

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
