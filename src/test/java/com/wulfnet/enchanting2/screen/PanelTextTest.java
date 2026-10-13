package com.wulfnet.enchanting2.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import blue.endless.jankson.Jankson;
import blue.endless.jankson.JsonObject;
import com.wulfnet.enchanting2.screen.PanelText.Arg;
import com.wulfnet.enchanting2.screen.PanelText.Msg;
import com.wulfnet.enchanting2.screen.PanelText.Row;
import com.wulfnet.enchanting2.screen.PanelText.Tone;
import com.wulfnet.enchanting2.screen.PanelText.Unit;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

/** Owner, 2026-10-13: a bare "40000" in a panel read as confusing, so every amount says what it counts. */
class PanelTextTest {
	private static final String K = PanelText.AMOUNT_KEY;

	@Test
	void levelsAreSingularOnlyForOneEitherWay() {
		assertEquals(Msg.of(K + "level", new Arg.Num(1)), PanelText.amount(1, Unit.LEVELS));
		assertEquals(Msg.of(K + "level", new Arg.Num(-1)), PanelText.amount(-1, Unit.LEVELS), "a one-level discount");
		assertEquals(Msg.of(K + "levels", new Arg.Num(5)), PanelText.amount(5, Unit.LEVELS));
		assertEquals(Msg.of(K + "levels", new Arg.Num(0)), PanelText.amount(0, Unit.LEVELS));
		assertEquals(Msg.of(K + "levels", new Arg.Num(-2)), PanelText.amount(-2, Unit.LEVELS));
		assertEquals(Msg.of(K + "levels", new Arg.Num(40_000)), PanelText.amount(40_000, Unit.LEVELS));
	}

	@Test
	void lapisIsLapis() {
		assertEquals(Msg.of(K + "lapis", new Arg.Num(1)), PanelText.amount(1, Unit.LAPIS));
		assertEquals(Msg.of(K + "lapis", new Arg.Num(17)), PanelText.amount(17, Unit.LAPIS));
	}

	@Test
	void aRowsAmountIsLevelsUnlessSaidOtherwise() {
		Msg label = Msg.of("enchanting2.anvil.panel.total");
		assertEquals(Optional.of(PanelText.amount(5, Unit.LEVELS)),
				new Row(label, OptionalLong.of(5), Tone.NORMAL, false).amountText());
		assertEquals(Optional.of(PanelText.amount(17, Unit.LAPIS)),
				new Row(label, OptionalLong.of(17), Tone.NORMAL, false, Unit.LAPIS).amountText());
		assertEquals(Optional.empty(), Row.of(label, Tone.NORMAL).amountText(), "no amount, no unit");
	}

	@Test
	void everyAmountKeyHasTextWithOnePlaceholder() throws Exception {
		JsonObject lang;
		try (InputStream in = PanelTextTest.class.getResourceAsStream("/assets/enchanting2/lang/en_us.json")) {
			assertNotNull(in, "en_us.json is missing");
			lang = Jankson.builder().build().load(in);
		}
		for (String key : List.of("level", "levels", "lapis")) {
			String text = lang.get(String.class, K + key);
			assertNotNull(text, "en_us.json has no " + K + key);
			assertTrue(text.contains("%s") && text.indexOf("%s") == text.lastIndexOf("%s"), K + key + " = " + text);
		}
	}
}
