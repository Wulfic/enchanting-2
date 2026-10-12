package com.wulfnet.enchanting2.enchantment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import blue.endless.jankson.Jankson;
import blue.endless.jankson.JsonObject;
import com.wulfnet.enchanting2.enchantment.LevelCeilings.Ceiling;
import com.wulfnet.enchanting2.enchantment.LevelCeilings.Kind;
import com.wulfnet.enchanting2.enchantment.MaxLevels.Resolution;
import com.wulfnet.enchanting2.enchantment.MaxLevels.Why;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Phase 10jA: every vanilla and own enchantment has a ceiling row, each note has text, and hard ceilings clamp. */
class LevelCeilingsTest {
	private static Set<String> ownIds() throws Exception {
		URL dir = LevelCeilingsTest.class.getResource("/data/enchanting2/enchantment");
		assertNotNull(dir, "data/enchanting2/enchantment is missing");
		try (Stream<Path> files = Files.list(Path.of(dir.toURI()))) {
			Set<String> ids = new TreeSet<>();
			files.map(file -> file.getFileName().toString()).filter(name -> name.endsWith(".json"))
					.forEach(name -> ids.add("enchanting2:" + name.substring(0, name.length() - ".json".length())));
			return ids;
		}
	}

	/** A new enchantment stage can't skip its row (DESIGN §1 principle; TODO 10j). */
	@Test
	void everyVanillaAndOwnEnchantmentHasExactlyOneRow() throws Exception {
		Set<String> expected = new TreeSet<>(VanillaEnchantments.ids());
		expected.addAll(ownIds());
		assertEquals(expected, new TreeSet<>(LevelCeilings.TABLE.keySet()));
	}

	@Test
	void everyNoteHasText() throws Exception {
		JsonObject lang;
		try (InputStream in = LevelCeilingsTest.class.getResourceAsStream("/assets/enchanting2/lang/en_us.json")) {
			assertNotNull(in, "en_us.json is missing");
			lang = Jankson.builder().build().load(in);
		}
		LevelCeilings.TABLE.forEach((id, ceiling) -> {
			String key = LevelCeilings.noteKey(id);
			assertEquals(ceiling.note(), lang.containsKey(key), key);
		});
	}

	@Test
	void anOverrideAboveAHardCeilingClamps() {
		assertEquals(new Resolution(127, Why.OVERRIDE_CLAMPED),
				MaxLevels.resolve("minecraft:piercing", 4, OptionalInt.of(200)));
		assertEquals(new Resolution(13, Why.OVERRIDE_CLAMPED), MaxLevels.resolve("minecraft:lunge", 3, OptionalInt.of(255)));
		assertEquals(new Resolution(14, Why.OVERRIDE_CLAMPED),
				MaxLevels.resolve("minecraft:wind_burst", 3, OptionalInt.of(15)));
		assertEquals(new Resolution(127, Why.OVERRIDE), MaxLevels.resolve("minecraft:piercing", 4, OptionalInt.of(127)));
	}

	@Test
	void softAndNoCeilingRowsAcceptAnyLevelUpTo255() {
		assertEquals(new Resolution(255, Why.OVERRIDE), MaxLevels.resolve("minecraft:efficiency", 5, OptionalInt.of(255)));
		assertEquals(new Resolution(255, Why.OVERRIDE), MaxLevels.resolve("minecraft:thorns", 3, OptionalInt.of(255)));
		assertEquals(new Resolution(255, Why.OVERRIDE), MaxLevels.resolve("othermod:zap", 1, OptionalInt.of(255)));
	}

	@Test
	void theHardCeilingsAreTheProvenOnes() {
		assertEquals(Map.of("minecraft:lure", 5, "minecraft:riptide", 7, "minecraft:quick_charge", 4,
				"minecraft:piercing", 127, "minecraft:lunge", 13, "minecraft:wind_burst", 14), LevelCeilings.hardCeilings());
		assertEquals(OptionalInt.empty(), LevelCeilings.hardMax("minecraft:thorns"));
		assertEquals(Kind.SOFT, LevelCeilings.of("minecraft:thorns").orElseThrow().kind());
		assertTrue(LevelCeilings.of("othermod:zap").isEmpty());
	}

	@Test
	void aCeilingWithoutANoteOrOutOfRangeIsRefused() {
		assertThrows(IllegalArgumentException.class, () -> new Ceiling(Kind.HARD, 5, false));
		assertThrows(IllegalArgumentException.class, () -> new Ceiling(Kind.SOFT, 0, true));
		assertThrows(IllegalArgumentException.class, () -> new Ceiling(Kind.NONE, 256, false));
	}
}
