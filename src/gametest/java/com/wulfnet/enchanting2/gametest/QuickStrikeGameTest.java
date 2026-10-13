package com.wulfnet.enchanting2.gametest;

import com.wulfnet.enchanting2.enchantment.ModEnchantments;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 10cFA: Quick Strike through the real melee paths, {@code Mob.doHurtTarget} (husks, ticked so their sword's
 * damage applies) and {@code Player.attack}. Level XX makes the chance 1, so every hit repeats; each case is compared
 * with the same hit from a plain sword in a mirrored spot.
 */
public class QuickStrikeGameTest {
	private static final float HEALTH = 20.0F;

	@SafeVarargs
	private static ItemStack sword(GameTestHelper helper, ResourceKey<Enchantment>... keys) {
		ItemStack stack = new ItemStack(Items.IRON_SWORD);
		for (ResourceKey<Enchantment> key : keys) {
			int level = key == ModEnchantments.QUICK_STRIKE ? 20 : 5;
			stack.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key),
					level);
		}
		return stack;
	}

	private static void check(GameTestHelper helper, boolean condition, String message) {
		helper.assertTrue(condition, Component.literal(message));
	}

	private static void assertClose(GameTestHelper helper, double expected, double actual, String what) {
		check(helper, Math.abs(expected - actual) < 1.0E-4, what + ": expected " + expected + ", got " + actual);
	}

	/** A still, armorless husk at column {@code x}, row {@code z}, holding {@code weapon}. */
	private static Husk husk(GameTestHelper helper, int x, int z, ItemStack weapon) {
		Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(x, 1, z));
		husk.setNoAi(true);
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			husk.setItemSlot(slot, ItemStack.EMPTY);
		}
		husk.setItemSlot(EquipmentSlot.MAINHAND, weapon);
		husk.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		return husk;
	}

	private static float lost(Husk husk) {
		return HEALTH - husk.getHealth();
	}

	@GameTest(maxTicks = 20)
	public void quickStrikeRepeatsAMobsHitWithoutTheKnockback(GameTestHelper helper) {
		Husk quick = husk(helper, 1, 1, sword(helper, ModEnchantments.QUICK_STRIKE));
		Husk plain = husk(helper, 4, 1, sword(helper));
		Husk quickVictim = husk(helper, 1, 3, ItemStack.EMPTY);
		Husk plainVictim = husk(helper, 4, 3, ItemStack.EMPTY);
		helper.runAfterDelay(3, () -> {
			check(helper, quick.doHurtTarget(helper.getLevel(), quickVictim), "Quick Strike's hit missed");
			check(helper, plain.doHurtTarget(helper.getLevel(), plainVictim), "the plain hit missed");
			check(helper, lost(plainVictim) > 1.0F, "the plain sword's damage didn't apply: " + lost(plainVictim));
			assertClose(helper, 2.0 * lost(plainVictim), lost(quickVictim), "Quick Strike XX's damage");
			Vec3 quickMotion = quickVictim.getDeltaMovement();
			Vec3 plainMotion = plainVictim.getDeltaMovement();
			check(helper, quickMotion.distanceTo(plainMotion) < 1.0E-6,
					"a second knockback: " + quickMotion + " vs " + plainMotion);
			helper.succeed();
		});
	}

	/** Vampirism heals for the first hit only (owner: the second hit is damage only). */
	@GameTest(maxTicks = 20)
	public void quickStrikesSecondHitDoesNotHeal(GameTestHelper helper) {
		Husk both = husk(helper, 1, 1, sword(helper, ModEnchantments.QUICK_STRIKE, ModEnchantments.VAMPIRISM));
		Husk vampire = husk(helper, 4, 1, sword(helper, ModEnchantments.VAMPIRISM));
		Husk bothVictim = husk(helper, 1, 3, ItemStack.EMPTY);
		Husk vampireVictim = husk(helper, 4, 3, ItemStack.EMPTY);
		helper.runAfterDelay(3, () -> {
			both.setHealth(5.0F);
			vampire.setHealth(5.0F);
			both.doHurtTarget(helper.getLevel(), bothVictim);
			vampire.doHurtTarget(helper.getLevel(), vampireVictim);
			check(helper, lost(bothVictim) > lost(vampireVictim) + 1.0F, "the second hit didn't land");
			check(helper, vampire.getHealth() > 5.0F, "Vampirism didn't heal at all");
			assertClose(helper, vampire.getHealth(), both.getHealth(), "healing with Quick Strike");
			helper.succeed();
		});
	}

	/** A player's hit repeats too, and the sword pays durability once. */
	@GameTest(maxTicks = 20)
	public void quickStrikeRepeatsAPlayersHitForOneDurability(GameTestHelper helper) {
		Husk quickVictim = husk(helper, 1, 3, ItemStack.EMPTY);
		Husk plainVictim = husk(helper, 4, 3, ItemStack.EMPTY);
		helper.runAfterDelay(3, () -> {
			ServerPlayer quick = attacker(helper, 1, sword(helper, ModEnchantments.QUICK_STRIKE));
			ServerPlayer plain = attacker(helper, 4, sword(helper));
			quick.attack(quickVictim);
			plain.attack(plainVictim);
			check(helper, lost(plainVictim) > 0.0F, "the plain hit missed");
			assertClose(helper, 2.0 * lost(plainVictim), lost(quickVictim), "Quick Strike XX's damage from a player");
			helper.assertValueEqual(1, quick.getMainHandItem().getDamageValue(),
					Component.literal("Quick Strike sword durability used"));
			helper.assertValueEqual(1, plain.getMainHandItem().getDamageValue(),
					Component.literal("plain sword durability used"));
			helper.succeed();
		});
	}

	private static ServerPlayer attacker(GameTestHelper helper, int x, ItemStack sword) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.snapTo(helper.absoluteVec(new Vec3(x + 0.5, 1.0, 1.5)), 0.0F, 0.0F);
		player.setItemInHand(InteractionHand.MAIN_HAND, sword);
		return player;
	}
}
