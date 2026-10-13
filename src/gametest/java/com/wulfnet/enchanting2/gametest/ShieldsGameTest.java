package com.wulfnet.enchanting2.gametest;

import com.wulfnet.enchanting2.enchantment.ModEnchantments;
import com.wulfnet.enchanting2.shield.BulwarkEffect;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Phase 10cCA: Steadfast, Spiked, Bulwark and Riposte through vanilla's real blocking path ({@code hurtServer} →
 * {@code applyItemBlocking}). A no-AI husk raising a shield stands in for a player: blocking reads only the item in
 * use and the attacker's position, and a mock player is never ticked, so it would never get past the block delay.
 * Husks, as zombies burn in the test level's daylight. Every husk that takes damage has 0 armor, so the numbers are
 * exact. Each test checks the bare case too.
 */
public class ShieldsGameTest {
	private static final float HEALTH = 20.0F;
	/** Long enough for a raised shield to pass its 0.25 s block delay. */
	private static final int RAISED = 10;

	private static Holder<Enchantment> get(GameTestHelper helper, ResourceKey<Enchantment> key) {
		return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	private static ItemStack shield(GameTestHelper helper, ResourceKey<Enchantment> key, int level) {
		ItemStack stack = new ItemStack(Items.SHIELD);
		if (key != null) {
			stack.enchant(get(helper, key), level);
		}
		return stack;
	}

	private static void check(GameTestHelper helper, boolean condition, String message) {
		helper.assertTrue(condition, Component.literal(message));
	}

	private static void assertClose(GameTestHelper helper, double expected, double actual, String what) {
		check(helper, Math.abs(expected - actual) < 1.0E-4, what + ": expected " + expected + ", got " + actual);
	}

	/** A still, armorless husk with nothing in hand, facing south (+z). */
	private static Husk husk(GameTestHelper helper, int x, int z) {
		Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(x, 1, z));
		husk.setNoAi(true);
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			husk.setItemSlot(slot, ItemStack.EMPTY);
		}
		husk.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		husk.setYRot(0.0F);
		husk.setYHeadRot(0.0F);
		husk.setYBodyRot(0.0F);
		return husk;
	}

	/** A husk at column {@code x} raising {@code shield} in its off hand, facing an attacker two blocks south. */
	private static Husk blocker(GameTestHelper helper, int x, ItemStack shield) {
		Husk husk = husk(helper, x, 1);
		husk.setItemSlot(EquipmentSlot.OFFHAND, shield);
		husk.startUsingItem(InteractionHand.OFF_HAND);
		return husk;
	}

	/** The attacker in front of {@link #blocker}'s column {@code x}. */
	private static Husk attacker(GameTestHelper helper, int x) {
		return husk(helper, x, 3);
	}

	/** {@code victim} takes {@code amount} from {@code source} with no i-frames left over from an earlier hit. */
	private static void hit(GameTestHelper helper, Husk victim, DamageSource source, float amount) {
		victim.invulnerableTime = 0;
		victim.hurtServer(helper.getLevel(), source, amount);
	}

	private static float lostBy(Husk husk) {
		return HEALTH - husk.getHealth();
	}

	@GameTest(maxTicks = 40)
	public void spikedHurtsAMeleeAttackerWhoseHitIsBlocked(GameTestHelper helper) {
		Husk spiked = blocker(helper, 1, shield(helper, ModEnchantments.SPIKED, 5));
		Husk plain = blocker(helper, 4, shield(helper, null, 0));
		Husk attacker = attacker(helper, 1);
		Husk other = attacker(helper, 4);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(RAISED, () -> {
			check(helper, spiked.isBlocking() && plain.isBlocking(), "the shields aren't raised");
			hit(helper, plain, level.damageSources().mobAttack(other), 4.0F);
			check(helper, plain.getHealth() == HEALTH, "a plain shield didn't block the hit");
			check(helper, other.getHealth() == HEALTH, "a plain shield hurt the attacker");

			hit(helper, spiked, level.damageSources().mobAttack(attacker), 4.0F);
			assertClose(helper, 5.0, lostBy(attacker), "Spiked V on a blocked melee hit");

			attacker.setHealth(HEALTH);
			attacker.invulnerableTime = 0;
			Arrow arrow = new Arrow(level, attacker.getX(), attacker.getEyeY(), attacker.getZ(),
					new ItemStack(Items.ARROW), null);
			hit(helper, spiked, level.damageSources().arrow(arrow, attacker), 4.0F);
			check(helper, attacker.getHealth() == HEALTH, "a blocked arrow hurt its shooter");
			hit(helper, spiked, level.damageSources().thorns(attacker), 4.0F);
			check(helper, attacker.getHealth() == HEALTH, "a thorns hit was answered with thorns");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 260)
	public void bulwarkGivesAHeartPerLevelEveryTenSeconds(GameTestHelper helper) {
		Husk blocker = blocker(helper, 1, shield(helper, ModEnchantments.BULWARK, 2));
		Husk attacker = attacker(helper, 1);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(RAISED, () -> {
			check(helper, blocker.getAbsorptionAmount() == 0.0F, "absorption before any block");
			hit(helper, blocker, level.damageSources().mobAttack(attacker), 4.0F);
			check(helper, blocker.getHealth() == HEALTH, "the hit wasn't blocked");
		});
		// The effect fills absorption on its first tick, as vanilla's Absorption does.
		helper.runAfterDelay(RAISED + 2, () -> {
			assertClose(helper, 4.0, blocker.getAbsorptionAmount(), "Bulwark II absorption");
			MobEffectInstance effect = blocker.getEffect(BulwarkEffect.holder());
			check(helper, effect != null && !effect.isVisible() && !effect.showIcon(), "the effect isn't hidden");
			int left = effect.getDuration();
			hit(helper, blocker, level.damageSources().mobAttack(attacker), 4.0F);
			check(helper, blocker.getEffect(BulwarkEffect.holder()).getDuration() == left,
					"a block during the cooldown refreshed Bulwark");
		});
		helper.runAfterDelay(RAISED + 110, () -> {
			check(helper, !blocker.hasEffect(BulwarkEffect.holder()), "Bulwark outlasted 5 s");
			check(helper, blocker.getAbsorptionAmount() == 0.0F,
					"absorption stayed after Bulwark ended: " + blocker.getAbsorptionAmount());
			hit(helper, blocker, level.damageSources().mobAttack(attacker), 4.0F);
		});
		helper.runAfterDelay(RAISED + 112, () -> check(helper, blocker.getAbsorptionAmount() == 0.0F,
				"Bulwark granted again 6 s after the last grant"));
		helper.runAfterDelay(RAISED + 205, () -> hit(helper, blocker, level.damageSources().mobAttack(attacker), 4.0F));
		helper.runAfterDelay(RAISED + 207, () -> {
			assertClose(helper, 4.0, blocker.getAbsorptionAmount(), "Bulwark after the 10 s cooldown");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 80)
	public void riposteRaisesTheNextMeleeHitWithinTwoSeconds(GameTestHelper helper) {
		Husk blocker = blocker(helper, 1, shield(helper, ModEnchantments.RIPOSTE, 5));
		Husk attacker = attacker(helper, 1);
		Husk target = husk(helper, 4, 4);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(RAISED, () -> {
			hit(helper, target, level.damageSources().mobAttack(blocker), 10.0F);
			assertClose(helper, 10.0, lostBy(target), "a hit before any block");

			hit(helper, blocker, level.damageSources().mobAttack(attacker), 4.0F);
			target.setHealth(HEALTH);
			hit(helper, target, level.damageSources().thorns(blocker), 1.0F);
			assertClose(helper, 1.0, lostBy(target), "a thorns hit after a block");
			target.setHealth(HEALTH);
			hit(helper, target, level.damageSources().mobAttack(blocker), 10.0F);
			assertClose(helper, 11.5, lostBy(target), "Riposte V's hit after a block (the thorns hit didn't use it)");
			target.setHealth(HEALTH);
			hit(helper, target, level.damageSources().mobAttack(blocker), 10.0F);
			assertClose(helper, 10.0, lostBy(target), "the hit after the riposte");

			hit(helper, blocker, level.damageSources().mobAttack(attacker), 4.0F);
		});
		helper.runAfterDelay(RAISED + 41, () -> {
			target.setHealth(HEALTH);
			hit(helper, target, level.damageSources().mobAttack(blocker), 10.0F);
			assertClose(helper, 10.0, lostBy(target), "a riposte 41 ticks old");
			helper.succeed();
		});
	}

	/** Ticks until {@code player}'s cooldowns let {@code shield} be raised again. */
	private static int ticksDisabled(ServerPlayer player, ItemStack shield) {
		ItemCooldowns cooldowns = player.getCooldowns();
		int ticks = 0;
		while (cooldowns.isOnCooldown(shield) && ticks < 1000) {
			cooldowns.tick();
			ticks++;
		}
		return ticks;
	}

	private static int disabledFor(GameTestHelper helper, ItemStack shield) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setItemSlot(EquipmentSlot.OFFHAND, shield);
		BlocksAttacks blocking = shield.get(DataComponents.BLOCKS_ATTACKS);
		blocking.disable(helper.getLevel(), player, 5.0F, shield);
		return ticksDisabled(player, shield);
	}

	/** Owner: −33% per level, III = never, from vanilla's 5 s axe. */
	@GameTest
	public void steadfastShortensAnAxeDisableAThirdPerLevel(GameTestHelper helper) {
		helper.assertValueEqual(100, disabledFor(helper, shield(helper, null, 0)), Component.literal("a plain shield"));
		// Vanilla rounds the ticks: 5 s × 2/3 is 66.7 ticks, so 67; × 1/3 is 33.3, so 33.
		helper.assertValueEqual(67, disabledFor(helper, shield(helper, ModEnchantments.STEADFAST, 1)),
				Component.literal("Steadfast I"));
		helper.assertValueEqual(33, disabledFor(helper, shield(helper, ModEnchantments.STEADFAST, 2)),
				Component.literal("Steadfast II"));
		helper.assertValueEqual(0, disabledFor(helper, shield(helper, ModEnchantments.STEADFAST, 3)),
				Component.literal("Steadfast III"));
		helper.succeed();
	}
}
