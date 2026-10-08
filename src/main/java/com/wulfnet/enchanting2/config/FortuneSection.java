package com.wulfnet.enchanting2.config;

import blue.endless.jankson.JsonObject;
import blue.endless.jankson.JsonPrimitive;
import com.wulfnet.enchanting2.fortune.SilkFortuneMode;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/** DESIGN §4 Silk Touch + Fortune and §6 Fortune on all mining tools. Which blocks multiply is a data tag, not config. */
public record FortuneSection(SilkFortuneMode silkFortuneMode) {
	public static final FortuneSection DEFAULTS = new FortuneSection(SilkFortuneMode.SILK_WINS);

	public FortuneSection {
		Objects.requireNonNull(silkFortuneMode, "silkFortuneMode");
	}

	static FortuneSection read(ConfigReader r) {
		return new FortuneSection(
				r.readOptionalEnum("silk_fortune_mode", SilkFortuneMode.class).orElse(DEFAULTS.silkFortuneMode));
	}

	JsonObject write() {
		JsonObject o = new JsonObject();
		o.put("silk_fortune_mode", JsonPrimitive.of(silkFortuneMode.name().toLowerCase(Locale.ROOT)),
				"A tool with Silk Touch and Fortune. One of " + Arrays.stream(SilkFortuneMode.values())
						.map(m -> m.name().toLowerCase(Locale.ROOT)).collect(Collectors.joining(", "))
						+ ". silk_wins = Fortune never multiplies a silk-touched drop; fortune_multiplies_silk = it "
						+ "does, on natural blocks only. WARNING: on ores that multiplies the ore blocks themselves, "
						+ "which are then mined again with Fortune (about 3.1x the ore at Fortune V, more if "
						+ "placed-block tracking is lost).");
		return o;
	}
}
