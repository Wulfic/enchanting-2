package com.wulfnet.enchanting2.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.platform.Services;
import java.util.ArrayList;
import java.util.List;
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
	/** What this class registered, in order, for the log line; read back from here (NeoForge fills the registry later). */
	private static final List<Identifier> REGISTERED = new ArrayList<>();
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
	/** Tilling a block also tills the square of this radius around it (Tiller, Phase 10c). */
	public static final DataComponentType<LevelBasedValue> TILL_RADIUS = register("till_radius",
			LevelBasedValue.CODEC);
	/** Chance a landed melee hit lands again at once, damage only (Quick Strike, Phase 10c). */
	public static final DataComponentType<LevelBasedValue> EXTRA_HIT_CHANCE = register("extra_hit_chance",
			LevelBasedValue.CODEC);
	/** A lethal hit leaves the wearer at 1 health, once per cooldown (Last Stand, Phase 10c). */
	public static final DataComponentType<LastStandEffect> LAST_STAND = register("last_stand",
			LastStandEffect.CODEC);
	/** Extra elytra flight speed as a fraction, gliding and rocket boosts (Speed, Phase 10c). */
	public static final DataComponentType<LevelBasedValue> GLIDE_SPEED = register("glide_speed",
			LevelBasedValue.CODEC);
	/** Share of a firework rocket's boost each tick while gliding with jump held, 1 = a rocket (Flight, Phase 10c). */
	public static final DataComponentType<LevelBasedValue> FLIGHT_THRUST = register("flight_thrust",
			LevelBasedValue.CODEC);
	/** One extra jump in the air, ready again on landing (Double Jump, Phase 10c). */
	public static final DataComponentType<Unit> DOUBLE_JUMP = register("double_jump", Unit.CODEC);
	/** Entity effect for {@code minecraft:projectile_spawned}: the arrow ignores gravity for a while (TrueShot Aura). */
	private static final Identifier SUSPEND_GRAVITY_ID =
			Identifier.fromNamespaceAndPath(Enchanting2.MOD_ID, "suspend_gravity");
	public static final MapCodec<SuspendGravity> SUSPEND_GRAVITY = SuspendGravity.CODEC;

	static {
		Services.REGISTRY.register(BuiltInRegistries.ENCHANTMENT_ENTITY_EFFECT_TYPE, SUSPEND_GRAVITY_ID, SUSPEND_GRAVITY);
	}

	private ModEffects() {
	}

	/** Loads the class, which registers the fields above. Call once from mod init. */
	public static void register() {
		Enchanting2.LOGGER.debug("Registered effect components {} and entity effect {}", REGISTERED,
				SUSPEND_GRAVITY_ID);
		SuspendGravity.register();
	}

	private static <T> DataComponentType<T> register(String path, Codec<T> codec) {
		Identifier id = Identifier.fromNamespaceAndPath(Enchanting2.MOD_ID, path);
		DataComponentType<T> type = DataComponentType.<T>builder().persistent(codec).build();
		Services.REGISTRY.register(BuiltInRegistries.ENCHANTMENT_EFFECT_COMPONENT_TYPE, id, type);
		REGISTERED.add(id);
		return type;
	}

}
