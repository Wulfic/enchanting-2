package com.wulfnet.enchanting2.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wulfnet.enchanting2.config.ConfigFiles.LoadResult;
import com.wulfnet.enchanting2.config.ConfigFiles.Source;
import com.wulfnet.enchanting2.config.ConfigView.Connection;
import com.wulfnet.enchanting2.config.ConfigView.Mode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class ConfigViewTest {
	private static final CommonConfig FILE = withFixedPoints(200);
	private static final CommonConfig LIVE = withFixedPoints(300);
	private static final CommonConfig SERVER = withFixedPoints(400);

	private static CommonConfig withFixedPoints(int points) {
		return new CommonConfig(new ExperienceSection(true, 30, points), AllowTogetherSection.DEFAULTS,
				CostsSection.DEFAULTS, CursesSection.DEFAULTS, AnvilSection.DEFAULTS, TableSection.DEFAULTS,
				FortuneSection.DEFAULTS, BeheadingSection.DEFAULTS, SmeltingSection.DEFAULTS);
	}

	private static Supplier<LoadResult<CommonConfig>> file(Source source, boolean futureSchema) {
		return () -> new LoadResult<>(FILE, List.of(), source, futureSchema);
	}

	private static <T> Supplier<T> forbidden(String what) {
		return () -> {
			throw new AssertionError(what + " must not be read here");
		};
	}

	@Test
	void remoteShowsTheServerCopyReadOnlyAndNeverTouchesTheLocalFile() {
		ConfigView<CommonConfig> view = ConfigView.resolve(Connection.REMOTE, forbidden("the local file"),
				forbidden("the integrated server"), Optional.of(SERVER));
		assertEquals(Mode.SERVER_READ_ONLY, view.mode());
		assertEquals(Optional.of(SERVER), view.values());
		assertFalse(view.editable());
	}

	@Test
	void remoteWithoutACopyIsAVanillaServerWithNothingToShow() {
		ConfigView<CommonConfig> view = ConfigView.resolve(Connection.REMOTE, forbidden("the local file"),
				forbidden("the integrated server"), Optional.empty());
		assertEquals(Mode.VANILLA_SERVER, view.mode());
		assertEquals(Optional.empty(), view.values());
		assertFalse(view.editable());
	}

	@Test
	void titleScreenEditsTheFileAndIgnoresAStaleServerCopy() {
		for (Source source : List.of(Source.FILE, Source.CREATED, Source.CREATE_FAILED)) {
			ConfigView<CommonConfig> view = ConfigView.resolve(Connection.NONE, file(source, false),
					forbidden("the integrated server"), Optional.of(SERVER));
			assertEquals(Mode.EDIT_FILE, view.mode(), source.name());
			assertEquals(Optional.of(FILE), view.values(), source.name());
			assertTrue(view.editable(), source.name());
		}
	}

	@Test
	void hostingShowsTheLiveValuesNotTheFile() {
		ConfigView<CommonConfig> view = ConfigView.resolve(Connection.HOSTING, file(Source.FILE, false), () -> LIVE,
				Optional.of(SERVER));
		assertEquals(Mode.EDIT_HOSTED, view.mode());
		assertEquals(Optional.of(LIVE), view.values());
		assertTrue(view.editable());
	}

	@Test
	void anUnparseableFileLocksEditing() {
		ConfigView<CommonConfig> title = ConfigView.resolve(Connection.NONE, file(Source.UNREADABLE, false),
				forbidden("the integrated server"), Optional.empty());
		assertEquals(Mode.LOCKED_UNREADABLE, title.mode());
		assertEquals(Optional.empty(), title.values(), "the defaults a failed load returns are not the file's values");
		assertFalse(title.editable());

		ConfigView<CommonConfig> hosted = ConfigView.resolve(Connection.HOSTING, file(Source.UNREADABLE, false), () -> LIVE,
				Optional.empty());
		assertEquals(Mode.LOCKED_UNREADABLE, hosted.mode());
		assertEquals(Optional.of(LIVE), hosted.values());
		assertFalse(hosted.editable());
	}

	@Test
	void aNewerSchemaFileLocksEditingButStillShowsValues() {
		ConfigView<CommonConfig> title = ConfigView.resolve(Connection.NONE, file(Source.FILE, true),
				forbidden("the integrated server"), Optional.empty());
		assertEquals(Mode.LOCKED_NEWER_SCHEMA, title.mode());
		assertEquals(Optional.of(FILE), title.values());
		assertFalse(title.editable());

		ConfigView<CommonConfig> hosted = ConfigView.resolve(Connection.HOSTING, file(Source.FILE, true), () -> LIVE,
				Optional.empty());
		assertEquals(Mode.LOCKED_NEWER_SCHEMA, hosted.mode());
		assertEquals(Optional.of(LIVE), hosted.values());
		assertFalse(hosted.editable());
	}

	// --- enchantments.json5: never synced ---

	private static final EnchantmentOverrides OVERRIDES_FILE = new EnchantmentOverrides(Map.of(
			"minecraft:mending", new EnchantmentOverrides.Entry(OptionalInt.of(1))));
	private static final EnchantmentOverrides OVERRIDES_LIVE = EnchantmentOverrides.defaults();

	private static Supplier<LoadResult<EnchantmentOverrides>> overridesFile(Source source, boolean futureSchema) {
		return () -> new LoadResult<>(OVERRIDES_FILE, List.of(), source, futureSchema);
	}

	@Test
	void anUnsyncedFileOnARemoteServerShowsNothingAndNeverTouchesTheLocalFile() {
		ConfigView<EnchantmentOverrides> modded = ConfigView.resolveUnsynced(Connection.REMOTE,
				forbidden("the local file"), forbidden("the integrated server"), true);
		assertEquals(Mode.KEPT_ON_SERVER, modded.mode());
		assertEquals(Optional.empty(), modded.values());
		assertFalse(modded.editable());
		ConfigView<EnchantmentOverrides> vanilla = ConfigView.resolveUnsynced(Connection.REMOTE,
				forbidden("the local file"), forbidden("the integrated server"), false);
		assertEquals(Mode.VANILLA_SERVER, vanilla.mode());
		assertFalse(vanilla.editable());
	}

	@Test
	void anUnsyncedFileResolvesLikeAnyOtherLocally() {
		ConfigView<EnchantmentOverrides> title = ConfigView.resolveUnsynced(Connection.NONE,
				overridesFile(Source.FILE, false), forbidden("the integrated server"), true);
		assertEquals(Mode.EDIT_FILE, title.mode());
		assertEquals(Optional.of(OVERRIDES_FILE), title.values());
		ConfigView<EnchantmentOverrides> hosted = ConfigView.resolveUnsynced(Connection.HOSTING,
				overridesFile(Source.FILE, false), () -> OVERRIDES_LIVE, true);
		assertEquals(Mode.EDIT_HOSTED, hosted.mode());
		assertEquals(Optional.of(OVERRIDES_LIVE), hosted.values());
		assertEquals(Mode.LOCKED_UNREADABLE, ConfigView.resolveUnsynced(Connection.NONE,
				overridesFile(Source.UNREADABLE, false), forbidden("the integrated server"), true).mode());
		assertEquals(Mode.LOCKED_NEWER_SCHEMA, ConfigView.resolveUnsynced(Connection.HOSTING,
				overridesFile(Source.FILE, true), () -> OVERRIDES_LIVE, true).mode());
	}
}
