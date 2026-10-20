package com.wulfnet.enchanting2.gametest;

import com.wulfnet.enchanting2.cost.CostBreakdown;
import com.wulfnet.enchanting2.network.ServerNetworking;
import com.wulfnet.enchanting2.network.TableEnchantPayload;
import com.wulfnet.enchanting2.network.TablePreviewHolder;
import com.wulfnet.enchanting2.network.TablePreviewPayload;
import com.wulfnet.enchanting2.network.TableSelectPayload;
import com.wulfnet.enchanting2.table.TableEquipmentSlot;
import com.wulfnet.enchanting2.table.TableMenuAccess;
import com.wulfnet.enchanting2.table.TableOutcome;
import com.wulfnet.enchanting2.table.TableOutcome.Status;
import com.wulfnet.enchanting2.table.TableRow;
import com.wulfnet.enchanting2.table.TableSelection;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
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
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EnchantingTableBlock;

/**
 * DESIGN §6 enchanting table through the real {@code EnchantmentMenu}, a real table position with real bookshelves, and
 * the real C2S handlers. Rows and prices are {@code TableCalculatorGameTest}'s job; this is the wiring: what the server
 * holds, what it charges, and that nothing a client sends can buy anything else.
 */
public class TableGameTest {
	private static final String SHARPNESS = "minecraft:sharpness";
	private static final String EFFICIENCY = "minecraft:efficiency";
	private static final String UNBREAKING = "minecraft:unbreaking";
	private static final String MENDING = "minecraft:mending";
	private static final BlockPos TABLE = new BlockPos(2, 1, 2);
	private static final int CONTAINER = 1;
	private static final int RICH = 100;

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

	/** The first {@code n} of vanilla's 32 shelf positions get a bookshelf, the rest air. */
	private static void shelves(GameTestHelper helper, int n) {
		List<BlockPos> offsets = EnchantingTableBlock.BOOKSHELF_OFFSETS;
		for (int i = 0; i < offsets.size(); i++) {
			helper.setBlock(TABLE.offset(offsets.get(i)), i < n ? Blocks.BOOKSHELF : Blocks.AIR);
		}
	}

	/** A table with {@code shelves} around it, open for {@code player} (as its {@code containerMenu}). */
	private static EnchantmentMenu table(GameTestHelper helper, ServerPlayer player, int shelves, ItemStack item,
			int lapis) {
		helper.setBlock(TABLE, Blocks.ENCHANTING_TABLE);
		shelves(helper, shelves);
		EnchantmentMenu menu = new EnchantmentMenu(CONTAINER, player.getInventory(),
				ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(TABLE)));
		player.containerMenu = menu;
		menu.getSlot(1).set(lapis == 0 ? ItemStack.EMPTY : new ItemStack(Items.LAPIS_LAZULI, lapis));
		menu.getSlot(0).set(item);
		return menu;
	}

	private static TableMenuAccess access(EnchantmentMenu menu) {
		return (TableMenuAccess) menu;
	}

	private static TableOutcome outcome(EnchantmentMenu menu) {
		TableOutcome outcome = access(menu).enchanting2$outcome();
		if (outcome == null) {
			throw new IllegalStateException("the server-side menu has no outcome after its slots were set");
		}
		return outcome;
	}

	private static TablePreviewPayload preview(EnchantmentMenu menu) {
		TablePreviewPayload preview = ((TablePreviewHolder) menu).enchanting2$lastPreview();
		if (preview == null) {
			throw new IllegalStateException("no panel was built");
		}
		return preview;
	}

	private static TableSelection pick(Object... idLevel) {
		TableSelection selection = TableSelection.EMPTY;
		for (int i = 0; i < idLevel.length; i += 2) {
			selection = selection.with((String) idLevel[i], (Integer) idLevel[i + 1]);
		}
		return selection;
	}

	private static boolean select(ServerPlayer player, TableSelection selection) {
		return ServerNetworking.handleTableSelect(player, new TableSelectPayload(CONTAINER, selection));
	}

	private static boolean enchant(ServerPlayer player, TableSelection selection, int levels, int lapis) {
		return ServerNetworking.handleTableEnchant(player, new TableEnchantPayload(CONTAINER, selection, levels, lapis));
	}

	/** The Enchant button with exactly what the panel shows. */
	private static boolean enchantAsShown(ServerPlayer player, EnchantmentMenu menu) {
		TablePreviewPayload shown = preview(menu);
		return enchant(player, shown.selection(), shown.levelsCharged(), shown.lapisCharged());
	}

	private static ItemStack slot(EnchantmentMenu menu, int index) {
		return menu.getSlot(index).getItem();
	}

	private static int lapis(EnchantmentMenu menu) {
		return slot(menu, 1).getCount();
	}

	private static void check(GameTestHelper helper, boolean condition, String what) {
		helper.assertTrue(condition, Component.literal(what));
	}

	private static <T> void equal(GameTestHelper helper, T expected, T actual, String what) {
		helper.assertValueEqual(expected, actual, Component.literal(what));
	}

	// --- vanilla is replaced ---

	@GameTest
	public void noVanillaOffersAndThePanelIsSent(GameTestHelper helper) {
		ServerPlayer player = AnvilGameTest.player(helper, RICH);
		EnchantmentMenu menu = table(helper, player, 15, new ItemStack(Items.DIAMOND_SWORD), 64);
		for (int i = 0; i < 3; i++) {
			equal(helper, 0, menu.costs[i], "vanilla offer " + i + " cost");
			equal(helper, -1, menu.enchantClue[i], "vanilla offer " + i + " clue");
		}
		equal(helper, Status.NO_SELECTION, outcome(menu).status(), "status");
		equal(helper, 15, preview(menu).shelves(), "shelves counted");
		check(helper, preview(menu).rows().stream().anyMatch(r -> r.id().equals(SHARPNESS)), "Sharpness listed");

		check(helper, !menu.clickMenuButton(player, 0), "vanilla's offer button does nothing");
		check(helper, ItemStack.matches(new ItemStack(Items.DIAMOND_SWORD), slot(menu, 0)), "sword untouched");
		equal(helper, 64, lapis(menu), "lapis untouched");
		equal(helper, RICH, player.experienceLevel, "levels untouched");
		helper.succeed();
	}

	@GameTest
	public void shelvesAreCountedLikeVanilla(GameTestHelper helper) {
		ServerPlayer player = AnvilGameTest.player(helper, RICH);
		int[][] placedToCounted = {{0, 0}, {4, 4}, {8, 8}, {15, 15}, {32, 15}};
		for (int[] p : placedToCounted) {
			EnchantmentMenu menu = table(helper, player, p[0], new ItemStack(Items.DIAMOND_SWORD), 64);
			equal(helper, p[1], outcome(menu).shelves(), p[0] + " shelves placed");
		}
		// A block between the table and a shelf hides it, as in vanilla.
		EnchantmentMenu full = table(helper, player, 15, new ItemStack(Items.DIAMOND_SWORD), 64);
		// A corner shelf: no other shelf looks through the same block (edge shelves share theirs).
		BlockPos corner = EnchantingTableBlock.BOOKSHELF_OFFSETS.subList(0, 15).stream()
				.filter(o -> Math.abs(o.getX()) == 2 && Math.abs(o.getZ()) == 2).findFirst().orElseThrow();
		helper.setBlock(TABLE.offset(corner.getX() / 2, corner.getY(), corner.getZ() / 2), Blocks.STONE);
		full.slotsChanged(full.getSlot(0).container);
		equal(helper, 14, outcome(full).shelves(), "a blocked shelf doesn't count");
		helper.succeed();
	}

	// --- the player's equipment (owner 2026-10-13) ---

	/**
	 * The menu has the player's armor and offhand after the inventory, as the inventory screen's own slots: the worn
	 * items, armor only in its own slot, Binding stays on outside creative, and a shift-click moves worn armor onto the
	 * table and back into the inventory (never into another menu slot, so the indices vanilla uses are unchanged).
	 */
	@GameTest
	public void theMenuHasThePlayersArmorAndOffhand(GameTestHelper helper) {
		ServerPlayer player = AnvilGameTest.player(helper, RICH);
		player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
		player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		EnchantmentMenu menu = table(helper, player, 0, ItemStack.EMPTY, 0);

		equal(helper, 43, menu.slots.size(), "item, lapis, 36 inventory, 4 armor, offhand");
		List<EquipmentSlot> order = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
				EquipmentSlot.FEET, EquipmentSlot.OFFHAND);
		for (int i = 0; i < order.size(); i++) {
			Slot slot = menu.getSlot(TableEquipmentSlot.FIRST_MENU_INDEX + i);
			check(helper, slot instanceof TableEquipmentSlot equipment && equipment.equipmentSlot() == order.get(i),
					"menu slot " + (TableEquipmentSlot.FIRST_MENU_INDEX + i) + " is " + order.get(i));
			check(helper, slot.getItem() == player.getItemBySlot(order.get(i)), order.get(i) + " is the worn stack");
		}
		check(helper, slot(menu, 38).is(Items.IRON_HELMET) && slot(menu, 42).is(Items.SHIELD), "helmet and shield");

		check(helper, !menu.getSlot(38).mayPlace(new ItemStack(Items.STICK)), "a stick isn't a helmet");
		check(helper, !menu.getSlot(38).mayPlace(new ItemStack(Items.IRON_BOOTS)), "boots aren't a helmet");
		check(helper, menu.getSlot(41).mayPlace(new ItemStack(Items.IRON_BOOTS)), "boots go on the feet");
		check(helper, menu.getSlot(42).mayPlace(new ItemStack(Items.STICK)), "anything goes in the offhand");

		menu.getSlot(41).setByPlayer(new ItemStack(Items.IRON_BOOTS));
		check(helper, player.getItemBySlot(EquipmentSlot.FEET).is(Items.IRON_BOOTS), "boots put on through the menu");

		// The mock player is always creative (gotchas.md 2026-10-07), so the survival check asks a FakePlayer.
		player.setItemSlot(EquipmentSlot.HEAD, with(helper, Items.IRON_HELMET, Map.of(Enchantments.BINDING_CURSE, 1)));
		ServerPlayer survival = TestPlatform.survivalFakePlayer(helper.getLevel());
		check(helper, !survival.isCreative(), "the FakePlayer is in survival");
		check(helper, !menu.getSlot(38).mayPickup(survival), "Curse of Binding keeps the helmet on");
		check(helper, menu.getSlot(38).mayPickup(player), "but not in creative");

		menu.quickMoveStack(player, 39);
		check(helper, slot(menu, 0).is(Items.DIAMOND_CHESTPLATE), "shift-click puts the worn chestplate on the table");
		check(helper, player.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "and takes it off");
		menu.quickMoveStack(player, 0);
		check(helper, slot(menu, 0).isEmpty() && player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
				&& player.getInventory().contains(new ItemStack(Items.DIAMOND_CHESTPLATE)),
				"shift-click from the table goes to the inventory, as in vanilla");
		helper.succeed();
	}

	// --- enchanting ---

	@GameTest
	public void enchantChargesExactlyWhatThePanelShowed(GameTestHelper helper) {
		ServerPlayer player = AnvilGameTest.player(helper, 30);
		EnchantmentMenu menu = table(helper, player, 15, new ItemStack(Items.DIAMOND_PICKAXE), 64);
		int enchantsBefore = player.getStats().getValue(Stats.CUSTOM.get(Stats.ENCHANT_ITEM));
		TableSelection selection = pick(MENDING, 1, EFFICIENCY, 5);
		check(helper, select(player, selection), "selection accepted");
		equal(helper, selection, access(menu).enchanting2$selection(), "the server holds it");
		equal(helper, 17, preview(menu).levelsCharged(), "panel: levels");
		equal(helper, 17, preview(menu).lapisCharged(), "panel: lapis");
		check(helper, slot(menu, 0).is(Items.DIAMOND_PICKAXE) && !EnchantmentHelper.hasAnyEnchantments(slot(menu, 0)),
				"nothing is applied by selecting");

		check(helper, enchant(player, selection, 17, 17), "enchanted");
		ItemStack pickaxe = slot(menu, 0);
		equal(helper, 5, level(helper, pickaxe, Enchantments.EFFICIENCY), "Efficiency V");
		equal(helper, 1, level(helper, pickaxe, Enchantments.MENDING), "Mending");
		equal(helper, 2, EnchantmentHelper.getEnchantmentsForCrafting(pickaxe).size(), "nothing else");
		equal(helper, 13, player.experienceLevel, "30 - 17 levels");
		equal(helper, 47, lapis(menu), "64 - 17 lapis");
		equal(helper, TableSelection.EMPTY, access(menu).enchanting2$selection(), "selection cleared");
		equal(helper, Status.NO_SELECTION, outcome(menu).status(), "ready for the next pick");
		equal(helper, enchantsBefore + 1, player.getStats().getValue(Stats.CUSTOM.get(Stats.ENCHANT_ITEM)),
				"the vanilla stat");
		helper.succeed();
	}

	@GameTest
	public void booksAndUpgrades(GameTestHelper helper) {
		ServerPlayer player = AnvilGameTest.player(helper, RICH);
		EnchantmentMenu menu = table(helper, player, 15, new ItemStack(Items.BOOK), 64);
		select(player, pick(SHARPNESS, 5, EFFICIENCY, 4));
		check(helper, enchantAsShown(player, menu), "plain book enchanted");
		ItemStack book = slot(menu, 0);
		check(helper, book.is(Items.ENCHANTED_BOOK), "now an enchanted book");
		equal(helper, 5, level(helper, book, Enchantments.SHARPNESS), "Sharpness V stored");
		equal(helper, 4, level(helper, book, Enchantments.EFFICIENCY), "Efficiency IV stored");
		equal(helper, RICH - 9, player.experienceLevel, "9 levels");

		select(player, pick(EFFICIENCY, 5, UNBREAKING, 2));
		check(helper, enchantAsShown(player, menu), "the enchanted book takes more");
		equal(helper, 5, level(helper, slot(menu, 0), Enchantments.EFFICIENCY), "Efficiency raised");
		equal(helper, 2, level(helper, slot(menu, 0), Enchantments.UNBREAKING), "Unbreaking added");
		equal(helper, 5, level(helper, slot(menu, 0), Enchantments.SHARPNESS), "Sharpness kept");
		equal(helper, RICH - 9 - 3, player.experienceLevel, "1 + 2 levels");

		EnchantmentMenu sword = table(helper, player, 15,
				with(helper, Items.DIAMOND_SWORD, Map.of(Enchantments.SHARPNESS, 2)), 64);
		select(player, pick(SHARPNESS, 4));
		equal(helper, 2, preview(sword).levelsCharged(), "Sharpness II to IV costs 2");
		check(helper, enchantAsShown(player, sword), "upgraded");
		equal(helper, 4, level(helper, slot(sword, 0), Enchantments.SHARPNESS), "Sharpness IV");
		helper.succeed();
	}

	// --- refusals ---

	/** Every forged or stale payload: nothing charged, nothing consumed, the item unchanged. */
	@GameTest
	public void forgedPayloadsChangeNothing(GameTestHelper helper) {
		ServerPlayer player = AnvilGameTest.player(helper, RICH);
		EnchantmentMenu menu = table(helper, player, 4, new ItemStack(Items.DIAMOND_SWORD), 64);
		ItemStack sword = slot(menu, 0).copy();
		Runnable unchanged = () -> {
			check(helper, ItemStack.matches(sword, slot(menu, 0)), "sword unchanged");
			equal(helper, 64, lapis(menu), "lapis unchanged");
			equal(helper, RICH, player.experienceLevel, "levels unchanged");
		};

		check(helper, !ServerNetworking.handleTableSelect(player, new TableSelectPayload(CONTAINER + 1,
				pick(SHARPNESS, 1))), "a selection for another container is ignored");
		equal(helper, TableSelection.EMPTY, access(menu).enchanting2$selection(), "nothing stored");
		check(helper, !ServerNetworking.handleTableEnchant(player, new TableEnchantPayload(CONTAINER + 1,
				TableSelection.EMPTY, 0, 0)), "an enchant for another container is ignored");

		List<TableSelection> forged = List.of(pick(EFFICIENCY, 1), pick(SHARPNESS, 6), pick(SHARPNESS, 3),
				pick("minecraft:soul_speed", 1), pick(MENDING, 1), pick("e2test:nope", 1), pick(SHARPNESS, 2, EFFICIENCY, 1));
		for (TableSelection selection : forged) {
			check(helper, select(player, selection), "stored: " + selection);
			equal(helper, Status.INVALID_PICK, outcome(menu).status(), "invalid: " + selection);
			check(helper, !enchant(player, selection, 0, 0), "refused at 0: " + selection);
			check(helper, !enchant(player, selection, 2, 2), "refused at 2: " + selection);
			unchanged.run();
		}

		TableSelection valid = pick(SHARPNESS, 2);
		select(player, valid);
		equal(helper, 2, preview(menu).levelsCharged(), "Sharpness II costs 2 at 4 shelves");
		check(helper, !enchant(player, valid, 1, 2), "stale levels");
		check(helper, !enchant(player, valid, 2, 1), "stale lapis");
		check(helper, !enchant(player, valid, 3, 3), "overpaying is refused too");
		check(helper, !enchant(player, pick(SHARPNESS, 1), 1, 1), "a selection other than the stored one");
		unchanged.run();
		TablePreviewPayload before = preview(menu);
		check(helper, !enchant(player, valid, 1, 1), "stale again");
		check(helper, before != preview(menu), "a refused enchant resends the panel");

		player.containerMenu = player.inventoryMenu;
		check(helper, !enchant(player, valid, 2, 2), "the table is closed");
		unchanged.run();
		helper.succeed();
	}

	@GameTest
	public void insufficientLevelsOrLapisAreRefused(GameTestHelper helper) {
		TableSelection seventeen = pick(MENDING, 1, EFFICIENCY, 5);
		ServerPlayer poor = AnvilGameTest.player(helper, 16);
		EnchantmentMenu menu = table(helper, poor, 15, new ItemStack(Items.DIAMOND_PICKAXE), 64);
		select(poor, seventeen);
		equal(helper, Status.INSUFFICIENT_LEVELS, outcome(menu).status(), "16 levels");
		check(helper, !enchant(poor, seventeen, 17, 17), "refused");
		equal(helper, 16, poor.experienceLevel, "levels unchanged");
		equal(helper, 64, lapis(menu), "lapis unchanged");

		ServerPlayer rich = AnvilGameTest.player(helper, RICH);
		EnchantmentMenu short16 = table(helper, rich, 15, new ItemStack(Items.DIAMOND_PICKAXE), 16);
		select(rich, seventeen);
		equal(helper, Status.INSUFFICIENT_LAPIS, outcome(short16).status(), "16 lapis");
		check(helper, !enchant(rich, seventeen, 17, 17), "refused");
		equal(helper, RICH, rich.experienceLevel, "levels unchanged");
		equal(helper, 16, lapis(short16), "lapis unchanged");

		// The player gains levels while the panel says insufficient: the server's own check decides, not the panel.
		poor.experienceLevel = 17;
		check(helper, enchant(poor, seventeen, 17, 17), "enough levels now");
		equal(helper, 0, poor.experienceLevel, "17 - 17");
		helper.succeed();
	}

	@GameTest
	public void aShelfBrokenAfterSelectingBlocksTheEnchant(GameTestHelper helper) {
		ServerPlayer player = AnvilGameTest.player(helper, RICH);
		EnchantmentMenu menu = table(helper, player, 15, new ItemStack(Items.DIAMOND_PICKAXE), 64);
		TableSelection mending = pick(MENDING, 1);
		select(player, mending);
		equal(helper, Status.OK, outcome(menu).status(), "Mending at 15 shelves");
		shelves(helper, 14);
		check(helper, !enchant(player, mending, 2, 2), "refused at 14 shelves");
		equal(helper, Status.INVALID_PICK, preview(menu).status(), "the panel now says why");
		equal(helper, TableRow.Status.NEEDS_FULL_POWER, preview(menu).invalid().orElseThrow().reason(), "reason");
		check(helper, !EnchantmentHelper.hasAnyEnchantments(slot(menu, 0)), "pickaxe unchanged");
		equal(helper, RICH, player.experienceLevel, "levels unchanged");
		equal(helper, 64, lapis(menu), "lapis unchanged");
		helper.succeed();
	}

	@GameTest
	public void selectionIsForTheItemItWasMadeOn(GameTestHelper helper) {
		ServerPlayer player = AnvilGameTest.player(helper, RICH);
		EnchantmentMenu menu = table(helper, player, 15, new ItemStack(Items.DIAMOND_SWORD), 64);
		TableSelection sharp = pick(SHARPNESS, 5);
		select(player, sharp);
		menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 10));
		equal(helper, sharp, access(menu).enchanting2$selection(), "a lapis change keeps it");
		menu.getSlot(0).set(with(helper, Items.DIAMOND_SWORD, Map.of(Enchantments.UNBREAKING, 1)));
		equal(helper, TableSelection.EMPTY, access(menu).enchanting2$selection(), "another item clears it");
		check(helper, !enchant(player, sharp, 5, 5), "the old selection can't be enchanted onto the new item");
		equal(helper, 0, level(helper, slot(menu, 0), Enchantments.SHARPNESS), "new item unchanged");
		helper.succeed();
	}

	@GameTest
	public void aTableWithNoBlockOffersNothing(GameTestHelper helper) {
		ServerPlayer player = AnvilGameTest.player(helper, RICH);
		EnchantmentMenu menu = new EnchantmentMenu(CONTAINER, player.getInventory(), ContainerLevelAccess.NULL);
		player.containerMenu = menu;
		menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 64));
		menu.getSlot(0).set(new ItemStack(Items.DIAMOND_SWORD));
		select(player, pick(SHARPNESS, 1));
		equal(helper, Status.NONE, outcome(menu).status(), "status");
		check(helper, !enchant(player, pick(SHARPNESS, 1), 1, 1), "refused");
		equal(helper, RICH, player.experienceLevel, "levels unchanged");
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
		List<TableRow> rows = new ArrayList<>();
		for (TableRow.Status status : TableRow.Status.values()) {
			if (status == TableRow.Status.NOT_OFFERED) {
				continue;
			}
			rows.add(new TableRow("minecraft:" + status.name().toLowerCase(), 2, 255, 200, 3, status,
					status == TableRow.Status.CONFLICT ? Optional.of(SHARPNESS) : Optional.empty()));
		}
		TableSelection selection = pick(SHARPNESS, 255, MENDING, 1);
		List<CostBreakdown.Line> lines = new ArrayList<>();
		for (CostBreakdown.Kind kind : CostBreakdown.Kind.values()) {
			lines.add(new CostBreakdown.Line(kind, kind == CostBreakdown.Kind.ENCHANT ? SHARPNESS : "", 1, 5,
					kind == CostBreakdown.Kind.CURSE_DISCOUNT ? -3 : 40_000));
		}
		long sum = lines.stream().mapToLong(CostBreakdown.Line::amount).sum();
		long enchantSum = lines.stream().filter(l -> l.kind() == CostBreakdown.Kind.ENCHANT
				|| l.kind() == CostBreakdown.Kind.SURCHARGE || l.kind() == CostBreakdown.Kind.CURSE_DISCOUNT)
				.mapToLong(CostBreakdown.Line::amount).sum();
		CostBreakdown cost = new CostBreakdown(lines, (int) enchantSum, (int) sum, 64, true, false);
		for (Status status : Status.values()) {
			Optional<TableOutcome.Invalid> invalid = status == Status.INVALID_PICK
					? Optional.of(new TableOutcome.Invalid(EFFICIENCY, 3, TableRow.Status.CONFLICT, Optional.of(SHARPNESS)))
					: Optional.empty();
			TablePreviewPayload sent = new TablePreviewPayload(9, selection, status, rows, invalid,
					status.priced() ? Optional.of(cost) : Optional.empty(), 15);
			equal(helper, sent, roundTrip(helper, TablePreviewPayload.CODEC, sent), "preview with " + status);
		}
		for (TableRow.Status reason : List.of(TableRow.Status.NOT_OFFERED, TableRow.Status.ABOVE_MAX)) {
			TablePreviewPayload sent = new TablePreviewPayload(9, selection, Status.INVALID_PICK, List.of(),
					Optional.of(new TableOutcome.Invalid("e2test:nope", 1, reason, Optional.empty())), Optional.empty(), 0);
			equal(helper, sent, roundTrip(helper, TablePreviewPayload.CODEC, sent), "invalid " + reason);
		}
		TableSelectPayload select = new TableSelectPayload(3, selection);
		equal(helper, select, roundTrip(helper, TableSelectPayload.CODEC, select), "select");
		TableEnchantPayload enchant = new TableEnchantPayload(3, selection, Integer.MAX_VALUE, 64);
		equal(helper, enchant, roundTrip(helper, TableEnchantPayload.CODEC, enchant), "enchant");
		TableSelectPayload empty = new TableSelectPayload(3, TableSelection.EMPTY);
		equal(helper, empty, roundTrip(helper, TableSelectPayload.CODEC, empty), "empty selection");
		helper.succeed();
	}

	/** Level 0, or more picks than any registry holds, is a decode error, so it never reaches the menu. */
	@GameTest
	public void malformedSelectionsFailDecode(GameTestHelper helper) {
		FriendlyByteBuf zero = new FriendlyByteBuf(Unpooled.buffer());
		ByteBufCodecs.VAR_INT.encode(zero, 1); // container
		ByteBufCodecs.VAR_INT.encode(zero, 1); // one pick
		ByteBufCodecs.stringUtf8(1024).encode(zero, SHARPNESS);
		ByteBufCodecs.VAR_INT.encode(zero, 0); // level 0
		check(helper, decodeFails(zero), "level 0 decoded");

		FriendlyByteBuf many = new FriendlyByteBuf(Unpooled.buffer());
		ByteBufCodecs.VAR_INT.encode(many, 1);
		ByteBufCodecs.VAR_INT.encode(many, TableSelection.MAX_PICKS + 1);
		for (int i = 0; i <= TableSelection.MAX_PICKS; i++) {
			ByteBufCodecs.stringUtf8(1024).encode(many, "test:e" + i);
			ByteBufCodecs.VAR_INT.encode(many, 1);
		}
		check(helper, decodeFails(many), MAX_PICKS_MESSAGE);

		Map<String, Integer> tooMany = new HashMap<>();
		for (int i = 0; i <= TableSelection.MAX_PICKS; i++) {
			tooMany.put("test:e" + i, 1);
		}
		try {
			new TableSelection(tooMany);
			helper.fail("an oversize selection was built");
		} catch (IllegalArgumentException expected) {
			helper.succeed();
		}
	}

	private static final String MAX_PICKS_MESSAGE = "more than " + TableSelection.MAX_PICKS + " picks decoded";

	private static boolean decodeFails(FriendlyByteBuf buf) {
		try {
			TableSelectPayload.CODEC.decode(buf);
			return false;
		} catch (RuntimeException expected) {
			return true;
		}
	}

	// --- property ---

	private static final List<Item> TARGETS = List.of(Items.DIAMOND_PICKAXE, Items.DIAMOND_SWORD, Items.BOW,
			Items.DIAMOND_CHESTPLATE, Items.DIAMOND_BOOTS, Items.BOOK, Items.ENCHANTED_BOOK);

	/**
	 * Through the real menu and handlers, with random items, existing enchantments, shelves, levels, lapis and
	 * selections (mostly from the panel, sometimes forged): an enchant that goes through charges exactly what the panel
	 * showed, keeps every prior (enchantment, level) at least as high, and adds exactly the picks. One that doesn't
	 * changes nothing.
	 */
	@GameTest(maxTicks = 600)
	public void anEnchantChargesThePanelAndKeepsEverything(GameTestHelper helper) {
		List<Holder<Enchantment>> all = new ArrayList<>(helper.getLevel().registryAccess()
				.lookupOrThrow(Registries.ENCHANTMENT).listElements().map(h -> (Holder<Enchantment>) h).toList());
		all.sort(Comparator.comparing(Holder::getRegisteredName));
		Random random = new Random(0x7AB1EL);
		ServerPlayer player = AnvilGameTest.player(helper, 0);
		int enchanted = 0;
		for (int i = 0; i < 300; i++) {
			ItemStack item = randomlyEnchanted(TARGETS.get(random.nextInt(TARGETS.size())), all, random);
			int shelves = random.nextInt(17);
			int lapisIn = random.nextInt(65);
			int levels = random.nextInt(60);
			player.experienceLevel = levels;
			EnchantmentMenu menu = table(helper, player, shelves, item.copy(), lapisIn);
			TableSelection selection = randomSelection(preview(menu), all, random);
			select(player, selection);
			TablePreviewPayload shown = preview(menu);
			String label = "case " + i + " (" + item + ", " + shelves + " shelves, " + lapisIn + " lapis, " + levels
					+ " levels, " + selection + " -> " + shown.status() + ")";

			boolean done = enchantAsShown(player, menu);
			ItemStack after = slot(menu, 0);
			check(helper, done == (shown.status() == Status.OK), label + ": enchanted " + done);
			if (!done) {
				check(helper, ItemStack.matches(item, after), label + ": item changed without an enchant");
				equal(helper, levels, player.experienceLevel, label + ": levels changed without an enchant");
				equal(helper, lapisIn, lapis(menu), label + ": lapis changed without an enchant");
				continue;
			}
			enchanted++;
			equal(helper, levels - shown.levelsCharged(), player.experienceLevel, label + ": levels");
			equal(helper, lapisIn - shown.lapisCharged(), lapis(menu), label + ": lapis");
			Map<String, Integer> expected = entries(item);
			selection.levels().forEach(expected::put);
			equal(helper, expected, entries(after), label + ": entries are the item's plus exactly the picks");
			entries(item).forEach((id, lvl) -> check(helper, entries(after).getOrDefault(id, 0) >= lvl,
					label + ": " + id + " " + lvl + " lowered or lost"));
		}
		// Guards the generator: a property that never enchants checks nothing.
		check(helper, enchanted >= 60, "only " + enchanted + " of 300 cases enchanted");
		helper.succeed();
	}

	private static Map<String, Integer> entries(ItemStack stack) {
		Map<String, Integer> levels = new HashMap<>();
		EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet()
				.forEach(e -> levels.put(e.getKey().getRegisteredName(), e.getIntValue()));
		return levels;
	}

	/** Mostly picks from the panel's pickable rows at a level it allows; sometimes any row or any enchantment. */
	private static TableSelection randomSelection(TablePreviewPayload panel, List<Holder<Enchantment>> all,
			Random random) {
		TableSelection selection = TableSelection.EMPTY;
		int picks = 1 + random.nextInt(3);
		for (int p = 0; p < picks; p++) {
			int kind = random.nextInt(10);
			if (kind < 8 && !panel.rows().isEmpty()) {
				TableRow row = panel.rows().get(random.nextInt(panel.rows().size()));
				int low = row.currentLevel() + 1;
				int high = Math.max(low, row.maxSelectable());
				selection = selection.with(row.id(), Math.min(TableSelection.MAX_LEVEL,
						kind < 7 ? low + random.nextInt(high - low + 1) : 1 + random.nextInt(row.maxLevel() + 1)));
			} else {
				Holder<Enchantment> any = all.get(random.nextInt(all.size()));
				selection = selection.with(any.getRegisteredName(), 1 + random.nextInt(any.value().getMaxLevel()));
			}
		}
		return selection;
	}

	private static ItemStack randomlyEnchanted(Item item, List<Holder<Enchantment>> all, Random random) {
		ItemStack stack = new ItemStack(item);
		if (item == Items.BOOK) {
			return stack;
		}
		ItemEnchantments.Mutable m = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		int n = item == Items.ENCHANTED_BOOK ? 1 + random.nextInt(3) : random.nextInt(4);
		for (int i = 0; i < n; i++) {
			Holder<Enchantment> enchantment = all.get(random.nextInt(all.size()));
			m.set(enchantment, 1 + random.nextInt(enchantment.value().getMaxLevel()));
		}
		EnchantmentHelper.setEnchantments(stack, m.toImmutable());
		return stack;
	}
}
