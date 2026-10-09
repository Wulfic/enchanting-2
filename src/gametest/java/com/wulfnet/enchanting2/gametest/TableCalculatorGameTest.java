package com.wulfnet.enchanting2.gametest;

import com.wulfnet.enchanting2.acquisition.AcquisitionRules;
import com.wulfnet.enchanting2.config.AcquisitionSettings;
import com.wulfnet.enchanting2.config.AllowTogetherSection;
import com.wulfnet.enchanting2.config.CommonConfig;
import com.wulfnet.enchanting2.config.CostsSection;
import com.wulfnet.enchanting2.config.EnchantmentOverrides;
import com.wulfnet.enchanting2.config.TableSection;
import com.wulfnet.enchanting2.cost.CostBreakdown;
import com.wulfnet.enchanting2.cost.PricingTable;
import com.wulfnet.enchanting2.enchantment.ConflictFamily;
import com.wulfnet.enchanting2.table.TableCalculator;
import com.wulfnet.enchanting2.table.TableOutcome;
import com.wulfnet.enchanting2.table.TableOutcome.Status;
import com.wulfnet.enchanting2.table.TablePool;
import com.wulfnet.enchanting2.table.TableRow;
import com.wulfnet.enchanting2.table.TableSelection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * DESIGN §6 enchanting table rows, picks and prices from {@link TableCalculator} alone. The menu wiring is
 * {@code TableGameTest}'s job.
 */
public class TableCalculatorGameTest {
	private static final String SHARPNESS = "minecraft:sharpness";
	private static final String EFFICIENCY = "minecraft:efficiency";
	private static final String UNBREAKING = "minecraft:unbreaking";
	private static final String MENDING = "minecraft:mending";
	private static final String FORTUNE = "minecraft:fortune";
	private static final String SILK_TOUCH = "minecraft:silk_touch";
	private static final String FROST_WALKER = "minecraft:frost_walker";
	private static final String VANISHING = "minecraft:vanishing_curse";
	private static final int FULL = 15;
	private static final int PLENTY = 1000;

	// --- helpers ---

	private static Registry<Enchantment> registry(GameTestHelper helper) {
		return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
	}

	private static Holder<Enchantment> get(GameTestHelper helper, ResourceKey<Enchantment> key) {
		return registry(helper).getOrThrow(key);
	}

	private static ItemStack with(GameTestHelper helper, Item item, Map<ResourceKey<Enchantment>, Integer> levels) {
		ItemStack stack = new ItemStack(item);
		ItemEnchantments.Mutable m = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		levels.forEach((key, level) -> m.set(get(helper, key), level));
		EnchantmentHelper.setEnchantments(stack, m.toImmutable());
		return stack;
	}

	private static int level(GameTestHelper helper, ItemStack stack, ResourceKey<Enchantment> key) {
		return EnchantmentHelper.getEnchantmentsForCrafting(stack).getLevel(get(helper, key));
	}

	private static final AcquisitionRules NO_OVERRIDES = new AcquisitionRules(EnchantmentOverrides.defaults());

	private static List<Holder<Enchantment>> pool(GameTestHelper helper) {
		return TablePool.members(registry(helper), NO_OVERRIDES);
	}

	private static TableSelection pick(Object... idLevel) {
		TableSelection selection = TableSelection.EMPTY;
		for (int i = 0; i < idLevel.length; i += 2) {
			selection = selection.with((String) idLevel[i], (Integer) idLevel[i + 1]);
		}
		return selection;
	}

	private static TableOutcome compute(GameTestHelper helper, ItemStack target, int shelves, TableSelection selection) {
		return compute(helper, target, 64, shelves, selection, PLENTY, false, CommonConfig.defaults());
	}

	private static TableOutcome compute(GameTestHelper helper, ItemStack target, int lapis, int shelves,
			TableSelection selection, int levels, boolean creative, CommonConfig config) {
		return TableCalculator.compute(target, lapis, shelves, selection, levels, creative, config,
				PricingTable.builtIn(), pool(helper), NO_OVERRIDES);
	}

	/** Default config, with {@code rules} filtering the pool and deciding treasure (Phase 9). */
	private static TableOutcome compute(GameTestHelper helper, ItemStack target, int shelves, TableSelection selection,
			AcquisitionRules rules) {
		return TableCalculator.compute(target, 64, shelves, selection, PLENTY, false, CommonConfig.defaults(),
				PricingTable.builtIn(), TablePool.members(registry(helper), rules), rules);
	}

	private static AcquisitionRules rules(Map<String, AcquisitionSettings> settings) {
		Map<String, EnchantmentOverrides.Entry> entries = new HashMap<>();
		settings.forEach((id, a) -> entries.put(id, new EnchantmentOverrides.Entry(a)));
		return new AcquisitionRules(new EnchantmentOverrides(entries));
	}

	private static AcquisitionSettings acquisition(Optional<Boolean> enabled, Optional<Boolean> table,
			Optional<Boolean> treasure) {
		return new AcquisitionSettings(enabled, table, Optional.empty(), Optional.empty(), OptionalInt.empty(),
				OptionalInt.empty(), treasure);
	}

	private static CommonConfig config(AllowTogetherSection allowTogether, CostsSection costs, TableSection table) {
		CommonConfig d = CommonConfig.defaults();
		return new CommonConfig(d.experience(), allowTogether, costs, d.curses(), d.anvil(), table, d.fortune(), d.beheading(), d.smelting());
	}

	private static CostsSection maximum(int maximum, boolean creativeFree) {
		CostsSection d = CostsSection.DEFAULTS;
		return new CostsSection(d.renameCost(), d.repairCostPerMaterial(), d.repairCombineCost(), maximum, creativeFree);
	}

	private static TableRow row(TableOutcome outcome, String id) {
		return outcome.rows().stream().filter(r -> r.id().equals(id)).findFirst()
				.orElseThrow(() -> new AssertionError("no row " + id + " in " + outcome.rows()));
	}

	private static List<String> ids(TableOutcome outcome) {
		return outcome.rows().stream().map(TableRow::id).toList();
	}

	private static void check(GameTestHelper helper, boolean condition, String what) {
		helper.assertTrue(condition, Component.literal(what));
	}

	private static <T> void equal(GameTestHelper helper, T expected, T actual, String what) {
		helper.assertValueEqual(expected, actual, Component.literal(what));
	}

	/** Refused: the named pick and reason, no price, no result. */
	private static void invalid(GameTestHelper helper, TableOutcome outcome, String id, TableRow.Status reason,
			String what) {
		equal(helper, Status.INVALID_PICK, outcome.status(), what + ": status");
		equal(helper, id, outcome.invalid().orElseThrow().id(), what + ": the blocking pick");
		equal(helper, reason, outcome.invalid().orElseThrow().reason(), what + ": reason");
		check(helper, outcome.result().isEmpty(), what + ": no result");
		equal(helper, 0, outcome.levelsCharged(), what + ": nothing charged");
	}

	// --- pool (7A.3) ---

	@GameTest
	public void poolIsTheVanillaTablePlusTheOwnersFour(GameTestHelper helper) {
		List<String> pool = pool(helper).stream().map(Holder::getRegisteredName).toList();
		for (String id : List.of(MENDING, FROST_WALKER, "minecraft:binding_curse", VANISHING)) {
			check(helper, pool.contains(id), id + " is offered");
		}
		registry(helper).get(EnchantmentTags.IN_ENCHANTING_TABLE).orElseThrow().forEach(
				h -> check(helper, pool.contains(h.getRegisteredName()), h.getRegisteredName() + " is offered"));
		for (String id : List.of("minecraft:soul_speed", "minecraft:swift_sneak", "minecraft:wind_burst")) {
			check(helper, !pool.contains(id), id + " stays loot-only");
		}
		check(helper, !pool.contains("e2test:third_party"), "a third-party enchantment outside the tags isn't offered");
		equal(helper, pool.stream().sorted().toList(), pool, "members in ID order");
		helper.succeed();
	}

	// --- rows ---

	/** Applicability is {@code supported_items}, checked here against vanilla's own {@code isSupportedItem}. */
	@GameTest
	public void swordRowsAreEveryOfferedEnchantmentItSupports(GameTestHelper helper) {
		ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
		TableOutcome outcome = compute(helper, sword, FULL, TableSelection.EMPTY);
		equal(helper, Status.NO_SELECTION, outcome.status(), "status before a pick");
		List<String> expected = pool(helper).stream().filter(h -> h.value().isSupportedItem(sword))
				.map(Holder::getRegisteredName).toList();
		equal(helper, expected, ids(outcome), "rows");
		check(helper, ids(outcome).containsAll(List.of(SHARPNESS, UNBREAKING, MENDING, VANISHING)), "sword basics");
		check(helper, !ids(outcome).contains(EFFICIENCY), "no Efficiency on a sword");
		helper.succeed();
	}

	@GameTest
	public void plainBookListsTheWholePool(GameTestHelper helper) {
		TableOutcome outcome = compute(helper, new ItemStack(Items.BOOK), FULL, TableSelection.EMPTY);
		equal(helper, pool(helper).stream().map(Holder::getRegisteredName).toList(), ids(outcome), "rows");
		helper.succeed();
	}

	@GameTest
	public void nothingToWorkOn(GameTestHelper helper) {
		equal(helper, Status.NONE, compute(helper, ItemStack.EMPTY, FULL, pick(SHARPNESS, 1)).status(), "empty");
		equal(helper, Status.NONE, compute(helper, new ItemStack(Items.DIAMOND_SWORD, 2), FULL, pick(SHARPNESS, 1))
				.status(), "a stack of two");
		// setEnchantments on a plain book writes ENCHANTMENTS, as a command would.
		ItemStack commandBook = with(helper, Items.BOOK, Map.of(Enchantments.SHARPNESS, 1));
		equal(helper, Status.NONE, compute(helper, commandBook, FULL, pick(UNBREAKING, 1)).status(),
				"a plain book holding enchantments");
		TableOutcome stick = compute(helper, new ItemStack(Items.STICK), FULL, pick(SHARPNESS, 1));
		equal(helper, Status.NOTHING_TO_ENCHANT, stick.status(), "a stick");
		check(helper, stick.rows().isEmpty(), "a stick lists nothing");
		helper.succeed();
	}

	// --- power ---

	@GameTest
	public void shelvesGateTheLevel(GameTestHelper helper) {
		int[][] shelvesToMax = {{0, 1}, {4, 2}, {8, 3}, {12, 4}, {15, 5}, {32, 5}};
		for (int[] s : shelvesToMax) {
			TableOutcome outcome = compute(helper, new ItemStack(Items.DIAMOND_SWORD), s[0], TableSelection.EMPTY);
			equal(helper, s[1], row(outcome, SHARPNESS).maxSelectable(), s[0] + " shelves: Sharpness max");
			equal(helper, s[1], outcome.tier(), s[0] + " shelves: tier");
		}
		invalid(helper, compute(helper, new ItemStack(Items.DIAMOND_SWORD), 4, pick(SHARPNESS, 3)), SHARPNESS,
				TableRow.Status.NEEDS_POWER, "Sharpness III at 4 shelves");
		equal(helper, Status.OK, compute(helper, new ItemStack(Items.DIAMOND_SWORD), 8, pick(SHARPNESS, 3)).status(),
				"Sharpness III at 8 shelves");
		helper.succeed();
	}

	@GameTest
	public void treasureNeedsFullPowerAndCursesDont(GameTestHelper helper) {
		ItemStack boots = new ItemStack(Items.DIAMOND_BOOTS);
		TableOutcome weak = compute(helper, boots, 14, TableSelection.EMPTY);
		equal(helper, TableRow.Status.NEEDS_FULL_POWER, row(weak, MENDING).status(), "Mending at 14 shelves");
		equal(helper, TableRow.Status.NEEDS_FULL_POWER, row(weak, FROST_WALKER).status(), "Frost Walker at 14");
		equal(helper, TableRow.Status.OK, row(weak, VANISHING).status(), "Vanishing at 14");
		equal(helper, TableRow.Status.OK, row(compute(helper, boots, 0, TableSelection.EMPTY), "minecraft:binding_curse")
				.status(), "Binding at 0 shelves");
		invalid(helper, compute(helper, boots, 14, pick(MENDING, 1)), MENDING, TableRow.Status.NEEDS_FULL_POWER,
				"Mending picked at 14 shelves");
		TableOutcome full = compute(helper, boots, FULL, TableSelection.EMPTY);
		equal(helper, TableRow.Status.OK, row(full, MENDING).status(), "Mending at 15 shelves");
		equal(helper, TableRow.Status.OK, row(full, FROST_WALKER).status(), "Frost Walker at 15 shelves");

		CommonConfig ungated = config(AllowTogetherSection.DEFAULTS, CostsSection.DEFAULTS, new TableSection(false));
		TableOutcome off = compute(helper, boots, 64, 0, pick(MENDING, 1), PLENTY, false, ungated);
		equal(helper, Status.OK, off.status(), "Mending at 0 shelves with the toggle off");
		helper.succeed();
	}

	// --- prices (DESIGN §5 table) ---

	@GameTest
	public void mendingPlusEfficiencyFiveCostsSeventeenAndSeventeenLapis(GameTestHelper helper) {
		TableOutcome outcome = compute(helper, new ItemStack(Items.DIAMOND_PICKAXE), FULL,
				pick(MENDING, 1, EFFICIENCY, 5));
		equal(helper, Status.OK, outcome.status(), "status");
		equal(helper, 17, outcome.levelsCharged(), "levels");
		equal(helper, 17, outcome.lapisCharged(), "lapis");
		equal(helper, 5, level(helper, outcome.result(), Enchantments.EFFICIENCY), "Efficiency on the result");
		equal(helper, 1, level(helper, outcome.result(), Enchantments.MENDING), "Mending on the result");
		helper.succeed();
	}

	@GameTest
	public void upgradeChargesOnlyTheAddedLevels(GameTestHelper helper) {
		ItemStack pickaxe = with(helper, Items.DIAMOND_PICKAXE, Map.of(Enchantments.EFFICIENCY, 3));
		ItemStack before = pickaxe.copy();
		TableOutcome outcome = compute(helper, pickaxe, FULL, pick(EFFICIENCY, 5));
		equal(helper, 3, row(outcome, EFFICIENCY).currentLevel(), "current level");
		equal(helper, Status.OK, outcome.status(), "status");
		equal(helper, 2, outcome.levelsCharged(), "Efficiency III to V costs 2");
		equal(helper, 2, outcome.lapisCharged(), "lapis follows");
		equal(helper, 5, level(helper, outcome.result(), Enchantments.EFFICIENCY), "result level");
		check(helper, ItemStack.matches(before, pickaxe), "the input is never mutated");
		helper.succeed();
	}

	/** Order independence: Efficiency V, then Mending at a second visit, costs what both at once cost. */
	@GameTest
	public void mendingInTwoVisitsCostsTheSameAsOne(GameTestHelper helper) {
		TableOutcome first = compute(helper, new ItemStack(Items.DIAMOND_PICKAXE), FULL, pick(EFFICIENCY, 5));
		TableOutcome second = compute(helper, first.result(), FULL, pick(MENDING, 1));
		equal(helper, 5, first.levelsCharged(), "first visit");
		equal(helper, 12, second.levelsCharged(), "second visit: 2 + 2 x 5");
		TableOutcome once = compute(helper, new ItemStack(Items.DIAMOND_PICKAXE), FULL, pick(EFFICIENCY, 5, MENDING, 1));
		equal(helper, once.levelsCharged(), first.levelsCharged() + second.levelsCharged(), "same total");
		helper.succeed();
	}

	@GameTest
	public void curseIsAnUngatedDiscount(GameTestHelper helper) {
		TableOutcome outcome = compute(helper, new ItemStack(Items.DIAMOND_SWORD), FULL,
				pick(SHARPNESS, 5, UNBREAKING, 5, VANISHING, 1));
		equal(helper, Status.OK, outcome.status(), "status");
		equal(helper, 9, outcome.levelsCharged(), "ceil(10 x 0.9)");
		equal(helper, 9, outcome.lapisCharged(), "lapis follows the discounted cost");
		CostBreakdown cost = outcome.cost().orElseThrow();
		check(helper, cost.lines().stream().anyMatch(l -> l.kind() == CostBreakdown.Kind.CURSE_DISCOUNT
				&& l.amount() == -1), "a negative discount line of 1");
		equal(helper, 1, level(helper, outcome.result(), Enchantments.VANISHING_CURSE), "the curse is on the result");
		helper.succeed();
	}

	// --- books ---

	@GameTest
	public void plainBookBecomesAnEnchantedBookWithEveryPick(GameTestHelper helper) {
		TableOutcome outcome = compute(helper, new ItemStack(Items.BOOK), FULL, pick(SHARPNESS, 5, EFFICIENCY, 4));
		equal(helper, Status.OK, outcome.status(), "status");
		ItemStack book = outcome.result();
		check(helper, book.is(Items.ENCHANTED_BOOK), "an enchanted book");
		ItemEnchantments stored = book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
		equal(helper, 2, stored.size(), "two stored entries");
		equal(helper, 5, stored.getLevel(get(helper, Enchantments.SHARPNESS)), "Sharpness V stored");
		equal(helper, 4, stored.getLevel(get(helper, Enchantments.EFFICIENCY)), "Efficiency IV stored");
		check(helper, book.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty(),
				"nothing in the item enchantments component");
		equal(helper, 9, outcome.levelsCharged(), "5 + 4");
		helper.succeed();
	}

	@GameTest
	public void enchantedBookGainsEntries(GameTestHelper helper) {
		ItemStack book = with(helper, Items.ENCHANTED_BOOK, Map.of(Enchantments.UNBREAKING, 2));
		TableOutcome outcome = compute(helper, book, FULL, pick(UNBREAKING, 3, SHARPNESS, 1));
		equal(helper, Status.OK, outcome.status(), "status");
		equal(helper, 3, level(helper, outcome.result(), Enchantments.UNBREAKING), "Unbreaking raised");
		equal(helper, 1, level(helper, outcome.result(), Enchantments.SHARPNESS), "Sharpness added");
		equal(helper, 2, outcome.levelsCharged(), "1 + 1");
		helper.succeed();
	}

	// --- refusals ---

	@GameTest
	public void upgradeBeyondThePowerIsRefusedAndTheItemKept(GameTestHelper helper) {
		ItemStack pickaxe = with(helper, Items.DIAMOND_PICKAXE, Map.of(Enchantments.EFFICIENCY, 4));
		TableOutcome outcome = compute(helper, pickaxe, 4, TableSelection.EMPTY);
		equal(helper, TableRow.Status.NEEDS_POWER, row(outcome, EFFICIENCY).status(), "row at tier 2");
		equal(helper, 4, row(outcome, EFFICIENCY).currentLevel(), "current level shown");
		invalid(helper, compute(helper, pickaxe, 4, pick(EFFICIENCY, 5)), EFFICIENCY, TableRow.Status.NEEDS_POWER,
				"Efficiency V at tier 2");
		equal(helper, 4, level(helper, pickaxe, Enchantments.EFFICIENCY), "the item keeps Efficiency IV");
		ItemStack maxed = with(helper, Items.DIAMOND_PICKAXE, Map.of(Enchantments.EFFICIENCY, 5));
		equal(helper, TableRow.Status.MAXED, row(compute(helper, maxed, FULL, TableSelection.EMPTY), EFFICIENCY)
				.status(), "Efficiency V is maxed");
		helper.succeed();
	}

	/** Only a forged packet could send these; each one blocks the whole selection, valid picks included. */
	@GameTest
	public void forgedPicksAreRefused(GameTestHelper helper) {
		ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
		invalid(helper, compute(helper, sword, FULL, pick(EFFICIENCY, 1, SHARPNESS, 5)), EFFICIENCY,
				TableRow.Status.NOT_OFFERED, "Efficiency on a sword");
		invalid(helper, compute(helper, new ItemStack(Items.DIAMOND_BOOTS), FULL, pick("minecraft:soul_speed", 1)),
				"minecraft:soul_speed", TableRow.Status.NOT_OFFERED, "Soul Speed isn't offered");
		invalid(helper, compute(helper, sword, FULL, pick("e2test:nope", 1)), "e2test:nope",
				TableRow.Status.NOT_OFFERED, "an unknown ID");
		invalid(helper, compute(helper, sword, FULL, pick(SHARPNESS, 6)), SHARPNESS, TableRow.Status.ABOVE_MAX,
				"Sharpness VI");
		ItemStack pickaxe = with(helper, Items.DIAMOND_PICKAXE, Map.of(Enchantments.EFFICIENCY, 3));
		invalid(helper, compute(helper, pickaxe, FULL, pick(EFFICIENCY, 3)), EFFICIENCY,
				TableRow.Status.ALREADY_AT_LEVEL, "Efficiency III onto Efficiency III");
		invalid(helper, compute(helper, pickaxe, FULL, pick(EFFICIENCY, 2)), EFFICIENCY,
				TableRow.Status.ALREADY_AT_LEVEL, "a lower level");
		helper.succeed();
	}

	@GameTest
	public void conflictIsShownBeforeAndAfterThePick(GameTestHelper helper) {
		Set<ConflictFamily> allowed = EnumSet.allOf(ConflictFamily.class);
		allowed.remove(ConflictFamily.SILK_TOUCH_FORTUNE);
		CommonConfig exclusive = config(new AllowTogetherSection(allowed), CostsSection.DEFAULTS, TableSection.DEFAULTS);
		ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);

		TableOutcome fortune = compute(helper, pickaxe, 64, FULL, pick(FORTUNE, 3), PLENTY, false, exclusive);
		equal(helper, Status.OK, fortune.status(), "Fortune alone");
		TableRow silk = row(fortune, SILK_TOUCH);
		equal(helper, TableRow.Status.CONFLICT, silk.status(), "unpicked Silk Touch shows the conflict");
		equal(helper, Optional.of(FORTUNE), silk.conflictsWith(), "with Fortune");

		TableOutcome both = compute(helper, pickaxe, 64, FULL, pick(FORTUNE, 3, SILK_TOUCH, 1), PLENTY, false,
				exclusive);
		invalid(helper, both, SILK_TOUCH, TableRow.Status.CONFLICT, "both picked: the later ID is refused");
		equal(helper, Optional.of(FORTUNE), both.invalid().orElseThrow().conflictsWith(), "names Fortune");
		equal(helper, Status.OK, compute(helper, pickaxe, FULL, pick(FORTUNE, 3, SILK_TOUCH, 1)).status(),
				"allowed together by default");
		helper.succeed();
	}

	@GameTest
	public void capLevelsAndLapis(GameTestHelper helper) {
		ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
		TableSelection seventeen = pick(MENDING, 1, EFFICIENCY, 5);
		CommonConfig capped = config(AllowTogetherSection.DEFAULTS, maximum(10, true), TableSection.DEFAULTS);
		TableOutcome tooMuch = compute(helper, pickaxe, 64, FULL, seventeen, PLENTY, false, capped);
		equal(helper, Status.TOO_EXPENSIVE, tooMuch.status(), "17 over a cap of 10");
		equal(helper, 17, tooMuch.cost().orElseThrow().total(), "the price is still shown");
		check(helper, tooMuch.result().isEmpty(), "no result");

		TableOutcome creative = compute(helper, pickaxe, 0, FULL, seventeen, 0, true, capped);
		equal(helper, Status.OK, creative.status(), "creative bypasses the cap");
		equal(helper, 0, creative.levelsCharged(), "creative_free: no levels");
		equal(helper, 0, creative.lapisCharged(), "creative_free: no lapis");

		CommonConfig paying = config(AllowTogetherSection.DEFAULTS, maximum(10, false), TableSection.DEFAULTS);
		equal(helper, 17, compute(helper, pickaxe, 64, FULL, seventeen, PLENTY, true, paying).levelsCharged(),
				"creative without creative_free pays, uncapped");

		CommonConfig d = CommonConfig.defaults();
		equal(helper, Status.INSUFFICIENT_LEVELS, compute(helper, pickaxe, 64, FULL, seventeen, 16, false, d).status(),
				"16 levels");
		equal(helper, Status.INSUFFICIENT_LAPIS, compute(helper, pickaxe, 16, FULL, seventeen, 17, false, d).status(),
				"16 lapis");
		equal(helper, Status.OK, compute(helper, pickaxe, 17, FULL, seventeen, 17, false, d).status(),
				"exactly 17 of each");
		helper.succeed();
	}

	/** Nothing on the item is lowered or removed, unknown third-party entries included. */
	@GameTest
	public void existingEntriesAreKept(GameTestHelper helper) {
		Holder<Enchantment> thirdParty = registry(helper).getOrThrow(ResourceKey.create(Registries.ENCHANTMENT,
				Identifier.fromNamespaceAndPath("e2test", "third_party")));
		ItemStack pickaxe = with(helper, Items.DIAMOND_PICKAXE, Map.of(Enchantments.UNBREAKING, 4));
		EnchantmentHelper.updateEnchantments(pickaxe, m -> m.set(thirdParty, 1));
		TableOutcome outcome = compute(helper, pickaxe, FULL, pick(EFFICIENCY, 5));
		equal(helper, Status.OK, outcome.status(), "status");
		ItemEnchantments result = EnchantmentHelper.getEnchantmentsForCrafting(outcome.result());
		equal(helper, 1, result.getLevel(thirdParty), "third-party entry kept");
		equal(helper, 4, result.getLevel(get(helper, Enchantments.UNBREAKING)), "Unbreaking kept");
		equal(helper, 5, result.getLevel(get(helper, Enchantments.EFFICIENCY)), "Efficiency added");
		equal(helper, 3, result.size(), "nothing else");
		equal(helper, 5, outcome.levelsCharged(), "only the new entry is charged");
		helper.succeed();
	}

	// --- acquisition (9B.6) ---

	@GameTest
	public void tableFlagAndDisabledTakeEnchantmentsOutOfThePool(GameTestHelper helper) {
		Optional<Boolean> no = Optional.of(false);
		Optional<Boolean> unset = Optional.empty();
		AcquisitionRules rules = rules(Map.of(SHARPNESS, acquisition(unset, no, unset),
				UNBREAKING, acquisition(no, unset, unset),
				"minecraft:smite", acquisition(unset, Optional.of(true), unset)));
		List<String> rows = ids(compute(helper, new ItemStack(Items.DIAMOND_SWORD), FULL, TableSelection.EMPTY, rules));
		check(helper, !rows.contains(SHARPNESS), "table: false Sharpness has no row: " + rows);
		check(helper, !rows.contains(UNBREAKING), "enabled: false Unbreaking has no row: " + rows);
		check(helper, rows.contains("minecraft:smite"), "table: true Smite keeps its row: " + rows);
		List<String> pool = TablePool.members(registry(helper), rules).stream().map(Holder::getRegisteredName).toList();
		check(helper, !pool.contains(SHARPNESS) && !pool.contains(UNBREAKING), "pool without both: " + pool);
		equal(helper, pool(helper).size() - 2, pool.size(), "exactly two left the pool");
		equal(helper, Status.INVALID_PICK, compute(helper, new ItemStack(Items.DIAMOND_SWORD), FULL,
				pick(SHARPNESS, 1), rules).status(), "a stale pick of a removed enchantment enchants nothing");
		helper.succeed();
	}

	@GameTest
	public void treasureOverrideMovesTheFullPowerGate(GameTestHelper helper) {
		Optional<Boolean> unset = Optional.empty();
		AcquisitionRules rules = rules(Map.of(MENDING, acquisition(unset, unset, Optional.of(false)),
				SHARPNESS, acquisition(unset, unset, Optional.of(true)),
				VANISHING, acquisition(unset, unset, Optional.of(true))));
		equal(helper, TableRow.Status.OK, row(compute(helper, new ItemStack(Items.DIAMOND_BOOTS), 0,
				TableSelection.EMPTY, rules), MENDING).status(), "treasure: false Mending at 0 shelves");
		ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
		equal(helper, TableRow.Status.NEEDS_FULL_POWER, row(compute(helper, sword, FULL - 1, TableSelection.EMPTY,
				rules), SHARPNESS).status(), "treasure: true Sharpness at 14 shelves");
		equal(helper, TableRow.Status.OK, row(compute(helper, sword, FULL, TableSelection.EMPTY, rules), SHARPNESS)
				.status(), "treasure: true Sharpness at 15 shelves");
		equal(helper, TableRow.Status.OK, row(compute(helper, sword, 0, TableSelection.EMPTY, rules), VANISHING)
				.status(), "a curse is never gated, even marked treasure");
		helper.succeed();
	}
}
