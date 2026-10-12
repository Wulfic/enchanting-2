package com.wulfnet.enchanting2.tooltip;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/** Phase 10h: which enchantment lines get a description, and when the "hold Ctrl" hint shows. */
class DescriptionPlanTest {
	private static final Predicate<String> ALL_KEYS = key -> true;

	@Test
	void keyFollowsTheEnchantmentDescriptionsConvention() {
		assertEquals("enchantment.minecraft.sharpness.desc", DescriptionPlan.key("minecraft:sharpness"));
		assertEquals("enchantment.enchanting2.true_shot_aura.desc", DescriptionPlan.key("enchanting2:true_shot_aura"));
		assertEquals("enchantment.minecraft.mending.desc", DescriptionPlan.key("mending"));
		assertEquals("enchantment.other.sub.thing.desc", DescriptionPlan.key("other:sub/thing"));
	}

	@Test
	void ctrlDownDescribesEveryShownEnchantmentInOrderAndNoHint() {
		DescriptionPlan plan = DescriptionPlan.of(List.of("minecraft:sharpness", "enchanting2:vision"), ALL_KEYS, true,
				true, true);
		assertEquals(List.of("minecraft:sharpness", "enchanting2:vision"), List.copyOf(plan.descriptions().keySet()));
		assertEquals("enchantment.enchanting2.vision.desc", plan.descriptions().get("enchanting2:vision"));
		assertEquals(false, plan.hint());
	}

	@Test
	void ctrlUpShowsOnlyTheHintAndOnlyIfItIsOn() {
		List<String> shown = List.of("minecraft:sharpness");
		assertEquals(new DescriptionPlan(Map.of(), true), DescriptionPlan.of(shown, ALL_KEYS, false, true, true));
		assertEquals(DescriptionPlan.NONE, DescriptionPlan.of(shown, ALL_KEYS, false, true, false));
	}

	@Test
	void aMissingKeyGetsNoLineAndNoKeyAtAllMeansNoHint() {
		Set<String> known = Set.of("enchantment.minecraft.sharpness.desc");
		DescriptionPlan plan = DescriptionPlan.of(List.of("minecraft:sharpness", "othermod:mystery"), known::contains,
				true, true, true);
		assertEquals(Map.of("minecraft:sharpness", "enchantment.minecraft.sharpness.desc"), plan.descriptions());
		// Nothing would show on Ctrl, so don't promise anything.
		assertEquals(DescriptionPlan.NONE, DescriptionPlan.of(List.of("othermod:mystery"), known::contains, false,
				true, true));
	}

	/** A hidden enchantment has no tooltip line, so the hook never lists it, and nothing (hint included) appears. */
	@Test
	void nothingShownOrTurnedOffMeansNothing() {
		assertEquals(DescriptionPlan.NONE, DescriptionPlan.of(List.of(), ALL_KEYS, true, true, true));
		assertEquals(DescriptionPlan.NONE, DescriptionPlan.of(List.of(), ALL_KEYS, false, true, true));
		assertEquals(DescriptionPlan.NONE,
				DescriptionPlan.of(List.of("minecraft:sharpness"), ALL_KEYS, true, false, true));
		assertEquals(DescriptionPlan.NONE,
				DescriptionPlan.of(List.of("minecraft:sharpness"), ALL_KEYS, false, false, true));
	}
}
