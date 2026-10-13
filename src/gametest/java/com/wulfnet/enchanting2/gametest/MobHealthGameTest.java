package com.wulfnet.enchanting2.gametest;

import com.wulfnet.enchanting2.config.CommonConfig;
import com.wulfnet.enchanting2.config.MobsSection;
import com.wulfnet.enchanting2.config.ServerConfigHolder;
import com.wulfnet.enchanting2.config.ServerConfigs;
import com.wulfnet.enchanting2.mobs.MobHealth;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * TODO 10m (owner, 2026-10-13): {@code mobs.health_multiplier}. Every config switch is set, used and put back inside one
 * synchronous block, so mobs other tests spawn meanwhile never see it.
 */
public class MobHealthGameTest {
	private static void check(GameTestHelper helper, boolean condition, String what) {
		helper.assertTrue(condition, Component.literal(what));
	}

	private static CommonConfig withMultiplier(CommonConfig base, int multiplier) {
		return new CommonConfig(base.experience(), base.allowTogether(), base.costs(), base.curses(), base.anvil(),
				base.table(), base.fortune(), base.beheading(), base.smelting(), base.silkTouch(), base.lastStand(),
				new MobsSection(multiplier));
	}

	private static Zombie zombie(GameTestHelper helper, int x) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(x, 1, 1));
		zombie.setNoAi(true);
		return zombie;
	}

	@GameTest
	public void aSpawnedMobGetsTheMultiplierAndAPlayerDoesNot(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerConfigHolder holder = (ServerConfigHolder) server;
		CommonConfig previous = ServerConfigs.get(server);
		holder.enchanting2$setConfig(withMultiplier(previous, 3));
		Zombie zombie;
		try {
			zombie = zombie(helper, 1);
			check(helper, zombie.getMaxHealth() == 60.0F && zombie.getHealth() == 60.0F,
					"a zombie spawned at 3x: " + zombie.getHealth() + " of " + zombie.getMaxHealth());
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			check(helper, player.getMaxHealth() == 20.0F, "players are never multiplied: " + player.getMaxHealth());
		} finally {
			holder.enchanting2$setConfig(previous);
		}
		// Back at 1x, the reload path takes the modifier off (mobs without it, other tests' too, are left alone).
		MobHealth.applyAll(server);
		check(helper, zombie.getMaxHealth() == 20.0F && zombie.getHealth() == 20.0F,
				"after a reload to 1x: " + zombie.getHealth() + " of " + zombie.getMaxHealth());
		check(helper, zombie.getAttribute(Attributes.MAX_HEALTH).getModifier(MobHealth.MODIFIER_ID) == null,
				"no modifier left at 1x");
		helper.succeed();
	}

	@GameTest
	public void aChangeKeepsEachMobsShareOfHealth(GameTestHelper helper) {
		Zombie zombie = zombie(helper, 1);
		check(helper, MobHealth.apply(zombie, 3), "3x changed it");
		zombie.setHealth(30.0F);
		check(helper, MobHealth.apply(zombie, 1), "1x changed it");
		check(helper, zombie.getMaxHealth() == 20.0F && zombie.getHealth() == 10.0F,
				"half health stays half at 1x: " + zombie.getHealth() + " of " + zombie.getMaxHealth());
		MobHealth.apply(zombie, 3);
		check(helper, zombie.getMaxHealth() == 60.0F && zombie.getHealth() == 30.0F,
				"and at 3x again: " + zombie.getHealth() + " of " + zombie.getMaxHealth());
		check(helper, !MobHealth.apply(zombie, 3), "the same multiplier again changes nothing");
		check(helper, zombie.getHealth() == 30.0F, "not even its health");
		helper.succeed();
	}

	/** Owner, 2026-10-13: 100x is really 100x, past vanilla's 1024 ceiling. */
	@GameTest
	public void aHundredTimesGoesPastVanillasCeiling(GameTestHelper helper) {
		check(helper, ((RangedAttribute) Attributes.MAX_HEALTH.value()).getMaxValue() == MobHealth.MAX_HEALTH_CEILING,
				"max_health's ceiling is raised");
		check(helper, ((RangedAttribute) Attributes.ARMOR.value()).getMaxValue() == 30.0,
				"other attributes keep theirs");
		Zombie zombie = zombie(helper, 1);
		MobHealth.apply(zombie, 100);
		check(helper, zombie.getMaxHealth() == 2000.0F && zombie.getHealth() == 2000.0F,
				"a zombie at 100x: " + zombie.getHealth() + " of " + zombie.getMaxHealth());
		helper.succeed();
	}

	/**
	 * The modifier is saved with the mob, so vanilla loads it before the saved health: a 3x zombie at 30 of 60 comes back
	 * at 30 of 60. (A transient modifier would clamp the load to 20, and the share kept on load would heal it to 60.)
	 */
	@GameTest
	public void aMultipliedMobKeepsItsHealthThroughASaveAndLoad(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerConfigHolder holder = (ServerConfigHolder) server;
		CommonConfig previous = ServerConfigs.get(server);
		holder.enchanting2$setConfig(withMultiplier(previous, 3));
		try {
			Zombie zombie = zombie(helper, 1);
			zombie.setHealth(30.0F);
			Zombie loaded = NewEnchantmentsGameTest.reload(helper, zombie);
			check(helper, loaded.getMaxHealth() == 60.0F && loaded.getHealth() == 30.0F,
					"after a save and load: " + loaded.getHealth() + " of " + loaded.getMaxHealth());
		} finally {
			holder.enchanting2$setConfig(previous);
		}
		helper.succeed();
	}
}
