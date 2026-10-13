package com.wulfnet.enchanting2.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.wulfnet.enchanting2.Enchanting2;
import java.util.stream.Stream;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.world.item.enchantment.LevelBasedValue;

/**
 * DESIGN §9 route E: our enchantment effect components. The enchantment JSON names them under {@code effects}, so the
 * behaviour follows the component and a datapack can put one on any enchantment. Registered before any datapack
 * loads (mod init), as the enchantment registry's codec looks them up while parsing.
 */
public final class ModEffects {
	/** Fraction of the health a direct melee hit took from its victim that the attacker heals (Vampirism). */
	public static final DataComponentType<LevelBasedValue> LIFESTEAL = register("lifesteal", LevelBasedValue.CODEC);
	/** Chance that a player's direct melee kill drops the victim's head (Beheading). */
	public static final DataComponentType<LevelBasedValue> HEAD_DROP_CHANCE = register("head_drop_chance",
			LevelBasedValue.CODEC);
	/** The broken block's drops are smelted (Smelting). */
	public static final DataComponentType<Unit> SMELT_DROPS = register("smelt_drops", Unit.CODEC);
	/** Night Vision while worn, for players (Vision). */
	public static final DataComponentType<Unit> NIGHT_VISION = register("night_vision", Unit.CODEC);
	/** Fraction of movement speed taken per worn piece, summed and capped by config (Curse of Weight, Phase 10e). */
	public static final DataComponentType<LevelBasedValue> MOVEMENT_PENALTY = register("movement_penalty",
			LevelBasedValue.CODEC);
	/** Extra hunger exhaustion as a fraction, summed over worn pieces, uncapped (Curse of Hunger, Phase 10e). */
	public static final DataComponentType<LevelBasedValue> EXHAUSTION = register("exhaustion", LevelBasedValue.CODEC);
	/** Endermen don't notice the wearer staring, the carved-pumpkin effect (Calm Gaze, Phase 10c). */
	public static final DataComponentType<Unit> GAZE_DISGUISE = register("gaze_disguise", Unit.CODEC);
	/** Phantoms don't target the wearer unless provoked (Phantom Ward, Phase 10c). */
	public static final DataComponentType<Unit> IGNORED_BY_PHANTOMS = register("ignored_by_phantoms", Unit.CODEC);
	/** The listed mob effects last shorter on the wearer, refused at 1 (Clarity, Phase 10c). */
	public static final DataComponentType<ShortenEffects> SHORTEN_EFFECTS = register("shorten_effects",
			ShortenEffects.CODEC);
	/** Fraction of all hunger exhaustion removed, summed over worn pieces, clamped to 1 (Sustenance, Phase 10c). */
	public static final DataComponentType<LevelBasedValue> EXHAUSTION_REDUCTION = register("exhaustion_reduction",
			LevelBasedValue.CODEC);
	/** As {@link #EXHAUSTION_REDUCTION}, for sprinting and sprint-jumping only (Endurance, Phase 10c). */
	public static final DataComponentType<LevelBasedValue> SPRINT_EXHAUSTION_REDUCTION = register(
			"sprint_exhaustion_reduction", LevelBasedValue.CODEC);
	/** The wearer stands on lava's surface unless sneaking, as a strider does (Lava Walking, Phase 10c). */
	public static final DataComponentType<Unit> LAVA_WALKER = register("lava_walker", Unit.CODEC);
	/** The wearer walks on powder snow and doesn't freeze, the leather-boots effect (Snow Walker, Phase 10c). */
	public static final DataComponentType<Unit> POWDER_SNOW_WALKER = register("powder_snow_walker", Unit.CODEC);
	/** Fraction taken off the time an axe disables the shield, on that shield; 1 = never (Steadfast, Phase 10c). */
	public static final DataComponentType<LevelBasedValue> SHIELD_DISABLE_REDUCTION = register(
			"shield_disable_reduction", LevelBasedValue.CODEC);
	/** Damage a melee attacker takes when the shield blocks its hit, Thorns for shields (Spiked, Phase 10c). */
	public static final DataComponentType<LevelBasedValue> BLOCK_RETALIATION = register("block_retaliation",
			LevelBasedValue.CODEC);
	/** Absorption health a blocked hit gives, off a cooldown (Bulwark, Phase 10c). */
	public static final DataComponentType<BlockAbsorption> BLOCK_ABSORPTION = register("block_absorption",
			BlockAbsorption.CODEC);
	/** After a block, the next melee hit soon after deals more (Riposte, Phase 10c). */
	public static final DataComponentType<RiposteBonus> RIPOSTE = register("riposte", RiposteBonus.CODEC);
	/** Chance a projectile the raised shield would block flies back at its shooter instead (Deflect, Phase 10c). */
	public static final DataComponentType<LevelBasedValue> DEFLECT_CHANCE = register("deflect_chance",
			LevelBasedValue.CODEC);
	/** No walking slowdown while this item is in use (Steady Stance, Phase 10c). */
	public static final DataComponentType<Unit> STEADY_STANCE = register("steady_stance", Unit.CODEC);
	/** An attack while this shield is raised knocks back and slows, then lowers it (Shield Bash, Phase 10c). */
	public static final DataComponentType<BashEffect> SHIELD_BASH = register("shield_bash", BashEffect.CODEC);
	/** Drops from blocks this tool breaks and mobs it kills go into the inventory (Magnetic, Phase 10c). */
	public static final DataComponentType<Unit> DROPS_TO_INVENTORY = register("drops_to_inventory", Unit.CODEC);
	/** As {@link #DROPS_TO_INVENTORY}, into the ender chest (Ender Link, Phase 10c). */
	public static final DataComponentType<Unit> DROPS_TO_ENDER_CHEST = register("drops_to_ender_chest",
			Unit.CODEC);
	/** XP from blocks this tool breaks and mobs it kills spawns at the player (Magnetic, Phase 10c). */
	public static final DataComponentType<Unit> EXPERIENCE_TO_PLAYER = register("experience_to_player",
			Unit.CODEC);
	/** The item stays in its slot through death and is back there on respawn (Keep on Death, Phase 10c). */
	public static final DataComponentType<Unit> KEEP_ON_DEATH = register("keep_on_death", Unit.CODEC);
	/** Breaking a block also breaks the 3×3 around it facing the hit face (Area Mining, Phase 10c). */
	public static final DataComponentType<Unit> AREA_MINING = register("area_mining", Unit.CODEC);
	/** Breaking a mature crop plants it again from one of its own seeds (Replant, Phase 10c). */
	public static final DataComponentType<Unit> REPLANT = register("replant", Unit.CODEC);
	/** Entity effect for {@code minecraft:projectile_spawned}: the arrow ignores gravity for a while (TrueShot Aura). */
	public static final MapCodec<SuspendGravity> SUSPEND_GRAVITY = Registry.register(
			BuiltInRegistries.ENCHANTMENT_ENTITY_EFFECT_TYPE,
			Identifier.fromNamespaceAndPath(Enchanting2.MOD_ID, "suspend_gravity"), SuspendGravity.CODEC);

	private ModEffects() {
	}

	/** Loads the class, which registers the fields above. Call once from mod init. */
	public static void register() {
		Enchanting2.LOGGER.debug("Registered effect components {} and entity effect {}",
				Stream.of(LIFESTEAL, HEAD_DROP_CHANCE, SMELT_DROPS, NIGHT_VISION, MOVEMENT_PENALTY, EXHAUSTION,
						GAZE_DISGUISE, IGNORED_BY_PHANTOMS, SHORTEN_EFFECTS, EXHAUSTION_REDUCTION,
						SPRINT_EXHAUSTION_REDUCTION, LAVA_WALKER, POWDER_SNOW_WALKER, SHIELD_DISABLE_REDUCTION,
						BLOCK_RETALIATION, BLOCK_ABSORPTION, RIPOSTE, DEFLECT_CHANCE, STEADY_STANCE,
						SHIELD_BASH, DROPS_TO_INVENTORY, DROPS_TO_ENDER_CHEST, EXPERIENCE_TO_PLAYER, KEEP_ON_DEATH,
						AREA_MINING, REPLANT)
						.map(ModEffects::key).toList(),
				BuiltInRegistries.ENCHANTMENT_ENTITY_EFFECT_TYPE.getKey(SUSPEND_GRAVITY));
		SuspendGravity.register();
	}

	private static <T> DataComponentType<T> register(String path, Codec<T> codec) {
		return Registry.register(BuiltInRegistries.ENCHANTMENT_EFFECT_COMPONENT_TYPE,
				Identifier.fromNamespaceAndPath(Enchanting2.MOD_ID, path),
				DataComponentType.<T>builder().persistent(codec).build());
	}

	private static Identifier key(DataComponentType<?> type) {
		return BuiltInRegistries.ENCHANTMENT_EFFECT_COMPONENT_TYPE.getKey(type);
	}
}
