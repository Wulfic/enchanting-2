package com.wulfnet.enchanting2.gametest;

import com.wulfnet.enchanting2.anvil.AnvilCalculator;
import com.wulfnet.enchanting2.anvil.AnvilOutcome;
import com.wulfnet.enchanting2.anvil.AnvilOutcome.Status;
import com.wulfnet.enchanting2.anvil.AnvilOutcomeHolder;
import com.wulfnet.enchanting2.anvil.InvalidBookMode;
import com.wulfnet.enchanting2.anvil.TransferRules.Fate;
import com.wulfnet.enchanting2.config.AllowTogetherSection;
import com.wulfnet.enchanting2.config.AnvilSection;
import com.wulfnet.enchanting2.config.CommonConfig;
import com.wulfnet.enchanting2.config.CostsSection;
import com.wulfnet.enchanting2.config.ServerConfigHolder;
import com.wulfnet.enchanting2.config.ServerConfigs;
import com.wulfnet.enchanting2.cost.PricingTable;
import com.wulfnet.enchanting2.enchantment.ConflictFamily;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * DESIGN §6 Anvil through a real {@code AnvilMenu} and a mock {@code ServerPlayer}: slots are set, the result is clicked,
 * and what the player holds, pays and gets back is checked. Tests that need another config swap the server's config
 * and restore it inside one synchronous call, so no other test can observe it.
 */
public class AnvilGameTest {
	private static final int RICH = 100_000;

	// --- helpers ---

	private static Holder<Enchantment> get(GameTestHelper helper, ResourceKey<Enchantment> key) {
		return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	/** {@code item} holding {@code levels}; an enchanted book stores them. */
	private static ItemStack with(GameTestHelper helper, Item item, Map<ResourceKey<Enchantment>, Integer> levels) {
		ItemStack stack = new ItemStack(item);
		ItemEnchantments.Mutable m = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		levels.forEach((key, level) -> m.set(get(helper, key), level));
		EnchantmentHelper.setEnchantments(stack, m.toImmutable());
		return stack;
	}

	static ItemStack book(GameTestHelper helper, Map<ResourceKey<Enchantment>, Integer> levels) {
		return with(helper, Items.ENCHANTED_BOOK, levels);
	}

	private static int level(GameTestHelper helper, ItemStack stack, ResourceKey<Enchantment> key) {
		return EnchantmentHelper.getEnchantmentsForCrafting(stack).getLevel(get(helper, key));
	}

	/**
	 * A survival-like player. The mock's {@code gameMode()} is hard-wired to CREATIVE, which sets {@code instabuild}, and
	 * the anvil's creative test ({@code hasInfiniteMaterials}) reads only {@code instabuild}, so clearing it is enough.
	 */
	static ServerPlayer player(GameTestHelper helper, int levels) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.getAbilities().instabuild = false;
		player.experienceLevel = levels;
		return player;
	}

	/** {@code ContainerLevelAccess.NULL}: no anvil block, so taking never damages or breaks one. */
	static AnvilMenu anvil(ServerPlayer player, ItemStack left, ItemStack right) {
		AnvilMenu menu = new AnvilMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
		menu.getSlot(AnvilMenu.INPUT_SLOT).set(left);
		menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(right);
		return menu;
	}

	static AnvilOutcome outcome(AnvilMenu menu) {
		AnvilOutcome outcome = ((AnvilOutcomeHolder) menu).enchanting2$outcome();
		if (outcome == null) {
			throw new IllegalStateException("the server-side menu has no outcome after its slots were set");
		}
		return outcome;
	}

	/** Clicks the result slot like a player would; returns what ended up on the cursor (empty if the take was refused). */
	private static ItemStack take(AnvilMenu menu, ServerPlayer player) {
		menu.clicked(AnvilMenu.RESULT_SLOT, 0, ClickType.PICKUP, player);
		ItemStack carried = menu.getCarried();
		menu.setCarried(ItemStack.EMPTY);
		return carried;
	}

	private static CommonConfig config(InvalidBookMode mode, AllowTogetherSection allowTogether, CostsSection costs) {
		CommonConfig d = CommonConfig.defaults();
		return new CommonConfig(d.experience(), allowTogether, costs, d.curses(), new AnvilSection(mode), d.table(),
				d.fortune(), d.beheading(), d.smelting());
	}

	private static CommonConfig mode(InvalidBookMode mode) {
		CommonConfig d = CommonConfig.defaults();
		return config(mode, d.allowTogether(), d.costs());
	}

	private static AllowTogetherSection silkFortuneExclusive() {
		return new AllowTogetherSection(EnumSet.complementOf(EnumSet.of(ConflictFamily.SILK_TOUCH_FORTUNE)));
	}

	/** Runs {@code body} with {@code config} on the server, then puts the previous one back, even if it throws. */
	private static void withConfig(GameTestHelper helper, CommonConfig config, Runnable body) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerConfigHolder holder = (ServerConfigHolder) server;
		CommonConfig previous = ServerConfigs.get(server);
		holder.enchanting2$setConfig(config);
		try {
			body.run();
		} finally {
			holder.enchanting2$setConfig(previous);
		}
	}

	private static AnvilOutcome compute(ItemStack left, ItemStack right, CommonConfig config) {
		return AnvilCalculator.compute(left, right, null, false, config, PricingTable.builtIn());
	}

	private static void check(GameTestHelper helper, boolean condition, String what) {
		helper.assertTrue(condition, Component.literal(what));
	}

	private static <T> void equal(GameTestHelper helper, T expected, T actual, String what) {
		helper.assertValueEqual(expected, actual, Component.literal(what));
	}

	// --- books ---

	/** Default mode: valid entries apply, the rest come back on the book (owner, 2026-10-07). */
	@GameTest
	public void bookApplyValidLeavesTheRestOnTheBook(GameTestHelper helper) {
		ServerPlayer player = player(helper, RICH);
		ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
		ItemStack book = book(helper, Map.of(Enchantments.EFFICIENCY, 5, Enchantments.SHARPNESS, 5,
				Enchantments.UNBREAKING, 3));
		AnvilMenu menu = anvil(player, pickaxe, book);
		equal(helper, Status.OK, outcome(menu).status(), "status");
		equal(helper, 8, menu.getCost(), "Efficiency V + Unbreaking III");

		ItemStack taken = take(menu, player);
		equal(helper, 5, level(helper, taken, Enchantments.EFFICIENCY), "Efficiency on the pickaxe");
		equal(helper, 3, level(helper, taken, Enchantments.UNBREAKING), "Unbreaking on the pickaxe");
		equal(helper, 0, level(helper, taken, Enchantments.SHARPNESS), "Sharpness on the pickaxe");
		equal(helper, RICH - 8, player.experienceLevel, "levels after the take");
		ItemStack leftover = menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem();
		check(helper, leftover.is(Items.ENCHANTED_BOOK), "a book is left in the right slot, got " + leftover);
		equal(helper, 5, level(helper, leftover, Enchantments.SHARPNESS), "Sharpness stays on the book");
		equal(helper, 1, EnchantmentHelper.getEnchantmentsForCrafting(leftover).size(), "only Sharpness stays");
		check(helper, menu.getSlot(AnvilMenu.INPUT_SLOT).getItem().isEmpty(), "left slot emptied");
		helper.succeed();
	}

	@GameTest
	public void bookFullyAppliedIsConsumed(GameTestHelper helper) {
		ServerPlayer player = player(helper, RICH);
		AnvilMenu menu = anvil(player, new ItemStack(Items.DIAMOND_PICKAXE),
				book(helper, Map.of(Enchantments.EFFICIENCY, 5)));
		ItemStack taken = take(menu, player);
		equal(helper, 5, level(helper, taken, Enchantments.EFFICIENCY), "Efficiency");
		check(helper, menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem().isEmpty(), "book consumed");
		helper.succeed();
	}

	/** A lower level of an ID the item already has higher is not applied, and under apply_valid stays on the book. */
	@GameTest
	public void bookDominatedEntryStaysOnTheBook(GameTestHelper helper) {
		ServerPlayer player = player(helper, RICH);
		AnvilMenu menu = anvil(player, with(helper, Items.DIAMOND_PICKAXE, Map.of(Enchantments.EFFICIENCY, 5)),
				book(helper, Map.of(Enchantments.EFFICIENCY, 3, Enchantments.UNBREAKING, 3)));
		equal(helper, 3, menu.getCost(), "only Unbreaking III is charged");
		ItemStack taken = take(menu, player);
		equal(helper, 5, level(helper, taken, Enchantments.EFFICIENCY), "Efficiency kept at V");
		ItemStack leftover = menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem();
		equal(helper, 3, level(helper, leftover, Enchantments.EFFICIENCY), "Efficiency III back on the book");
		helper.succeed();
	}

	@GameTest
	public void bookRejectAllRefusesOnAnyInvalidEntry(GameTestHelper helper) {
		ItemStack book = book(helper, Map.of(Enchantments.EFFICIENCY, 5, Enchantments.SHARPNESS, 5));
		AnvilOutcome outcome = compute(new ItemStack(Items.DIAMOND_PICKAXE), book, mode(InvalidBookMode.REJECT_ALL));
		equal(helper, Status.REFUSED, outcome.status(), "status");
		check(helper, outcome.result().isEmpty(), "no result");
		check(helper, outcome.entries().stream().anyMatch(e -> e.fate() == Fate.REFUSE
				&& e.id().equals("minecraft:sharpness")), "Sharpness named as the reason: " + outcome.entries());
		helper.succeed();
	}

	/** strip_invalid through the real menu: the book is consumed and Sharpness is gone, as the outcome said. */
	@GameTest
	public void bookStripInvalidConsumesTheBook(GameTestHelper helper) {
		ServerPlayer player = player(helper, RICH);
		withConfig(helper, mode(InvalidBookMode.STRIP_INVALID), () -> {
			AnvilMenu menu = anvil(player, new ItemStack(Items.DIAMOND_PICKAXE),
					book(helper, Map.of(Enchantments.EFFICIENCY, 5, Enchantments.SHARPNESS, 5)));
			check(helper, outcome(menu).entries().stream().anyMatch(e -> e.fate() == Fate.LOSE
					&& e.id().equals("minecraft:sharpness")), "Sharpness previewed as lost: " + outcome(menu).entries());
			ItemStack taken = take(menu, player);
			equal(helper, 5, level(helper, taken, Enchantments.EFFICIENCY), "Efficiency");
			check(helper, menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem().isEmpty(), "book consumed");
		});
		helper.succeed();
	}

	/** Books skip applicability (DESIGN §4), so two books merge whatever they hold. */
	@GameTest
	public void bookOntoBookMerges(GameTestHelper helper) {
		ServerPlayer player = player(helper, RICH);
		AnvilMenu menu = anvil(player, book(helper, Map.of(Enchantments.SHARPNESS, 3)),
				book(helper, Map.of(Enchantments.EFFICIENCY, 5)));
		ItemStack taken = take(menu, player);
		check(helper, taken.is(Items.ENCHANTED_BOOK), "result is a book");
		equal(helper, 3, level(helper, taken, Enchantments.SHARPNESS), "Sharpness");
		equal(helper, 5, level(helper, taken, Enchantments.EFFICIENCY), "Efficiency");
		helper.succeed();
	}

	/** A plain book is enchanted at the table (Phase 7.4); the anvil shows nothing, as vanilla does. */
	@GameTest
	public void plainBookOnTheLeftHasNoResult(GameTestHelper helper) {
		AnvilOutcome outcome = compute(new ItemStack(Items.BOOK), book(helper, Map.of(Enchantments.EFFICIENCY, 5)),
				CommonConfig.defaults());
		equal(helper, Status.NONE, outcome.status(), "status");
		helper.succeed();
	}

	// --- item + item ---

	@GameTest
	public void itemPlusItemRefusesALossUnlessStrip(GameTestHelper helper) {
		ItemStack silk = with(helper, Items.DIAMOND_PICKAXE, Map.of(Enchantments.SILK_TOUCH, 1,
				Enchantments.EFFICIENCY, 5));
		// Unbreaking applies, so strip_invalid has something to do; Efficiency II is dominated by V.
		ItemStack fortune = with(helper, Items.DIAMOND_PICKAXE, Map.of(Enchantments.FORTUNE, 3,
				Enchantments.EFFICIENCY, 2, Enchantments.UNBREAKING, 3));
		CommonConfig d = CommonConfig.defaults();
		for (InvalidBookMode refusing : List.of(InvalidBookMode.REJECT_ALL, InvalidBookMode.APPLY_VALID)) {
			AnvilOutcome outcome = compute(silk, fortune, config(refusing, silkFortuneExclusive(), d.costs()));
			equal(helper, Status.REFUSED, outcome.status(), refusing + " status");
		}
		AnvilOutcome strip = compute(silk, fortune, config(InvalidBookMode.STRIP_INVALID, silkFortuneExclusive(),
				d.costs()));
		equal(helper, Status.OK, strip.status(), "strip status");
		check(helper, strip.entries().stream().anyMatch(e -> e.id().equals("minecraft:fortune")
				&& e.fate() == Fate.LOSE && e.conflictsWith().orElse("").equals("minecraft:silk_touch")),
				"Fortune previewed as lost to Silk Touch: " + strip.entries());
		check(helper, strip.entries().stream().anyMatch(e -> e.id().equals("minecraft:efficiency")
				&& e.fate() == Fate.CONSUME_DOMINATED), "Efficiency II is dominated, not lost: " + strip.entries());
		helper.succeed();
	}

	/** Only dominated entries: not a loss, so the combine goes ahead in every mode. */
	@GameTest
	public void itemPlusItemDominatedIsNotALoss(GameTestHelper helper) {
		ItemStack target = with(helper, Items.DIAMOND_PICKAXE, Map.of(Enchantments.EFFICIENCY, 5));
		target.setDamageValue(1000);
		ItemStack sacrifice = with(helper, Items.DIAMOND_PICKAXE, Map.of(Enchantments.EFFICIENCY, 3));
		for (InvalidBookMode mode : InvalidBookMode.values()) {
			AnvilOutcome outcome = compute(target, sacrifice, mode(mode));
			equal(helper, Status.OK, outcome.status(), mode + " status");
			equal(helper, 2, outcome.levelsCharged(), mode + " combine repair only");
		}
		helper.succeed();
	}

	// --- repair and rename ---

	@GameTest
	public void materialRepairChargesAndConsumesPerItem(GameTestHelper helper) {
		ServerPlayer player = player(helper, RICH);
		ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
		int step = pickaxe.getMaxDamage() / 4;
		pickaxe.setDamageValue(3 * step);
		AnvilMenu menu = anvil(player, pickaxe, new ItemStack(Items.DIAMOND, 5));
		equal(helper, 3, menu.getCost(), "3 diamonds at 1 level each");
		ItemStack taken = take(menu, player);
		equal(helper, 0, taken.getDamageValue(), "fully repaired");
		equal(helper, 2, menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem().getCount(), "diamonds left");
		equal(helper, RICH - 3, player.experienceLevel, "levels after");
		helper.succeed();
	}

	@GameTest
	public void combineRepairPlusEnchantment(GameTestHelper helper) {
		ServerPlayer player = player(helper, RICH);
		ItemStack target = new ItemStack(Items.DIAMOND_PICKAXE);
		target.setDamageValue(1000);
		ItemStack sacrifice = with(helper, Items.DIAMOND_PICKAXE, Map.of(Enchantments.UNBREAKING, 1));
		sacrifice.setDamageValue(1000);
		AnvilMenu menu = anvil(player, target, sacrifice);
		equal(helper, 3, menu.getCost(), "repair_combine_cost 2 + Unbreaking I");
		ItemStack taken = take(menu, player);
		check(helper, taken.getDamageValue() < 1000, "durability went up, damage " + taken.getDamageValue());
		equal(helper, 1, level(helper, taken, Enchantments.UNBREAKING), "Unbreaking moved across");
		check(helper, menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem().isEmpty(), "sacrifice consumed");
		helper.succeed();
	}

	@GameTest
	public void renameOnlyCostsOneAndWorksOnAStack(GameTestHelper helper) {
		ServerPlayer player = player(helper, RICH);
		AnvilMenu menu = anvil(player, new ItemStack(Items.NAME_TAG, 16), ItemStack.EMPTY);
		menu.setItemName("Bob");
		equal(helper, 1, menu.getCost(), "rename_cost");
		ItemStack taken = take(menu, player);
		equal(helper, 16, taken.getCount(), "the whole stack");
		equal(helper, "Bob", taken.getHoverName().getString(), "name");
		helper.succeed();
	}

	/** A book in the right slot that gives nothing while renaming goes back untouched, even under strip_invalid. */
	@GameTest
	public void renameWithAnUnusableBookKeepsTheBook(GameTestHelper helper) {
		ServerPlayer player = player(helper, RICH);
		withConfig(helper, mode(InvalidBookMode.STRIP_INVALID), () -> {
			ItemStack book = book(helper, Map.of(Enchantments.SHARPNESS, 5));
			AnvilMenu menu = anvil(player, new ItemStack(Items.DIAMOND_PICKAXE), book.copy());
			menu.setItemName("Digger");
			equal(helper, 1, menu.getCost(), "rename only");
			take(menu, player);
			check(helper, ItemStack.matches(book, menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem()),
					"book untouched, got " + menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem());
		});
		helper.succeed();
	}

	/** DESIGN §6: the prior-work component is never read or written (owner, 2026-10-07). */
	@GameTest
	public void priorWorkComponentIsUntouched(GameTestHelper helper) {
		ItemStack worked = new ItemStack(Items.DIAMOND_PICKAXE);
		worked.set(DataComponents.REPAIR_COST, 7);
		AnvilOutcome outcome = compute(worked, book(helper, Map.of(Enchantments.EFFICIENCY, 1)),
				CommonConfig.defaults());
		equal(helper, 1, outcome.levelsCharged(), "no penalty added to the price");
		equal(helper, 7, outcome.result().get(DataComponents.REPAIR_COST), "component kept as is");
		AnvilOutcome fresh = compute(new ItemStack(Items.DIAMOND_PICKAXE),
				book(helper, Map.of(Enchantments.EFFICIENCY, 1)), CommonConfig.defaults());
		check(helper, !fresh.result().has(DataComponents.REPAIR_COST)
				|| fresh.result().get(DataComponents.REPAIR_COST) == 0, "never raised on a fresh item");
		helper.succeed();
	}

	// --- paying ---

	@GameTest
	public void insufficientLevelsBlocksTheTake(GameTestHelper helper) {
		ServerPlayer player = player(helper, 4);
		ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
		ItemStack book = book(helper, Map.of(Enchantments.EFFICIENCY, 5));
		AnvilMenu menu = anvil(player, pickaxe.copy(), book.copy());
		equal(helper, 5, menu.getCost(), "cost");
		check(helper, take(menu, player).isEmpty(), "nothing taken");
		equal(helper, 4, player.experienceLevel, "levels untouched");
		check(helper, ItemStack.matches(pickaxe, menu.getSlot(AnvilMenu.INPUT_SLOT).getItem()), "pickaxe still there");
		check(helper, ItemStack.matches(book, menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem()), "book still there");
		helper.succeed();
	}

	/** Vanilla needs cost > 0 to take; a curse's own line costs 0 (DESIGN §5), so this must be takeable for free. */
	@GameTest
	public void curseOnlyBookIsFreeAndTakeable(GameTestHelper helper) {
		ServerPlayer player = player(helper, 0);
		AnvilMenu menu = anvil(player, new ItemStack(Items.DIAMOND_PICKAXE),
				book(helper, Map.of(Enchantments.VANISHING_CURSE, 1)));
		equal(helper, 0, menu.getCost(), "cost");
		ItemStack taken = take(menu, player);
		equal(helper, 1, level(helper, taken, Enchantments.VANISHING_CURSE), "curse applied");
		helper.succeed();
	}

	@GameTest
	public void creativeIsFreeByDefaultAndPaysWhenConfigured(GameTestHelper helper) {
		ServerPlayer player = player(helper, 0);
		player.getAbilities().instabuild = true;
		ItemStack book = book(helper, Map.of(Enchantments.EFFICIENCY, 5));
		AnvilMenu free = anvil(player, new ItemStack(Items.DIAMOND_PICKAXE), book.copy());
		equal(helper, 0, free.getCost(), "creative_free");
		check(helper, !take(free, player).isEmpty(), "taken for free");

		CommonConfig d = CommonConfig.defaults();
		CostsSection paying = new CostsSection(d.costs().renameCost(), d.costs().repairCostPerMaterial(),
				d.costs().repairCombineCost(), d.costs().maximumOperationCost(), false);
		withConfig(helper, config(d.anvil().invalidBookMode(), d.allowTogether(), paying), () -> {
			AnvilMenu menu = anvil(player, new ItemStack(Items.DIAMOND_PICKAXE), book.copy());
			equal(helper, 5, menu.getCost(), "creative pays");
			check(helper, take(menu, player).isEmpty(), "refused at level 0");
			player.experienceLevel = 10;
			check(helper, !take(menu, player).isEmpty(), "taken at level 10");
			equal(helper, 5, player.experienceLevel, "charged in creative");
		});
		helper.succeed();
	}

	@GameTest
	public void maximumOperationCostRefusesSurvivalOnly(GameTestHelper helper) {
		CommonConfig d = CommonConfig.defaults();
		CostsSection capped = new CostsSection(d.costs().renameCost(), d.costs().repairCostPerMaterial(),
				d.costs().repairCombineCost(), 7, d.costs().creativeFree());
		CommonConfig config = config(d.anvil().invalidBookMode(), d.allowTogether(), capped);
		ItemStack book = book(helper, Map.of(Enchantments.EFFICIENCY, 5, Enchantments.UNBREAKING, 3));
		AnvilOutcome survival = compute(new ItemStack(Items.DIAMOND_PICKAXE), book, config);
		equal(helper, Status.TOO_EXPENSIVE, survival.status(), "8 > 7");
		equal(helper, 8, survival.breakdown().orElseThrow().total(), "the price is still shown");
		AnvilOutcome creative = AnvilCalculator.compute(new ItemStack(Items.DIAMOND_PICKAXE), book, null, true, config,
				PricingTable.builtIn());
		equal(helper, Status.OK, creative.status(), "creative ignores the maximum");
		helper.succeed();
	}

	/** Vanilla's 40-level cap is gone (DESIGN §6). Mending + Sharpness/Smite/Bane V = 15 + 2 + 2 × 15 = 47. */
	@GameTest
	public void costPastFortyIsTakeable(GameTestHelper helper) {
		ServerPlayer player = player(helper, 50);
		AnvilMenu menu = anvil(player, new ItemStack(Items.DIAMOND_SWORD), book(helper, Map.of(Enchantments.SHARPNESS,
				5, Enchantments.SMITE, 5, Enchantments.BANE_OF_ARTHROPODS, 5, Enchantments.MENDING, 1)));
		equal(helper, 47, menu.getCost(), "cost");
		check(helper, !take(menu, player).isEmpty(), "taken");
		equal(helper, 3, player.experienceLevel, "levels after");
		helper.succeed();
	}

	@GameTest
	public void stacksAreNotEnchanted(GameTestHelper helper) {
		ItemStack books = book(helper, Map.of(Enchantments.SHARPNESS, 1));
		books.set(DataComponents.MAX_STACK_SIZE, 2);
		books.setCount(2);
		ItemStack single = book(helper, Map.of(Enchantments.EFFICIENCY, 1));
		equal(helper, Status.STACKED_ITEM, compute(books, single, CommonConfig.defaults()).status(), "stacked left");
		equal(helper, Status.STACKED_ITEM, compute(new ItemStack(Items.DIAMOND_PICKAXE), books,
				CommonConfig.defaults()).status(), "stacked right");
		helper.succeed();
	}

	// --- conservation property ---

	private static final List<Item> TARGETS = List.of(Items.DIAMOND_PICKAXE, Items.DIAMOND_SWORD, Items.BOW,
			Items.DIAMOND_CHESTPLATE, Items.FISHING_ROD, Items.TRIDENT, Items.ENCHANTED_BOOK);

	/**
	 * DESIGN §6 "never silently deletes an enchantment", through the real menu: every enchantment that went in is, after
	 * the take, on the result or back in the right slot at the same level or higher. The only exceptions are a dominated
	 * entry, and under {@code strip_invalid} an entry the outcome listed as lost before the take. Nothing is on both.
	 */
	@GameTest(maxTicks = 400)
	public void noEnchantmentIsEverSilentlyLost(GameTestHelper helper) {
		List<Holder<Enchantment>> pool = new ArrayList<>(helper.getLevel().registryAccess()
				.lookupOrThrow(Registries.ENCHANTMENT).listElements().toList());
		pool.sort(Comparator.comparing(Holder::getRegisteredName));
		Random random = new Random(0x6B5L);
		CommonConfig d = CommonConfig.defaults();
		ServerPlayer player = player(helper, RICH);
		int taken = 0;
		for (int i = 0; i < 300; i++) {
			InvalidBookMode mode = InvalidBookMode.values()[random.nextInt(InvalidBookMode.values().length)];
			EnumSet<ConflictFamily> allowed = EnumSet.noneOf(ConflictFamily.class);
			for (ConflictFamily family : ConflictFamily.values()) {
				if (random.nextBoolean()) {
					allowed.add(family);
				}
			}
			Item item = TARGETS.get(random.nextInt(TARGETS.size()));
			ItemStack left = randomlyEnchanted(item, pool, random, 3, false);
			boolean useBook = item == Items.ENCHANTED_BOOK || random.nextBoolean();
			ItemStack right = randomlyEnchanted(useBook ? Items.ENCHANTED_BOOK : item, pool, random, 4, true);
			if (left.isDamageableItem()) {
				left.setDamageValue(random.nextInt(left.getMaxDamage()));
			}
			if (right.isDamageableItem()) {
				right.setDamageValue(random.nextInt(right.getMaxDamage()));
			}
			String label = "case " + i + " (" + mode + ", " + left + " + " + right + ")";
			player.experienceLevel = RICH;
			int[] took = {0};
			withConfig(helper, config(mode, new AllowTogetherSection(allowed), d.costs()), () -> {
				AnvilMenu menu = anvil(player, left.copy(), right.copy());
				AnvilOutcome outcome = outcome(menu);
				ItemStack result = take(menu, player);
				ItemStack rightAfter = menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem();
				if (!outcome.ok()) {
					check(helper, result.isEmpty(), label + ": took a result from a " + outcome.status());
					check(helper, ItemStack.matches(left, menu.getSlot(AnvilMenu.INPUT_SLOT).getItem())
							&& ItemStack.matches(right, rightAfter), label + ": inputs changed without a result");
					return;
				}
				took[0] = 1;
				check(helper, !result.isEmpty(), label + ": OK but nothing taken");
				ItemEnchantments before = EnchantmentHelper.getEnchantmentsForCrafting(left);
				ItemEnchantments after = EnchantmentHelper.getEnchantmentsForCrafting(result);
				ItemEnchantments back = rightAfter.is(Items.ENCHANTED_BOOK) || rightAfter.is(right.getItem())
						? EnchantmentHelper.getEnchantmentsForCrafting(rightAfter) : ItemEnchantments.EMPTY;
				for (Object2IntMap.Entry<Holder<Enchantment>> e : before.entrySet()) {
					check(helper, after.getLevel(e.getKey()) >= e.getIntValue(),
							label + ": the item lost " + e.getKey().getRegisteredName());
				}
				Map<String, Fate> fates = new LinkedHashMap<>();
				outcome.entries().forEach(entry -> fates.put(entry.id(), entry.fate()));
				for (Object2IntMap.Entry<Holder<Enchantment>> e : EnchantmentHelper.getEnchantmentsForCrafting(right)
						.entrySet()) {
					Holder<Enchantment> enchantment = e.getKey();
					int level = e.getIntValue();
					boolean onResult = after.getLevel(enchantment) >= level;
					boolean onBook = back.getLevel(enchantment) >= level;
					boolean listedLost = mode == InvalidBookMode.STRIP_INVALID
							&& fates.get(enchantment.getRegisteredName()) == Fate.LOSE;
					check(helper, onResult || onBook || listedLost, label + ": " + enchantment.getRegisteredName()
							+ " " + level + " vanished (fate " + fates.get(enchantment.getRegisteredName()) + ")");
					boolean applied = before.getLevel(enchantment) < level && after.getLevel(enchantment) >= level;
					check(helper, !(applied && onBook), label + ": " + enchantment.getRegisteredName()
							+ " was applied and also came back (duplicated)");
				}
				check(helper, menu.getSlot(AnvilMenu.INPUT_SLOT).getItem().isEmpty(), label + ": left slot not emptied");
			});
			taken += took[0];
		}
		// Guards the generator: a property that never reaches a take checks nothing.
		check(helper, taken >= 60, "only " + taken + " of 300 cases produced a result");
		helper.succeed();
	}

	/** {@code count} random entries (or fewer, on duplicates); a book's may go one past max, as loot from mods can. */
	private static ItemStack randomlyEnchanted(Item item, List<Holder<Enchantment>> pool, Random random, int count,
			boolean pastMax) {
		ItemEnchantments.Mutable m = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		int n = random.nextInt(count + 1);
		for (int i = 0; i < n; i++) {
			Holder<Enchantment> enchantment = pool.get(random.nextInt(pool.size()));
			int max = enchantment.value().getMaxLevel() + (pastMax && random.nextInt(8) == 0 ? 1 : 0);
			m.set(enchantment, 1 + random.nextInt(max));
		}
		ItemStack stack = new ItemStack(item);
		EnchantmentHelper.setEnchantments(stack, m.toImmutable());
		return stack;
	}
}
