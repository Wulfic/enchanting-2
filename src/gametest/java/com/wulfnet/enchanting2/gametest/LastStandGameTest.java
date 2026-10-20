package com.wulfnet.enchanting2.gametest;

import com.wulfnet.enchanting2.combat.LastStand;
import com.wulfnet.enchanting2.config.CommonConfig;
import com.wulfnet.enchanting2.config.LastStandSection;
import com.wulfnet.enchanting2.config.ServerConfigHolder;
import com.wulfnet.enchanting2.config.ServerConfigs;
import com.wulfnet.enchanting2.enchantment.ModEnchantments;
import com.wulfnet.enchanting2.gametest.mixin.PacketListenerInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 10cFB: Last Stand through vanilla's real death check in {@code hurtServer}. Husks wear it (any living entity
 * can, as with a totem); the PvP toggle is checked on mock players, with the server's config swapped and put back.
 */
public class LastStandGameTest {
	private static final float LETHAL = 100.0F;

	private static ItemStack chestplate(GameTestHelper helper, int level) {
		ItemStack stack = new ItemStack(Items.IRON_CHESTPLATE);
		stack.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
				.getOrThrow(ModEnchantments.LAST_STAND), level);
		return stack;
	}

	private static void check(GameTestHelper helper, boolean condition, String message) {
		helper.assertTrue(condition, Component.literal(message));
	}

	private static Husk wearer(GameTestHelper helper, int x, int level) {
		Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(x, 1, 1));
		husk.setNoAi(true);
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			husk.setItemSlot(slot, ItemStack.EMPTY);
		}
		husk.setItemSlot(EquipmentSlot.CHEST, chestplate(helper, level));
		return husk;
	}

	private static void hit(GameTestHelper helper, LivingEntity victim, DamageSource source) {
		victim.invulnerableTime = 0;
		victim.hurtServer(helper.getLevel(), source, LETHAL);
	}

	private static long readyIn(GameTestHelper helper, LivingEntity entity) {
		return LastStand.READY_AT.getOrElse(entity, 0L) - helper.getLevel().getGameTime();
	}

	@GameTest
	public void lastStandSavesOnceThenTheCooldownLetsTheNextHitKill(GameTestHelper helper) {
		Husk husk = wearer(helper, 1, 1);
		hit(helper, husk, helper.getLevel().damageSources().generic());
		check(helper, husk.isAlive() && husk.getHealth() == 1.0F, "a lethal hit wasn't survived: " + husk.getHealth());
		check(helper, husk.hasEffect(MobEffects.ABSORPTION), "no absorption after the save");
		helper.assertValueEqual(12000L, readyIn(helper, husk), Component.literal("Last Stand I's cooldown (10 min)"));
		hit(helper, husk, helper.getLevel().damageSources().generic());
		check(helper, husk.isDeadOrDying(), "Last Stand saved twice in its cooldown");
		helper.succeed();
	}

	/** Owner: a Totem of Undying fires first and doesn't use the cooldown. */
	@GameTest
	public void aTotemFiresFirstAndLeavesTheCooldownAlone(GameTestHelper helper) {
		Husk husk = wearer(helper, 1, 1);
		husk.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
		hit(helper, husk, helper.getLevel().damageSources().generic());
		check(helper, husk.isAlive() && husk.getOffhandItem().isEmpty(), "the totem didn't fire first");
		check(helper, LastStand.READY_AT.get(husk) == null, "the totem's save used Last Stand's cooldown");
		husk.setHealth(1.0F);
		husk.removeAllEffects();
		hit(helper, husk, helper.getLevel().damageSources().generic());
		check(helper, husk.isAlive() && husk.getHealth() == 1.0F, "Last Stand didn't save after the totem");
		helper.succeed();
	}

	@GameTest
	public void theVoidKillsThroughLastStand(GameTestHelper helper) {
		Husk husk = wearer(helper, 1, 1);
		hit(helper, husk, helper.getLevel().damageSources().fellOutOfWorld());
		check(helper, husk.isDeadOrDying(), "Last Stand saved from the void");
		helper.succeed();
	}

	/** 10 / 8 / 6 minutes, never below V's 2 (agent: VI's formula reaches 0). */
	@GameTest
	public void theCooldownShortensPerLevelAndStopsAtTwoMinutes(GameTestHelper helper) {
		Husk three = wearer(helper, 1, 3);
		Husk ten = wearer(helper, 4, 10);
		hit(helper, three, helper.getLevel().damageSources().generic());
		hit(helper, ten, helper.getLevel().damageSources().generic());
		helper.assertValueEqual(7200L, readyIn(helper, three), Component.literal("Last Stand III's cooldown"));
		helper.assertValueEqual(2400L, readyIn(helper, ten), Component.literal("Last Stand X's cooldown"));
		helper.succeed();
	}

	private static ServerPlayer survivor(GameTestHelper helper, int x) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.setPos(helper.absoluteVec(new Vec3(x + 0.5, 1.0, 3.5)));
		// A player whose client hasn't loaded can't be hurt (ServerPlayer.isInvulnerableTo); a mock's never does.
		((PacketListenerInvoker) player.connection).e2test$markClientLoaded();
		return player;
	}

	/** Owner: on in PvP by default; {@code last_stand.pvp} off lets another player's lethal hit through. */
	@GameTest
	public void thePvpToggleDecidesPlayerOnPlayerHits(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer attacker = survivor(helper, 1);
		ServerPlayer saved = survivor(helper, 3);
		ServerPlayer killed = survivor(helper, 5);
		saved.setItemSlot(EquipmentSlot.CHEST, chestplate(helper, 1));
		killed.setItemSlot(EquipmentSlot.CHEST, chestplate(helper, 1));

		hit(helper, saved, level.damageSources().playerAttack(attacker));
		check(helper, saved.isAlive() && saved.getHealth() == 1.0F, "pvp on: a player's lethal hit wasn't survived: health "
				+ saved.getHealth() + ", dead " + saved.isDeadOrDying() + ", can harm " + attacker.canHarmPlayer(saved));

		MinecraftServer server = level.getServer();
		ServerConfigHolder holder = (ServerConfigHolder) server;
		CommonConfig previous = ServerConfigs.get(server);
		holder.enchanting2$setConfig(new CommonConfig(previous.experience(), previous.allowTogether(),
				previous.costs(), previous.curses(), previous.anvil(), previous.table(), previous.fortune(),
				previous.beheading(), previous.smelting(), previous.silkTouch(), new LastStandSection(false)));
		try {
			hit(helper, killed, level.damageSources().playerAttack(attacker));
			check(helper, killed.isDeadOrDying(), "pvp off: Last Stand saved from a player");
		} finally {
			holder.enchanting2$setConfig(previous);
		}
		helper.succeed();
	}
}
