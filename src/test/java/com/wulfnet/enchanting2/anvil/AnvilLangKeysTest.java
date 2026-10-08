package com.wulfnet.enchanting2.anvil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import blue.endless.jankson.Jankson;
import blue.endless.jankson.JsonObject;
import blue.endless.jankson.JsonPrimitive;
import com.wulfnet.enchanting2.anvil.AnvilOutcome.Entry;
import com.wulfnet.enchanting2.anvil.AnvilOutcome.Status;
import com.wulfnet.enchanting2.anvil.TransferRules.Fate;
import com.wulfnet.enchanting2.cost.CostBreakdown.Kind;
import com.wulfnet.enchanting2.cost.CostBreakdown.Line;
import com.wulfnet.enchanting2.cost.CostBreakdown;
import com.wulfnet.enchanting2.enchantment.EnchantRules.Verdict;
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
 * Stage 6D: every message the anvil screen can show has English text, with as many placeholders as it has arguments.
 * The messages come from running {@link AnvilPreviewView} over every status, verdict, fate and line kind, so a new enum
 * value without text fails here.
 */
class AnvilLangKeysTest {
	/** Built in client code, which JUnit can't load; kept in step by hand. */
	private static final List<String> CLIENT_KEYS = List.of(AnvilPreviewView.KEY + "panel.title",
			AnvilPreviewView.KEY + "tab.show", AnvilPreviewView.KEY + "tab.hide", AnvilPreviewView.KEY + "target.empty",
			"enchanting2.config.category.display", "enchanting2.config.display.show_preview_panel",
			"enchanting2.config.display.show_preview_panel.desc");
	private static final Pattern PLACEHOLDER = Pattern.compile("%(?:(\\d+)\\$)?s");

	private static JsonObject lang() throws Exception {
		try (InputStream in = AnvilLangKeysTest.class.getResourceAsStream("/assets/enchanting2/lang/en_us.json")) {
			assertNotNull(in, "en_us.json is missing");
			return Jankson.builder().build().load(in);
		}
	}

	/** Every message, keyed by lang key; the same key always takes the same number of arguments. */
	private static Map<String, Integer> everyMessage() {
		Map<String, Integer> messages = new TreeMap<>();
		List<Msg> all = new ArrayList<>();
		List<Entry> entries = new ArrayList<>();
		for (Verdict verdict : Verdict.values()) {
			for (Fate fate : Fate.values()) {
				Optional<String> with = verdict == Verdict.CONFLICT ? Optional.of("minecraft:smite") : Optional.empty();
				entries.add(new Entry("minecraft:sharpness", 3, verdict, with, fate));
			}
		}
		entries.add(new Entry("minecraft:sharpness", 3, Verdict.CONFLICT, Optional.empty(), Fate.LOSE));
		List<Line> lines = new ArrayList<>();
		for (Kind kind : Kind.values()) {
			long amount = kind == Kind.CURSE_DISCOUNT ? -1 : 1;
			lines.add(new Line(kind, "minecraft:mending", 0, 2, amount));
			lines.add(new Line(kind, "minecraft:mending", 1, 2, amount));
			lines.add(new Line(kind, "minecraft:mending", 2, 0, amount));
			lines.add(new Line(kind, "minecraft:mending", 2, 2, amount));
		}
		long sum = lines.stream().mapToLong(Line::amount).sum();
		long enchantSum = lines.stream().filter(l -> l.kind() == Kind.ENCHANT || l.kind() == Kind.SURCHARGE
				|| l.kind() == Kind.CURSE_DISCOUNT).mapToLong(Line::amount).sum();
		List<CostBreakdown> breakdowns = new ArrayList<>();
		for (boolean free : new boolean[] {false, true}) {
			for (boolean tooExpensive : new boolean[] {false, true}) {
				breakdowns.add(new CostBreakdown(lines, (int) enchantSum, (int) sum, 0, free, tooExpensive));
			}
		}
		breakdowns.add(new CostBreakdown(List.of(), 0, 0, 0, false, false));
		for (Status status : Status.values()) {
			for (OptionalInt max : List.of(OptionalInt.empty(), OptionalInt.of(5))) {
				for (int level : new int[] {0, 1_000}) {
					List<Optional<CostBreakdown>> priced = new ArrayList<>();
					priced.add(Optional.empty());
					breakdowns.forEach(b -> priced.add(Optional.of(b)));
					for (Optional<CostBreakdown> breakdown : priced) {
						if (status == Status.OK && breakdown.isEmpty()) {
							continue; // an OK outcome is always priced
						}
						AnvilPreviewView.label(status, breakdown, entries, level, max)
								.ifPresent(label -> all.addAll(label.forms()));
						AnvilPreviewView.panel(status, breakdown, entries, level, max).forEach(row -> all.add(row.text()));
					}
				}
			}
		}
		entries.forEach(e -> all.add(AnvilPreviewView.reason(e)));
		for (Fate fate : Fate.values()) {
			all.add(AnvilPreviewView.fate(fate));
		}
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
	void everyAnvilMessageHasTextWithMatchingPlaceholders() throws Exception {
		JsonObject lang = lang();
		Map<String, Integer> messages = everyMessage();
		for (Map.Entry<String, Integer> message : messages.entrySet()) {
			String key = message.getKey();
			if (key.equals(AnvilPreviewView.VANILLA_COST) || key.equals(AnvilPreviewView.VANILLA_TOO_EXPENSIVE)) {
				continue; // vanilla's own text
			}
			assertTrue(lang.containsKey(key), "missing " + key);
			String text = ((JsonPrimitive) lang.get(key)).asString();
			assertEquals(message.getValue(), placeholders(text), key + " = \"" + text + "\"");
		}
	}

	/** Each verdict and fate shows up somewhere, so the walk above can't silently skip one. */
	@Test
	void theWalkReachesEveryVerdictFateAndLineKind() {
		Map<String, Integer> messages = everyMessage();
		for (Verdict verdict : Verdict.values()) {
			String key = AnvilPreviewView.KEY + "reason." + verdict.name().toLowerCase(Locale.ROOT);
			assertTrue(messages.containsKey(key), key);
		}
		for (Fate fate : Fate.values()) {
			String key = AnvilPreviewView.KEY + "fate." + fate.name().toLowerCase(Locale.ROOT);
			assertTrue(messages.containsKey(key), key);
		}
		for (Kind kind : Kind.values()) {
			String prefix = AnvilPreviewView.KEY + "line." + kind.name().toLowerCase(Locale.ROOT);
			assertTrue(messages.keySet().stream().anyMatch(k -> k.startsWith(prefix)), prefix);
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
