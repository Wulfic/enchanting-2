package com.wulfnet.enchanting2.config;

import blue.endless.jankson.JsonObject;
import java.util.Objects;

/**
 * {@code config/enchanting2/common.json5}: gameplay settings, owned by the server and synced to clients.
 * Each phase adds its section here together with the code that reads it.
 */
public record CommonConfig(ExperienceSection experience, AllowTogetherSection allowTogether) {
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
					AllowTogetherSection.read(root.section("allow_together")));
		}

		@Override
		public void write(CommonConfig value, JsonObject root) {
			root.put("experience", value.experience.write(), "Experience curve (levels past 30).");
			root.put("allow_together", value.allowTogether.write(),
					"Vanilla exclusive sets. true = these may share an item; false = vanilla (only one of them).");
		}
	};

	public CommonConfig {
		Objects.requireNonNull(experience, "experience");
		Objects.requireNonNull(allowTogether, "allowTogether");
	}

	public static CommonConfig defaults() {
		return new CommonConfig(ExperienceSection.DEFAULTS, AllowTogetherSection.DEFAULTS);
	}
}
