package com.wulfnet.enchanting2.gametest;

import com.wulfnet.enchanting2.config.AllowTogetherSection;
import com.wulfnet.enchanting2.enchantment.ConflictFamily;
import com.wulfnet.enchanting2.enchantment.EnchantRules;
import com.wulfnet.enchanting2.gametest.mixin.EntityInvoker;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * DESIGN §4's "when combined" column (Phase 8A). Every item is built through {@link EnchantRules} with its family
 * allowed, and each test first proves the same build is a {@code CONFLICT} with the family off, so the combination comes
 * from our relaxation and not from {@code /enchant}. Vanilla evaluates each enchantment's effect components on its own
 * and sums them, so apart from the trident (which has {@code TridentItemMixin}) these pin vanilla behavior we rely on.
 */
public class ConflictEffectsGameTest {
	private static final float EPSILON = 1.0E-4F;

	private record Entry(ResourceKey<Enchantment> key, int level) {
	}

	private static Entry e(ResourceKey<Enchantment> key, int level) {
		return new Entry(key, level);
	}

	private static Holder<Enchantment> get(GameTestHelper helper, ResourceKey<Enchantment> key) {
		return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	/** The highest level the registry allows (DESIGN §8 after our MODIFY), so tests follow the configured maxes. */
	private static Entry max(GameTestHelper helper, ResourceKey<Enchantment> key) {
		return e(key, get(helper, key).value().getMaxLevel());
	}

	/**
	 * {@code item} with {@code entries}, each added through {@link EnchantRules#check} with every family allowed. With
	 * {@code family} off, at least one entry must be refused as a conflict.
	 */
	private static ItemStack build(GameTestHelper helper, ItemStack item, ConflictFamily family, Entry... entries) {
		ItemEnchantments allowed = apply(helper, item, AllowTogetherSection.DEFAULTS, entries);
		helper.assertTrue(allowed != null, Component.literal(family + ": not buildable with the family allowed"));
		ItemEnchantments vanilla = apply(helper, item,
				new AllowTogetherSection(EnumSet.complementOf(EnumSet.of(family))), entries);
		helper.assertTrue(vanilla == null, Component.literal(family + ": buildable with the family off, so the test "
				+ "would not prove the relaxation"));
		item.set(DataComponents.ENCHANTMENTS, allowed);
		return item;
	}

	/** The enchantments after adding every entry, or null when {@code EnchantRules} refuses one as a conflict. */
	private static ItemEnchantments apply(GameTestHelper helper, ItemStack item, AllowTogetherSection allowTogether,
			Entry... entries) {
		ItemEnchantments result = ItemEnchantments.EMPTY;
		for (Entry entry : entries) {
			Holder<Enchantment> holder = get(helper, entry.key());
			EnchantRules.Result check = EnchantRules.check(item, holder, entry.level(), result, allowTogether);
			if (check.verdict() == EnchantRules.Verdict.CONFLICT) {
				return null;
			}
			helper.assertTrue(check.ok(), Component.literal(entry + ": " + check.verdict()));
			result = EnchantRules.withEnchantment(result, holder, entry.level());
		}
		return result;
	}

	private static ItemStack single(GameTestHelper helper, ItemStack item, Entry entry) {
		item.enchant(get(helper, entry.key()), entry.level());
		return item;
	}

	private static void assertClose(GameTestHelper helper, float expected, float actual, String what) {
		helper.assertTrue(Math.abs(expected - actual) < EPSILON,
				Component.literal(what + ": expected " + expected + ", got " + actual));
	}

	/** The bonus a weapon adds over a bare hit against {@code target}. */
	private static float bonus(GameTestHelper helper, ItemStack weapon, Entity target, DamageSource source) {
		float base = 1.0F;
		return EnchantmentHelper.modifyDamage(helper.getLevel(), weapon, target, source, base) - base;
	}

	@GameTest
	public void sharpnessAndSmiteBothAddAgainstUndead(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Entity zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
		DamageSource source = helper.getLevel().damageSources().playerAttack(player);
		Entry sharpness = max(helper, Enchantments.SHARPNESS);
		Entry smite = max(helper, Enchantments.SMITE);
		ItemStack both = build(helper, new ItemStack(Items.DIAMOND_SWORD), ConflictFamily.DAMAGE, sharpness, smite);
		float sharpOnly = bonus(helper, single(helper, new ItemStack(Items.DIAMOND_SWORD), sharpness), zombie, source);
		float smiteOnly = bonus(helper, single(helper, new ItemStack(Items.DIAMOND_SWORD), smite), zombie, source);
		helper.assertTrue(sharpOnly > 0 && smiteOnly > 0, Component.literal("each alone adds nothing"));
		assertClose(helper, sharpOnly + smiteOnly, bonus(helper, both, zombie, source), "Sharpness + Smite vs zombie");
		helper.succeed();
	}

	@GameTest
	public void sharpnessAndBaneBothAddAgainstArthropods(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Entity spider = helper.spawn(EntityType.SPIDER, new BlockPos(1, 1, 1));
		DamageSource source = helper.getLevel().damageSources().playerAttack(player);
		Entry sharpness = max(helper, Enchantments.SHARPNESS);
		Entry bane = max(helper, Enchantments.BANE_OF_ARTHROPODS);
		ItemStack both = build(helper, new ItemStack(Items.IRON_AXE), ConflictFamily.DAMAGE, sharpness, bane);
		float sharpOnly = bonus(helper, single(helper, new ItemStack(Items.IRON_AXE), sharpness), spider, source);
		float baneOnly = bonus(helper, single(helper, new ItemStack(Items.IRON_AXE), bane), spider, source);
		helper.assertTrue(sharpOnly > 0 && baneOnly > 0, Component.literal("each alone adds nothing"));
		assertClose(helper, sharpOnly + baneOnly, bonus(helper, both, spider, source), "Sharpness + Bane vs spider");
		helper.succeed();
	}

	/** A mace holds four of the six: Smite and Bane add as above, and Density and Breach each keep their own effect. */
	@GameTest
	public void maceKeepsDensityAndBreachWithSmiteAndBane(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Entity zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
		ServerLevel level = helper.getLevel();
		DamageSource source = level.damageSources().playerAttack(player);
		Entry density = max(helper, Enchantments.DENSITY);
		Entry breach = max(helper, Enchantments.BREACH);
		Entry smite = max(helper, Enchantments.SMITE);
		Entry bane = max(helper, Enchantments.BANE_OF_ARTHROPODS);
		ItemStack mace = build(helper, new ItemStack(Items.MACE), ConflictFamily.DAMAGE, density, breach, smite, bane);
		float densityOnly = EnchantmentHelper.modifyFallBasedDamage(level,
				single(helper, new ItemStack(Items.MACE), density), zombie, source, 0.0F);
		float breachOnly = EnchantmentHelper.modifyArmorEffectiveness(level,
				single(helper, new ItemStack(Items.MACE), breach), zombie, source, 1.0F);
		helper.assertTrue(densityOnly > 0 && breachOnly < 1.0F, Component.literal("Density or Breach alone does nothing"));
		assertClose(helper, densityOnly, EnchantmentHelper.modifyFallBasedDamage(level, mace, zombie, source, 0.0F),
				"Density per fallen block on the four-way mace");
		assertClose(helper, breachOnly, EnchantmentHelper.modifyArmorEffectiveness(level, mace, zombie, source, 1.0F),
				"Breach armor effectiveness on the four-way mace");
		float smiteOnly = bonus(helper, single(helper, new ItemStack(Items.MACE), smite), zombie, source);
		assertClose(helper, smiteOnly, bonus(helper, mace, zombie, source), "Smite on the four-way mace vs zombie");
		helper.succeed();
	}

	/** All four protections apply, and vanilla's clamp still caps the total at 20 EPF (an 80% reduction). */
	@GameTest
	public void protectionFamilyStacksUpToTheEpfCap(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ServerLevel level = helper.getLevel();
		DamageSource explosion = level.damageSources().explosion(null, null);
		Entry protection = max(helper, Enchantments.PROTECTION);
		Entry blast = max(helper, Enchantments.BLAST_PROTECTION);
		Entry fire = max(helper, Enchantments.FIRE_PROTECTION);
		Entry projectile = max(helper, Enchantments.PROJECTILE_PROTECTION);
		Map<EquipmentSlot, ItemStack> armor = Map.of(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET),
				EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE), EquipmentSlot.LEGS,
				new ItemStack(Items.DIAMOND_LEGGINGS), EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));

		armor.forEach((slot, piece) -> player.setItemSlot(slot, single(helper, piece.copy(), protection)));
		float protectionOnly = EnchantmentHelper.getDamageProtection(level, player, explosion);
		armor.forEach((slot, piece) -> player.setItemSlot(slot, single(helper, piece.copy(), blast)));
		float blastOnly = EnchantmentHelper.getDamageProtection(level, player, explosion);
		armor.forEach((slot, piece) -> player.setItemSlot(slot,
				build(helper, piece.copy(), ConflictFamily.PROTECTION, protection, blast, fire, projectile)));
		float all = EnchantmentHelper.getDamageProtection(level, player, explosion);

		helper.assertTrue(protectionOnly > 0 && blastOnly > 0, Component.literal("a protection alone adds no EPF"));
		assertClose(helper, protectionOnly + blastOnly, all, "EPF vs explosion with all four on every piece");
		helper.assertTrue(all > 20.0F, Component.literal("EPF " + all + " never reaches the cap, so the cap is untested"));
		assertClose(helper, 10.0F * (1.0F - 20.0F / 25.0F), CombatRules.getDamageAfterMagicAbsorb(10.0F, all),
				"10 damage after EPF " + all + " (capped at 20)");
		helper.succeed();
	}

	@GameTest
	public void mendingAndInfinityBothWorkOnABow(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Entry mending = max(helper, Enchantments.MENDING);
		ItemStack bow = build(helper, new ItemStack(Items.BOW), ConflictFamily.MENDING_INFINITY, mending,
				max(helper, Enchantments.INFINITY));
		helper.assertValueEqual(0, EnchantmentHelper.processAmmoUse(level, bow, new ItemStack(Items.ARROW), 1),
				Component.literal("arrows used per shot"));
		int mendingOnly = EnchantmentHelper.modifyDurabilityToRepairFromXp(level,
				single(helper, new ItemStack(Items.BOW), mending), 10);
		helper.assertTrue(mendingOnly > 0, Component.literal("Mending alone repairs nothing"));
		helper.assertValueEqual(mendingOnly, EnchantmentHelper.modifyDurabilityToRepairFromXp(level, bow, 10),
				Component.literal("durability repaired by 10 XP"));
		helper.succeed();
	}

	@GameTest
	public void multishotAndPiercingBothApply(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ServerLevel level = helper.getLevel();
		Entry piercing = max(helper, Enchantments.PIERCING);
		ItemStack crossbow = build(helper, new ItemStack(Items.CROSSBOW), ConflictFamily.MULTISHOT_PIERCING,
				max(helper, Enchantments.MULTISHOT), piercing);
		helper.assertValueEqual(3, EnchantmentHelper.processProjectileCount(level, crossbow, player, 1),
				Component.literal("projectiles per shot"));
		helper.assertValueEqual(piercing.level(),
				EnchantmentHelper.getPiercingCount(level, crossbow, new ItemStack(Items.ARROW)),
				Component.literal("arrow pierce count"));
		helper.succeed();
	}

	@GameTest
	public void depthStriderAndFrostWalkerBothApply(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		for (int x = 0; x <= 4; x++) {
			for (int z = 0; z <= 4; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.GLASS);
				helper.setBlock(new BlockPos(x, 1, z), Blocks.WATER);
			}
		}
		ItemStack boots = build(helper, new ItemStack(Items.DIAMOND_BOOTS), ConflictFamily.DEPTH_STRIDER_FROST_WALKER,
				max(helper, Enchantments.DEPTH_STRIDER), max(helper, Enchantments.FROST_WALKER));
		List<Double> waterMovement = new ArrayList<>();
		EnchantmentHelper.forEachModifier(boots, EquipmentSlot.FEET, (attribute, modifier) -> {
			if (attribute.is(Attributes.WATER_MOVEMENT_EFFICIENCY)) {
				waterMovement.add(modifier.amount());
			}
		});
		helper.assertTrue(waterMovement.size() == 1 && waterMovement.get(0) > 0,
				Component.literal("Depth Strider water movement modifiers: " + waterMovement));

		player.setItemSlot(EquipmentSlot.FEET, boots);
		player.snapTo(helper.absoluteVec(new Vec3(2.5, 2.0, 2.5)));
		player.setOnGround(true);
		EnchantmentHelper.runLocationChangedEffects(helper.getLevel(), boots, player, EquipmentSlot.FEET);
		helper.assertBlockPresent(Blocks.FROSTED_ICE, new BlockPos(2, 1, 2));
		helper.succeed();
	}

	/** A 3×3, two-deep water pool walled with glass; returns the centre of its bottom water block. */
	private static Vec3 pool(GameTestHelper helper) {
		for (int x = 1; x <= 5; x++) {
			for (int z = 1; z <= 5; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.GLASS);
				for (int y = 1; y <= 2; y++) {
					boolean wall = x == 1 || x == 5 || z == 1 || z == 5;
					helper.setBlock(new BlockPos(x, y, z), wall ? Blocks.GLASS : Blocks.WATER);
				}
			}
		}
		return helper.absoluteVec(new Vec3(3.5, 1.0, 3.5));
	}

	/** {@code use}, then release after 20 ticks (vanilla needs 10), as a player holding the trident does. */
	private static boolean useAndRelease(GameTestHelper helper, ServerPlayer player, ItemStack trident) {
		player.setItemInHand(InteractionHand.MAIN_HAND, trident);
		InteractionResult used = trident.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		helper.assertTrue(used != InteractionResult.FAIL, Component.literal("use refused the trident"));
		return trident.getItem().releaseUsing(trident, helper.getLevel(), player, trident.getUseDuration(player) - 20);
	}

	private static List<ThrownTrident> thrown(GameTestHelper helper, ServerPlayer player) {
		return helper.getLevel().getEntitiesOfClass(ThrownTrident.class, new AABB(player.blockPosition()).inflate(8));
	}

	private static ItemStack riptideTrident(GameTestHelper helper) {
		return build(helper, new ItemStack(Items.TRIDENT), ConflictFamily.RIPTIDE_LOYALTY_CHANNELING,
				max(helper, Enchantments.RIPTIDE), max(helper, Enchantments.LOYALTY),
				max(helper, Enchantments.CHANNELING));
	}

	@GameTest
	public void riptideLoyaltyChannelingThrowsOnLand(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.snapTo(helper.absoluteVec(new Vec3(1.5, 1.0, 1.5)), 0.0F, 0.0F);
		helper.assertFalse(player.isInWaterOrRain(), Component.literal("mock player is wet"));
		helper.assertTrue(useAndRelease(helper, player, riptideTrident(helper)),
				Component.literal("release did nothing on land"));
		helper.assertFalse(player.isAutoSpinAttack(), Component.literal("Riptide launched on land"));
		List<ThrownTrident> thrown = thrown(helper, player);
		helper.assertValueEqual(1, thrown.size(), Component.literal("thrown tridents"));
		ItemStack weapon = thrown.get(0).getWeaponItem();
		helper.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(get(helper, Enchantments.LOYALTY), weapon) > 0
				&& EnchantmentHelper.getItemEnchantmentLevel(get(helper, Enchantments.CHANNELING), weapon) > 0,
				Component.literal("the thrown trident lost Loyalty or Channeling"));
		helper.succeed();
	}

	/** Owner, 2026-10-08: a Riptide-only trident throws on land too (vanilla refuses to use it there). */
	@GameTest
	public void riptideAloneThrowsOnLand(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.snapTo(helper.absoluteVec(new Vec3(1.5, 1.0, 1.5)), 0.0F, 0.0F);
		ItemStack trident = single(helper, new ItemStack(Items.TRIDENT), max(helper, Enchantments.RIPTIDE));
		helper.assertTrue(useAndRelease(helper, player, trident), Component.literal("release did nothing on land"));
		helper.assertValueEqual(1, thrown(helper, player).size(), Component.literal("thrown tridents"));
		helper.succeed();
	}

	@GameTest(skyAccess = true)
	public void riptideLoyaltyChannelingLaunchesInWater(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.snapTo(pool(helper), 0.0F, 0.0F);
		helper.assertTrue(((EntityInvoker) player).e2test$updateInWaterStateAndDoFluidPushing() && player.isInWaterOrRain(),
				Component.literal("mock player is not in the pool's water"));
		helper.assertTrue(useAndRelease(helper, player, riptideTrident(helper)),
				Component.literal("release did nothing in water"));
		helper.assertTrue(player.isAutoSpinAttack(), Component.literal("Riptide did not launch in water"));
		helper.assertValueEqual(0, thrown(helper, player).size(), Component.literal("thrown tridents"));
		helper.succeed();
	}
}
