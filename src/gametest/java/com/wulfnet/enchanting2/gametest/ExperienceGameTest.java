package com.wulfnet.enchanting2.gametest;

import com.wulfnet.enchanting2.config.ExperienceSection;
import com.wulfnet.enchanting2.config.ServerConfigs;
import com.wulfnet.enchanting2.mixin.ServerPlayerAccessor;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/**
 * DESIGN §3 through the real {@code Player} methods, with the default config (flat 112 from level 30). The mixin
 * reads the live server config, so every test first asserts it is the default (see ConfigWiringGameTest's environment).
 */
public class ExperienceGameTest {
	private static final int FLAT = 112;

	private static ServerPlayer player(GameTestHelper helper) {
		helper.assertValueEqual(ExperienceSection.DEFAULTS,
				ServerConfigs.get(helper.getLevel().getServer()).experience(),
				Component.literal("live experience config (these tests assume the defaults)"));
		return helper.makeMockServerPlayerInLevel();
	}

	private static void place(ServerPlayer player, int level, int points) {
		player.setExperienceLevels(level);
		player.setExperiencePoints(points);
	}

	/** What {@code /xp query <player> points} prints. */
	private static int queryPoints(ServerPlayer player) {
		return Mth.floor(player.experienceProgress * (float) player.getXpNeededForNextLevel());
	}

	private static String describe(ServerPlayer player) {
		return "level " + player.experienceLevel + " + " + queryPoints(player) + " points (progress "
				+ player.experienceProgress + ", needed " + player.getXpNeededForNextLevel() + ")";
	}

	@GameTest
	public void neededPointsFollowTheConfiguredCurve(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		int[][] cases = {{0, 7}, {15, 37}, {29, 107}, {30, FLAT}, {31, FLAT}, {49, FLAT}, {99, FLAT}, {9999, FLAT},
				{2_000_000_000, FLAT}, {Integer.MAX_VALUE, FLAT}};
		List<String> wrong = new ArrayList<>();
		for (int[] c : cases) {
			player.setExperienceLevels(c[0]);
			if (player.getXpNeededForNextLevel() != c[1]) {
				wrong.add("level " + c[0] + ": expected " + c[1] + ", got " + player.getXpNeededForNextLevel());
			}
		}
		if (!wrong.isEmpty()) {
			helper.fail("needed points: " + wrong);
			return;
		}
		helper.succeed();
	}

	@GameTest
	public void setPointsThenQueryAboveThirty(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		List<String> wrong = new ArrayList<>();
		for (int points : new int[] {0, 1, 56, FLAT - 1}) {
			place(player, 50, points);
			if (player.experienceLevel != 50 || queryPoints(player) != points) {
				wrong.add("set " + points + " at 50 -> " + describe(player));
			}
		}
		if (!wrong.isEmpty()) {
			helper.fail("/xp set points then query: " + wrong);
			return;
		}
		helper.succeed();
	}

	@GameTest
	public void pointsAcrossThirtyLandOnTheRightLevel(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		place(player, 29, 0);
		player.giveExperiencePoints(107 + 50);
		helper.assertValueEqual(30, player.experienceLevel, Component.literal("level after 29 + 157 points"));
		helper.assertValueEqual(50, queryPoints(player), Component.literal("points after 29 + 157 points"));
		player.giveExperiencePoints(3 * FLAT + 5);
		helper.assertValueEqual(33, player.experienceLevel, Component.literal("level after another 341 points"));
		helper.assertValueEqual(55, queryPoints(player), Component.literal("points after another 341 points"));
		helper.succeed();
	}

	@GameTest
	public void spendingLevelsFromFortyToTwentyFive(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		place(player, 40, 50);
		float progress = player.experienceProgress;
		player.giveExperienceLevels(-15);
		helper.assertValueEqual(25, player.experienceLevel, Component.literal("level after spending 15"));
		helper.assertValueEqual(87, player.getXpNeededForNextLevel(), Component.literal("vanilla needed at 25"));
		helper.assertValueEqual(progress, player.experienceProgress, Component.literal("progress after spending"));
		helper.succeed();
	}

	@GameTest
	public void levelNearIntMaxStopsAtMaxAndNeverWraps(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		place(player, Integer.MAX_VALUE - 1, 0);
		player.giveExperiencePoints(3 * FLAT);
		helper.assertValueEqual(Integer.MAX_VALUE, player.experienceLevel, Component.literal("level"));
		helper.assertTrue(player.getXpNeededForNextLevel() > 0, Component.literal("needed > 0: " + describe(player)));
		helper.assertTrue(player.experienceProgress >= 0 && player.experienceProgress < 1,
				Component.literal("progress in [0, 1): " + describe(player)));
		helper.succeed();
	}

	/** 3B.4. Reports every failing case at once, so a float-drift failure comes with real data. */
	@GameTest
	public void addThenRemovePointsIsLossless(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		int[][] starts = {{20, 10}, {29, 100}, {45, 0}, {45, 37}, {1000, FLAT - 1}};
		List<String> wrong = new ArrayList<>();
		for (int[] start : starts) {
			for (int n : new int[] {1, 50, 500, 10_000}) {
				place(player, start[0], start[1]);
				String before = describe(player);
				int level = player.experienceLevel;
				int points = queryPoints(player);
				player.giveExperiencePoints(n);
				player.giveExperiencePoints(-n);
				if (player.experienceLevel != level || queryPoints(player) != points) {
					wrong.add("+/-" + n + ": " + before + " -> " + describe(player));
				}
			}
		}
		if (!wrong.isEmpty()) {
			helper.fail(wrong.size() + " lossy round trip(s): " + wrong);
			return;
		}
		helper.succeed();
	}

	/** 3B.7. A save from before the mod stores vanilla's {@code p / n}, which can read back as {@code p - 1}. */
	@GameTest
	public void legacyVanillaProgressGainsExactlyOnePoint(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		int needed = 62;
		List<String> wrong = new ArrayList<>();
		for (int points = 0; points < needed - 1; points++) {
			player.setExperienceLevels(20);
			player.experienceProgress = (float) points / (float) needed;
			player.giveExperiencePoints(1);
			if (player.experienceLevel != 20 || queryPoints(player) != points + 1) {
				wrong.add("20 + " + points + "/" + needed + " + 1 -> " + describe(player));
			}
		}
		helper.assertValueEqual(needed, player.getXpNeededForNextLevel(), Component.literal("vanilla needed at 20"));
		if (!wrong.isEmpty()) {
			helper.fail(wrong.size() + " legacy reads off by one: " + wrong);
			return;
		}
		helper.succeed();
	}

	/**
	 * 3B.7. Vanilla resyncs only when totalExperience changes. {@code /xp set levels} leaves it at 0, and removing
	 * points then clamps it at 0 again, so the snap must force the resend.
	 */
	@GameTest
	public void snapResyncsTheClientWhileTotalIsClampedAtZero(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		ServerPlayerAccessor sync = (ServerPlayerAccessor) player;
		place(player, 50, 10);
		player.totalExperience = 0;
		sync.enchanting2$setLastSentExp(0);
		player.giveExperiencePoints(-5);
		helper.assertValueEqual(5, queryPoints(player), Component.literal("points after -5"));
		helper.assertValueEqual(0, player.totalExperience, Component.literal("total stays clamped at 0"));
		helper.assertValueEqual(-1, sync.enchanting2$getLastSentExp(), Component.literal("lastSentExp forces a resend"));
		helper.succeed();
	}

	@GameTest
	public void respawnCopiesExperienceOnlyWithKeepInventory(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		GameRules rules = level.getGameRules();
		boolean keepInventory = rules.get(GameRules.KEEP_INVENTORY);
		try {
			rules.set(GameRules.KEEP_INVENTORY, true, server);
			ServerPlayer kept = player(helper);
			place(kept, 45, 37);
			ServerPlayer respawned = helper.makeMockServerPlayerInLevel();
			respawned.restoreFrom(kept, false);
			helper.assertValueEqual(45, respawned.experienceLevel, Component.literal("level with keepInventory"));
			helper.assertValueEqual(kept.experienceProgress, respawned.experienceProgress,
					Component.literal("progress with keepInventory"));
			helper.assertValueEqual(37, queryPoints(respawned), Component.literal("points with keepInventory"));

			rules.set(GameRules.KEEP_INVENTORY, false, server);
			ServerPlayer lost = player(helper);
			place(lost, 45, 37);
			ServerPlayer fresh = helper.makeMockServerPlayerInLevel();
			fresh.restoreFrom(lost, false);
			helper.assertValueEqual(0, fresh.experienceLevel, Component.literal("level without keepInventory"));
			helper.assertValueEqual(0.0F, fresh.experienceProgress, Component.literal("progress without keepInventory"));
		} finally {
			rules.set(GameRules.KEEP_INVENTORY, keepInventory, server);
		}
		helper.succeed();
	}

	@GameTest
	public void saveAndLoadAtTwoBillionKeepsEverything(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer saved = player(helper);
		place(saved, 2_000_000_000, 50);
		ProblemReporter.Collector problems = new ProblemReporter.Collector();
		TagValueOutput out = TagValueOutput.createWithContext(problems, level.registryAccess());
		saved.saveWithoutId(out);
		ServerPlayer loaded = helper.makeMockServerPlayerInLevel();
		loaded.load(TagValueInput.create(problems, level.registryAccess(), out.buildResult()));
		helper.assertTrue(problems.isEmpty(), Component.literal("save/load problems: " + problems.getTreeReport()));
		helper.assertValueEqual(2_000_000_000, loaded.experienceLevel, Component.literal("level"));
		helper.assertValueEqual(saved.experienceProgress, loaded.experienceProgress, Component.literal("progress"));
		helper.assertValueEqual(saved.totalExperience, loaded.totalExperience, Component.literal("total"));
		helper.assertValueEqual(50, queryPoints(loaded), Component.literal("points"));
		helper.succeed();
	}
}
