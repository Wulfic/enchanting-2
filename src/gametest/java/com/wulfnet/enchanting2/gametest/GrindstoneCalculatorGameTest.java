package com.wulfnet.enchanting2.gametest;

import com.wulfnet.enchanting2.config.CommonConfig;
import com.wulfnet.enchanting2.config.CostsSection;
import com.wulfnet.enchanting2.config.CursesSection;
import com.wulfnet.enchanting2.cost.PricingTable;
import com.wulfnet.enchanting2.grindstone.GrindstoneAction;
import com.wulfnet.enchanting2.grindstone.GrindstoneCalculator;
import com.wulfnet.enchanting2.grindstone.GrindstoneChoice;
import com.wulfnet.enchanting2.grindstone.GrindstoneOption;
import com.wulfnet.enchanting2.grindstone.GrindstoneOutcome;
import com.wulfnet.enchanting2.grindstone.GrindstoneOutcome.Status;
import com.wulfnet.enchanting2.mixin.GrindstoneMenuInvoker;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BinaryOperator;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * DESIGN §6 grindstone rows and results from {@link GrindstoneCalculator} alone, with vanilla's own
 * {@code computeResult} for Disenchant and Repair. The menu wiring is {@code GrindstoneGameTest}'s job.
 */
public class GrindstoneCalculatorGameTest {
	private static final String EFFICIENCY = "minecraft:efficiency";
	private static final String MENDING = "minecraft:mending";
	private static final String UNBREAKING = "minecraft:unbreaking";
	private static final String SHARPNESS = "minecraft:sharpness";
	private static final String LOOTING = "minecraft:looting";
	private static final String VANISHING = "minecraft:vanishing_curse";

	// --- helpers ---

	private static Holder<Enchantment> get(GameTestHelper helper, ResourceKey<Enchantment> key) {
		return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
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

	private static int entryCount(ItemStack stack) {
		return EnchantmentHelper.getEnchantmentsForCrafting(stack).size();
	}

	private static BinaryOperator<ItemStack> vanilla(GameTestHelper helper) {
		GrindstoneMenu menu = new GrindstoneMenu(1, helper.makeMockServerPlayerInLevel().getInventory());
		return ((GrindstoneMenuInvoker) menu)::enchanting2$computeResult;
	}

	private static GrindstoneOutcome compute(GameTestHelper helper, ItemStack slot0, ItemStack slot1,
			GrindstoneChoice choice) {
		return compute(helper, slot0, slot1, Optional.ofNullable(choice), false, CommonConfig.defaults());
	}

	private static GrindstoneOutcome compute(GameTestHelper helper, ItemStack slot0, ItemStack slot1,
			Optional<GrindstoneChoice> choice, boolean creative, CommonConfig config) {
		return GrindstoneCalculator.compute(slot0, slot1, choice, creative, config, PricingTable.builtIn(),
				vanilla(helper));
	}

	private static CommonConfig costs(CostsSection costs) {
		CommonConfig d = CommonConfig.defaults();
		return new CommonConfig(d.experience(), d.allowTogether(), costs, d.curses(), d.anvil(), d.table(),
				d.fortune(), d.beheading(), d.smelting());
	}

	private static CostsSection maximum(int maximum, boolean creativeFree) {
		CostsSection d = CostsSection.DEFAULTS;
		return new CostsSection(d.renameCost(), d.repairCostPerMaterial(), d.repairCombineCost(), maximum, creativeFree);
	}

	private static GrindstoneChoice extract(String id) {
		return GrindstoneChoice.of(GrindstoneAction.EXTRACT, id);
	}

	private static GrindstoneChoice cleanse(String id) {
		return GrindstoneChoice.of(GrindstoneAction.CLEANSE, id);
	}

	private static GrindstoneOption row(GrindstoneOutcome outcome, GrindstoneChoice choice) {
		return outcome.options().stream().filter(o -> o.choice().equals(choice)).findFirst()
				.orElseThrow(() -> new AssertionError("no row " + choice + " in " + outcome.options()));
	}

	private static void check(GameTestHelper helper, boolean condition, String what) {
		helper.assertTrue(condition, Component.literal(what));
	}

	private static <T> void equal(GameTestHelper helper, T expected, T actual, String what) {
		helper.assertValueEqual(expected, actual, Component.literal(what));
	}

	private static ItemStack pickaxe(GameTestHelper helper) {
		return with(helper, Items.DIAMOND_PICKAXE, Map.of(Enchantments.EFFICIENCY, 5, Enchantments.UNBREAKING, 3,
				Enchantments.MENDING, 1));
	}

	// --- Extract ---

	@GameTest
	public void extractListsOneRowPerEntryAndWaitsForAPick(GameTestHelper helper) {
		GrindstoneOutcome outcome = compute(helper, pickaxe(helper), new ItemStack(Items.BOOK, 3), null);
		equal(helper, Status.NO_CHOICE, outcome.status(), "status before a pick");
		check(helper, outcome.result().isEmpty(), "no result before a pick");
		equal(helper, List.of(extract(EFFICIENCY), extract(MENDING), extract(UNBREAKING)),
				outcome.options().stream().map(GrindstoneOption::choice).toList(), "rows in ID order");
		equal(helper, 5, row(outcome, extract(EFFICIENCY)).cost(), "Efficiency V costs 5");
		equal(helper, 2, row(outcome, extract(MENDING)).cost(), "Mending costs 2, no surcharge");
		equal(helper, 3, row(outcome, extract(UNBREAKING)).cost(), "Unbreaking III costs 3");
		helper.succeed();
	}

	/** Moved, not copied: the book has it, the pickaxe doesn't, everything else stays, one book is used. */
	@GameTest
	public void extractMovesTheEntryOntoOneBook(GameTestHelper helper) {
		ItemStack pickaxe = pickaxe(helper);
		ItemStack books = new ItemStack(Items.BOOK, 3);
		GrindstoneOutcome outcome = compute(helper, pickaxe, books, extract(EFFICIENCY));
		equal(helper, Status.OK, outcome.status(), "status");
		equal(helper, 5, outcome.levelsCharged(), "levels charged");

		check(helper, outcome.result().is(Items.ENCHANTED_BOOK), "result is an enchanted book");
		equal(helper, 5, level(helper, outcome.result(), Enchantments.EFFICIENCY), "Efficiency V on the book");
		equal(helper, 1, entryCount(outcome.result()), "only Efficiency on the book");

		ItemStack source = outcome.slot0After();
		check(helper, source.is(Items.DIAMOND_PICKAXE), "the pickaxe stays in its slot");
		equal(helper, 0, level(helper, source, Enchantments.EFFICIENCY), "Efficiency gone from the pickaxe");
		equal(helper, 3, level(helper, source, Enchantments.UNBREAKING), "Unbreaking stays");
		equal(helper, 1, level(helper, source, Enchantments.MENDING), "Mending stays");
		equal(helper, 2, outcome.slot1After().getCount(), "one book used");
		check(helper, outcome.slot1After().is(Items.BOOK), "the rest are plain books");

		equal(helper, 5, level(helper, pickaxe, Enchantments.EFFICIENCY), "input pickaxe not mutated");
		equal(helper, 3, books.getCount(), "input books not mutated");
		helper.succeed();
	}

	@GameTest
	public void extractWorksWithTheBookOnTop(GameTestHelper helper) {
		GrindstoneOutcome outcome = compute(helper, new ItemStack(Items.BOOK), pickaxe(helper), extract(UNBREAKING));
		equal(helper, Status.OK, outcome.status(), "status");
		check(helper, outcome.slot0After().isEmpty(), "the only book was used");
		equal(helper, 0, level(helper, outcome.slot1After(), Enchantments.UNBREAKING), "Unbreaking gone from source");
		equal(helper, 5, level(helper, outcome.slot1After(), Enchantments.EFFICIENCY), "Efficiency stays");
		equal(helper, 3, level(helper, outcome.result(), Enchantments.UNBREAKING), "Unbreaking III on the book");
		helper.succeed();
	}

	@GameTest
	public void extractSplitsAMultiEnchantmentBook(GameTestHelper helper) {
		ItemStack source = with(helper, Items.ENCHANTED_BOOK, Map.of(Enchantments.SHARPNESS, 5,
				Enchantments.LOOTING, 3));
		GrindstoneOutcome outcome = compute(helper, source, new ItemStack(Items.BOOK), extract(LOOTING));
		equal(helper, Status.OK, outcome.status(), "status");
		equal(helper, 3, level(helper, outcome.result(), Enchantments.LOOTING), "Looting III on the new book");
		equal(helper, 1, entryCount(outcome.result()), "only Looting on the new book");
		check(helper, outcome.slot0After().is(Items.ENCHANTED_BOOK), "the source stays an enchanted book");
		equal(helper, 5, level(helper, outcome.slot0After(), Enchantments.SHARPNESS), "Sharpness stays");
		equal(helper, 1, entryCount(outcome.slot0After()), "only Sharpness stays");
		helper.succeed();
	}

	@GameTest
	public void extractRefusesABookHoldingOnlyThatEntry(GameTestHelper helper) {
		ItemStack source = with(helper, Items.ENCHANTED_BOOK, Map.of(Enchantments.SHARPNESS, 5));
		GrindstoneOutcome outcome = compute(helper, source, new ItemStack(Items.BOOK), extract(SHARPNESS));
		equal(helper, Status.NOTHING_TO_SPLIT, row(outcome, extract(SHARPNESS)).status(), "row status");
		equal(helper, Status.NOTHING_TO_SPLIT, outcome.status(), "outcome status");
		check(helper, outcome.result().isEmpty(), "no result");
		equal(helper, 0, outcome.levelsCharged(), "nothing charged");
		helper.succeed();
	}

	/** Splitting a curse off a book is a real split: the clean entry leaves, the curse stays behind. */
	@GameTest
	public void extractSplitsAnEntryFromACursedBook(GameTestHelper helper) {
		ItemStack source = with(helper, Items.ENCHANTED_BOOK, Map.of(Enchantments.SHARPNESS, 5,
				Enchantments.VANISHING_CURSE, 1));
		GrindstoneOutcome outcome = compute(helper, source, new ItemStack(Items.BOOK), extract(SHARPNESS));
		equal(helper, Status.OK, outcome.status(), "status");
		equal(helper, 1, entryCount(outcome.result()), "a clean Sharpness book");
		equal(helper, 1, level(helper, outcome.slot0After(), Enchantments.VANISHING_CURSE), "the curse stays");
		equal(helper, 1, entryCount(outcome.slot0After()), "only the curse stays");
		helper.succeed();
	}

	@GameTest
	public void extractRefusesCurses(GameTestHelper helper) {
		ItemStack sword = with(helper, Items.DIAMOND_SWORD, Map.of(Enchantments.SHARPNESS, 5,
				Enchantments.VANISHING_CURSE, 1));
		GrindstoneOutcome outcome = compute(helper, sword, new ItemStack(Items.BOOK), extract(VANISHING));
		equal(helper, Status.CURSE_NOT_EXTRACTABLE, row(outcome, extract(VANISHING)).status(), "curse row");
		equal(helper, Status.CURSE_NOT_EXTRACTABLE, outcome.status(), "outcome");
		check(helper, outcome.result().isEmpty(), "no result");
		equal(helper, Status.OK, row(outcome, extract(SHARPNESS)).status(), "Sharpness row still OK");
		helper.succeed();
	}

	/** A lowered max level never touches existing items, so the entry moves at the level it has. */
	@GameTest
	public void extractKeepsALevelAboveTheMaximum(GameTestHelper helper) {
		ItemStack pickaxe = with(helper, Items.DIAMOND_PICKAXE, Map.of(Enchantments.EFFICIENCY, 10,
				Enchantments.UNBREAKING, 1));
		GrindstoneOutcome outcome = compute(helper, pickaxe, new ItemStack(Items.BOOK), extract(EFFICIENCY));
		equal(helper, 10, level(helper, outcome.result(), Enchantments.EFFICIENCY), "Efficiency X on the book");
		equal(helper, 10, outcome.levelsCharged(), "priced at its level");
		helper.succeed();
	}

	// --- Cleanse and Disenchant ---

	@GameTest
	public void singleItemOffersDisenchantAndOneCleansePerCurse(GameTestHelper helper) {
		ItemStack sword = with(helper, Items.DIAMOND_SWORD, Map.of(Enchantments.SHARPNESS, 5,
				Enchantments.UNBREAKING, 3, Enchantments.VANISHING_CURSE, 1));
		GrindstoneOutcome outcome = compute(helper, sword, ItemStack.EMPTY, null);
		equal(helper, Status.NO_CHOICE, outcome.status(), "status before a pick");
		equal(helper, List.of(cleanse(VANISHING), GrindstoneChoice.of(GrindstoneAction.DISENCHANT)),
				outcome.options().stream().map(GrindstoneOption::choice).toList(), "rows in action order");
		helper.succeed();
	}

	/** DESIGN §5a: max(removal_min_cost, ceil(own % × undiscounted cost of the non-curse enchantments)). */
	@GameTest
	public void cleanseCostsTheMinimumOrTheRepaidDiscount(GameTestHelper helper) {
		ItemStack sword = with(helper, Items.DIAMOND_SWORD, Map.of(Enchantments.SHARPNESS, 5,
				Enchantments.UNBREAKING, 3, Enchantments.VANISHING_CURSE, 1));
		GrindstoneOutcome outcome = compute(helper, sword, ItemStack.EMPTY, cleanse(VANISHING));
		equal(helper, Status.OK, outcome.status(), "status");
		equal(helper, 30, outcome.levelsCharged(), "max(30, ceil(10% × 8)) = 30");

		CommonConfig d = CommonConfig.defaults();
		CommonConfig noMinimum = new CommonConfig(d.experience(), d.allowTogether(), d.costs(),
				new CursesSection(d.curses().maxDiscountPercent(), 0), d.anvil(), d.table(), d.fortune(), d.beheading(), d.smelting());
		GrindstoneOutcome repaid = compute(helper, sword, ItemStack.EMPTY, Optional.of(cleanse(VANISHING)), false,
				noMinimum);
		equal(helper, 1, repaid.levelsCharged(), "ceil(10% × 8) = 1 with no minimum");
		helper.succeed();
	}

	@GameTest
	public void cleanseRemovesOnlyThatCurse(GameTestHelper helper) {
		ItemStack chestplate = with(helper, Items.DIAMOND_CHESTPLATE, Map.of(Enchantments.PROTECTION, 4,
				Enchantments.VANISHING_CURSE, 1, Enchantments.BINDING_CURSE, 1));
		GrindstoneOutcome outcome = compute(helper, ItemStack.EMPTY, chestplate, cleanse(VANISHING));
		equal(helper, Status.OK, outcome.status(), "status");
		equal(helper, 0, level(helper, outcome.result(), Enchantments.VANISHING_CURSE), "Vanishing removed");
		equal(helper, 1, level(helper, outcome.result(), Enchantments.BINDING_CURSE), "Binding stays");
		equal(helper, 4, level(helper, outcome.result(), Enchantments.PROTECTION), "Protection stays");
		check(helper, outcome.slot0After().isEmpty() && outcome.slot1After().isEmpty(), "the source is consumed");
		equal(helper, 1, level(helper, chestplate, Enchantments.VANISHING_CURSE), "input not mutated");
		helper.succeed();
	}

	@GameTest
	public void cleanseOfANonCurseIsNotAvailable(GameTestHelper helper) {
		ItemStack sword = with(helper, Items.DIAMOND_SWORD, Map.of(Enchantments.SHARPNESS, 5,
				Enchantments.VANISHING_CURSE, 1));
		GrindstoneOutcome outcome = compute(helper, sword, ItemStack.EMPTY, cleanse(SHARPNESS));
		equal(helper, Status.NOT_AVAILABLE, outcome.status(), "status");
		check(helper, outcome.result().isEmpty(), "no result");
		helper.succeed();
	}

	@GameTest
	public void disenchantIsVanillasResult(GameTestHelper helper) {
		ItemStack sword = with(helper, Items.DIAMOND_SWORD, Map.of(Enchantments.SHARPNESS, 5,
				Enchantments.VANISHING_CURSE, 1));
		GrindstoneChoice disenchant = GrindstoneChoice.of(GrindstoneAction.DISENCHANT);
		for (boolean top : new boolean[] {true, false}) {
			ItemStack slot0 = top ? sword : ItemStack.EMPTY;
			ItemStack slot1 = top ? ItemStack.EMPTY : sword;
			GrindstoneOutcome outcome = compute(helper, slot0, slot1, disenchant);
			equal(helper, Status.OK, outcome.status(), "status, sword on top " + top);
			ItemStack expected = vanilla(helper).apply(slot0, slot1);
			check(helper, ItemStack.matches(expected, outcome.result()), "vanilla's result, sword on top " + top
					+ ": expected " + expected.getComponents() + ", got " + outcome.result().getComponents());
			equal(helper, 0, outcome.levelsCharged(), "free, as in vanilla");
			check(helper, outcome.vanillaTake(), "vanilla's take runs");
		}
		helper.succeed();
	}

	@GameTest
	public void curseOnlyItemHasNoDisenchantRow(GameTestHelper helper) {
		ItemStack sword = with(helper, Items.DIAMOND_SWORD, Map.of(Enchantments.VANISHING_CURSE, 1));
		GrindstoneOutcome outcome = compute(helper, sword, ItemStack.EMPTY, null);
		equal(helper, List.of(cleanse(VANISHING)), outcome.options().stream().map(GrindstoneOption::choice).toList(),
				"only the Cleanse row");
		helper.succeed();
	}

	// --- Repair ---

	@GameTest
	public void repairIsVanillasCombine(GameTestHelper helper) {
		ItemStack a = with(helper, Items.DIAMOND_SWORD, Map.of(Enchantments.SHARPNESS, 5));
		a.setDamageValue(1000);
		ItemStack b = new ItemStack(Items.DIAMOND_SWORD);
		b.setDamageValue(1000);
		GrindstoneOutcome outcome = compute(helper, a, b, GrindstoneChoice.of(GrindstoneAction.REPAIR));
		equal(helper, Status.OK, outcome.status(), "status");
		check(helper, ItemStack.matches(vanilla(helper).apply(a, b), outcome.result()), "vanilla's merged sword");
		check(helper, outcome.vanillaTake(), "vanilla's take runs");
		helper.succeed();
	}

	@GameTest
	public void plainBooksNeverRepairOrDisenchant(GameTestHelper helper) {
		ItemStack damaged = new ItemStack(Items.DIAMOND_SWORD);
		damaged.setDamageValue(10);
		equal(helper, Status.NONE, compute(helper, new ItemStack(Items.BOOK), damaged, null).status(),
				"book + unenchanted sword");
		equal(helper, Status.NONE, compute(helper, new ItemStack(Items.BOOK), new ItemStack(Items.BOOK), null)
				.status(), "two plain books");
		equal(helper, Status.NONE, compute(helper, new ItemStack(Items.BOOK, 5), ItemStack.EMPTY, null).status(),
				"plain books alone");
		equal(helper, Status.NONE, compute(helper, ItemStack.EMPTY, ItemStack.EMPTY, extract(EFFICIENCY)).status(),
				"empty grindstone with a pick");
		helper.succeed();
	}

	// --- picks, costs, stacks ---

	@GameTest
	public void aPickThatIsNotARowIsNotAvailable(GameTestHelper helper) {
		ItemStack books = new ItemStack(Items.BOOK);
		equal(helper, Status.NOT_AVAILABLE, compute(helper, pickaxe(helper), books, extract(SHARPNESS)).status(),
				"an enchantment the pickaxe doesn't have");
		equal(helper, Status.NOT_AVAILABLE, compute(helper, pickaxe(helper), books,
				GrindstoneChoice.of(GrindstoneAction.DISENCHANT)).status(), "Disenchant with a book in");
		equal(helper, Status.NOT_AVAILABLE, compute(helper, pickaxe(helper), ItemStack.EMPTY, extract(EFFICIENCY))
				.status(), "Extract without a book");
		helper.succeed();
	}

	@GameTest
	public void maximumOperationCostCapsExtractAndCleanse(GameTestHelper helper) {
		CommonConfig capped = costs(maximum(4, true));
		ItemStack books = new ItemStack(Items.BOOK);
		GrindstoneOutcome outcome = compute(helper, pickaxe(helper), books, Optional.of(extract(EFFICIENCY)), false,
				capped);
		equal(helper, Status.TOO_EXPENSIVE, outcome.status(), "Efficiency V (5) above 4");
		check(helper, outcome.result().isEmpty(), "no result");
		equal(helper, Status.OK, row(outcome, extract(UNBREAKING)).status(), "Unbreaking III (3) fits");

		ItemStack cursed = with(helper, Items.DIAMOND_SWORD, Map.of(Enchantments.VANISHING_CURSE, 1));
		equal(helper, Status.TOO_EXPENSIVE, compute(helper, cursed, ItemStack.EMPTY,
				Optional.of(cleanse(VANISHING)), false, capped).status(), "Cleanse's minimum 30 above 4");

		GrindstoneOutcome creative = compute(helper, pickaxe(helper), books, Optional.of(extract(EFFICIENCY)), true,
				capped);
		equal(helper, Status.OK, creative.status(), "creative is never too expensive");
		helper.succeed();
	}

	@GameTest
	public void creativeFreeChargesNothingButShowsThePrice(GameTestHelper helper) {
		ItemStack books = new ItemStack(Items.BOOK);
		GrindstoneOutcome free = compute(helper, pickaxe(helper), books, Optional.of(extract(EFFICIENCY)), true,
				costs(maximum(CostsSection.NO_MAXIMUM, true)));
		equal(helper, 0, free.levelsCharged(), "creative_free: nothing charged");
		equal(helper, 5, row(free, extract(EFFICIENCY)).cost(), "the price is still shown");
		check(helper, row(free, extract(EFFICIENCY)).free(), "the row says free");

		GrindstoneOutcome paid = compute(helper, pickaxe(helper), books, Optional.of(extract(EFFICIENCY)), true,
				costs(maximum(CostsSection.NO_MAXIMUM, false)));
		equal(helper, 5, paid.levelsCharged(), "creative_free off: creative pays");
		helper.succeed();
	}

	@GameTest
	public void aStackedSourceIsRefused(GameTestHelper helper) {
		ItemStack stacked = with(helper, Items.STICK, Map.of(Enchantments.UNBREAKING, 1));
		stacked.setCount(2);
		equal(helper, Status.STACKED_ITEM, compute(helper, stacked, ItemStack.EMPTY, null).status(), "alone");
		equal(helper, Status.STACKED_ITEM, compute(helper, stacked, new ItemStack(Items.BOOK), extract(UNBREAKING))
				.status(), "with a book");
		helper.succeed();
	}
}
