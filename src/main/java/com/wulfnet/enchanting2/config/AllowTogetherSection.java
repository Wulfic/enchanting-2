package com.wulfnet.enchanting2.config;

import blue.endless.jankson.JsonObject;
import blue.endless.jankson.JsonPrimitive;
import com.wulfnet.enchanting2.enchantment.ConflictFamily;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * DESIGN §4. One key per {@link ConflictFamily}: {@code true} lets its members share an item, {@code false} keeps vanilla's
 * exclusive set. Named {@code allow_together} rather than {@code conflicts} because {@code conflicts.x: true} reads as
 * "they conflict" (owner, 2026-10-07).
 */
public record AllowTogetherSection(Set<ConflictFamily> allowed) {
	public static final AllowTogetherSection DEFAULTS = new AllowTogetherSection(EnumSet.allOf(ConflictFamily.class));

	public AllowTogetherSection {
		Objects.requireNonNull(allowed, "allowed");
		EnumSet<ConflictFamily> copy = EnumSet.noneOf(ConflictFamily.class);
		copy.addAll(allowed);
		allowed = Collections.unmodifiableSet(copy);
	}

	public boolean allows(ConflictFamily family) {
		return allowed.contains(family);
	}

	static AllowTogetherSection read(ConfigReader r) {
		EnumSet<ConflictFamily> allowed = EnumSet.noneOf(ConflictFamily.class);
		for (ConflictFamily family : ConflictFamily.values()) {
			if (r.readBool(family.key(), DEFAULTS.allows(family))) {
				allowed.add(family);
			}
		}
		return new AllowTogetherSection(allowed);
	}

	JsonObject write() {
		JsonObject o = new JsonObject();
		for (ConflictFamily family : ConflictFamily.values()) {
			o.put(family.key(), JsonPrimitive.of(allows(family)), String.join(", ", family.members().stream()
					.sorted().map(id -> id.substring(id.indexOf(':') + 1)).toList()));
		}
		return o;
	}
}
