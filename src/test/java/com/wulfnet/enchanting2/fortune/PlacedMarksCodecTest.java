package com.wulfnet.enchanting2.fortune;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlacedMarksCodecTest {
	private static PlacedMarks decode(JsonElement json) {
		return PlacedMarksCodec.INSTANCE.parse(JsonOps.INSTANCE, json).getOrThrow();
	}

	private static JsonElement encode(PlacedMarks marks) {
		return PlacedMarksCodec.INSTANCE.encodeStart(JsonOps.INSTANCE, marks).getOrThrow();
	}

	private static String words(int count, String word) {
		return String.join(", ", java.util.Collections.nCopies(count, word));
	}

	@Test
	void marksRoundTrip() {
		PlacedMarks marks = PlacedMarks.EMPTY.withMark(0, -64, 0).withMark(15, 319, 15).withMark(7, 70, 9);
		JsonElement json = encode(marks);
		assertEquals(PlacedMarksCodec.VERSION, json.getAsJsonObject().get("v").getAsInt());
		assertEquals(marks, decode(json));
		assertEquals(PlacedMarks.EMPTY, decode(encode(PlacedMarks.EMPTY)));
	}

	@Test
	void everyBitOfAWordRoundTrips() {
		PlacedMarks marks = PlacedMarks.EMPTY;
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 4; z++) {
				marks = marks.withMark(x, 0, z);
			}
		}
		assertEquals(64, marks.count());
		assertEquals(marks, decode(encode(marks)));
	}

	/** Anything unreadable fails closed (placed everywhere) and is written back exactly as it was read. */
	@Test
	void corruptDataIsUnreadableAndWrittenBackUnchanged() {
		String good = "[" + words(64, "0") + "]";
		List<String> corrupt = List.of(
				"\"not a map\"",
				"{}",
				"{ \"v\": 2, \"sections\": [] }",
				"{ \"v\": \"one\", \"sections\": [] }",
				"{ \"v\": 1 }",
				"{ \"v\": 1, \"sections\": \"oops\" }",
				"{ \"v\": 1, \"sections\": [ 5 ] }",
				"{ \"v\": 1, \"sections\": [ { \"y\": 0, \"bits\": [1, 2, 3] } ] }",
				"{ \"v\": 1, \"sections\": [ { \"y\": 0 } ] }",
				"{ \"v\": 1, \"sections\": [ { \"bits\": " + good + " } ] }",
				"{ \"v\": 1, \"sections\": [ { \"y\": 128, \"bits\": " + good + " } ] }",
				"{ \"v\": 1, \"sections\": [ { \"y\": 2, \"bits\": " + good + " }, { \"y\": 2, \"bits\": " + good + " } ] }");
		for (String text : corrupt) {
			JsonElement json = JsonParser.parseString(text);
			PlacedMarks marks = decode(json);
			assertTrue(marks.isUnreadable(), text);
			assertTrue(marks.isMarked(4, 64, 4), text);
			assertEquals(json, encode(marks), text);
		}
	}

	@Test
	void allZeroSectionsReadAsNothingPlaced() {
		JsonElement json = JsonParser.parseString(
				"{ \"v\": 1, \"sections\": [ { \"y\": 3, \"bits\": [" + words(64, "0") + "] } ] }");
		assertEquals(PlacedMarks.EMPTY, decode(json));
	}

	@Test
	void sectionBoundsAreAccepted() {
		String bits = "[1, " + words(63, "0") + "]";
		PlacedMarks marks = decode(JsonParser.parseString("{ \"v\": 1, \"sections\": [ { \"y\": "
				+ PlacedMarksCodec.MIN_SECTION_Y + ", \"bits\": " + bits + " }, { \"y\": " + PlacedMarksCodec.MAX_SECTION_Y
				+ ", \"bits\": " + bits + " } ] }"));
		assertTrue(marks.isMarked(0, PlacedMarksCodec.MIN_SECTION_Y * 16, 0));
		assertTrue(marks.isMarked(0, PlacedMarksCodec.MAX_SECTION_Y * 16, 0));
		assertEquals(2, marks.count());
	}
}
