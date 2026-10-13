package com.wulfnet.enchanting2.config;

import blue.endless.jankson.JsonObject;
import java.util.Objects;

/**
 * {@code config/enchanting2/common.json5}: gameplay settings, owned by the server and synced to clients.
 * Each phase adds its section here together with the code that reads it.
 */
public record CommonConfig(ExperienceSection experience, AllowTogetherSection allowTogether, CostsSection costs,
		CursesSection curses, AnvilSection anvil, TableSection table, FortuneSection fortune, BeheadingSection beheading,
		SmeltingSection smelting, SilkTouchSection silkTouch, LastStandSection lastStand, MobsSection mobs) {
	public static final String FILE_NAME = "common.json5";
	public static final int SCHEMA_VERSION = 1;

	public static final ConfigCodec<CommonConfig> CODEC = new ConfigCodec<>() {
		@Override
		public String fileName() {
			return FILE_NAME;
		}

		@Override
		public int schemaVersion() {
			return SCHEMA_VERSION;
		}

		@Override
		public CommonConfig defaults() {
			return CommonConfig.defaults();
		}

		@Override
		public CommonConfig read(ConfigReader root) {
			return new CommonConfig(ExperienceSection.read(root.section("experience")),
					AllowTogetherSection.read(root.section("allow_together")), CostsSection.read(root.section("costs")),
					CursesSection.read(root.section("curses")), AnvilSection.read(root.section("anvil")),
					TableSection.read(root.section("table")), FortuneSection.read(root.section("fortune")),
					BeheadingSection.read(root.section("beheading")), SmeltingSection.read(root.section("smelting")),
					SilkTouchSection.read(root.section("silk_touch")),
					LastStandSection.read(root.section("last_stand")), MobsSection.read(root.section("mobs")));
		}

		@Override
		public void write(CommonConfig value, JsonObject root) {
			root.put("experience", value.experience.write(), "Experience curve (levels past 30).");
			root.put("allow_together", value.allowTogether.write(),
					"Vanilla exclusive sets. true = these may share an item; false = vanilla (only one of them).");
			root.put("costs", value.costs.write(), "XP level costs that aren't per enchantment (see enchantments.json5).");
			root.put("curses", value.curses.write(), "Each curse on the result makes enchanting cheaper.");
			root.put("anvil", value.anvil.write(), "Anvil behavior.");
			root.put("table", value.table.write(), "Enchanting table behavior.");
			root.put("fortune", value.fortune.write(), "Fortune and Silk Touch on mining tools.");
			root.put("beheading", value.beheading.write(), "The Beheading enchantment.");
			root.put("smelting", value.smelting.write(), "The Smelting enchantment.");
			root.put("silk_touch", value.silkTouch.write(), "Silk Touch on mob spawners.");
			root.put("last_stand", value.lastStand.write(), "The Last Stand enchantment.");
			root.put("mobs", value.mobs.write(), "Mobs.");
		}
	};

	public CommonConfig {
		Objects.requireNonNull(experience, "experience");
		Objects.requireNonNull(allowTogether, "allowTogether");
		Objects.requireNonNull(costs, "costs");
		Objects.requireNonNull(curses, "curses");
		Objects.requireNonNull(anvil, "anvil");
		Objects.requireNonNull(table, "table");
		Objects.requireNonNull(fortune, "fortune");
		Objects.requireNonNull(beheading, "beheading");
		Objects.requireNonNull(smelting, "smelting");
		Objects.requireNonNull(silkTouch, "silkTouch");
		Objects.requireNonNull(lastStand, "lastStand");
		Objects.requireNonNull(mobs, "mobs");
	}

	/** Every section but {@code mobs}, which stays at its defaults: for callers that never touch mobs. */
	public CommonConfig(ExperienceSection experience, AllowTogetherSection allowTogether, CostsSection costs,
			CursesSection curses, AnvilSection anvil, TableSection table, FortuneSection fortune,
			BeheadingSection beheading, SmeltingSection smelting, SilkTouchSection silkTouch,
			LastStandSection lastStand) {
		this(experience, allowTogether, costs, curses, anvil, table, fortune, beheading, smelting, silkTouch, lastStand,
				MobsSection.DEFAULTS);
	}

	public static CommonConfig defaults() {
		return new CommonConfig(ExperienceSection.DEFAULTS, AllowTogetherSection.DEFAULTS, CostsSection.DEFAULTS,
				CursesSection.DEFAULTS, AnvilSection.DEFAULTS, TableSection.DEFAULTS, FortuneSection.DEFAULTS,
				BeheadingSection.DEFAULTS, SmeltingSection.DEFAULTS, SilkTouchSection.DEFAULTS, LastStandSection.DEFAULTS,
				MobsSection.DEFAULTS);
	}
}
