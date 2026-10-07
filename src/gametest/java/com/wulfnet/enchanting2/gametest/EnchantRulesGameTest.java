package com.wulfnet.enchanting2.gametest;

import com.wulfnet.enchanting2.config.AllowTogetherSection;
import com.wulfnet.enchanting2.enchantment.ConflictFamily;
import com.wulfnet.enchanting2.enchantment.EnchantRules;
import com.wulfnet.enchanting2.enchantment.EnchantRules.Result;
import com.wulfnet.enchanting2.enchantment.EnchantRules.Verdict;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * DESIGN §4 through the live registry (v1 max levels from Stage 4A). {@code e2test:third_party} is GameTest-only data: a
 * mining enchantment that the GameTest mod also appends to vanilla's {@code exclusive_set/mining}, standing in for another
 * mod's enchantment.
 */
public class EnchantRulesGameTest {
	private static final ResourceKey<Enchantment> THIRD_PARTY = ResourceKey.create(Registries.ENCHANTMENT,
			Identifier.fromNamespaceAndPath("e2test", "third_party"));
	private static final AllowTogetherSection ALL_ALLOWED = AllowTogetherSection.DEFAULTS;
	private static final AllowTogetherSection SILK_FORTUNE_EXCLUSIVE = new AllowTogetherSection(
			EnumSet.complementOf(EnumSet.of(ConflictFamily.SILK_TOUCH_FORTUNE)));

	private static Holder<Enchantment> get(GameTestHelper helper, ResourceKey<Enchantment> key) {
		return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	private static ItemEnchantments held(GameTestHelper helper, Map<ResourceKey<Enchantment>, Integer> levels) {
		ItemEnchantments.Mutable m = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		levels.forEach((key, level) -> m.set(get(helper, key), level));
		return m.toImmutable();
	}

	private static void expect(GameTestHelper helper, Verdict expected, Result actual, String what) {
		// GameTestHelper#assertValueEqual takes (expected, actual).
		helper.assertValueEqual(expected, actual.verdict(), Component.literal(what + " (result " + actual + ")"));
	}

	@GameTest
	public void applicabilityComesFromSupportedItems(GameTestHelper helper) {
		expect(helper, Verdict.NOT_APPLICABLE, EnchantRules.check(new ItemStack(Items.DIAMOND_HELMET),
				get(helper, Enchantments.EFFICIENCY), 1, ItemEnchantments.EMPTY, ALL_ALLOWED), "Efficiency on a helmet");
		expect(helper, Verdict.NOT_APPLICABLE, EnchantRules.check(new ItemStack(Items.DIAMOND_PICKAXE),
				get(helper, Enchantments.PROTECTION), 1, ItemEnchantments.EMPTY, ALL_ALLOWED), "Protection on a pickaxe");
		expect(helper, Verdict.OK, EnchantRules.check(new ItemStack(Items.DIAMOND_PICKAXE),
				get(helper, Enchantments.EFFICIENCY), 5, ItemEnchantments.EMPTY, ALL_ALLOWED), "Efficiency V on a pickaxe");
		helper.succeed();
	}

	@GameTest
	public void levelMustBeWithinOneToTheRegistryMax(GameTestHelper helper) {
		ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
		Holder<Enchantment> fortune = get(helper, Enchantments.FORTUNE);
		expect(helper, Verdict.OK, EnchantRules.check(pickaxe, fortune, 5, ItemEnchantments.EMPTY, ALL_ALLOWED),
				"Fortune V (v1 max)");
		expect(helper, Verdict.ABOVE_MAX, EnchantRules.check(pickaxe, fortune, 6, ItemEnchantments.EMPTY, ALL_ALLOWED),
				"Fortune VI");
		expect(helper, Verdict.INVALID_LEVEL, EnchantRules.check(pickaxe, fortune, 0, ItemEnchantments.EMPTY,
				ALL_ALLOWED), "Fortune 0");
		helper.succeed();
	}

	@GameTest
	public void conflictFamilyFollowsTheConfig(GameTestHelper helper) {
		ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
		Holder<Enchantment> fortune = get(helper, Enchantments.FORTUNE);
		ItemEnchantments silk = held(helper, Map.of(Enchantments.SILK_TOUCH, 1));

		expect(helper, Verdict.OK, EnchantRules.check(pickaxe, fortune, 3, silk, ALL_ALLOWED), "Fortune onto Silk, allowed");
		Result off = EnchantRules.check(pickaxe, fortune, 3, silk, SILK_FORTUNE_EXCLUSIVE);
		expect(helper, Verdict.CONFLICT, off, "Fortune onto Silk, family exclusive");
		helper.assertValueEqual(Optional.of(get(helper, Enchantments.SILK_TOUCH)), off.conflictsWith(),
				Component.literal("the enchantment Fortune conflicts with"));
		// Turning one family off leaves the others relaxed.
		expect(helper, Verdict.OK, EnchantRules.check(new ItemStack(Items.DIAMOND_SWORD),
				get(helper, Enchantments.SMITE), 5, held(helper, Map.of(Enchantments.SHARPNESS, 5)), SILK_FORTUNE_EXCLUSIVE),
				"Smite onto Sharpness with only silk_touch_fortune exclusive");
		helper.succeed();
	}

	@GameTest
	public void datapackMembersOfAVanillaExclusiveSetAreNotRelaxed(GameTestHelper helper) {
		ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
		Result r = EnchantRules.check(pickaxe, get(helper, THIRD_PARTY), 1, held(helper, Map.of(Enchantments.FORTUNE, 3)),
				ALL_ALLOWED);
		expect(helper, Verdict.CONFLICT, r, "third_party onto Fortune, every family allowed");
		helper.assertValueEqual(Optional.of(get(helper, Enchantments.FORTUNE)), r.conflictsWith(),
				Component.literal("the enchantment third_party conflicts with"));
		helper.succeed();
	}

	@GameTest
	public void booksSkipApplicabilityButNotConflicts(GameTestHelper helper) {
		ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
		ItemEnchantments stored = held(helper, Map.of(Enchantments.PROTECTION, 4));
		expect(helper, Verdict.OK, EnchantRules.check(book, get(helper, Enchantments.EFFICIENCY), 5, stored, ALL_ALLOWED),
				"Efficiency onto a Protection book");
		expect(helper, Verdict.OK, EnchantRules.check(new ItemStack(Items.BOOK), get(helper, Enchantments.EFFICIENCY), 5,
				ItemEnchantments.EMPTY, ALL_ALLOWED), "Efficiency onto a plain book");
		expect(helper, Verdict.CONFLICT, EnchantRules.check(book, get(helper, Enchantments.FORTUNE), 3,
				held(helper, Map.of(Enchantments.SILK_TOUCH, 1)), SILK_FORTUNE_EXCLUSIVE), "Fortune onto a Silk book, exclusive");
		expect(helper, Verdict.ABOVE_MAX, EnchantRules.check(book, get(helper, Enchantments.FORTUNE), 6,
				ItemEnchantments.EMPTY, ALL_ALLOWED), "Fortune VI onto a book");
		helper.succeed();
	}

	@GameTest
	public void existingEntriesAreGrandfathered(GameTestHelper helper) {
		ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
		ItemEnchantments pair = held(helper, Map.of(Enchantments.SILK_TOUCH, 1, Enchantments.FORTUNE, 3));
		Result upgrade = EnchantRules.check(pickaxe, get(helper, Enchantments.FORTUNE), 5, pair, SILK_FORTUNE_EXCLUSIVE);
		expect(helper, Verdict.OK, upgrade, "Fortune III -> V beside Silk, family since made exclusive");
		helper.assertValueEqual(3, upgrade.currentLevel(), Component.literal("current Fortune level"));
		expect(helper, Verdict.OK, EnchantRules.check(pickaxe, get(helper, Enchantments.UNBREAKING), 3, pair,
				SILK_FORTUNE_EXCLUSIVE), "Unbreaking onto a grandfathered Silk + Fortune pickaxe");
		helper.succeed();
	}

	@GameTest
	public void anItemAboveTheMaxKeepsWorking(GameTestHelper helper) {
		// Quick Charge's v1 max is 4. A crossbow already holding V stands in for "max lowered after the item was made".
		ItemStack crossbow = new ItemStack(Items.CROSSBOW);
		ItemEnchantments five = held(helper, Map.of(Enchantments.QUICK_CHARGE, 5));
		Holder<Enchantment> quickCharge = get(helper, Enchantments.QUICK_CHARGE);
		Result same = EnchantRules.check(crossbow, quickCharge, 4, five, ALL_ALLOWED);
		expect(helper, Verdict.ALREADY_AT_LEVEL, same, "Quick Charge IV onto Quick Charge V");
		helper.assertValueEqual(5, same.currentLevel(), Component.literal("current Quick Charge level"));
		expect(helper, Verdict.ABOVE_MAX, EnchantRules.check(crossbow, quickCharge, 5, five, ALL_ALLOWED),
				"Quick Charge V onto Quick Charge V (max 4)");
		expect(helper, Verdict.OK, EnchantRules.check(crossbow, get(helper, Enchantments.UNBREAKING), 3, five,
				ALL_ALLOWED), "Unbreaking onto a Quick Charge V crossbow");
		helper.succeed();
	}

	@GameTest
	public void mergeKeepsTheHigherLevelAndEveryOtherEntry(GameTestHelper helper) {
		Holder<Enchantment> fortune = get(helper, Enchantments.FORTUNE);
		ItemEnchantments before = held(helper,
				Map.of(Enchantments.FORTUNE, 4, Enchantments.UNBREAKING, 2, THIRD_PARTY, 1));

		ItemEnchantments up = EnchantRules.withEnchantment(before, fortune, 5);
		helper.assertValueEqual(held(helper,
				Map.of(Enchantments.FORTUNE, 5, Enchantments.UNBREAKING, 2, THIRD_PARTY, 1)), up,
				Component.literal("Fortune IV + V"));
		// Equal or lower never raises (no vanilla IV + IV = V, decisions.md 2026-10-07) and never lowers.
		helper.assertValueEqual(before, EnchantRules.withEnchantment(before, fortune, 4),
				Component.literal("Fortune IV + IV"));
		helper.assertValueEqual(before, EnchantRules.withEnchantment(before, fortune, 2),
				Component.literal("Fortune IV + II"));
		helper.assertValueEqual(held(helper, Map.of(Enchantments.FORTUNE, 4, Enchantments.UNBREAKING, 2, THIRD_PARTY, 1,
				Enchantments.MENDING, 1)), EnchantRules.withEnchantment(before, get(helper, Enchantments.MENDING), 1),
				Component.literal("Mending added"));

		boolean threw = false;
		try {
			EnchantRules.withEnchantment(before, fortune, 0);
		} catch (IllegalArgumentException expected) {
			threw = true;
		}
		helper.assertTrue(threw, Component.literal("withEnchantment at level 0 must throw"));
		helper.succeed();
	}
}
