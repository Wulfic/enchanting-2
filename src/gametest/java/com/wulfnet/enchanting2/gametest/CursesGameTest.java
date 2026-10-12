package com.wulfnet.enchanting2.gametest;

import com.wulfnet.enchanting2.acquisition.AcquisitionRules;
import com.wulfnet.enchanting2.config.EnchantmentOverrides;
import com.wulfnet.enchanting2.curse.CurseEffects;
import com.wulfnet.enchanting2.enchantment.ModEnchantments;
import com.wulfnet.enchanting2.gametest.mixin.FoodDataAccessor;
import com.wulfnet.enchanting2.table.TablePool;
import java.util.List;
import java.util.function.IntSupplier;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * Phase 10e: our curses against the live registry (owner's numbers, decisions.md "10e curse scaling"). Each effect test
 * fails if its JSON loses or changes the effect; Fragility II also fails if durability damage truncates again, and
 * Weight and Hunger fail if their hook ({@code LivingEntityMixin}, {@code PlayerMixin}) is gone.
 */
public class CursesGameTest {
	private static final double EPSILON = 1.0E-6;

	/** Vanilla's Binding and Vanishing are in these, and so are ours. */
	private static final List<TagKey<Enchantment>> CURSE_TAGS = List.of(EnchantmentTags.CURSE, EnchantmentTags.TREASURE,
			EnchantmentTags.ON_RANDOM_LOOT, EnchantmentTags.TRADEABLE, TablePool.OFFERED);
	/** Sources a curse never comes from, as for vanilla's. */
	private static final List<TagKey<Enchantment>> NOT_CURSE_TAGS = List.of(EnchantmentTags.NON_TREASURE,
			EnchantmentTags.IN_ENCHANTING_TABLE, EnchantmentTags.ON_MOB_SPAWN_EQUIPMENT,
			EnchantmentTags.ON_TRADED_EQUIPMENT);

	private static Holder<Enchantment> get(GameTestHelper helper, ResourceKey<Enchantment> key) {
		return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	private static ItemStack enchanted(GameTestHelper helper, Item item, ResourceKey<Enchantment> key, int level) {
		ItemStack stack = new ItemStack(item);
		stack.enchant(get(helper, key), level);
		return stack;
	}

	@GameTest
	public void everyCurseIsTaggedLikeVanillasAndOfferedAtTheTable(GameTestHelper helper) {
		List<Holder<Enchantment>> pool = TablePool.members(
				helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT),
				new AcquisitionRules(EnchantmentOverrides.defaults()));
		for (ResourceKey<Enchantment> key : ModEnchantments.CURSES) {
			Holder<Enchantment> holder = get(helper, key);
			CURSE_TAGS.forEach(tag -> helper.assertTrue(holder.is(tag),
					Component.literal(key.identifier() + " is not in #" + tag.location())));
			NOT_CURSE_TAGS.forEach(tag -> helper.assertFalse(holder.is(tag),
					Component.literal(key.identifier() + " is in #" + tag.location())));
			helper.assertTrue(pool.contains(holder), Component.literal(key.identifier() + " is not in the table pool"));
		}
		helper.succeed();
	}

	/** ×2 at I, +0.5 per level: I and V are whole (exactly 2 and 4), II averages 2.5 (2 or 3, never truncated to 2). */
	@GameTest
	public void fragilityMultipliesDurabilityDamage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ItemStack one = enchanted(helper, Items.DIAMOND_PICKAXE, ModEnchantments.FRAGILITY, 1);
		ItemStack two = enchanted(helper, Items.DIAMOND_PICKAXE, ModEnchantments.FRAGILITY, 2);
		ItemStack five = enchanted(helper, Items.DIAMOND_PICKAXE, ModEnchantments.FRAGILITY, 5);
		assertMean(helper, () -> EnchantmentHelper.processDurabilityChange(level, one, 1), 2, 2, 2.0, 0.0,
				"Fragility I, 1 damage");
		assertMean(helper, () -> EnchantmentHelper.processDurabilityChange(level, two, 1), 2, 3, 2.5, 0.03,
				"Fragility II, 1 damage");
		assertMean(helper, () -> EnchantmentHelper.processDurabilityChange(level, five, 1), 4, 4, 4.0, 0.0,
				"Fragility V, 1 damage");
		assertMean(helper, () -> EnchantmentHelper.processDurabilityChange(level,
				new ItemStack(Items.DIAMOND_PICKAXE), 1), 1, 1, 1.0, 0.0, "no curse");
		helper.succeed();
	}

	/** −10% per level, multiplying with XP Boost (+25% at V). X gives nothing (the soft ceiling's note). */
	@GameTest
	public void greedTakesTenPercentOfExperiencePerLevel(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Pig pig = helper.spawn(EntityType.PIG, new BlockPos(1, 1, 1));
		pig.setNoAi(true);
		player.setItemSlot(EquipmentSlot.MAINHAND, enchanted(helper, Items.DIAMOND_SWORD, ModEnchantments.GREED, 3));
		assertMean(helper, () -> EnchantmentHelper.processMobExperience(level, player, pig, 10), 7, 7, 7.0, 0.0,
				"mob XP with Greed III");
		ItemStack boosted = enchanted(helper, Items.DIAMOND_SWORD, ModEnchantments.GREED, 3);
		boosted.enchant(get(helper, ModEnchantments.XP_BOOST), 5);
		player.setItemSlot(EquipmentSlot.MAINHAND, boosted);
		assertMean(helper, () -> EnchantmentHelper.processMobExperience(level, player, pig, 10), 8, 9, 8.75, 0.03,
				"mob XP with Greed III and XP Boost V");
		ItemStack five = enchanted(helper, Items.DIAMOND_PICKAXE, ModEnchantments.GREED, 5);
		assertMean(helper, () -> EnchantmentHelper.processBlockExperience(level, five, 4), 2, 2, 2.0, 0.0,
				"block XP with Greed V");
		ItemStack ten = enchanted(helper, Items.DIAMOND_PICKAXE, ModEnchantments.GREED, 10);
		assertMean(helper, () -> EnchantmentHelper.processBlockExperience(level, ten, 4), 0, 0, 0.0, 0.0,
				"block XP with Greed X");
		helper.succeed();
	}

	/** −15% of the whole break speed per level; VII is −105%, which the attribute floors at 0 (the ceiling note). */
	@GameTest
	public void fumblingSlowsBlockBreakingFifteenPercentPerLevel(GameTestHelper helper) {
		assertBreakSpeed(helper, 2, 0.7);
		assertBreakSpeed(helper, 5, 0.25);
		assertBreakSpeed(helper, 7, 0.0);
		helper.succeed();
	}

	private static void assertBreakSpeed(GameTestHelper helper, int level, double expected) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack pickaxe = enchanted(helper, Items.DIAMOND_PICKAXE, ModEnchantments.FUMBLING, level);
		EnchantmentHelper.forEachModifier(pickaxe, EquipmentSlot.MAINHAND,
				(holder, modifier) -> player.getAttributes().getInstance(holder).addOrUpdateTransientModifier(modifier));
		double actual = player.getAttributeValue(Attributes.BLOCK_BREAK_SPEED);
		helper.assertTrue(Math.abs(expected - actual) < EPSILON, Component.literal(
				"block break speed with Fumbling " + level + ": expected " + expected + ", got " + actual));
	}

	/**
	 * −10% speed per level per worn piece, summed into one modifier capped at 50% (owner, 2026-10-12), set by the real
	 * equipment-change hook on a ticking zombie: I alone, III + III capped, held in a hand ignored, back after a
	 * reload, gone when the armor comes off.
	 */
	@GameTest
	public void weightSlowsTenPercentPerLevelPerPieceCappedAtHalf(GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
		zombie.setNoAi(true);
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			zombie.setItemSlot(slot, ItemStack.EMPTY);
		}
		Zombie[] loaded = new Zombie[1];
		helper.startSequence()
				.thenIdle(1)
				.thenExecute(() -> {
					assertWeight(helper, zombie, 0.0, "no armor");
					zombie.setItemSlot(EquipmentSlot.CHEST,
							enchanted(helper, Items.IRON_CHESTPLATE, ModEnchantments.WEIGHT, 1));
					zombie.setItemSlot(EquipmentSlot.MAINHAND,
							enchanted(helper, Items.IRON_LEGGINGS, ModEnchantments.WEIGHT, 5));
				})
				.thenIdle(1)
				.thenExecute(() -> {
					assertWeight(helper, zombie, -0.1, "Weight I on one piece (Weight V held doesn't count)");
					zombie.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
					zombie.setItemSlot(EquipmentSlot.CHEST,
							enchanted(helper, Items.IRON_CHESTPLATE, ModEnchantments.WEIGHT, 3));
					zombie.setItemSlot(EquipmentSlot.LEGS,
							enchanted(helper, Items.IRON_LEGGINGS, ModEnchantments.WEIGHT, 3));
				})
				.thenIdle(1)
				.thenExecute(() -> {
					assertWeight(helper, zombie, -0.5, "Weight III + III, capped");
					loaded[0] = NewEnchantmentsGameTest.reload(helper, zombie);
				})
				.thenIdle(1)
				.thenExecute(() -> {
					assertWeight(helper, loaded[0], -0.5, "after a save and load");
					loaded[0].setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
					loaded[0].setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
				})
				.thenIdle(1)
				.thenExecute(() -> assertWeight(helper, loaded[0], 0.0, "armor taken off"))
				.thenSucceed();
	}

	/** 0 means no modifier at all, not a zero one. */
	private static void assertWeight(GameTestHelper helper, LivingEntity entity, double expected, String what) {
		AttributeModifier modifier = entity.getAttribute(Attributes.MOVEMENT_SPEED)
				.getModifier(CurseEffects.WEIGHT_MODIFIER);
		double actual = modifier == null ? 0.0 : modifier.amount();
		helper.assertTrue(Math.abs(expected - actual) < EPSILON && (expected != 0.0 || modifier == null),
				Component.literal("Weight modifier, " + what + ": expected " + expected + ", got " + modifier));
		if (modifier != null) {
			helper.assertValueEqual(AttributeModifier.Operation.ADD_MULTIPLIED_BASE, modifier.operation(),
					Component.literal("Weight modifier operation"));
		}
	}

	/** +10% exhaustion per level per worn piece, uncapped (owner): four pieces at V = ×3; held items don't count. */
	@GameTest
	public void hungerAddsTenPercentExhaustionPerLevelPerPiece(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.getAbilities().invulnerable = false; // invulnerable players get no exhaustion at all
		assertExhaustion(helper, player, 1.0F, "no curse");
		player.setItemSlot(EquipmentSlot.HEAD, enchanted(helper, Items.IRON_HELMET, ModEnchantments.HUNGER, 1));
		assertExhaustion(helper, player, 1.1F, "Hunger I on one piece");
		player.setItemSlot(EquipmentSlot.MAINHAND, enchanted(helper, Items.IRON_HELMET, ModEnchantments.HUNGER, 5));
		assertExhaustion(helper, player, 1.1F, "Hunger V held in a hand");
		player.setItemSlot(EquipmentSlot.HEAD, enchanted(helper, Items.IRON_HELMET, ModEnchantments.HUNGER, 5));
		player.setItemSlot(EquipmentSlot.CHEST, enchanted(helper, Items.IRON_CHESTPLATE, ModEnchantments.HUNGER, 5));
		player.setItemSlot(EquipmentSlot.LEGS, enchanted(helper, Items.IRON_LEGGINGS, ModEnchantments.HUNGER, 5));
		player.setItemSlot(EquipmentSlot.FEET, enchanted(helper, Items.IRON_BOOTS, ModEnchantments.HUNGER, 5));
		assertExhaustion(helper, player, 3.0F, "Hunger V on four pieces");
		helper.succeed();
	}

	private static void assertExhaustion(GameTestHelper helper, ServerPlayer player, float expected, String what) {
		FoodDataAccessor food = (FoodDataAccessor) player.getFoodData();
		food.e2test$setExhaustionLevel(0.0F);
		player.causeFoodExhaustion(1.0F);
		float actual = food.e2test$getExhaustionLevel();
		helper.assertTrue(Math.abs(expected - actual) < 1.0E-5F,
				Component.literal("exhaustion from 1.0, " + what + ": expected " + expected + ", got " + actual));
	}

	private static void assertMean(GameTestHelper helper, IntSupplier amount, int min, int max, double mean,
			double tolerance, String what) {
		int calls = 4000;
		long total = 0;
		for (int i = 0; i < calls; i++) {
			int value = amount.getAsInt();
			helper.assertTrue(value >= min && value <= max,
					Component.literal(what + ": " + value + " outside [" + min + ", " + max + "]"));
			total += value;
		}
		double actual = total / (double) calls;
		helper.assertTrue(Math.abs(actual - mean) <= tolerance,
				Component.literal(what + ": mean " + actual + ", want " + mean + " ± " + tolerance));
	}
}
