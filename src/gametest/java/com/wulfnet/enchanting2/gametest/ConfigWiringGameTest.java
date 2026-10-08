package com.wulfnet.enchanting2.gametest;

import com.wulfnet.enchanting2.config.AllowTogetherSection;
import com.wulfnet.enchanting2.config.AnvilSection;
import com.wulfnet.enchanting2.config.CommonConfig;
import com.wulfnet.enchanting2.config.ConfigFiles;
import com.wulfnet.enchanting2.config.CostsSection;
import com.wulfnet.enchanting2.config.CursesSection;
import com.wulfnet.enchanting2.config.EnchantmentOverrides;
import com.wulfnet.enchanting2.config.ExperienceSection;
import com.wulfnet.enchanting2.config.ServerConfigHolder;
import com.wulfnet.enchanting2.config.ServerConfigs;
import com.wulfnet.enchanting2.config.TableSection;
import com.wulfnet.enchanting2.network.ConfigSyncPayload;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;

public class ConfigWiringGameTest {
	private static CommonConfig held(MinecraftServer server) {
		return ((ServerConfigHolder) server).enchanting2$config();
	}

	@GameTest
	public void holderIsSetAtStartup(GameTestHelper helper) {
		helper.assertTrue(held(helper.getLevel().getServer()) != null,
				Component.literal("no config on the MinecraftServer after SERVER_STARTING"));
		helper.assertTrue(((ServerConfigHolder) helper.getLevel().getServer()).enchanting2$enchantments() != null,
				Component.literal("no enchantments.json5 on the MinecraftServer after SERVER_STARTING"));
		helper.assertTrue(Files.exists(ServerConfigs.commonFile()),
				Component.literal("first boot did not write " + ServerConfigs.commonFile()));
		helper.succeed();
	}

	@GameTest
	public void payloadRoundTrips(GameTestHelper helper) {
		ConfigSyncPayload sent = ConfigSyncPayload.of(new CommonConfig(new ExperienceSection(false, 7, 99),
				AllowTogetherSection.DEFAULTS, CostsSection.DEFAULTS, CursesSection.DEFAULTS, AnvilSection.DEFAULTS,
				TableSection.DEFAULTS));
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		ConfigSyncPayload.CODEC.encode(buf, sent);
		ConfigSyncPayload received = ConfigSyncPayload.CODEC.decode(buf);
		helper.assertValueEqual(sent, received, Component.literal("payload"));
		helper.assertTrue(buf.readableBytes() == 0, Component.literal("decode left bytes behind"));
		helper.succeed();
	}

	@GameTest
	public void oversizePayloadFailsDecode(GameTestHelper helper) {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		ByteBufCodecs.stringUtf8(ConfigSyncPayload.MAX_CHARS + 1).encode(buf, "x".repeat(ConfigSyncPayload.MAX_CHARS + 1));
		try {
			ConfigSyncPayload.CODEC.decode(buf);
		} catch (RuntimeException expected) {
			helper.succeed();
			return;
		}
		helper.fail("a payload over MAX_CHARS decoded");
	}

	/**
	 * One sequence, because every step edits the shared config file. It runs inside the GameTest run dir, which
	 * {@code clearRunDirectory} wipes, and it refuses to run anywhere else. It runs in its own environment because batches
	 * are grouped by environment and run one at a time. Otherwise tests that read the live config (ExperienceGameTest)
	 * would see this test's temporary values.
	 */
	@GameTest(maxTicks = 1200, environment = "enchanting2:config_reload")
	public void reloadRereadsTheFileAndKeepsThePreviousConfigOnGarbage(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Path file = ServerConfigs.commonFile();
		Path gameDir = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
		if (!gameDir.endsWith(Path.of("build", "run", "gameTest"))
				|| !file.toAbsolutePath().normalize().startsWith(gameDir)) {
			helper.fail("refusing to edit " + file + ": not inside build/run/gameTest");
			return;
		}
		Path enchantmentsFile = ServerConfigs.enchantmentsFile();
		if (!enchantmentsFile.toAbsolutePath().normalize().startsWith(gameDir)) {
			helper.fail("refusing to edit " + enchantmentsFile + ": not inside build/run/gameTest");
			return;
		}
		String original = read(file);
		String originalEnchantments = read(enchantmentsFile);
		helper.assertValueEqual(ConfigFiles.load(file, CommonConfig.CODEC).config(), held(server),
				Component.literal("holder vs file at start"));
		helper.assertValueEqual(ConfigFiles.load(enchantmentsFile, EnchantmentOverrides.CODEC).config(),
				ServerConfigs.enchantments(server), Component.literal("enchantments holder vs file at start"));

		int defaultPoints = ExperienceSection.DEFAULTS.fixedPointsPerLevel();
		AtomicReference<CompletableFuture<Void>> reload = new AtomicReference<>();
		helper.startSequence()
				.thenExecute(() -> {
					// A bad value plus a valid marker: the marker proves the reload happened.
					write(file, "{ schema_version: 1, experience: { fixed_points_per_level: -5, flatten_after_level: 41 } }");
					write(enchantmentsFile, "{ schema_version: 1, overrides: { \"minecraft:efficiency\": { base_cost: 7 } } }");
					reload.set(server.reloadResources(server.getPackRepository().getSelectedIds()));
				})
				.thenWaitUntil(() -> {
					helper.assertTrue(reload.get().isDone(), Component.literal("reload still running"));
					helper.assertValueEqual(41, held(server).experience().flattenAfterLevel(),
							Component.literal("marker after /reload"));
					helper.assertValueEqual(7, efficiencyBaseCost(server),
							Component.literal("enchantments marker after /reload"));
				})
				.thenExecute(() -> {
					helper.assertValueEqual(defaultPoints, held(server).experience().fixedPointsPerLevel(),
							Component.literal("bad key after /reload"));
					write(file, "{ experience: { flatten_after_level: ");
					write(enchantmentsFile, "{ overrides: { ");
					ServerConfigs.reload(server);
					helper.assertValueEqual(41, held(server).experience().flattenAfterLevel(),
							Component.literal("marker after an unparseable reload"));
					helper.assertValueEqual(7, efficiencyBaseCost(server),
							Component.literal("enchantments marker after an unparseable reload"));
					write(file, original);
					write(enchantmentsFile, originalEnchantments);
					ServerConfigs.reload(server);
					helper.assertValueEqual(ConfigFiles.load(file, CommonConfig.CODEC).config(), held(server),
							Component.literal("holder vs file after restore"));
					helper.assertValueEqual(ConfigFiles.load(enchantmentsFile, EnchantmentOverrides.CODEC).config(),
							ServerConfigs.enchantments(server), Component.literal("enchantments holder after restore"));
				})
				.thenSucceed();
	}

	private static int efficiencyBaseCost(MinecraftServer server) {
		EnchantmentOverrides.Entry entry = ServerConfigs.enchantments(server).entries().get("minecraft:efficiency");
		return entry == null ? -1 : entry.baseCost().orElse(-1);
	}

	private static String read(Path file) {
		try {
			return Files.readString(file, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException("could not read " + file, e);
		}
	}

	private static void write(Path file, String text) {
		try {
			Files.writeString(file, text, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException("could not write " + file, e);
		}
	}
}
