package com.wulfnet.enchanting2.tooltip;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * DESIGN §6 "Enchantment descriptions in tooltips" (owner, 2026-10-10), without Minecraft types: which enchantment
 * lines get a description, and whether the "hold Ctrl" hint goes after the last one. The client hook turns this into
 * tooltip lines.
 *
 * @param descriptions enchantment ID → its description's lang key, in tooltip order
 * @param hint         add the hint line after the last enchantment line
 */
public record DescriptionPlan(Map<String, String> descriptions, boolean hint) {
	public static final DescriptionPlan NONE = new DescriptionPlan(Map.of(), false);

	public DescriptionPlan {
		descriptions = Collections.unmodifiableMap(new LinkedHashMap<>(descriptions));
	}

	/** {@code enchantment.<namespace>.<path>.desc}, the Enchantment Descriptions mod's convention. */
	public static String key(String id) {
		Objects.requireNonNull(id, "id");
		int colon = id.indexOf(':');
		String namespace = colon < 0 ? "minecraft" : id.substring(0, colon);
		String path = colon < 0 ? id : id.substring(colon + 1);
		return "enchantment." + namespace + "." + path.replace('/', '.') + ".desc";
	}

	/**
	 * @param shown       the enchantment IDs whose lines the tooltip shows, in order (a hidden one isn't here)
	 * @param hasKey      whether the language has a lang key
	 * @param ctrlDown    Ctrl is held
	 * @param enabled     {@code tooltips.enchantment_descriptions}
	 * @param showHint    {@code tooltips.show_hint}
	 */
	public static DescriptionPlan of(List<String> shown, Predicate<String> hasKey, boolean ctrlDown, boolean enabled,
			boolean showHint) {
		if (!enabled || shown.isEmpty()) {
			return NONE;
		}
		Map<String, String> described = new LinkedHashMap<>();
		for (String id : shown) {
			String key = key(id);
			if (hasKey.test(key)) {
				described.put(id, key);
			}
		}
		if (described.isEmpty()) {
			return NONE;
		}
		return ctrlDown ? new DescriptionPlan(described, false) : new DescriptionPlan(Map.of(), showHint);
	}
}
