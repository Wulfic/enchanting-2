package com.wulfnet.enchanting2.gametest.platform.forgelike;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.serialization.Lifecycle;
import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.client.ClientConfigState;
import com.wulfnet.enchanting2.client.config.FormScreen;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The release gate's client world check (rung 5) on NeoForge and Forge, which have no client GameTest API (TODO.md
 * 13G). Off unless {@code -Denchanting2.clientBootCheck=true} ({@code :<node>:runClientBootCheck}); never shipped.
 * Driven by a client tick, it takes the path a player takes: title screen, Create New World (vanilla's screen and its
 * button), into the world, the mod's registered config screen, Save and Quit, then closes the game. Every stage logs an
 * "enchanting2 client boot:" line that scripts/client_boot_check.py {@code @expect}s; a stage that throws or outlives
 * the watchdog logs FAILED at ERROR and closes the game.
 * <p>
 * With {@code -Denchanting2.clientBootCheck.join=<host:port>} it joins that server from the title screen instead (Add
 * Server's path, {@code ConnectScreen}), and leaves through Disconnect: the same fingerprint, from a dedicated server.
 */
public final class ClientBootCheck {
	public static final String PROPERTY = "enchanting2.clientBootCheck";
	public static final String JOIN_PROPERTY = "enchanting2.clientBootCheck.join";
	private static final Logger LOG = LoggerFactory.getLogger(ClientBootCheck.class);
	private static final String PREFIX = "enchanting2 client boot: ";
	/** World creation on a cold dev client takes about a minute; five leaves room for a slow machine. */
	private static final long WATCHDOG_NANOS = 5L * 60 * 1_000_000_000;
	/** Ticks in the world before the fingerprint, so the server's config sync has had time to arrive. */
	private static final int SETTLE_TICKS = 40;
	private static final int CONFIG_SCREEN_TICKS = 10;
	/** A stage that waits this long says what it sees, so a stuck run's log names the screen it is stuck on. */
	private static final int WAITING_REPORT_TICKS = 400;

	private enum Stage { TITLE, CREATE_WORLD, JOINING, IN_WORLD, CONFIG_SCREEN, LEAVING, DONE }

	private final Function<@Nullable Screen, Optional<Screen>> configScreen;
	/** The server to join, or {@code null} to create a singleplayer world. */
	private final @Nullable String joinAddress;
	private final long startedAt = System.nanoTime();
	private Stage stage = Stage.TITLE;
	private int stageTicks;
	private @Nullable Screen openedConfig;
	private @Nullable String worldId;
	private String where = "";

	/** {@code configScreen}: the screen the loader's registered config factory builds for a parent, empty if none. */
	public ClientBootCheck(Function<@Nullable Screen, Optional<Screen>> configScreen) {
		this.configScreen = configScreen;
		String join = System.getProperty(JOIN_PROPERTY, "").strip();
		this.joinAddress = join.isEmpty() ? null : join;
	}

	public static boolean enabled() {
		return Boolean.getBoolean(PROPERTY);
	}

	/** Once per client tick, after the game's own. */
	public void tick(Minecraft client) {
		if (stage == Stage.DONE) {
			return;
		}
		if (System.nanoTime() - startedAt > WATCHDOG_NANOS) {
			fail(client, "timed out after 5 minutes (screen " + screenName(client.screen) + ")", null);
			return;
		}
		stageTicks++;
		if (stage == Stage.TITLE && stageTicks == 1) {
			LOG.info(PREFIX + "first client tick (screen {})", screenName(client.screen));
		} else if (stageTicks % WAITING_REPORT_TICKS == 0) {
			LOG.info(PREFIX + "still at {} after {} ticks (screen {})", stage, stageTicks, screenName(client.screen));
		}
		try {
			step(client);
		} catch (RuntimeException e) {
			fail(client, e.toString(), e);
		}
	}

	private void step(Minecraft client) {
		// NeoForge/Forge show mod-loading warnings and errors on a screen of their own before the title screen; a
		// player would have to click through it, so it fails the check (named, so the log says which).
		if (client.screen != null && client.screen.getClass().getSimpleName().equals("LoadingErrorScreen")) {
			throw new IllegalStateException("the loader's mod-loading warning/error screen is showing ("
					+ client.screen.getClass().getName() + "); see the log's LOADING lines");
		}
		// Kicked or refused: the screen's narration is its title and the reason, which nothing else logs.
		if (client.screen instanceof DisconnectedScreen disconnected) {
			throw new IllegalStateException("disconnected: " + disconnected.getNarrationMessage().getString());
		}
		switch (stage) {
			case TITLE -> {
				if (client.screen instanceof TitleScreen title) {
					if (joinAddress != null) {
						LOG.info(PREFIX + "title screen reached, joining server {}", joinAddress);
						ConnectScreen.startConnecting(title, client, ServerAddress.parseString(joinAddress),
								new ServerData("Enchanting 2 boot check", joinAddress, ServerData.Type.OTHER), false, null);
						next(Stage.JOINING);
					} else {
						LOG.info(PREFIX + "title screen reached, creating a new world");
						CreateWorldScreen.openFresh(client, () -> client.setScreen(new TitleScreen()));
						next(Stage.CREATE_WORLD);
					}
				}
			}
			case CREATE_WORLD -> {
				// openFresh loads the data packs first, then shows the screen.
				if (client.screen instanceof CreateWorldScreen screen) {
					requireStableRegistries(screen.getUiState().getSettings().worldgenRegistries().compositeAccess());
					createButton(screen).onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
					LOG.info(PREFIX + "pressed Create New World");
					next(Stage.JOINING);
				}
			}
			case JOINING -> {
				if (client.player != null && client.level != null && client.screen == null) {
					if (joinAddress != null) {
						where = "server " + joinAddress;
					} else if (client.getSingleplayerServer() != null) {
						Path root = client.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
						worldId = root.getFileName().toString();
						where = "world " + worldId;
					} else {
						return;
					}
					next(Stage.IN_WORLD);
				}
			}
			case IN_WORLD -> {
				if (stageTicks >= SETTLE_TICKS) {
					fingerprint(client);
					Screen screen = configScreen.apply(null)
							.orElseThrow(() -> new IllegalStateException("the mod registered no config screen factory"));
					openedConfig = screen;
					client.setScreen(screen);
					next(Stage.CONFIG_SCREEN);
				}
			}
			case CONFIG_SCREEN -> {
				if (stageTicks >= CONFIG_SCREEN_TICKS) {
					if (client.screen != openedConfig || !(openedConfig instanceof FormScreen)) {
						throw new IllegalStateException("the config screen didn't stay open: expected FormScreen, showing "
								+ screenName(client.screen) + " (built " + screenName(openedConfig) + ")");
					}
					openedConfig.onClose();
					LOG.info(PREFIX + "config screen opened and closed ({})", openedConfig.getClass().getSimpleName());
					// The pause menu's Save and Quit to Title (Disconnect on a server): saves the world and stops the
					// integrated server, or leaves the server for the server list.
					client.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
					next(Stage.LEAVING);
				}
			}
			case LEAVING -> {
				if (joinAddress != null && client.level == null && client.screen instanceof JoinMultiplayerScreen) {
					LOG.info(PREFIX + "PASS (left {}, back on the server list); closing the game", where);
					next(Stage.DONE);
					client.stop();
				} else if (joinAddress == null && client.level == null && client.getSingleplayerServer() == null
						&& client.screen instanceof TitleScreen) {
					if (!client.getLevelSource().levelExists(worldId)) {
						throw new IllegalStateException("world " + worldId + " isn't in the saves folder after Save and Quit");
					}
					LOG.info(PREFIX + "PASS (world {} saved, back on the title screen); closing the game", worldId);
					next(Stage.DONE);
					client.stop();
				}
			}
			case DONE -> {
			}
		}
	}

	/** The fingerprint: our enchantments reached the client, the max-level hook ran, the server's config arrived. */
	private void fingerprint(Minecraft client) {
		Registry<Enchantment> enchantments = client.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		long ours = enchantments.keySet().stream().filter(id -> id.getNamespace().equals(Enchanting2.MOD_ID)).count();
		if (ours == 0) {
			throw new IllegalStateException("no " + Enchanting2.MOD_ID + " enchantments in the client's registry");
		}
		Enchantment knockback = enchantments.getValue(Enchantments.KNOCKBACK);
		int knockbackMax = knockback == null ? -1 : knockback.getMaxLevel();
		if (ClientConfigState.serverCopy().isEmpty()) {
			throw new IllegalStateException("the server's Enchanting 2 config never reached the client");
		}
		LOG.info(PREFIX + "joined {}: {} {} enchantments, Knockback max level {}, server config received",
				where, ours, Enchanting2.MOD_ID, knockbackMax);
	}

	/**
	 * Vanilla asks "experimental settings, proceed?" before creating a world whose registries hold an entry that isn't
	 * {@code Lifecycle.stable()} (CreateWorldScreen.onCreate). A player would see that on every new world, so the check
	 * names the entries and fails instead of clicking through.
	 */
	private static void requireStableRegistries(RegistryAccess registries) {
		List<String> unstable = new ArrayList<>();
		registries.registries().forEach(entry -> collectUnstable(entry.value(), unstable));
		if (!unstable.isEmpty()) {
			LOG.error(PREFIX + "{} registry entries aren't stable: {}", unstable.size(),
					unstable.subList(0, Math.min(unstable.size(), 20)));
			throw new IllegalStateException("the new world would open behind the experimental settings warning ("
					+ unstable.size() + " entries aren't Lifecycle.stable(), first " + unstable.getFirst() + ")");
		}
	}

	private static <T> void collectUnstable(Registry<T> registry, List<String> unstable) {
		if (registry.registryLifecycle() == Lifecycle.stable()) {
			return;
		}
		int before = unstable.size();
		for (ResourceKey<T> key : registry.registryKeySet()) {
			Lifecycle lifecycle = registry.registrationInfo(key).map(RegistrationInfo::lifecycle).orElse(Lifecycle.experimental());
			if (lifecycle != Lifecycle.stable()) {
				unstable.add(key + " " + lifecycle);
			}
		}
		if (unstable.size() == before) {
			unstable.add(registry.key() + " (the registry itself) " + registry.registryLifecycle());
		}
	}

	private static Button createButton(CreateWorldScreen screen) {
		Component label = Component.translatable("selectWorld.create");
		return screen.children().stream()
				.filter(child -> child instanceof Button button && button.getMessage().equals(label) && button.active)
				.map(Button.class::cast)
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("no active Create New World button on " + screen));
	}

	private void next(Stage stage) {
		this.stage = stage;
		stageTicks = 0;
	}

	private void fail(Minecraft client, String why, @Nullable Throwable cause) {
		LOG.error(PREFIX + "FAILED at {}: {}", stage, why, cause);
		stage = Stage.DONE;
		client.stop();
	}

	private static String screenName(@Nullable Screen screen) {
		return screen == null ? "none" : screen.getClass().getName() + " \"" + screen.getTitle().getString() + "\"";
	}
}
