package com.wulfnet.enchanting2.gametest;

import com.wulfnet.enchanting2.enchantment.ModEnchantments;
import com.wulfnet.enchanting2.gametest.mixin.EnderManInvoker;
import com.wulfnet.enchanting2.gametest.mixin.EntityInvoker;
import com.wulfnet.enchanting2.gametest.mixin.FoodDataAccessor;
import com.wulfnet.enchanting2.gametest.mixin.PhantomInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 10cA: the helmet and leggings enchantments against the live registry and their real hooks (owner answers in
 * decisions.md "10c order and eight design answers"). Each test fails if its JSON loses the effect or its mixin
 * ({@code EnderManMixin}, {@code PhantomMixin}, {@code LivingEntityMixin}, {@code PlayerMixin},
 * {@code ServerPlayerMixin}, {@code PowderSnowBlockMixin}) is gone, and each checks the bare case first so it can't
 * pass vacuously.
 */
public class WornEnchantmentsGameTest {
	/** Vanilla's powder snow multiplier is the float 0.9 widened to double. */
	private static final double POWDER_SNOW = 0.9F;

	private static Holder<Enchantment> get(GameTestHelper helper, ResourceKey<Enchantment> key) {
		return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	private static ItemStack enchanted(GameTestHelper helper, Item item, ResourceKey<Enchantment> key, int level) {
		ItemStack stack = new ItemStack(item);
		stack.enchant(get(helper, key), level);
		return stack;
	}

	private static void check(GameTestHelper helper, boolean condition, String message) {
		helper.assertTrue(condition, Component.literal(message));
	}

	private static void assertClose(GameTestHelper helper, double expected, double actual, String what) {
		check(helper, Math.abs(expected - actual) < 1.0E-5, what + ": expected " + expected + ", got " + actual);
	}

	private static Zombie still(GameTestHelper helper, BlockPos pos) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, pos);
		zombie.setNoAi(true);
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			zombie.setItemSlot(slot, ItemStack.EMPTY);
		}
		return zombie;
	}

	@GameTest
	public void calmGazeStopsAnEndermanNoticingTheStare(GameTestHelper helper) {
		EnderMan enderman = helper.spawn(EntityType.ENDERMAN, new BlockPos(1, 1, 1));
		enderman.setNoAi(true);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setPos(helper.absoluteVec(new Vec3(1.5, 1.0, 5.5)));
		player.lookAt(EntityAnchorArgument.Anchor.EYES, enderman.getEyePosition());
		EnderManInvoker stare = (EnderManInvoker) enderman;
		check(helper, stare.e2test$isBeingStaredBy(player), "a bare-headed player looking at its eyes isn't staring");
		player.setItemSlot(EquipmentSlot.MAINHAND, enchanted(helper, Items.IRON_HELMET, ModEnchantments.CALM_GAZE, 1));
		check(helper, stare.e2test$isBeingStaredBy(player), "Calm Gaze held in a hand counts");
		player.setItemSlot(EquipmentSlot.HEAD, enchanted(helper, Items.IRON_HELMET, ModEnchantments.CALM_GAZE, 1));
		check(helper, !stare.e2test$isBeingStaredBy(player), "a Calm Gaze helmet still angers the enderman");
		helper.succeed();
	}

	/**
	 * Phantoms only hunt players, but the mock player is creative and so never a valid target; zombies stand in, as
	 * the ward reads only the target's equipment. The unwarded zombie is the "other players" case (owner).
	 */
	@GameTest
	public void phantomWardRefusesTheWearerUntilProvoked(GameTestHelper helper) {
		Phantom phantom = helper.spawn(EntityType.PHANTOM, new BlockPos(1, 3, 1));
		phantom.setNoAi(true);
		Zombie warded = still(helper, new BlockPos(1, 1, 1));
		Zombie other = still(helper, new BlockPos(3, 1, 1));
		PhantomInvoker invoker = (PhantomInvoker) phantom;
		TargetingConditions conditions = TargetingConditions.DEFAULT;
		check(helper, invoker.e2test$canAttack(helper.getLevel(), warded, conditions), "a bare-headed zombie is refused");
		warded.setItemSlot(EquipmentSlot.HEAD, enchanted(helper, Items.IRON_HELMET, ModEnchantments.PHANTOM_WARD, 1));
		check(helper, !invoker.e2test$canAttack(helper.getLevel(), warded, conditions), "the wearer is attackable");
		check(helper, invoker.e2test$canAttack(helper.getLevel(), other, conditions), "an unwarded zombie is refused");

		phantom.setLastHurtByMob(warded);
		check(helper, invoker.e2test$canAttack(helper.getLevel(), warded, conditions),
				"a phantom the wearer just hurt can't hit back");
		phantom.setTarget(warded);
		phantom.setLastHurtByMob(null);
		check(helper, invoker.e2test$canAttack(helper.getLevel(), warded, conditions),
				"the provoked phantom lets its target go once the hurt memory expires");
		phantom.setTarget(null);
		check(helper, !invoker.e2test$canAttack(helper.getLevel(), warded, conditions),
				"after vanilla dropped the target, the wearer is attackable again");
		phantom.setTarget(warded);
		check(helper, !invoker.e2test$canAttack(helper.getLevel(), warded, conditions),
				"the provocation came back without a new hit");
		helper.succeed();
	}

	private static int durationAfterAdding(GameTestHelper helper, LivingEntity entity, Holder<MobEffect> effect,
			int duration, boolean forced) {
		entity.removeAllEffects();
		MobEffectInstance instance = new MobEffectInstance(effect, duration);
		if (forced) {
			entity.forceAddEffect(instance, null);
		} else {
			entity.addEffect(instance);
		}
		MobEffectInstance landed = entity.getEffect(effect);
		return landed == null ? 0 : landed.getDuration();
	}

	private static ServerPlayer withClarity(GameTestHelper helper, int level) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setItemSlot(EquipmentSlot.HEAD, enchanted(helper, Items.IRON_HELMET, ModEnchantments.CLARITY, level));
		return player;
	}

	/** A third shorter per level, rounded (II on 300 ticks is 100, not a floored 99); III refuses; 0 = not applied. */
	@GameTest
	public void clarityShortensBlindnessDarknessAndNausea(GameTestHelper helper) {
		int infinite = MobEffectInstance.INFINITE_DURATION;
		ServerPlayer bare = helper.makeMockServerPlayerInLevel();
		helper.assertValueEqual(300, durationAfterAdding(helper, bare, MobEffects.BLINDNESS, 300, false),
				Component.literal("Blindness without Clarity"));
		ServerPlayer one = withClarity(helper, 1);
		helper.assertValueEqual(200, durationAfterAdding(helper, one, MobEffects.BLINDNESS, 300, false),
				Component.literal("Blindness 300 with Clarity I"));
		helper.assertValueEqual(200, durationAfterAdding(helper, one, MobEffects.NAUSEA, 300, true),
				Component.literal("Nausea 300 forced, with Clarity I"));
		helper.assertValueEqual(infinite, durationAfterAdding(helper, one, MobEffects.DARKNESS, infinite, false),
				Component.literal("infinite Darkness with Clarity I"));
		ServerPlayer two = withClarity(helper, 2);
		helper.assertValueEqual(100, durationAfterAdding(helper, two, MobEffects.DARKNESS, 300, false),
				Component.literal("Darkness 300 with Clarity II"));
		ServerPlayer three = withClarity(helper, 3);
		helper.assertValueEqual(0, durationAfterAdding(helper, three, MobEffects.BLINDNESS, 300, false),
				Component.literal("Blindness with Clarity III"));
		helper.assertValueEqual(0, durationAfterAdding(helper, three, MobEffects.DARKNESS, infinite, true),
				Component.literal("infinite Darkness forced, with Clarity III"));
		helper.assertValueEqual(300, durationAfterAdding(helper, three, MobEffects.SPEED, 300, false),
				Component.literal("Speed with Clarity III (not one of the three)"));
		Zombie zombie = still(helper, new BlockPos(1, 1, 1));
		zombie.setItemSlot(EquipmentSlot.HEAD, enchanted(helper, Items.IRON_HELMET, ModEnchantments.CLARITY, 3));
		helper.assertValueEqual(0, durationAfterAdding(helper, zombie, MobEffects.NAUSEA, 300, false),
				Component.literal("Nausea on a zombie with Clarity III (any wearer)"));
		helper.succeed();
	}

	private static ServerPlayer hungry(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.getAbilities().invulnerable = false; // invulnerable players get no exhaustion at all
		player.setOnGround(true);
		return player;
	}

	private static float exhaustionFrom(ServerPlayer player, Runnable action) {
		FoodDataAccessor food = (FoodDataAccessor) player.getFoodData();
		food.e2test$setExhaustionLevel(0.0F);
		action.run();
		return food.e2test$getExhaustionLevel();
	}

	/** Every source, 4% per level; multiplies with Curse of Hunger (3.0 × 0.8 = 2.4). */
	@GameTest
	public void sustenanceTakesFourPercentOfAllExhaustionPerLevel(GameTestHelper helper) {
		ServerPlayer player = hungry(helper);
		assertClose(helper, 1.0, exhaustionFrom(player, () -> player.causeFoodExhaustion(1.0F)), "no Sustenance");
		player.setItemSlot(EquipmentSlot.HEAD, enchanted(helper, Items.IRON_HELMET, ModEnchantments.SUSTENANCE, 5));
		assertClose(helper, 0.8, exhaustionFrom(player, () -> player.causeFoodExhaustion(1.0F)), "Sustenance V");
		ItemStack both = enchanted(helper, Items.IRON_HELMET, ModEnchantments.SUSTENANCE, 5);
		both.enchant(get(helper, ModEnchantments.HUNGER), 5);
		player.setItemSlot(EquipmentSlot.HEAD, both);
		player.setItemSlot(EquipmentSlot.CHEST, enchanted(helper, Items.IRON_CHESTPLATE, ModEnchantments.HUNGER, 5));
		player.setItemSlot(EquipmentSlot.LEGS, enchanted(helper, Items.IRON_LEGGINGS, ModEnchantments.HUNGER, 5));
		player.setItemSlot(EquipmentSlot.FEET, enchanted(helper, Items.IRON_BOOTS, ModEnchantments.HUNGER, 5));
		assertClose(helper, 2.4, exhaustionFrom(player, () -> player.causeFoodExhaustion(1.0F)),
				"Sustenance V with Hunger V on four pieces");
		helper.succeed();
	}

	/**
	 * Through vanilla's own sprint and jump paths: 1 m of sprinting is 0.1 and a sprint-jump 0.2, both 25% off at V;
	 * a plain jump's 0.05 is untouched; Sustenance multiplies on top.
	 */
	@GameTest
	public void enduranceTakesFivePercentOfSprintingExhaustionPerLevel(GameTestHelper helper) {
		ServerPlayer player = hungry(helper);
		player.setSprinting(true);
		assertClose(helper, 0.1, exhaustionFrom(player, () -> player.checkMovementStatistics(1.0, 0.0, 0.0)),
				"1 m sprint without Endurance");
		assertClose(helper, 0.2, exhaustionFrom(player, player::jumpFromGround), "sprint-jump without Endurance");
		player.setItemSlot(EquipmentSlot.LEGS, enchanted(helper, Items.IRON_LEGGINGS, ModEnchantments.ENDURANCE, 5));
		assertClose(helper, 0.075, exhaustionFrom(player, () -> player.checkMovementStatistics(1.0, 0.0, 0.0)),
				"1 m sprint with Endurance V");
		assertClose(helper, 0.15, exhaustionFrom(player, player::jumpFromGround), "sprint-jump with Endurance V");
		player.setItemSlot(EquipmentSlot.HEAD, enchanted(helper, Items.IRON_HELMET, ModEnchantments.SUSTENANCE, 5));
		assertClose(helper, 0.06, exhaustionFrom(player, () -> player.checkMovementStatistics(1.0, 0.0, 0.0)),
				"1 m sprint with Endurance V and Sustenance V");
		player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		player.setSprinting(false);
		assertClose(helper, 0.05, exhaustionFrom(player, player::jumpFromGround), "plain jump with Endurance V");
		helper.succeed();
	}

	/** The stuck multiplier powder snow sets on a player inside it, with Sure Footing at {@code level} (0 = none). */
	private static Vec3 powderSnowMultiplier(GameTestHelper helper, int level) {
		BlockPos snow = new BlockPos(1, 1, 1);
		helper.setBlock(snow, Blocks.POWDER_SNOW);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setPos(helper.absoluteVec(Vec3.atBottomCenterOf(snow)));
		if (level > 0) {
			// A mock player is never ticked, so add the leggings' attribute as equipping does.
			ItemStack leggings = enchanted(helper, Items.DIAMOND_LEGGINGS, ModEnchantments.SURE_FOOTING, level);
			EnchantmentHelper.forEachModifier(leggings, EquipmentSlot.LEGS, (attribute, modifier) ->
					player.getAttributes().getInstance(attribute).addOrUpdateTransientModifier(modifier));
		}
		helper.getLevel().getBlockState(helper.absolutePos(snow)).entityInside(helper.getLevel(),
				helper.absolutePos(snow), player, InsideBlockEffectApplier.NOOP, true);
		return ((EntityInvoker) player).e2test$getStuckSpeedMultiplier();
	}

	@GameTest
	public void sureFootingTakesAThirdOfThePowderSnowSlowdownPerLevel(GameTestHelper helper) {
		Vec3 none = powderSnowMultiplier(helper, 0);
		assertClose(helper, POWDER_SNOW, none.x, "x without Sure Footing");
		assertClose(helper, POWDER_SNOW + (1.0 - POWDER_SNOW) / 3.0, powderSnowMultiplier(helper, 1).x,
				"x with Sure Footing I");
		Vec3 three = powderSnowMultiplier(helper, 3);
		assertClose(helper, 1.0, three.x, "x with Sure Footing III");
		assertClose(helper, 1.0, three.z, "z with Sure Footing III");
		assertClose(helper, none.y, three.y, "y (sinking) with Sure Footing III");
		helper.succeed();
	}
}
