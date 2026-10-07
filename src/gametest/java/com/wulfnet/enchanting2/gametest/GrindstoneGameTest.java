package com.wulfnet.enchanting2.gametest;

import com.wulfnet.enchanting2.grindstone.GrindstoneAction;
import com.wulfnet.enchanting2.grindstone.GrindstoneCalculator;
import com.wulfnet.enchanting2.grindstone.GrindstoneChoice;
import com.wulfnet.enchanting2.grindstone.GrindstoneMenuAccess;
import com.wulfnet.enchanting2.grindstone.GrindstoneOption;
import com.wulfnet.enchanting2.grindstone.GrindstoneOutcome;
import com.wulfnet.enchanting2.grindstone.GrindstoneOutcome.Status;
import com.wulfnet.enchanting2.network.GrindstoneChoicePayload;
import com.wulfnet.enchanting2.network.GrindstoneOptionsHolder;
import com.wulfnet.enchanting2.network.GrindstoneOptionsPayload;
import com.wulfnet.enchanting2.network.ServerNetworking;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.AABB;

/**
 * DESIGN §6 grindstone through a real {@code GrindstoneMenu} and a mock {@code ServerPlayer}: slots are set, a pick is
 * made (through the menu or the C2S handler), the result is clicked, and what the player holds, pays and gets back is
 * checked. Every test uses the default config.
 */
public class GrindstoneGameTest {
	private static final int RICH = 100_000;
	private static final String EFFICIENCY = "minecraft:efficiency";
	private static final String UNBREAKING = "minecraft:unbreaking";
	private static final String SHARPNESS = "minecraft:sharpness";
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

	private static ItemStack pickaxe(GameTestHelper helper) {
		return with(helper, Items.DIAMOND_PICKAXE, Map.of(Enchantments.EFFICIENCY, 5, Enchantments.UNBREAKING, 3,
				Enchantments.MENDING, 1));
	}

	private static int level(GameTestHelper helper, ItemStack stack, ResourceKey<Enchantment> key) {
		return EnchantmentHelper.getEnchantmentsForCrafting(stack).getLevel(get(helper, key));
	}

	private static BlockPos origin(GameTestHelper helper) {
		return helper.absolutePos(BlockPos.ZERO);
	}

	/** A grindstone "at" the test's origin, so vanilla's take spawns its orbs where {@link #orbs} looks. */
	private static GrindstoneMenu grindstone(GameTestHelper helper, ServerPlayer player, ItemStack slot0,
			ItemStack slot1) {
		GrindstoneMenu menu = new GrindstoneMenu(1, player.getInventory(),
				ContainerLevelAccess.create(helper.getLevel(), origin(helper)));
		menu.getSlot(GrindstoneMenu.INPUT_SLOT).set(slot0);
		menu.getSlot(GrindstoneMenu.ADDITIONAL_SLOT).set(slot1);
		return menu;
	}

	private static int orbs(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, new AABB(origin(helper)).inflate(2)).size();
	}

	private static GrindstoneMenuAccess access(GrindstoneMenu menu) {
		return (GrindstoneMenuAccess) menu;
	}

	private static GrindstoneOutcome outcome(GrindstoneMenu menu) {
		GrindstoneOutcome outcome = access(menu).enchanting2$outcome();
		if (outcome == null) {
			throw new IllegalStateException("the server-side menu has no outcome after its slots were set");
		}
		return outcome;
	}

	private static void pick(GrindstoneMenu menu, GrindstoneChoice choice) {
		access(menu).enchanting2$choose(Optional.of(choice));
	}

	/** Clicks the result slot like a player would; returns what ended up on the cursor (empty if the take was refused). */
	private static ItemStack take(GrindstoneMenu menu, ServerPlayer player) {
		menu.clicked(GrindstoneMenu.RESULT_SLOT, 0, ClickType.PICKUP, player);
		ItemStack carried = menu.getCarried();
		menu.setCarried(ItemStack.EMPTY);
		return carried;
	}

	private static ItemStack slot(GrindstoneMenu menu, int index) {
		return menu.getSlot(index).getItem();
	}

	private static GrindstoneChoice extract(String id) {
		return GrindstoneChoice.of(GrindstoneAction.EXTRACT, id);
	}

	private static GrindstoneChoice cleanse(String id) {
		return GrindstoneChoice.of(GrindstoneAction.CLEANSE, id);
	}

	private static void check(GameTestHelper helper, boolean condition, String what) {
		helper.assertTrue(condition, Component.literal(what));
	}

	private static <T> void equal(GameTestHelper helper, T expected, T actual, String what) {
		helper.assertValueEqual(expected, actual, Component.literal(what));
	}

	// --- picks and takes ---

	@GameTest
	public void nothingIsProducedBeforeAPick(GameTestHelper helper) {
		ServerPlayer player = AnvilGameTest.player(helper, RICH);
		GrindstoneMenu menu = grindstone(helper, player, pickaxe(helper), new ItemStack(Items.BOOK, 3));
		equal(helper, Status.NO_CHOICE, outcome(menu).status(), "status");
		check(helper, slot(menu, GrindstoneMenu.RESULT_SLOT).isEmpty(), "result slot empty");
		check(helper, take(menu, player).isEmpty(), "nothing to take");
		equal(helper, 5, level(helper, slot(menu, GrindstoneMenu.INPUT_SLOT), Enchantments.EFFICIENCY),
				"pickaxe untouched");
		equal(helper, 3, slot(menu, GrindstoneMenu.ADDITIONAL_SLOT).getCount(), "books untouched");
		GrindstoneOptionsPayload options = ((GrindstoneOptionsHolder) menu).enchanting2$lastOptions();
		check(helper, options != null && options.options().size() == 3, "the panel lists 3 rows, got " + options);
		helper.succeed();
	}

	/** Moved, never copied or lost: on the book, off the pickaxe, one book used, the price paid, no XP orbs. */
	@GameTest
	public void extractMovesTheEntryExactlyOnce(GameTestHelper helper) {
		ServerPlayer player = AnvilGameTest.player(helper, RICH);
		GrindstoneMenu menu = grindstone(helper, player, pickaxe(helper), new ItemStack(Items.BOOK, 3));
		pick(menu, extract(EFFICIENCY));
		ItemStack taken = take(menu, player);
		check(helper, taken.is(Items.ENCHANTED_BOOK), "took an enchanted book, got " + taken);
		equal(helper, 5, level(helper, taken, Enchantments.EFFICIENCY), "Efficiency V on the book");
		equal(helper, 1, EnchantmentHelper.getEnchantmentsForCrafting(taken).size(), "only Efficiency on the book");

		ItemStack source = slot(menu, GrindstoneMenu.INPUT_SLOT);
		equal(helper, 0, level(helper, source, Enchantments.EFFICIENCY), "Efficiency gone from the pickaxe");
		equal(helper, 3, level(helper, source, Enchantments.UNBREAKING), "Unbreaking stays");
		equal(helper, 1, level(helper, source, Enchantments.MENDING), "Mending stays");
		equal(helper, 2, slot(menu, GrindstoneMenu.ADDITIONAL_SLOT).getCount(), "one book used");
		equal(helper, RICH - 5, player.experienceLevel, "levels after the take");
		equal(helper, 0, orbs(helper), "no XP orbs");

		// The source changed, so the pick is gone and nothing more can be taken until the player picks again.
		check(helper, access(menu).enchanting2$choice().isEmpty(), "pick cleared");
		check(helper, slot(menu, GrindstoneMenu.RESULT_SLOT).isEmpty(), "no new result");
		helper.succeed();
	}

	@GameTest
	public void extractWorksWithTheBookOnTop(GameTestHelper helper) {
		ServerPlayer player = AnvilGameTest.player(helper, RICH);
		GrindstoneMenu menu = grindstone(helper, player, new ItemStack(Items.BOOK), pickaxe(helper));
		pick(menu, extract(UNBREAKING));
		ItemStack taken = take(menu, player);
		equal(helper, 3, level(helper, taken, Enchantments.UNBREAKING), "Unbreaking III on the book");
		check(helper, slot(menu, GrindstoneMenu.INPUT_SLOT).isEmpty(), "the only book was used");
		equal(helper, 0, level(helper, slot(menu, GrindstoneMenu.ADDITIONAL_SLOT), Enchantments.UNBREAKING),
				"Unbreaking gone from the pickaxe");
		equal(helper, 5, level(helper, slot(menu, GrindstoneMenu.ADDITIONAL_SLOT), Enchantments.EFFICIENCY),
				"Efficiency stays");
		equal(helper, RICH - 3, player.experienceLevel, "levels after the take");
		helper.succeed();
	}

	@GameTest
	public void cleanseChargesTheRepayCostAndRemovesOnlyThatCurse(GameTestHelper helper) {
		ServerPlayer player = AnvilGameTest.player(helper, RICH);
		ItemStack sword = with(helper, Items.DIAMOND_SWORD, Map.of(Enchantments.SHARPNESS, 5,
				Enchantments.VANISHING_CURSE, 1));
		GrindstoneMenu menu = grindstone(helper, player, sword, ItemStack.EMPTY);
		pick(menu, cleanse(VANISHING));
		ItemStack taken = take(menu, player);
		equal(helper, 0, level(helper, taken, Enchantments.VANISHING_CURSE), "Vanishing removed");
		equal(helper, 5, level(helper, taken, Enchantments.SHARPNESS), "Sharpness kept");
		equal(helper, RICH - 30, player.experienceLevel, "removal_min_cost 30 charged");
		check(helper, slot(menu, GrindstoneMenu.INPUT_SLOT).isEmpty(), "the source was consumed");
		equal(helper, 0, orbs(helper), "no XP orbs for a Cleanse");
		helper.succeed();
	}

	@GameTest
	public void disenchantIsVanillaWithItsOrbs(GameTestHelper helper) {
		ServerPlayer player = AnvilGameTest.player(helper, RICH);
		ItemStack sword = with(helper, Items.DIAMOND_SWORD, Map.of(Enchantments.SHARPNESS, 5,
				Enchantments.VANISHING_CURSE, 1));
		GrindstoneMenu menu = grindstone(helper, player, sword, ItemStack.EMPTY);
		pick(menu, GrindstoneChoice.of(GrindstoneAction.DISENCHANT));
		ItemStack taken = take(menu, player);
		equal(helper, 0, level(helper, taken, Enchantments.SHARPNESS), "Sharpness stripped");
		equal(helper, 1, level(helper, taken, Enchantments.VANISHING_CURSE), "the curse stays (vanilla)");
		equal(helper, RICH, player.experienceLevel, "free");
		check(helper, orbs(helper) > 0, "vanilla's XP orbs spawned");
		check(helper, slot(menu, GrindstoneMenu.INPUT_SLOT).isEmpty(), "input emptied (vanilla)");
		helper.succeed();
	}

	@GameTest
	public void repairIsVanillaAfterAPick(GameTestHelper helper) {
		ServerPlayer player = AnvilGameTest.player(helper, RICH);
		ItemStack a = new ItemStack(Items.DIAMOND_SWORD);
		a.setDamageValue(1000);
		ItemStack b = new ItemStack(Items.DIAMOND_SWORD);
		b.setDamageValue(1000);
		GrindstoneMenu menu = grindstone(helper, player, a, b);
		check(helper, slot(menu, GrindstoneMenu.RESULT_SLOT).isEmpty(), "no result before the pick");
		pick(menu, GrindstoneChoice.of(GrindstoneAction.REPAIR));
		ItemStack taken = take(menu, player);
		check(helper, taken.is(Items.DIAMOND_SWORD) && taken.getDamageValue() < 1000, "a repaired sword, got " + taken);
		check(helper, slot(menu, GrindstoneMenu.INPUT_SLOT).isEmpty()
				&& slot(menu, GrindstoneMenu.ADDITIONAL_SLOT).isEmpty(), "both inputs used (vanilla)");
		helper.succeed();
	}

	@GameTest
	public void tooFewLevelsRefusesTheTakeAndChangesNothing(GameTestHelper helper) {
		ServerPlayer player = AnvilGameTest.player(helper, 4);
		GrindstoneMenu menu = grindstone(helper, player, pickaxe(helper), new ItemStack(Items.BOOK, 3));
		pick(menu, extract(EFFICIENCY));
		check(helper, take(menu, player).isEmpty(), "take refused");
		equal(helper, 4, player.experienceLevel, "no levels taken");
		equal(helper, 5, level(helper, slot(menu, GrindstoneMenu.INPUT_SLOT), Enchantments.EFFICIENCY),
				"Efficiency still on the pickaxe");
		equal(helper, 3, slot(menu, GrindstoneMenu.ADDITIONAL_SLOT).getCount(), "no book used");
		check(helper, !slot(menu, GrindstoneMenu.RESULT_SLOT).isEmpty(), "the result is still offered");
		helper.succeed();
	}

	@GameTest
	public void shiftClickIntoAFullInventoryChangesNothing(GameTestHelper helper) {
		ServerPlayer player = AnvilGameTest.player(helper, RICH);
		for (int i = 0; i < player.getInventory().getNonEquipmentItems().size(); i++) {
			player.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
		}
		GrindstoneMenu menu = grindstone(helper, player, pickaxe(helper), new ItemStack(Items.BOOK, 3));
		pick(menu, extract(EFFICIENCY));
		menu.clicked(GrindstoneMenu.RESULT_SLOT, 0, ClickType.QUICK_MOVE, player);
		equal(helper, RICH, player.experienceLevel, "no levels taken");
		equal(helper, 5, level(helper, slot(menu, GrindstoneMenu.INPUT_SLOT), Enchantments.EFFICIENCY),
				"Efficiency still on the pickaxe");
		equal(helper, 3, slot(menu, GrindstoneMenu.ADDITIONAL_SLOT).getCount(), "no book used");
		check(helper, !slot(menu, GrindstoneMenu.RESULT_SLOT).isEmpty(), "the result is still offered");
		helper.succeed();
	}

	@GameTest
	public void plainBooksArePlaceableOnTheServer(GameTestHelper helper) {
		GrindstoneMenu menu = grindstone(helper, AnvilGameTest.player(helper, RICH), ItemStack.EMPTY, ItemStack.EMPTY);
		for (int i : new int[] {GrindstoneMenu.INPUT_SLOT, GrindstoneMenu.ADDITIONAL_SLOT}) {
			check(helper, menu.getSlot(i).mayPlace(new ItemStack(Items.BOOK, 16)), "plain books in slot " + i);
			check(helper, !menu.getSlot(i).mayPlace(new ItemStack(Items.DIRT)), "vanilla still refuses dirt in " + i);
		}
		helper.succeed();
	}

	// --- the pick: validation, staleness ---

	@GameTest
	public void forgedOrStalePicksProduceNothing(GameTestHelper helper) {
		ServerPlayer player = AnvilGameTest.player(helper, RICH);
		ItemStack sword = with(helper, Items.DIAMOND_SWORD, Map.of(Enchantments.SHARPNESS, 5,
				Enchantments.VANISHING_CURSE, 1));
		GrindstoneMenu menu = grindstone(helper, player, sword, new ItemStack(Items.BOOK));
		player.containerMenu = menu;
		try {
			check(helper, !ServerNetworking.handleGrindstoneChoice(player,
					new GrindstoneChoicePayload(menu.containerId + 1, Optional.of(extract(SHARPNESS)))),
					"another container ID is ignored");
			check(helper, access(menu).enchanting2$choice().isEmpty(), "no pick after a wrong container ID");

			Map<GrindstoneChoice, Status> forged = Map.of(
					extract(EFFICIENCY), Status.NOT_AVAILABLE,
					extract(VANISHING), Status.CURSE_NOT_EXTRACTABLE,
					cleanse(SHARPNESS), Status.NOT_AVAILABLE,
					GrindstoneChoice.of(GrindstoneAction.DISENCHANT), Status.NOT_AVAILABLE);
			forged.forEach((choice, status) -> {
				check(helper, ServerNetworking.handleGrindstoneChoice(player,
						new GrindstoneChoicePayload(menu.containerId, Optional.of(choice))), "applied " + choice);
				equal(helper, status, outcome(menu).status(), "status for " + choice);
				check(helper, take(menu, player).isEmpty(), "nothing to take for " + choice);
			});
			equal(helper, RICH, player.experienceLevel, "nothing charged");
			equal(helper, 5, level(helper, slot(menu, GrindstoneMenu.INPUT_SLOT), Enchantments.SHARPNESS),
					"the sword is untouched");

			player.containerMenu = player.inventoryMenu;
			check(helper, !ServerNetworking.handleGrindstoneChoice(player,
					new GrindstoneChoicePayload(menu.containerId, Optional.of(extract(SHARPNESS)))),
					"ignored once the grindstone isn't the open menu");
		} finally {
			player.containerMenu = player.inventoryMenu;
		}
		helper.succeed();
	}

	@GameTest
	public void swappingTheSourceClearsThePick(GameTestHelper helper) {
		ServerPlayer player = AnvilGameTest.player(helper, RICH);
		GrindstoneMenu menu = grindstone(helper, player, pickaxe(helper), new ItemStack(Items.BOOK, 3));
		pick(menu, extract(EFFICIENCY));
		check(helper, !slot(menu, GrindstoneMenu.RESULT_SLOT).isEmpty(), "result after the pick");
		ItemStack other = with(helper, Items.DIAMOND_PICKAXE, Map.of(Enchantments.EFFICIENCY, 4));
		menu.getSlot(GrindstoneMenu.INPUT_SLOT).set(other);
		check(helper, access(menu).enchanting2$choice().isEmpty(), "pick cleared");
		equal(helper, Status.NO_CHOICE, outcome(menu).status(), "status");
		check(helper, slot(menu, GrindstoneMenu.RESULT_SLOT).isEmpty(), "no result for the new pickaxe");
		helper.succeed();
	}

	@GameTest
	public void changingTheBookCountKeepsThePick(GameTestHelper helper) {
		ServerPlayer player = AnvilGameTest.player(helper, RICH);
		GrindstoneMenu menu = grindstone(helper, player, pickaxe(helper), new ItemStack(Items.BOOK, 3));
		pick(menu, extract(EFFICIENCY));
		GrindstoneOptionsPayload before = ((GrindstoneOptionsHolder) menu).enchanting2$lastOptions();
		menu.getSlot(GrindstoneMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.BOOK, 7));
		equal(helper, Optional.of(extract(EFFICIENCY)), access(menu).enchanting2$choice(), "pick kept");
		check(helper, !slot(menu, GrindstoneMenu.RESULT_SLOT).isEmpty(), "result still offered");
		check(helper, before == ((GrindstoneOptionsHolder) menu).enchanting2$lastOptions(),
				"an unchanged panel isn't rebuilt or resent");
		helper.succeed();
	}

	// --- payloads ---

	private static <T> T roundTrip(GameTestHelper helper, StreamCodec<ByteBuf, T> codec, T sent) {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		codec.encode(buf, sent);
		T received = codec.decode(buf);
		helper.assertTrue(buf.readableBytes() == 0, Component.literal("decode left bytes behind"));
		return received;
	}

	@GameTest
	public void payloadsRoundTripEveryEnumValue(GameTestHelper helper) {
		List<GrindstoneOption> rows = new ArrayList<>();
		for (Status status : GrindstoneOption.ROW_STATUSES) {
			rows.add(new GrindstoneOption(extract("minecraft:" + status.name().toLowerCase()), 3, 40_000, true,
					status));
		}
		rows.add(new GrindstoneOption(cleanse(VANISHING), 1, Integer.MAX_VALUE, false, Status.OK));
		rows.add(new GrindstoneOption(GrindstoneChoice.of(GrindstoneAction.DISENCHANT), 0, 0, false, Status.OK));
		rows.add(new GrindstoneOption(GrindstoneChoice.of(GrindstoneAction.REPAIR), 0, 0, false, Status.OK));
		for (Status status : Status.values()) {
			GrindstoneOptionsPayload sent = new GrindstoneOptionsPayload(7, status, rows,
					Optional.of(extract(EFFICIENCY)), 40_000);
			equal(helper, sent, roundTrip(helper, GrindstoneOptionsPayload.CODEC, sent), "options with " + status);
		}
		for (GrindstoneAction action : GrindstoneAction.values()) {
			GrindstoneChoice choice = action.takesEnchantment() ? GrindstoneChoice.of(action, SHARPNESS)
					: GrindstoneChoice.of(action);
			GrindstoneChoicePayload sent = new GrindstoneChoicePayload(3, Optional.of(choice));
			equal(helper, sent, roundTrip(helper, GrindstoneChoicePayload.CODEC, sent), "choice " + action);
		}
		GrindstoneChoicePayload none = new GrindstoneChoicePayload(3, Optional.empty());
		equal(helper, none, roundTrip(helper, GrindstoneChoicePayload.CODEC, none), "no choice");
		helper.succeed();
	}

	/** A forged Extract with no enchantment is a decode error, so it never reaches the menu. */
	@GameTest
	public void aMalformedChoiceFailsDecode(GameTestHelper helper) {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		ByteBufCodecs.VAR_INT.encode(buf, 1); // container
		ByteBufCodecs.BOOL.encode(buf, true); // choice present
		ByteBufCodecs.VAR_INT.encode(buf, GrindstoneAction.EXTRACT.ordinal());
		ByteBufCodecs.BOOL.encode(buf, false); // no enchantment
		try {
			GrindstoneChoicePayload.CODEC.decode(buf);
		} catch (RuntimeException expected) {
			helper.succeed();
			return;
		}
		helper.fail("Extract with no enchantment decoded");
	}

	@GameTest
	public void anOversizePanelIsRefusedWhereItIsBuilt(GameTestHelper helper) {
		List<GrindstoneOption> rows = new ArrayList<>();
		for (int i = 0; i <= GrindstoneOptionsPayload.MAX_OPTIONS; i++) {
			rows.add(new GrindstoneOption(extract("test:e" + i), 1, 1, false, Status.OK));
		}
		try {
			new GrindstoneOptionsPayload(1, Status.NO_CHOICE, rows, Optional.empty(), 0);
		} catch (IllegalArgumentException expected) {
			helper.succeed();
			return;
		}
		helper.fail("an oversize panel was built");
	}

	// --- conservation property ---

	private static final List<Item> SOURCES = List.of(Items.DIAMOND_PICKAXE, Items.DIAMOND_SWORD, Items.BOW,
			Items.DIAMOND_CHESTPLATE, Items.ENCHANTED_BOOK);

	/**
	 * DESIGN §6 "moved, not copied": through the real menu, random sources, book counts, orientations and picks (OK,
	 * refused and forged). After an Extract take, every (enchantment, level) that went in is in exactly one place
	 * (source, new book) and exactly one plain book was used; after a Cleanse take, exactly the cleansed curse is gone.
	 * A refused take changes nothing.
	 */
	@GameTest(maxTicks = 400)
	public void extractAndCleanseConserveEverything(GameTestHelper helper) {
		List<Holder<Enchantment>> pool = new ArrayList<>(helper.getLevel().registryAccess()
				.lookupOrThrow(Registries.ENCHANTMENT).listElements().toList());
		pool.sort(Comparator.comparing(Holder::getRegisteredName));
		Random random = new Random(0x6B6L);
		ServerPlayer player = AnvilGameTest.player(helper, RICH);
		int taken = 0;
		for (int i = 0; i < 300; i++) {
			ItemStack source = randomlyEnchanted(SOURCES.get(random.nextInt(SOURCES.size())), pool, random);
			int books = random.nextInt(4);
			ItemStack bookStack = books == 0 ? ItemStack.EMPTY : new ItemStack(Items.BOOK, books);
			boolean sourceOnTop = random.nextBoolean();
			ItemStack in0 = sourceOnTop ? source : bookStack;
			ItemStack in1 = sourceOnTop ? bookStack : source;
			int levels = random.nextInt(40);
			player.experienceLevel = levels;

			GrindstoneMenu menu = new GrindstoneMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
			menu.getSlot(GrindstoneMenu.INPUT_SLOT).set(in0.copy());
			menu.getSlot(GrindstoneMenu.ADDITIONAL_SLOT).set(in1.copy());
			GrindstoneChoice choice = randomChoice(outcome(menu), source, random);
			pick(menu, choice);
			GrindstoneOutcome outcome = outcome(menu);
			String label = "case " + i + " (" + in0 + " + " + in1 + ", " + choice + ", " + levels + " levels)";

			ItemStack result = take(menu, player);
			ItemStack out0 = slot(menu, GrindstoneMenu.INPUT_SLOT);
			ItemStack out1 = slot(menu, GrindstoneMenu.ADDITIONAL_SLOT);
			if (result.isEmpty()) {
				check(helper, ItemStack.matches(in0, out0) && ItemStack.matches(in1, out1),
						label + ": inputs changed without a take (" + outcome.status() + ")");
				equal(helper, levels, player.experienceLevel, label + ": charged without a take");
				continue;
			}
			check(helper, outcome.ok(), label + ": took a result from a " + outcome.status());
			equal(helper, levels - outcome.levelsCharged(), player.experienceLevel, label + ": levels");
			if (choice.action().vanillaTake()) {
				continue;
			}
			taken++;
			Map<String, Integer> before = entries(in0, in1);
			Map<String, Integer> after = entries(out0, out1, result);
			if (choice.action() == GrindstoneAction.EXTRACT) {
				equal(helper, before, after, label + ": (enchantment, level) entries before vs after");
				equal(helper, plainBooks(in0) + plainBooks(in1) - 1, plainBooks(out0) + plainBooks(out1),
						label + ": plain books left");
				check(helper, result.is(Items.ENCHANTED_BOOK), label + ": the result is an enchanted book");
			} else {
				String curse = choice.enchantment().orElseThrow();
				Map<String, Integer> expected = new HashMap<>(before);
				expected.remove(curse + "@" + level(source, curse));
				equal(helper, expected, after, label + ": only " + curse + " removed");
			}
		}
		// Guards the generator: a property that never reaches a take checks nothing.
		check(helper, taken >= 60, "only " + taken + " of 300 cases took an Extract or Cleanse");
		helper.succeed();
	}

	/** Mostly a listed row (OK or refused), sometimes a forged one the inputs don't allow. */
	private static GrindstoneChoice randomChoice(GrindstoneOutcome outcome, ItemStack source, Random random) {
		List<GrindstoneOption> rows = outcome.options();
		if (!rows.isEmpty() && random.nextInt(10) != 0) {
			// Bias toward Extract/Cleanse: they're what the property is about.
			List<GrindstoneOption> ours = rows.stream().filter(o -> o.choice().action().takesEnchantment()).toList();
			List<GrindstoneOption> from = !ours.isEmpty() && random.nextInt(4) != 0 ? ours : rows;
			return from.get(random.nextInt(from.size())).choice();
		}
		List<String> ids = new ArrayList<>(entries(source).keySet());
		String id = ids.isEmpty() ? EFFICIENCY : ids.get(random.nextInt(ids.size())).split("@")[0];
		return random.nextBoolean() ? extract(id) : cleanse(id);
	}

	/** Each (ID, level) as {@code id@level}, counted, over every stack given. Plain books count as nothing. */
	private static Map<String, Integer> entries(ItemStack... stacks) {
		Map<String, Integer> counts = new HashMap<>();
		for (ItemStack stack : stacks) {
			for (Object2IntMap.Entry<Holder<Enchantment>> e : EnchantmentHelper.getEnchantmentsForCrafting(stack)
					.entrySet()) {
				counts.merge(e.getKey().getRegisteredName() + "@" + e.getIntValue(), stack.getCount(), Integer::sum);
			}
		}
		return counts;
	}

	private static int level(ItemStack stack, String id) {
		for (Object2IntMap.Entry<Holder<Enchantment>> e : EnchantmentHelper.getEnchantmentsForCrafting(stack)
				.entrySet()) {
			if (e.getKey().getRegisteredName().equals(id)) {
				return e.getIntValue();
			}
		}
		throw new IllegalArgumentException(id + " isn't on " + stack);
	}

	private static int plainBooks(ItemStack stack) {
		return GrindstoneCalculator.isPlainBook(stack) ? stack.getCount() : 0;
	}

	private static ItemStack randomlyEnchanted(Item item, List<Holder<Enchantment>> pool, Random random) {
		ItemEnchantments.Mutable m = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		int n = random.nextInt(5);
		for (int i = 0; i < n; i++) {
			Holder<Enchantment> enchantment = pool.get(random.nextInt(pool.size()));
			m.set(enchantment, 1 + random.nextInt(enchantment.value().getMaxLevel()));
		}
		ItemStack stack = new ItemStack(item);
		EnchantmentHelper.setEnchantments(stack, m.toImmutable());
		return stack;
	}
}
