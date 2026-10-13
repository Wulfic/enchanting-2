package com.wulfnet.enchanting2.grindstone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import blue.endless.jankson.Jankson;
import blue.endless.jankson.JsonObject;
import blue.endless.jankson.JsonPrimitive;
import com.wulfnet.enchanting2.grindstone.GrindstoneOutcome.Status;
import com.wulfnet.enchanting2.grindstone.GrindstonePanelView.Item;
import com.wulfnet.enchanting2.screen.PanelText.Msg;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Stage 6bC: every message the grindstone panel can show has English text, with as many placeholders as it has
 * arguments. The messages come from running {@link GrindstonePanelView} over every status, action and row status, so a
 * new enum value without text fails here.
 */
class GrindstoneLangKeysTest {
	/** Built in client code, which JUnit can't load; kept in step by hand. */
	private static final List<String> CLIENT_KEYS = List.of(GrindstonePanelView.KEY + "row.cost",
			GrindstonePanelView.KEY + "tooltip.cost");
	private static final Pattern PLACEHOLDER = Pattern.compile("%(?:(\\d+)\\$)?s");

	private static JsonObject lang() throws Exception {
		try (InputStream in = GrindstoneLangKeysTest.class.getResourceAsStream("/assets/enchanting2/lang/en_us.json")) {
			assertNotNull(in, "en_us.json is missing");
			return Jankson.builder().build().load(in);
		}
	}

	private static Map<String, Integer> everyMessage() {
		List<GrindstoneOption> options = new ArrayList<>();
		for (GrindstoneAction action : GrindstoneAction.values()) {
			if (!action.takesEnchantment()) {
				options.add(new GrindstoneOption(GrindstoneChoice.of(action), 0, 0, false, Status.OK));
				continue;
			}
			int n = 0;
			for (Status status : GrindstoneOption.ROW_STATUSES) {
				for (boolean free : new boolean[] {false, true}) {
					options.add(new GrindstoneOption(GrindstoneChoice.of(action, "minecraft:e" + n++), 3, 5, free,
							status));
				}
			}
		}
		List<Msg> all = new ArrayList<>();
		for (Status status : Status.values()) {
			for (OptionalInt max : List.of(OptionalInt.empty(), OptionalInt.of(4))) {
				for (int level : new int[] {0, 1_000}) {
					List<Optional<GrindstoneChoice>> picks = new ArrayList<>();
					picks.add(Optional.empty());
					options.forEach(o -> picks.add(Optional.of(o.choice())));
					for (Optional<GrindstoneChoice> pick : picks) {
						for (int charged : new int[] {0, 5}) {
							GrindstonePanelView.panel(status, options, pick, charged, level, max).stream()
									.map(Item::row).forEach(row -> all.add(row.text()));
							GrindstonePanelView.label(status, options, pick, charged, level, max)
									.ifPresent(label -> all.addAll(label.forms()));
						}
					}
				}
			}
		}
		Map<String, Integer> messages = new TreeMap<>();
		for (Msg msg : all) {
			Integer previous = messages.put(msg.key(), msg.args().size());
			assertTrue(previous == null || previous == msg.args().size(), msg.key() + " takes varying arguments");
		}
		return messages;
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
	void everyGrindstoneMessageHasTextWithMatchingPlaceholders() throws Exception {
		JsonObject lang = lang();
		for (Map.Entry<String, Integer> message : everyMessage().entrySet()) {
			String key = message.getKey();
			if (key.equals(GrindstonePanelView.VANILLA_COST) || key.equals(GrindstonePanelView.VANILLA_TOO_EXPENSIVE)) {
				continue; // vanilla's own text
			}
			assertTrue(lang.containsKey(key), "missing " + key);
			String text = ((JsonPrimitive) lang.get(key)).asString();
			assertEquals(message.getValue(), placeholders(text), key + " = \"" + text + "\"");
		}
	}

	/** Each status and action shows up, so the walk above can't silently skip one. */
	@Test
	void theWalkReachesEveryStatusAndAction() {
		Map<String, Integer> messages = everyMessage();
		for (GrindstoneAction action : GrindstoneAction.values()) {
			String name = action.name().toLowerCase(Locale.ROOT);
			String key = GrindstonePanelView.KEY + (action.takesEnchantment() ? "heading." : "row.") + name;
			assertTrue(messages.containsKey(key), key);
		}
		for (String key : List.of("status.empty", "status.stacked", "status.pick", "status.not_available",
				"status.curse", "status.only_entry", "status.too_expensive", "status.insufficient", "status.free",
				"status.free_creative", "status.gives_xp", "row.curse", "row.only_entry", "row.enchantment")) {
			assertTrue(messages.containsKey(GrindstonePanelView.KEY + key), key);
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
