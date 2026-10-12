package com.wulfnet.enchanting2.enchantment;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * DESIGN §4 applicability widening (owner, 2026-10-10, Phase 10g): which vanilla enchantments move to our item tags, and
 * whether a loaded definition is still vanilla's so the move may happen. MC-free; {@link ItemWideningHook} applies it.
 * Each new tag lists the vanilla tag plus the new items, so a datapack that extends the vanilla tag still counts.
 */
public final class ItemWidening {
	/**
	 * One widened enchantment.
	 *
	 * @param vanillaSupported tag ID vanilla's {@code supported_items} points at
	 * @param supported        our replacement
	 * @param vanillaPrimary   tag ID of vanilla's {@code primary_items}, empty when vanilla has none (it then follows
	 *                         {@code supported_items}, so widening that widens the table too)
	 * @param primary          our replacement, present exactly when {@code vanillaPrimary} is
	 */
	public record Row(String vanillaSupported, String supported, Optional<String> vanillaPrimary,
			Optional<String> primary) {
		public Row {
			Objects.requireNonNull(vanillaSupported, "vanillaSupported");
			Objects.requireNonNull(supported, "supported");
			Objects.requireNonNull(vanillaPrimary, "vanillaPrimary");
			Objects.requireNonNull(primary, "primary");
			if (vanillaPrimary.isPresent() != primary.isPresent()) {
				throw new IllegalArgumentException("primary_items is replaced only where vanilla has one");
			}
		}

		static Row of(String vanillaSupported, String supported) {
			return new Row(vanillaSupported, supported, Optional.empty(), Optional.empty());
		}
	}

	public static final Map<String, Row> ROWS = Map.of(
			"minecraft:knockback", Row.of("minecraft:enchantable/melee_weapon", "enchanting2:enchantable/knockback"),
			"minecraft:looting", Row.of("minecraft:enchantable/melee_weapon", "enchanting2:enchantable/looting"),
			"minecraft:fire_aspect", new Row("minecraft:enchantable/fire_aspect", "enchanting2:enchantable/fire_aspect",
					Optional.of("minecraft:enchantable/melee_weapon"),
					Optional.of("enchanting2:enchantable/fire_aspect_primary")),
			"minecraft:flame", Row.of("minecraft:enchantable/bow", "enchanting2:enchantable/flame"),
			"minecraft:infinity", Row.of("minecraft:enchantable/bow", "enchanting2:enchantable/infinity"));

	public enum Why {
		/** Not one of ours: nothing to do. */
		NOT_LISTED,
		/** Still vanilla's tags: widen. */
		WIDEN,
		/** Something else already changed the items (a datapack, another mod): keep theirs. */
		CHANGED_ELSEWHERE
	}

	private ItemWidening() {
	}

	/**
	 * @param supportedTag the loaded {@code supported_items} tag ID, empty when it's a plain list
	 * @param primaryTag   the loaded {@code primary_items} tag ID; empty when absent or a plain list
	 * @param hasPrimary   whether {@code primary_items} is present at all
	 */
	public static Why decide(String id, Optional<String> supportedTag, Optional<String> primaryTag, boolean hasPrimary) {
		Row row = ROWS.get(id);
		if (row == null) {
			return Why.NOT_LISTED;
		}
		boolean supportedVanilla = supportedTag.equals(Optional.of(row.vanillaSupported()));
		boolean primaryVanilla = row.vanillaPrimary().isPresent()
				? hasPrimary && primaryTag.equals(row.vanillaPrimary())
				: !hasPrimary;
		return supportedVanilla && primaryVanilla ? Why.WIDEN : Why.CHANGED_ELSEWHERE;
	}
}
