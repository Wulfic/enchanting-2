package com.wulfnet.enchanting2.table;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import blue.endless.jankson.Jankson;
import blue.endless.jankson.JsonObject;
import blue.endless.jankson.JsonPrimitive;
import com.wulfnet.enchanting2.cost.CostBreakdown;
import com.wulfnet.enchanting2.cost.CostBreakdown.Kind;
import com.wulfnet.enchanting2.screen.PanelText.Msg;
import com.wulfnet.enchanting2.screen.PanelText.Row;
import com.wulfnet.enchanting2.table.TableOutcome.Invalid;
import com.wulfnet.enchanting2.table.TableOutcome.Status;
import com.wulfnet.enchanting2.table.TablePanelView.Item;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Stage 7C: every message the table panel can show has English text, with as many placeholders as it has arguments.
 * The messages come from running {@link TablePanelView} over every status, row status and invalid reason, so a new enum
 * value without text fails here.
 */
class TableLangKeysTest {
	/** Built in client code, which JUnit can't load; kept in step by hand. */
	private static final List<String> CLIENT_KEYS = List.of(TablePanelView.KEY + "panel.title");
	private static final Pattern PLACEHOLDER = Pattern.compile("%(?:(\\d+)\\$)?s");

	private static JsonObject lang() throws Exception {
		try (InputStream in = TableLangKeysTest.class.getResourceAsStream("/assets/enchanting2/lang/en_us.json")) {
			assertNotNull(in, "en_us.json is missing");
			return Jankson.builder().build().load(in);
		}
	}

	private static Map<String, Integer> everyMessage() {
		List<TableRow> rows = new ArrayList<>();
		int n = 0;
		for (TableRow.Status status : TableRow.Status.values()) {
			if (status == TableRow.Status.NOT_OFFERED) {
				continue;
			}
			Optional<String> with = status == TableRow.Status.CONFLICT ? Optional.of("minecraft:x") : Optional.empty();
			for (int current : new int[] {0, 2}) {
				// Unpicked, and picked at a level the status makes sense for.
				rows.add(new TableRow("minecraft:e" + n++, current, 5, 4, 0, status, with));
				rows.add(new TableRow("minecraft:e" + n++, current, 5, 4, current + 1, status, with));
			}
		}
		CostBreakdown.Line[] all = {new CostBreakdown.Line(Kind.ENCHANT, "minecraft:e0", 0, 4, 4),
				new CostBreakdown.Line(Kind.SURCHARGE, "minecraft:e0", 0, 4, 8),
				new CostBreakdown.Line(Kind.CURSE_DISCOUNT, "", 10, 10, -2)};
		List<Optional<CostBreakdown>> costs = List.of(Optional.empty(),
				Optional.of(new CostBreakdown(List.of(all), 10, 10, 10, false, false)),
				Optional.of(new CostBreakdown(List.of(all), 10, 10, 10, true, false)),
				Optional.of(new CostBreakdown(List.of(all), 10, 10, 10, false, true)));
		List<Msg> messages = new ArrayList<>();
		TablePanelView.body(rows, costs.get(1)).stream().map(Item::row).forEach(r -> messages.add(r.text()));
		TablePanelView.body(rows, costs.get(1)).forEach(i -> i.stepper().ifPresent(s -> messages.add(s.label())));
		for (Status status : Status.values()) {
			List<Optional<Invalid>> invalids = new ArrayList<>();
			if (status == Status.INVALID_PICK) {
				for (TableRow.Status reason : TableRow.Status.values()) {
					if (reason != TableRow.Status.OK && reason != TableRow.Status.MAXED) {
						invalids.add(Optional.of(new Invalid("minecraft:e1", 3, reason,
								reason == TableRow.Status.CONFLICT ? Optional.of("minecraft:x") : Optional.empty())));
					}
				}
			} else {
				invalids.add(Optional.empty());
			}
			for (Optional<Invalid> invalid : invalids) {
				for (Optional<CostBreakdown> cost : costs) {
					if (cost.isPresent() != status.priced()) {
						continue;
					}
					for (int levels : new int[] {0, 1_000}) {
						for (int lapis : new int[] {0, 64}) {
							for (OptionalInt max : List.of(OptionalInt.empty(), OptionalInt.of(4))) {
								TablePanelView.footer(status, rows, invalid, cost, levels, lapis, max).stream()
										.map(Row::text).forEach(messages::add);
							}
						}
					}
				}
			}
		}
		Map<String, Integer> keys = new TreeMap<>();
		for (Msg msg : messages) {
			Integer previous = keys.put(msg.key(), msg.args().size());
			assertTrue(previous == null || previous == msg.args().size(), msg.key() + " takes varying arguments");
		}
		return keys;
	}

	private static int placeholders(String text) {
		Matcher m = PLACEHOLDER.matcher(text);
		int positional = 0;
		int highest = 0;
		while (m.find()) {
			if (m.group(1) != null) {
				highest = Math.max(highest, Integer.parseInt(m.group(1)));
			} else {
				positional++;
			}
		}
		return Math.max(highest, positional);
	}

	@Test
	void everyTableMessageHasTextWithMatchingPlaceholders() throws Exception {
		JsonObject lang = lang();
		for (Map.Entry<String, Integer> message : everyMessage().entrySet()) {
			String key = message.getKey();
			if (key.equals(TablePanelView.VANILLA_TOO_EXPENSIVE)) {
				continue; // vanilla's own text
			}
			assertTrue(lang.containsKey(key), "missing " + key);
			String text = ((JsonPrimitive) lang.get(key)).asString();
			assertEquals(message.getValue(), placeholders(text), key + " = \"" + text + "\"");
		}
	}

	/** Each status and reason shows up, so the walk above can't silently skip one. */
	@Test
	void theWalkReachesEveryStatusAndReason() {
		Map<String, Integer> messages = everyMessage();
		for (String key : List.of("row.name", "row.current", "row.level", "line.surcharge", "line.curse_discount",
				"total.levels", "total.lapis", "total.free_creative", "status.empty", "status.nothing", "status.pick",
				"status.invalid", "status.too_expensive", "status.insufficient_levels", "status.insufficient_lapis",
				"status.ready", "reason.needs_shelves", "reason.conflict", "reason.maxed", "reason.already_at_level",
				"reason.above_max", "reason.not_offered")) {
			assertTrue(messages.containsKey(TablePanelView.KEY + key), key);
		}
	}

	@Test
	void clientOnlyKeysHaveText() throws Exception {
		JsonObject lang = lang();
		for (String key : CLIENT_KEYS) {
			assertTrue(lang.containsKey(key), key);
		}
	}
}
