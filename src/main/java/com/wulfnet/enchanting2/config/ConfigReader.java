package com.wulfnet.enchanting2.config;

import blue.endless.jankson.JsonElement;
import blue.endless.jankson.JsonGrammar;
import blue.endless.jankson.JsonObject;
import blue.endless.jankson.JsonPrimitive;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Typed, validating reads from one JSON object. A bad value never throws: it records a {@link ConfigIssue} and the
 * caller gets the default. Keys that were never read are reported by {@link #reportUnknownKeys()} so typos surface.
 */
public final class ConfigReader {
	private static final int MAX_SHOWN_VALUE = 80;

	private final JsonObject object;
	private final String path;
	private final List<ConfigIssue> issues;
	private final Set<String> consumed = new HashSet<>();
	private final List<ConfigReader> children = new ArrayList<>();

	public ConfigReader(JsonObject object, List<ConfigIssue> issues) {
		this(object, "", issues);
	}

	private ConfigReader(JsonObject object, String path, List<ConfigIssue> issues) {
		this.object = object;
		this.path = path;
		this.issues = issues;
	}

	/** Keys present in this object, sorted. Jankson's {@code keySet()} does not keep file order. */
	public List<String> keys() {
		return object.keySet().stream().sorted().toList();
	}

	public boolean has(String key) {
		return object.containsKey(key);
	}

	public int readInt(String key, int def, int min, int max) {
		JsonElement e = take(key);
		if (e == null) {
			return def;
		}
		if (e instanceof JsonPrimitive p && p.getValue() instanceof Long l) {
			if (l >= min && l <= max) {
				return (int) (long) l;
			}
			issue(key, e, String.valueOf(def), "must be between " + min + " and " + max);
			return def;
		}
		issue(key, e, String.valueOf(def), "expected a whole number");
		return def;
	}

	/** A key with no default: missing → empty. A bad value is reported as ignored and also reads as empty. */
	public OptionalInt readOptionalInt(String key, int min, int max) {
		JsonElement e = take(key);
		if (e == null) {
			return OptionalInt.empty();
		}
		if (e instanceof JsonPrimitive p && p.getValue() instanceof Long l) {
			if (l >= min && l <= max) {
				return OptionalInt.of((int) (long) l);
			}
			issue(key, e, null, "must be between " + min + " and " + max);
			return OptionalInt.empty();
		}
		issue(key, e, null, "expected a whole number");
		return OptionalInt.empty();
	}

	/**
	 * An enum spelled as its lower-case constant name, with no default: missing → empty. A bad value is reported as
	 * ignored, listing the accepted spellings, and also reads as empty.
	 */
	public <E extends Enum<E>> Optional<E> readOptionalEnum(String key, Class<E> type) {
		JsonElement e = take(key);
		if (e == null) {
			return Optional.empty();
		}
		if (e instanceof JsonPrimitive p && p.getValue() instanceof String s) {
			for (E constant : type.getEnumConstants()) {
				if (constant.name().toLowerCase(Locale.ROOT).equals(s)) {
					return Optional.of(constant);
				}
			}
		}
		issue(key, e, null, "expected one of " + String.join(", ", Arrays.stream(type.getEnumConstants())
				.map(c -> c.name().toLowerCase(Locale.ROOT)).toList()));
		return Optional.empty();
	}

	/** A key with no default: missing → empty. A bad value is reported as ignored and also reads as empty. */
	public Optional<Boolean> readOptionalBool(String key) {
		JsonElement e = take(key);
		if (e == null) {
			return Optional.empty();
		}
		if (e instanceof JsonPrimitive p && p.getValue() instanceof Boolean b) {
			return Optional.of(b);
		}
		issue(key, e, null, "expected true or false");
		return Optional.empty();
	}

	public boolean readBool(String key, boolean def) {
		JsonElement e = take(key);
		if (e == null) {
			return def;
		}
		if (e instanceof JsonPrimitive p && p.getValue() instanceof Boolean b) {
			return b;
		}
		issue(key, e, String.valueOf(def), "expected true or false");
		return def;
	}

	/**
	 * A nested object. Missing → an empty reader (every key defaults). Not an object → one issue and an empty reader.
	 */
	public ConfigReader section(String key) {
		JsonElement e = take(key);
		JsonObject child;
		if (e instanceof JsonObject o) {
			child = o;
		} else {
			if (e != null) {
				issue(key, e, "{}", "expected an object { ... }; every key in it uses its default");
			}
			child = new JsonObject();
		}
		ConfigReader reader = new ConfigReader(child, path + key + ".", issues);
		children.add(reader);
		return reader;
	}

	/** Marks {@code key} as handled and records it as ignored. */
	public void reject(String key, String reason) {
		JsonElement e = take(key);
		issue(key, e, null, reason);
	}

	/** Reports every key no read touched, here and in every nested section. */
	public void reportUnknownKeys() {
		for (String key : object.keySet()) {
			if (!consumed.contains(key)) {
				issue(key, object.get(key), null, "unknown key (typo?)");
			}
		}
		for (ConfigReader child : children) {
			child.reportUnknownKeys();
		}
	}

	private JsonElement take(String key) {
		consumed.add(key);
		return object.get(key);
	}

	private void issue(String key, JsonElement value, String def, String reason) {
		issues.add(new ConfigIssue(path + key, show(value), def, reason));
	}

	private static String show(JsonElement value) {
		if (value == null) {
			return "(missing)";
		}
		String text = value.toJson(JsonGrammar.COMPACT);
		return text.length() <= MAX_SHOWN_VALUE ? text : text.substring(0, MAX_SHOWN_VALUE) + "...";
	}
}
