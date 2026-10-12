package com.wulfnet.enchanting2.enchantment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.wulfnet.enchanting2.enchantment.ItemWidening.Why;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ItemWideningTest {
	private static final Optional<String> NONE = Optional.empty();

	@Test
	void vanillaDefinitionsWiden() {
		assertEquals(Why.WIDEN, ItemWidening.decide("minecraft:knockback",
				Optional.of("minecraft:enchantable/melee_weapon"), NONE, false));
		assertEquals(Why.WIDEN, ItemWidening.decide("minecraft:flame", Optional.of("minecraft:enchantable/bow"), NONE,
				false));
		assertEquals(Why.WIDEN, ItemWidening.decide("minecraft:fire_aspect",
				Optional.of("minecraft:enchantable/fire_aspect"), Optional.of("minecraft:enchantable/melee_weapon"),
				true));
	}

	/** The guard: a datapack's own items are never overwritten. */
	@Test
	void anyOtherDefinitionIsLeftAlone() {
		assertEquals(Why.CHANGED_ELSEWHERE, ItemWidening.decide("minecraft:knockback",
				Optional.of("mypack:clubs"), NONE, false), "another tag");
		assertEquals(Why.CHANGED_ELSEWHERE, ItemWidening.decide("minecraft:infinity", NONE, NONE, false),
				"a plain list");
		assertEquals(Why.CHANGED_ELSEWHERE, ItemWidening.decide("minecraft:looting",
				Optional.of("minecraft:enchantable/melee_weapon"), Optional.of("mypack:x"), true),
				"a primary added where vanilla has none");
		assertEquals(Why.CHANGED_ELSEWHERE, ItemWidening.decide("minecraft:fire_aspect",
				Optional.of("minecraft:enchantable/fire_aspect"), NONE, false), "fire aspect's primary removed");
		assertEquals(Why.CHANGED_ELSEWHERE, ItemWidening.decide("minecraft:fire_aspect",
				Optional.of("minecraft:enchantable/fire_aspect"), Optional.of("mypack:x"), true),
				"fire aspect's primary changed");
	}

	@Test
	void unlistedEnchantmentsAreNotTouched() {
		assertEquals(Why.NOT_LISTED, ItemWidening.decide("minecraft:power", Optional.of("minecraft:enchantable/bow"),
				NONE, false), "Power stays bow-only (DESIGN §4)");
		assertEquals(Why.NOT_LISTED, ItemWidening.decide("minecraft:sweeping_edge",
				Optional.of("minecraft:enchantable/sweeping"), NONE, false), "Sweeping Edge stays swords-only");
	}

	@Test
	void primaryIsReplacedOnlyWhereVanillaHasOne() {
		assertThrows(IllegalArgumentException.class, () -> new ItemWidening.Row("a", "b", Optional.of("c"), NONE));
		assertThrows(IllegalArgumentException.class, () -> new ItemWidening.Row("a", "b", NONE, Optional.of("d")));
	}
}
