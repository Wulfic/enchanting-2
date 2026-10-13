package com.wulfnet.enchanting2.gametest;

import com.wulfnet.enchanting2.acquisition.AcquisitionRules;
import com.wulfnet.enchanting2.config.EnchantmentOverrides;
import com.wulfnet.enchanting2.cost.Pricing;
import com.wulfnet.enchanting2.cost.PricingTable;
import com.wulfnet.enchanting2.cost.UpgradeMode;
import com.wulfnet.enchanting2.enchantment.ModEnchantments;
import com.wulfnet.enchanting2.gametest.mixin.EntityInvoker;
import com.wulfnet.enchanting2.table.TablePool;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntSupplier;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 10a: our data-only enchantments (DESIGN §9, numbers in TODO 10a) against the live registry. Each effect test
 * fails if its JSON loses or changes its effect, and the projectile tests fail if an on-hit effect picks up Fire
 * Aspect's {@code is_direct} requirement.
 */
public class NewEnchantmentsGameTest {
	private static final double EPSILON = 1.0E-6;

	/** The max level of every enchantment in {@link ModEnchantments#KEYS}, as DESIGN §9 says. */
	private static final Map<ResourceKey<Enchantment>, Integer> MAX = Map.ofEntries(
			Map.entry(ModEnchantments.WITHERING, 5),
			Map.entry(ModEnchantments.POISONING, 5),
			Map.entry(ModEnchantments.FREEZING, 5),
			Map.entry(ModEnchantments.ATTACK_SPEED, 5),
			Map.entry(ModEnchantments.CRITICAL_STRIKE, 5),
			Map.entry(ModEnchantments.REACH, 3),
			Map.entry(ModEnchantments.XP_BOOST, 5),
			Map.entry(ModEnchantments.TOUGHNESS, 5),
			Map.entry(ModEnchantments.HEALTH, 5),
			Map.entry(ModEnchantments.SWIFTNESS, 5),
			Map.entry(ModEnchantments.CUSHION, 3),
			Map.entry(ModEnchantments.VAMPIRISM, 5),
			Map.entry(ModEnchantments.BEHEADING, 5),
			Map.entry(ModEnchantments.SMELTING, 1),
			Map.entry(ModEnchantments.TRUE_SHOT_AURA, 1),
			Map.entry(ModEnchantments.VISION, 1),
			Map.entry(ModEnchantments.STEP_ASSIST, 1),
			Map.entry(ModEnchantments.ANCHORED, 5),
			Map.entry(ModEnchantments.SURE_FOOTING, 3),
			Map.entry(ModEnchantments.CALM_GAZE, 1),
			Map.entry(ModEnchantments.PHANTOM_WARD, 1),
			Map.entry(ModEnchantments.CLARITY, 3),
			Map.entry(ModEnchantments.SUSTENANCE, 5),
			Map.entry(ModEnchantments.ENDURANCE, 5),
			Map.entry(ModEnchantments.FRAGILITY, 5),
			Map.entry(ModEnchantments.GREED, 5),
			Map.entry(ModEnchantments.FUMBLING, 5),
			Map.entry(ModEnchantments.WEIGHT, 5),
			Map.entry(ModEnchantments.HUNGER, 5),
			Map.entry(ModEnchantments.LAVA_WALKING, 1),
			Map.entry(ModEnchantments.SNOW_WALKER, 1),
			Map.entry(ModEnchantments.STEADFAST, 3),
			Map.entry(ModEnchantments.SPIKED, 5),
			Map.entry(ModEnchantments.BULWARK, 3),
			Map.entry(ModEnchantments.RIPOSTE, 5),
			Map.entry(ModEnchantments.DEFLECT, 3),
			Map.entry(ModEnchantments.STEADY_STANCE, 1),
			Map.entry(ModEnchantments.SHIELD_BASH, 3),
			Map.entry(ModEnchantments.MAGNETIC, 1),
			Map.entry(ModEnchantments.ENDER_LINK, 1),
			Map.entry(ModEnchantments.KEEP_ON_DEATH, 1),
			Map.entry(ModEnchantments.AREA_MINING, 1),
			Map.entry(ModEnchantments.REPLANT, 1),
			Map.entry(ModEnchantments.TILLER, 3),
			Map.entry(ModEnchantments.QUICK_STRIKE, 5),
			Map.entry(ModEnchantments.LAST_STAND, 3),
			Map.entry(ModEnchantments.SPEED, 5),
			Map.entry(ModEnchantments.FLIGHT, 5),
			Map.entry(ModEnchantments.DOUBLE_JUMP, 1));

	/** The vanilla source tags (owner 2026-10-09: everywhere), plus our table pool. */
	private static final List<TagKey<Enchantment>> SOURCES = List.of(EnchantmentTags.NON_TREASURE,
			EnchantmentTags.IN_ENCHANTING_TABLE, EnchantmentTags.TRADEABLE, EnchantmentTags.ON_RANDOM_LOOT,
			EnchantmentTags.ON_MOB_SPAWN_EQUIPMENT, EnchantmentTags.ON_TRADED_EQUIPMENT, TablePool.OFFERED);

	private record OnHit(ResourceKey<Enchantment> key, Holder<MobEffect> effect) {
	}

	private static final List<OnHit> ON_HIT = List.of(new OnHit(ModEnchantments.WITHERING, MobEffects.WITHER),
			new OnHit(ModEnchantments.POISONING, MobEffects.POISON),
			new OnHit(ModEnchantments.FREEZING, MobEffects.SLOWNESS));

	private static final List<EquipmentSlot> ARMOR_SLOTS = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST,
			EquipmentSlot.LEGS, EquipmentSlot.FEET);
	private static final List<Item> DIAMOND_ARMOR = List.of(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE,
			Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS);

	private static HolderLookup.RegistryLookup<Enchantment> registry(GameTestHelper helper) {
		return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
	}

	private static Holder<Enchantment> get(GameTestHelper helper, ResourceKey<Enchantment> key) {
		return registry(helper).getOrThrow(key);
	}

	private static ItemStack enchanted(GameTestHelper helper, Item item, ResourceKey<Enchantment> key, int level) {
		ItemStack stack = new ItemStack(item);
		stack.enchant(get(helper, key), level);
		return stack;
	}

	private static Pig still(GameTestHelper helper, BlockPos pos) {
		Pig pig = helper.spawn(EntityType.PIG, pos);
		pig.setNoAi(true);
		return pig;
	}

	/** Level 3 = {@code 2 + (3 − 1)} = 4 s = 80 ticks, level I (amplifier 0). */
	private static void assertEffect(GameTestHelper helper, Pig pig, OnHit onHit) {
		MobEffectInstance instance = pig.getEffect(onHit.effect());
		helper.assertTrue(instance != null, Component.literal(onHit.key().identifier() + ": no " + onHit.effect()));
		helper.assertValueEqual(0, instance.getAmplifier(), Component.literal(onHit.key().identifier() + " amplifier"));
		helper.assertTrue(instance.getDuration() > 70 && instance.getDuration() <= 80,
				Component.literal(onHit.key().identifier() + " duration " + instance.getDuration() + ", want 80"));
	}

	/**
	 * {@code attribute} on {@code player} with the stack's enchantment modifiers added, as equipping it does
	 * ({@code LivingEntity} adds {@code ItemStack#forEachModifier}, which includes these). The item's own modifiers
	 * (a sword's attack speed) are left out, so the result is the enchantment's alone.
	 */
	private static double withEnchantment(ServerPlayer player, ItemStack stack, EquipmentSlot slot,
			Holder<Attribute> attribute) {
		EnchantmentHelper.forEachModifier(stack, slot,
				(holder, modifier) -> player.getAttributes().getInstance(holder).addOrUpdateTransientModifier(modifier));
		return player.getAttributeValue(attribute);
	}

	private static void assertClose(GameTestHelper helper, double expected, double actual, String what) {
		helper.assertTrue(Math.abs(expected - actual) < EPSILON,
				Component.literal(what + ": expected " + expected + ", got " + actual));
	}

	@GameTest
	public void everyOwnEnchantmentIsRegisteredAtItsMax(GameTestHelper helper) {
		Set<ResourceKey<Enchantment>> registered = new HashSet<>();
		registry(helper).listElementIds().filter(key -> key.identifier().getNamespace().equals("enchanting2"))
				.forEach(registered::add);
		helper.assertValueEqual(Set.copyOf(ModEnchantments.KEYS), registered,
				Component.literal("enchanting2 registry IDs vs ModEnchantments.KEYS"));
		helper.assertValueEqual(MAX.keySet(), registered, Component.literal("enchantments this test knows a max for"));
		MAX.forEach((key, max) -> helper.assertValueEqual(max, get(helper, key).value().getMaxLevel(),
				Component.literal("max level of " + key.identifier())));
		helper.succeed();
	}

	@GameTest
	public void everyOwnEnchantmentComesFromEverySource(GameTestHelper helper) {
		List<Holder<Enchantment>> pool = TablePool.members(registry(helper),
				new AcquisitionRules(EnchantmentOverrides.defaults()));
		for (ResourceKey<Enchantment> key : ModEnchantments.KEYS) {
			if (ModEnchantments.CURSES.contains(key)) {
				continue; // tagged like vanilla curses: CursesGameTest
			}
			Holder<Enchantment> holder = get(helper, key);
			for (TagKey<Enchantment> source : SOURCES) {
				helper.assertTrue(holder.is(source),
						Component.literal(key.identifier() + " is not in #" + source.location()));
			}
			helper.assertFalse(holder.is(EnchantmentTags.TREASURE),
					Component.literal(key.identifier() + " is treasure"));
			helper.assertTrue(pool.contains(holder), Component.literal(key.identifier() + " is not in the table pool"));
		}
		helper.succeed();
	}

	@GameTest
	public void supportedItemsFollowTheTags(GameTestHelper helper) {
		record Case(ResourceKey<Enchantment> key, Item item, boolean supported) {
		}
		List<Case> cases = List.of(
				new Case(ModEnchantments.WITHERING, Items.DIAMOND_SWORD, true),
				new Case(ModEnchantments.WITHERING, Items.IRON_AXE, true),
				new Case(ModEnchantments.WITHERING, Items.MACE, true),
				new Case(ModEnchantments.WITHERING, Items.BOW, true),
				new Case(ModEnchantments.WITHERING, Items.CROSSBOW, true),
				new Case(ModEnchantments.WITHERING, Items.TRIDENT, true),
				new Case(ModEnchantments.WITHERING, Items.DIAMOND_PICKAXE, false),
				new Case(ModEnchantments.POISONING, Items.CROSSBOW, true),
				new Case(ModEnchantments.FREEZING, Items.BOW, true),
				new Case(ModEnchantments.ATTACK_SPEED, Items.DIAMOND_SWORD, true),
				new Case(ModEnchantments.ATTACK_SPEED, Items.TRIDENT, true),
				new Case(ModEnchantments.ATTACK_SPEED, Items.BOW, false),
				new Case(ModEnchantments.CRITICAL_STRIKE, Items.BOW, true),
				new Case(ModEnchantments.CRITICAL_STRIKE, Items.DIAMOND_PICKAXE, false),
				new Case(ModEnchantments.REACH, Items.DIAMOND_PICKAXE, true),
				new Case(ModEnchantments.REACH, Items.SHEARS, true),
				new Case(ModEnchantments.REACH, Items.DIAMOND_SWORD, true),
				new Case(ModEnchantments.REACH, Items.BOW, false),
				new Case(ModEnchantments.REACH, Items.DIAMOND_CHESTPLATE, false),
				new Case(ModEnchantments.XP_BOOST, Items.DIAMOND_PICKAXE, true),
				new Case(ModEnchantments.XP_BOOST, Items.DIAMOND_SWORD, true),
				new Case(ModEnchantments.XP_BOOST, Items.CROSSBOW, true),
				new Case(ModEnchantments.XP_BOOST, Items.DIAMOND_CHESTPLATE, false),
				new Case(ModEnchantments.TOUGHNESS, Items.DIAMOND_HELMET, true),
				new Case(ModEnchantments.TOUGHNESS, Items.TURTLE_HELMET, true),
				new Case(ModEnchantments.TOUGHNESS, Items.ELYTRA, false),
				new Case(ModEnchantments.HEALTH, Items.LEATHER_LEGGINGS, true),
				new Case(ModEnchantments.HEALTH, Items.DIAMOND_SWORD, false),
				new Case(ModEnchantments.SWIFTNESS, Items.IRON_BOOTS, true),
				new Case(ModEnchantments.SWIFTNESS, Items.IRON_HELMET, false),
				new Case(ModEnchantments.CUSHION, Items.ELYTRA, true),
				new Case(ModEnchantments.CUSHION, Items.NETHERITE_CHESTPLATE, true),
				new Case(ModEnchantments.CUSHION, Items.NETHERITE_LEGGINGS, false),
				new Case(ModEnchantments.STEP_ASSIST, Items.LEATHER_LEGGINGS, true),
				new Case(ModEnchantments.STEP_ASSIST, Items.IRON_BOOTS, false),
				new Case(ModEnchantments.ANCHORED, Items.NETHERITE_LEGGINGS, true),
				new Case(ModEnchantments.ANCHORED, Items.NETHERITE_CHESTPLATE, false),
				new Case(ModEnchantments.SURE_FOOTING, Items.DIAMOND_LEGGINGS, true),
				new Case(ModEnchantments.SURE_FOOTING, Items.DIAMOND_BOOTS, false),
				new Case(ModEnchantments.CALM_GAZE, Items.TURTLE_HELMET, true),
				new Case(ModEnchantments.CALM_GAZE, Items.CARVED_PUMPKIN, false),
				new Case(ModEnchantments.PHANTOM_WARD, Items.LEATHER_HELMET, true),
				new Case(ModEnchantments.PHANTOM_WARD, Items.LEATHER_BOOTS, false),
				new Case(ModEnchantments.CLARITY, Items.NETHERITE_HELMET, true),
				new Case(ModEnchantments.CLARITY, Items.NETHERITE_CHESTPLATE, false),
				new Case(ModEnchantments.SUSTENANCE, Items.GOLDEN_HELMET, true),
				new Case(ModEnchantments.SUSTENANCE, Items.GOLDEN_LEGGINGS, false),
				new Case(ModEnchantments.ENDURANCE, Items.CHAINMAIL_LEGGINGS, true),
				new Case(ModEnchantments.ENDURANCE, Items.CHAINMAIL_HELMET, false),
				new Case(ModEnchantments.LAVA_WALKING, Items.NETHERITE_BOOTS, true),
				new Case(ModEnchantments.LAVA_WALKING, Items.NETHERITE_LEGGINGS, false),
				new Case(ModEnchantments.SNOW_WALKER, Items.LEATHER_BOOTS, true),
				new Case(ModEnchantments.SNOW_WALKER, Items.LEATHER_HELMET, false),
				new Case(ModEnchantments.STEADFAST, Items.SHIELD, true),
				new Case(ModEnchantments.STEADFAST, Items.IRON_AXE, false),
				new Case(ModEnchantments.SPIKED, Items.SHIELD, true),
				new Case(ModEnchantments.SPIKED, Items.IRON_CHESTPLATE, false),
				new Case(ModEnchantments.BULWARK, Items.SHIELD, true),
				new Case(ModEnchantments.BULWARK, Items.DIAMOND_SWORD, false),
				new Case(ModEnchantments.RIPOSTE, Items.SHIELD, true),
				new Case(ModEnchantments.RIPOSTE, Items.BOOK, false),
				new Case(ModEnchantments.DEFLECT, Items.SHIELD, true),
				new Case(ModEnchantments.DEFLECT, Items.BOW, false),
				new Case(ModEnchantments.STEADY_STANCE, Items.SHIELD, true),
				new Case(ModEnchantments.STEADY_STANCE, Items.LEATHER_BOOTS, false),
				new Case(ModEnchantments.SHIELD_BASH, Items.SHIELD, true),
				new Case(ModEnchantments.SHIELD_BASH, Items.IRON_SWORD, false),
				new Case(ModEnchantments.MAGNETIC, Items.IRON_PICKAXE, true),
				new Case(ModEnchantments.MAGNETIC, Items.BOW, true),
				new Case(ModEnchantments.MAGNETIC, Items.IRON_HELMET, false),
				new Case(ModEnchantments.ENDER_LINK, Items.NETHERITE_AXE, true),
				new Case(ModEnchantments.ENDER_LINK, Items.SHIELD, false),
				new Case(ModEnchantments.KEEP_ON_DEATH, Items.ELYTRA, true),
				new Case(ModEnchantments.KEEP_ON_DEATH, Items.WATER_BUCKET, true),
				new Case(ModEnchantments.KEEP_ON_DEATH, Items.DIAMOND, false),
				new Case(ModEnchantments.AREA_MINING, Items.STONE_PICKAXE, true),
				new Case(ModEnchantments.AREA_MINING, Items.GOLDEN_SHOVEL, true),
				new Case(ModEnchantments.AREA_MINING, Items.IRON_AXE, false),
				new Case(ModEnchantments.REPLANT, Items.NETHERITE_HOE, true),
				new Case(ModEnchantments.REPLANT, Items.IRON_SHOVEL, false),
				new Case(ModEnchantments.TILLER, Items.WOODEN_HOE, true),
				new Case(ModEnchantments.TILLER, Items.WOODEN_SHOVEL, false),
				new Case(ModEnchantments.QUICK_STRIKE, Items.IRON_SWORD, true),
				new Case(ModEnchantments.QUICK_STRIKE, Items.IRON_AXE, true),
				new Case(ModEnchantments.QUICK_STRIKE, Items.BOW, false),
				new Case(ModEnchantments.LAST_STAND, Items.NETHERITE_CHESTPLATE, true),
				new Case(ModEnchantments.LAST_STAND, Items.ELYTRA, false),
				new Case(ModEnchantments.SPEED, Items.ELYTRA, true),
				new Case(ModEnchantments.SPEED, Items.DIAMOND_CHESTPLATE, false),
				new Case(ModEnchantments.FLIGHT, Items.ELYTRA, true),
				new Case(ModEnchantments.FLIGHT, Items.LEATHER_CHESTPLATE, false),
				new Case(ModEnchantments.DOUBLE_JUMP, Items.GOLDEN_BOOTS, true),
				new Case(ModEnchantments.DOUBLE_JUMP, Items.GOLDEN_LEGGINGS, false));
		for (Case c : cases) {
			boolean actual = get(helper, c.key()).value().isSupportedItem(new ItemStack(c.item()));
			helper.assertValueEqual(c.supported(), actual,
					Component.literal(c.key().identifier() + " supports " + c.item()));
		}
		helper.succeed();
	}

	@GameTest
	public void meleeHitAppliesTheOnHitEffect(GameTestHelper helper) {
		for (int i = 0; i < ON_HIT.size(); i++) {
			OnHit onHit = ON_HIT.get(i);
			Pig pig = still(helper, new BlockPos(1 + 2 * i, 1, 1));
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			player.setItemSlot(EquipmentSlot.MAINHAND, enchanted(helper, Items.DIAMOND_SWORD, onHit.key(), 3));
			player.attack(pig);
			assertEffect(helper, pig, onHit);
		}
		helper.succeed();
	}

	/** An arrow fired from an enchanted bow carries the effect: vanilla runs post_attack with the firing weapon. */
	@GameTest
	public void arrowFromAnEnchantedBowAppliesTheOnHitEffect(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Pig[] pigs = new Pig[ON_HIT.size()];
		for (int i = 0; i < ON_HIT.size(); i++) {
			pigs[i] = still(helper, new BlockPos(1 + 2 * i, 1, 1));
			ItemStack bow = enchanted(helper, Items.BOW, ON_HIT.get(i).key(), 3);
			Vec3 above = pigs[i].position().add(0.0, 3.0, 0.0);
			Arrow arrow = new Arrow(level, above.x, above.y, above.z, new ItemStack(Items.ARROW), bow);
			arrow.setOwner(player);
			arrow.setDeltaMovement(0.0, -1.5, 0.0);
			level.addFreshEntity(arrow);
		}
		helper.succeedWhen(() -> {
			for (int i = 0; i < ON_HIT.size(); i++) {
				assertEffect(helper, pigs[i], ON_HIT.get(i));
			}
		});
	}

	/** {@code add_multiplied_base} +10% per level: level 1 = base × 1.1, level 5 = base × 1.5. */
	@GameTest
	public void attackSpeedAddsTenPercentOfBasePerLevel(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		double base = player.getAttributeBaseValue(Attributes.ATTACK_SPEED);
		assertClose(helper, base * 1.1, withEnchantment(player,
				enchanted(helper, Items.DIAMOND_SWORD, ModEnchantments.ATTACK_SPEED, 1), EquipmentSlot.MAINHAND,
				Attributes.ATTACK_SPEED), "Attack Speed I");
		assertClose(helper, base * 1.5, withEnchantment(player,
				enchanted(helper, Items.DIAMOND_SWORD, ModEnchantments.ATTACK_SPEED, 5), EquipmentSlot.MAINHAND,
				Attributes.ATTACK_SPEED), "Attack Speed V");
		helper.succeed();
	}

	@GameTest
	public void reachAddsHalfABlockPerLevelToBothRanges(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		double blockBase = player.getAttributeBaseValue(Attributes.BLOCK_INTERACTION_RANGE);
		double entityBase = player.getAttributeBaseValue(Attributes.ENTITY_INTERACTION_RANGE);
		ItemStack pickaxe = enchanted(helper, Items.DIAMOND_PICKAXE, ModEnchantments.REACH, 3);
		assertClose(helper, blockBase + 1.5,
				withEnchantment(player, pickaxe, EquipmentSlot.MAINHAND, Attributes.BLOCK_INTERACTION_RANGE),
				"Reach III block range");
		assertClose(helper, entityBase + 1.5, player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE),
				"Reach III entity range");
		helper.succeed();
	}

	/**
	 * 5% per level of ×1.5. Each hit is either the base or exactly 1.5 × it, and the rate stays inside about four
	 * standard deviations of the target (level V: 4000 rolls at 25%; level I: 4000 at 5%), so a wrong chance fails and
	 * a correct one essentially never does.
	 */
	@GameTest
	public void criticalStrikeMultipliesByOneAndAHalfAtFivePercentPerLevel(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Pig pig = still(helper, new BlockPos(1, 1, 1));
		DamageSource source = helper.getLevel().damageSources().playerAttack(player);
		assertCritRate(helper, enchanted(helper, Items.DIAMOND_SWORD, ModEnchantments.CRITICAL_STRIKE, 5), pig, source,
				0.22, 0.28);
		assertCritRate(helper, enchanted(helper, Items.DIAMOND_SWORD, ModEnchantments.CRITICAL_STRIKE, 1), pig, source,
				0.035, 0.065);
		assertCritRate(helper, new ItemStack(Items.DIAMOND_SWORD), pig, source, 0.0, 0.0);
		helper.succeed();
	}

	private static void assertCritRate(GameTestHelper helper, ItemStack weapon, Pig target, DamageSource source,
			double min, double max) {
		int rolls = 4000;
		int crits = 0;
		for (int i = 0; i < rolls; i++) {
			float damage = EnchantmentHelper.modifyDamage(helper.getLevel(), weapon, target, source, 10.0F);
			if (damage == 15.0F) {
				crits++;
			} else {
				helper.assertValueEqual(10.0F, damage, Component.literal("a non-critical hit"));
			}
		}
		double rate = crits / (double) rolls;
		helper.assertTrue(rate >= min && rate <= max,
				Component.literal("critical rate " + rate + " outside [" + min + ", " + max + "] for " + weapon));
	}

	/**
	 * XP Boost V = ×1.25 with random rounding (decisions.md "Phase 10a"): 5 mob XP is 6 or 7 and averages 6.25, 2 block
	 * XP is 2 or 3 and averages 2.5. Vanilla's truncation would give exactly 6 and 2. The bounds are about four
	 * standard deviations of the mean over 4000 calls.
	 */
	@GameTest
	public void xpBoostAddsFivePercentPerLevelOnAverage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Pig pig = still(helper, new BlockPos(1, 1, 1));
		player.setItemSlot(EquipmentSlot.MAINHAND, enchanted(helper, Items.DIAMOND_SWORD, ModEnchantments.XP_BOOST, 5));
		assertMeanXp(helper, () -> EnchantmentHelper.processMobExperience(level, player, pig, 5), 6, 7, 6.25, 0.03,
				"mob XP with XP Boost V");
		ItemStack pickaxe = enchanted(helper, Items.DIAMOND_PICKAXE, ModEnchantments.XP_BOOST, 5);
		assertMeanXp(helper, () -> EnchantmentHelper.processBlockExperience(level, pickaxe, 2), 2, 3, 2.5, 0.035,
				"block XP with XP Boost V");
		helper.succeed();
	}

	/** Without the enchantment every value is whole, so rounding changes nothing: exactly vanilla's number, every time. */
	@GameTest
	public void experienceWithoutXpBoostIsExactlyVanilla(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Pig pig = still(helper, new BlockPos(1, 1, 1));
		player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
		assertMeanXp(helper, () -> EnchantmentHelper.processMobExperience(level, player, pig, 5), 5, 5, 5.0, 0.0,
				"mob XP without XP Boost");
		assertMeanXp(helper, () -> EnchantmentHelper.processBlockExperience(level, new ItemStack(Items.DIAMOND_PICKAXE),
				2), 2, 2, 2.0, 0.0, "block XP without XP Boost");
		helper.succeed();
	}

	private static void assertMeanXp(GameTestHelper helper, IntSupplier xp, int min, int max, double mean,
			double tolerance, String what) {
		int calls = 4000;
		long total = 0;
		for (int i = 0; i < calls; i++) {
			int value = xp.getAsInt();
			helper.assertTrue(value >= min && value <= max,
					Component.literal(what + ": " + value + " outside [" + min + ", " + max + "]"));
			total += value;
		}
		double actual = total / (double) calls;
		helper.assertTrue(Math.abs(actual - mean) <= tolerance,
				Component.literal(what + ": mean " + actual + ", want " + mean + " ± " + tolerance));
	}

	/** {@code attribute} on a player wearing four diamond pieces, each with {@code key} at {@code level}. */
	private static double withFullSet(GameTestHelper helper, ResourceKey<Enchantment> key, int level,
			Holder<Attribute> attribute) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		for (int i = 0; i < ARMOR_SLOTS.size(); i++) {
			withEnchantment(player, enchanted(helper, DIAMOND_ARMOR.get(i), key, level), ARMOR_SLOTS.get(i), attribute);
		}
		return player.getAttributeValue(attribute);
	}

	/** Vanilla suffixes each enchantment attribute ID with the slot, so the four pieces add up. */
	@GameTest
	public void toughnessAndHealthStackAcrossFourPieces(GameTestHelper helper) {
		ServerPlayer bare = helper.makeMockServerPlayerInLevel();
		double toughness = bare.getAttributeBaseValue(Attributes.ARMOR_TOUGHNESS);
		double health = bare.getAttributeBaseValue(Attributes.MAX_HEALTH);
		assertClose(helper, toughness + 10.0,
				withFullSet(helper, ModEnchantments.TOUGHNESS, 5, Attributes.ARMOR_TOUGHNESS), "Toughness V, four pieces");
		assertClose(helper, toughness + 2.0,
				withFullSet(helper, ModEnchantments.TOUGHNESS, 1, Attributes.ARMOR_TOUGHNESS), "Toughness I, four pieces");
		assertClose(helper, health + 20.0, withFullSet(helper, ModEnchantments.HEALTH, 5, Attributes.MAX_HEALTH),
				"Health V, four pieces");
		assertClose(helper, health + 4.0, withFullSet(helper, ModEnchantments.HEALTH, 1, Attributes.MAX_HEALTH),
				"Health I, four pieces");
		helper.succeed();
	}

	@GameTest
	public void swiftnessAddsFivePercentOfBaseSpeedPerLevel(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		double base = player.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
		assertClose(helper, base * 1.25, withEnchantment(player,
				enchanted(helper, Items.DIAMOND_BOOTS, ModEnchantments.SWIFTNESS, 5), EquipmentSlot.FEET,
				Attributes.MOVEMENT_SPEED), "Swiftness V");
		helper.succeed();
	}

	@GameTest
	public void stepAssistRaisesStepHeightToAFullBlock(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		assertClose(helper, 0.6, player.getAttributeValue(Attributes.STEP_HEIGHT), "a player's base step height");
		assertClose(helper, 1.0, withEnchantment(player,
				enchanted(helper, Items.DIAMOND_LEGGINGS, ModEnchantments.STEP_ASSIST, 1), EquipmentSlot.LEGS,
				Attributes.STEP_HEIGHT), "Step Assist");
		helper.succeed();
	}

	/** Owner, 2026-10-12: hits only, so explosion knockback resistance is untouched. */
	@GameTest
	public void anchoredAddsTenPercentKnockbackResistancePerLevel(GameTestHelper helper) {
		ServerPlayer one = helper.makeMockServerPlayerInLevel();
		assertClose(helper, 0.1, withEnchantment(one, enchanted(helper, Items.IRON_LEGGINGS, ModEnchantments.ANCHORED, 1),
				EquipmentSlot.LEGS, Attributes.KNOCKBACK_RESISTANCE), "Anchored I");
		ServerPlayer five = helper.makeMockServerPlayerInLevel();
		assertClose(helper, 0.5, withEnchantment(five, enchanted(helper, Items.IRON_LEGGINGS, ModEnchantments.ANCHORED, 5),
				EquipmentSlot.LEGS, Attributes.KNOCKBACK_RESISTANCE), "Anchored V");
		assertClose(helper, 0.0, five.getAttributeValue(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE),
				"Anchored V explosion knockback resistance");
		helper.succeed();
	}

	/** A player standing on soul sand: vanilla's factor is 0.4, and each Sure Footing level takes a third of the gap. */
	private static float soulSandFactor(GameTestHelper helper, int sureFooting) {
		BlockPos floor = new BlockPos(1, 1, 1);
		helper.setBlock(floor, Blocks.SOUL_SAND);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setPos(helper.absoluteVec(Vec3.atBottomCenterOf(floor.above())));
		if (sureFooting > 0) {
			withEnchantment(player, enchanted(helper, Items.DIAMOND_LEGGINGS, ModEnchantments.SURE_FOOTING, sureFooting),
					EquipmentSlot.LEGS, Attributes.MOVEMENT_EFFICIENCY);
		}
		return ((EntityInvoker) player).e2test$getBlockSpeedFactor();
	}

	@GameTest
	public void sureFootingTakesAThirdOfTheSoulSandSlowdownPerLevel(GameTestHelper helper) {
		assertClose(helper, 0.4, soulSandFactor(helper, 0), "soul sand without Sure Footing");
		assertClose(helper, 0.6, soulSandFactor(helper, 1), "soul sand with Sure Footing I");
		assertClose(helper, 0.8, soulSandFactor(helper, 2), "soul sand with Sure Footing II");
		assertClose(helper, 1.0, soulSandFactor(helper, 3), "soul sand with Sure Footing III");
		helper.succeed();
	}

	/**
	 * Owner, 2026-10-10 (DESIGN §5): every single-level enchantment that isn't a curse or Mending has a flat-10 row. Runs
	 * over the whole registry, so a later single-level enchantment can't skip its row. Vanilla's and ours only: a
	 * third-party one (the GameTest datapack ships one) keeps the default price.
	 */
	@GameTest
	public void everySingleLevelEnchantmentCostsTen(GameTestHelper helper) {
		Pricing ten = new Pricing(10, 0, UpgradeMode.LEVEL_DIFFERENCE, 0, 10);
		registry(helper).listElements()
				.filter(holder -> Set.of("minecraft", "enchanting2").contains(holder.key().identifier().getNamespace()))
				.filter(holder -> holder.value().getMaxLevel() == 1 && !holder.is(EnchantmentTags.CURSE)
						&& !holder.is(Enchantments.MENDING))
				.forEach(holder -> helper.assertValueEqual(ten, PricingTable.builtIn().get(holder.getRegisteredName()),
						Component.literal("price of " + holder.getRegisteredName())));
		helper.succeed();
	}

	/** 4 protection points per level against flying into a wall, from a chestplate or an elytra; none against a fall. */
	@GameTest
	public void cushionProtectsAgainstFlyingIntoAWallOnly(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		DamageSource wall = level.damageSources().flyIntoWall();
		DamageSource fall = level.damageSources().fall();
		for (Item chest : List.of(Items.ELYTRA, Items.DIAMOND_CHESTPLATE)) {
			player.setItemSlot(EquipmentSlot.CHEST, enchanted(helper, chest, ModEnchantments.CUSHION, 3));
			assertClose(helper, 12.0, EnchantmentHelper.getDamageProtection(level, player, wall),
					"Cushion III on " + chest + " vs fly_into_wall");
			assertClose(helper, 0.0, EnchantmentHelper.getDamageProtection(level, player, fall),
					"Cushion III on " + chest + " vs a fall");
		}
		player.setItemSlot(EquipmentSlot.CHEST, enchanted(helper, Items.ELYTRA, ModEnchantments.CUSHION, 1));
		assertClose(helper, 4.0, EnchantmentHelper.getDamageProtection(level, player, wall), "Cushion I");
		helper.succeed();
	}

	/** A zombie in four pieces of Health V armor: max health 40 once its equipment update has run. */
	private static Zombie inHealthArmor(GameTestHelper helper, BlockPos pos) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, pos);
		zombie.setNoAi(true);
		for (int i = 0; i < ARMOR_SLOTS.size(); i++) {
			zombie.setItemSlot(ARMOR_SLOTS.get(i), enchanted(helper, DIAMOND_ARMOR.get(i), ModEnchantments.HEALTH, 5));
		}
		return zombie;
	}

	/** Saves {@code zombie}, removes it, and loads the save into a new zombie, as a chunk unload and reload does. */
	static Zombie reload(GameTestHelper helper, Zombie zombie) {
		ServerLevel level = helper.getLevel();
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		zombie.saveWithoutId(output);
		CompoundTag saved = output.buildResult();
		zombie.discard();
		Zombie loaded = EntityType.ZOMBIE.create(level, EntitySpawnReason.LOAD);
		helper.assertTrue(loaded != null, Component.literal("no zombie created"));
		loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
		level.addFreshEntity(loaded);
		return loaded;
	}

	/**
	 * MC-17876: the load clamps saved health to the base max before the armor's +20 is back. {@code LivingEntityMixin}
	 * restores it after the first equipment update.
	 */
	@GameTest
	public void healthKeepsItsValueThroughASaveAndLoad(GameTestHelper helper) {
		Zombie zombie = inHealthArmor(helper, new BlockPos(1, 1, 1));
		Zombie[] loaded = new Zombie[1];
		helper.startSequence()
				.thenIdle(1)
				.thenExecute(() -> {
					helper.assertValueEqual(40.0F, zombie.getMaxHealth(), Component.literal("max in Health V armor"));
					zombie.setHealth(34.0F);
					loaded[0] = reload(helper, zombie);
					helper.assertValueEqual(20.0F, loaded[0].getHealth(),
							Component.literal("health right after the load (vanilla clamps to the base max)"));
				})
				.thenIdle(1)
				.thenExecute(() -> {
					helper.assertValueEqual(40.0F, loaded[0].getMaxHealth(), Component.literal("max after the load"));
					helper.assertValueEqual(34.0F, loaded[0].getHealth(), Component.literal("health after the load"));
				})
				.thenSucceed();
	}

	/** The restore never goes past the live max: armor gone before the first tick → 20, not the saved 34. */
	@GameTest
	public void restoredHealthIsClampedToTheLiveMax(GameTestHelper helper) {
		Zombie zombie = inHealthArmor(helper, new BlockPos(1, 1, 1));
		Zombie[] loaded = new Zombie[1];
		helper.startSequence()
				.thenIdle(1)
				.thenExecute(() -> {
					zombie.setHealth(34.0F);
					loaded[0] = reload(helper, zombie);
					ARMOR_SLOTS.forEach(slot -> loaded[0].setItemSlot(slot, ItemStack.EMPTY));
				})
				.thenIdle(1)
				.thenExecute(() -> {
					helper.assertValueEqual(20.0F, loaded[0].getMaxHealth(), Component.literal("max without armor"));
					helper.assertValueEqual(20.0F, loaded[0].getHealth(), Component.literal("health without armor"));
				})
				.thenSucceed();
	}

	/** Vanilla, pinned: taking Health armor off clamps health to the new max. */
	@GameTest
	public void removingHealthArmorClampsHealth(GameTestHelper helper) {
		Zombie zombie = inHealthArmor(helper, new BlockPos(1, 1, 1));
		helper.startSequence()
				.thenIdle(1)
				.thenExecute(() -> {
					zombie.setHealth(34.0F);
					ARMOR_SLOTS.forEach(slot -> zombie.setItemSlot(slot, ItemStack.EMPTY));
				})
				.thenIdle(1)
				.thenExecute(() -> helper.assertValueEqual(20.0F, zombie.getHealth(),
						Component.literal("health after taking the armor off")))
				.thenSucceed();
	}
}
