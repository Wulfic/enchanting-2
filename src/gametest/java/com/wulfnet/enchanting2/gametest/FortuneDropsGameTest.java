package com.wulfnet.enchanting2.gametest;

import com.wulfnet.enchanting2.fortune.FortuneDrops;
import com.wulfnet.enchanting2.fortune.FortuneTags;
import com.wulfnet.enchanting2.fortune.PlacedBlocks;
import com.wulfnet.enchanting2.fortune.PlacedMarks;
import com.wulfnet.enchanting2.fortune.SilkFortuneMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * DESIGN §6 Fortune drops (Phase 8D), rolled through the real loot tables and our {@code MODIFY_DROPS} handler (live
 * config: SILK_WINS). FORTUNE_MULTIPLIES_SILK is tested through {@link FortuneDrops#apply} with the mode given, since
 * GameTests run in parallel and must not flip the server's config. Fortune V gives ×1 two times in seven, so each
 * "multiplies" test rolls {@value #ROLLS} times: an unmultiplied total would happen with odds (2/7)^20 ≈ 10⁻¹¹.
 * The GameTest datapack adds ancient debris to the pickaxes tag, to prove the hard exclusion beats the tags.
 */
public class FortuneDropsGameTest {
	private static final int ROLLS = 20;
	private static final int LEAF_ROLLS = 100;
	private static final BlockPos POS = new BlockPos(1, 1, 1);

	private static Holder<Enchantment> get(GameTestHelper helper, ResourceKey<Enchantment> key) {
		return helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	/** {@code item} with each key at its registry max (Fortune V, DESIGN §8). */
	@SafeVarargs
	private static ItemStack tool(GameTestHelper helper, Item item, ResourceKey<Enchantment>... keys) {
		ItemStack stack = new ItemStack(item);
		for (ResourceKey<Enchantment> key : keys) {
			Holder<Enchantment> holder = get(helper, key);
			stack.enchant(holder, holder.value().getMaxLevel());
		}
		return stack;
	}

	/** The block's drops at {@link #POS} with {@code tool}, through vanilla's {@code Block.getDrops}. */
	private static List<ItemStack> drops(GameTestHelper helper, BlockState state, ItemStack tool) {
		helper.setBlock(POS, state);
		ServerLevel level = helper.getLevel();
		return Block.getDrops(state, level, helper.absolutePos(POS), null, null, tool);
	}

	/** How many of {@code item} dropped; any other drop fails the test. */
	private static int count(GameTestHelper helper, List<ItemStack> stacks, Item item) {
		int total = 0;
		for (ItemStack stack : stacks) {
			helper.assertTrue(stack.is(item), Component.literal("unexpected drop " + stack + ", expected " + item));
			total += stack.getCount();
		}
		return total;
	}

	/** Totals over {@link #ROLLS} rolls; each roll's count is collected so single rolls can be checked too. */
	private static List<Integer> rolls(GameTestHelper helper, BlockState state, ItemStack tool, Item item) {
		List<Integer> counts = new ArrayList<>();
		for (int i = 0; i < ROLLS; i++) {
			counts.add(count(helper, drops(helper, state, tool), item));
		}
		return counts;
	}

	private static int sum(List<Integer> counts) {
		return counts.stream().mapToInt(Integer::intValue).sum();
	}

	private static void assertMultiplied(GameTestHelper helper, List<Integer> counts, int base, String what) {
		for (int count : counts) {
			helper.assertTrue(count % base == 0 && count >= base && count <= 6 * base,
					Component.literal(what + ": a roll gave " + count + " (expected " + base + "×1..6)"));
		}
		helper.assertTrue(sum(counts) > base * ROLLS,
				Component.literal(what + ": never multiplied over " + ROLLS + " rolls: " + counts));
	}

	private static void assertSingle(GameTestHelper helper, List<Integer> counts, int expected, String what) {
		for (int count : counts) {
			helper.assertValueEqual(expected, count, Component.literal(what + " (rolls: " + counts + ")"));
		}
	}

	private static void markPos(GameTestHelper helper) {
		PlacedBlocks.mark(helper.getLevel(), helper.absolutePos(POS));
	}

	@GameTest
	public void naturalLogMultipliesWithAFortuneAxe(GameTestHelper helper) {
		assertMultiplied(helper, rolls(helper, Blocks.OAK_LOG.defaultBlockState(),
				tool(helper, Items.IRON_AXE, Enchantments.FORTUNE), Items.OAK_LOG), 1, "natural oak log");
		helper.succeed();
	}

	@GameTest
	public void placedLogNeverMultiplies(GameTestHelper helper) {
		markPos(helper);
		assertSingle(helper, rolls(helper, Blocks.OAK_LOG.defaultBlockState(),
				tool(helper, Items.IRON_AXE, Enchantments.FORTUNE), Items.OAK_LOG), 1, "placed oak log");
		helper.succeed();
	}

	/**
	 * The whole loop DESIGN §6 is about, through a survival player: place a log, mine it with Fortune V, repeat. Each
	 * break must drop exactly one log, which also proves the mark is read before the break clears it.
	 */
	@GameTest
	public void placeAndMineLoopPaysNothing(GameTestHelper helper) {
		helper.setBlock(POS.below(), Blocks.STONE);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		BlockPos absolute = helper.absolutePos(POS);
		for (int i = 0; i < 10; i++) {
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.OAK_LOG));
			BlockPos below = absolute.below();
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(below).add(0, 0.5, 0), Direction.UP, below, false);
			player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
			helper.assertBlockPresent(Blocks.OAK_LOG, POS);
			player.setItemInHand(InteractionHand.MAIN_HAND, tool(helper, Items.DIAMOND_AXE, Enchantments.FORTUNE));
			helper.assertTrue(player.gameMode.destroyBlock(absolute), Component.literal("break refused"));
		}
		int logs = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(2)).stream()
				.filter(item -> item.getItem().is(Items.OAK_LOG)).mapToInt(item -> item.getItem().getCount()).sum();
		helper.assertValueEqual(10, logs, Component.literal("logs from 10 place-and-mine loops"));
		helper.succeed();
	}

	@GameTest
	public void naturalGravelMultipliesItsFlint(GameTestHelper helper) {
		// Vanilla gives flint 100% of the time from Fortune III up.
		assertMultiplied(helper, rolls(helper, Blocks.GRAVEL.defaultBlockState(),
				tool(helper, Items.IRON_SHOVEL, Enchantments.FORTUNE), Items.FLINT), 1, "natural gravel");
		helper.succeed();
	}

	@GameTest
	public void naturalClayMultipliesItsClayBalls(GameTestHelper helper) {
		assertMultiplied(helper, rolls(helper, Blocks.CLAY.defaultBlockState(),
				tool(helper, Items.IRON_SHOVEL, Enchantments.FORTUNE), Items.CLAY_BALL), 4, "natural clay");
		helper.succeed();
	}

	/** Owner, 2026-10-12: soil blocks joined the shovels tag. Grass, podzol and mycelium drop dirt; the rest themselves. */
	@GameTest
	public void naturalSoilMultipliesWithAFortuneShovel(GameTestHelper helper) {
		ItemStack shovel = tool(helper, Items.IRON_SHOVEL, Enchantments.FORTUNE);
		assertMultiplied(helper, rolls(helper, Blocks.GRASS_BLOCK.defaultBlockState(), shovel, Items.DIRT), 1, "grass");
		assertMultiplied(helper, rolls(helper, Blocks.PODZOL.defaultBlockState(), shovel, Items.DIRT), 1, "podzol");
		assertMultiplied(helper, rolls(helper, Blocks.MYCELIUM.defaultBlockState(), shovel, Items.DIRT), 1, "mycelium");
		assertMultiplied(helper, rolls(helper, Blocks.COARSE_DIRT.defaultBlockState(), shovel, Items.COARSE_DIRT), 1,
				"coarse dirt");
		assertMultiplied(helper, rolls(helper, Blocks.ROOTED_DIRT.defaultBlockState(), shovel, Items.ROOTED_DIRT), 1,
				"rooted dirt");
		helper.succeed();
	}

	@GameTest
	public void placedGrassNeverMultiplies(GameTestHelper helper) {
		markPos(helper);
		assertSingle(helper, rolls(helper, Blocks.GRASS_BLOCK.defaultBlockState(),
				tool(helper, Items.IRON_SHOVEL, Enchantments.FORTUNE), Items.DIRT), 1, "placed grass");
		helper.succeed();
	}

	/** Owner, 2026-10-12: bamboo (the plant) joined the axes tag. */
	@GameTest
	public void naturalBambooMultipliesWithAFortuneAxe(GameTestHelper helper) {
		helper.setBlock(POS.below(), Blocks.DIRT);
		assertMultiplied(helper, rolls(helper, Blocks.BAMBOO.defaultBlockState(),
				tool(helper, Items.IRON_AXE, Enchantments.FORTUNE), Items.BAMBOO), 1, "natural bamboo");
		helper.succeed();
	}

	/**
	 * Owner, 2026-10-12: {@code #minecraft:leaves} joined the hoes tag. Leaves drop a random mix, so this looks for
	 * what vanilla can never give from one block: two saplings or three sticks. At Fortune V a roll does that about
	 * 13% of the time, so none in {@value #LEAF_ROLLS} has odds near 10⁻⁶; placed leaves must never do it.
	 */
	@GameTest
	public void naturalLeavesMultiplyWithAFortuneHoe(GameTestHelper helper) {
		ItemStack hoe = tool(helper, Items.IRON_HOE, Enchantments.FORTUNE);
		BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState();
		boolean beyondVanilla = false;
		for (int i = 0; i < LEAF_ROLLS && !beyondVanilla; i++) {
			beyondVanilla = beyondVanilla(drops(helper, leaves, hoe));
		}
		helper.assertTrue(beyondVanilla, Component.literal("natural oak leaves never multiplied in " + LEAF_ROLLS));
		markPos(helper);
		for (int i = 0; i < LEAF_ROLLS; i++) {
			List<ItemStack> stacks = drops(helper, leaves, hoe);
			helper.assertFalse(beyondVanilla(stacks), Component.literal("placed oak leaves multiplied: " + stacks));
		}
		helper.succeed();
	}

	private static boolean beyondVanilla(List<ItemStack> stacks) {
		int saplings = stacks.stream().filter(s -> s.is(Items.OAK_SAPLING)).mapToInt(ItemStack::getCount).sum();
		int sticks = stacks.stream().filter(s -> s.is(Items.STICK)).mapToInt(ItemStack::getCount).sum();
		return saplings > 1 || sticks > 2;
	}

	@GameTest
	public void theWrongToolKindGivesNoBonus(GameTestHelper helper) {
		assertSingle(helper, rolls(helper, Blocks.OAK_LOG.defaultBlockState(),
				tool(helper, Items.IRON_HOE, Enchantments.FORTUNE), Items.OAK_LOG), 1, "oak log with a Fortune hoe");
		assertSingle(helper, rolls(helper, Blocks.OAK_LOG.defaultBlockState(), new ItemStack(Items.IRON_AXE),
				Items.OAK_LOG), 1, "oak log with a plain axe");
		helper.succeed();
	}

	/** Owner, 2026-10-08: SILK_WINS still multiplies where Silk Touch doesn't change the drop. */
	@GameTest
	public void silkWinsOnlyWhereSilkChangesTheDrop(GameTestHelper helper) {
		assertMultiplied(helper, rolls(helper, Blocks.OAK_LOG.defaultBlockState(),
				tool(helper, Items.IRON_AXE, Enchantments.SILK_TOUCH, Enchantments.FORTUNE), Items.OAK_LOG), 1,
				"oak log, Silk + Fortune");
		assertSingle(helper, rolls(helper, Blocks.CLAY.defaultBlockState(),
				tool(helper, Items.IRON_SHOVEL, Enchantments.SILK_TOUCH, Enchantments.FORTUNE), Items.CLAY), 1,
				"clay, Silk + Fortune");
		helper.succeed();
	}

	/** {@link FortuneDrops#apply} under FORTUNE_MULTIPLIES_SILK, on drops vanilla (and SILK_WINS) left alone. */
	private static List<Integer> rollsMultipliesSilk(GameTestHelper helper, BlockState state, ItemStack tool, Item item) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(POS, state);
		ResourceKey<LootTable> key = state.getBlock().getLootTable().orElseThrow();
		Holder<LootTable> table = level.getServer().reloadableRegistries().lookup()
				.lookupOrThrow(Registries.LOOT_TABLE).getOrThrow(key);
		List<Integer> counts = new ArrayList<>();
		for (int i = 0; i < ROLLS; i++) {
			LootParams params = new LootParams.Builder(level)
					.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(helper.absolutePos(POS)))
					.withParameter(LootContextParams.TOOL, tool)
					.withParameter(LootContextParams.BLOCK_STATE, state)
					.create(LootContextParamSets.BLOCK);
			List<ItemStack> drops = new ArrayList<>(table.value().getRandomItems(params));
			FortuneDrops.apply(table, new LootContext.Builder(params).create(Optional.empty()), drops,
					SilkFortuneMode.FORTUNE_MULTIPLIES_SILK);
			counts.add(count(helper, drops, item));
		}
		return counts;
	}

	@GameTest
	public void fortuneMultipliesSilkMultipliesNaturalOreBlocksOnly(GameTestHelper helper) {
		ItemStack pickaxe = tool(helper, Items.DIAMOND_PICKAXE, Enchantments.SILK_TOUCH, Enchantments.FORTUNE);
		BlockState ore = Blocks.DIAMOND_ORE.defaultBlockState();
		assertSingle(helper, rolls(helper, ore, pickaxe, Items.DIAMOND_ORE), 1, "silk diamond ore under SILK_WINS");
		assertMultiplied(helper, rollsMultipliesSilk(helper, ore, pickaxe, Items.DIAMOND_ORE), 1,
				"natural diamond ore under FORTUNE_MULTIPLIES_SILK");
		markPos(helper);
		assertSingle(helper, rollsMultipliesSilk(helper, ore, pickaxe, Items.DIAMOND_ORE), 1,
				"placed diamond ore under FORTUNE_MULTIPLIES_SILK");
		helper.succeed();
	}

	@GameTest
	public void ancientDebrisIsNeverMultiplied(GameTestHelper helper) {
		BlockState debris = Blocks.ANCIENT_DEBRIS.defaultBlockState();
		helper.assertTrue(FortuneTags.inAnyTag(debris),
				Component.literal("the test datapack didn't tag ancient debris, so this proves nothing"));
		assertSingle(helper, rolls(helper, debris, tool(helper, Items.DIAMOND_PICKAXE, Enchantments.FORTUNE),
				Items.ANCIENT_DEBRIS), 1, "natural ancient debris, Fortune pickaxe");
		assertSingle(helper, rollsMultipliesSilk(helper, debris,
				tool(helper, Items.DIAMOND_PICKAXE, Enchantments.SILK_TOUCH, Enchantments.FORTUNE), Items.ANCIENT_DEBRIS),
				1, "natural ancient debris under FORTUNE_MULTIPLIES_SILK");
		helper.succeed();
	}

	@GameTest
	public void anUnreadableChunkGivesNoBonus(GameTestHelper helper) {
		LevelChunk chunk = helper.getLevel().getChunkAt(helper.absolutePos(POS));
		PlacedMarks before = PlacedBlocks.CHUNK.get(chunk);
		PlacedBlocks.CHUNK.set(chunk, PlacedMarks.unreadable("test"));
		try {
			assertSingle(helper, rolls(helper, Blocks.OAK_LOG.defaultBlockState(),
					tool(helper, Items.IRON_AXE, Enchantments.FORTUNE), Items.OAK_LOG), 1, "oak log in an unreadable chunk");
		} finally {
			PlacedBlocks.CHUNK.set(chunk, before);
		}
		helper.succeed();
	}

	/** Only the broken block's own table: dirt (shovel-tagged) with oak log's table is left alone. */
	@GameTest
	public void anotherBlocksTableIsLeftAlone(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockState dirt = Blocks.DIRT.defaultBlockState();
		helper.setBlock(POS, dirt);
		Holder<LootTable> logTable = level.getServer().reloadableRegistries().lookup()
				.lookupOrThrow(Registries.LOOT_TABLE).getOrThrow(Blocks.OAK_LOG.getLootTable().orElseThrow());
		for (int i = 0; i < ROLLS; i++) {
			LootParams params = new LootParams.Builder(level)
					.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(helper.absolutePos(POS)))
					.withParameter(LootContextParams.TOOL, tool(helper, Items.IRON_SHOVEL, Enchantments.FORTUNE))
					.withParameter(LootContextParams.BLOCK_STATE, dirt)
					.create(LootContextParamSets.BLOCK);
			List<ItemStack> drops = new ArrayList<>(List.of(new ItemStack(Items.OAK_LOG)));
			FortuneDrops.apply(logTable, new LootContext.Builder(params).create(Optional.empty()), drops,
					SilkFortuneMode.SILK_WINS);
			helper.assertValueEqual(1, count(helper, drops, Items.OAK_LOG),
					Component.literal("drops from another block's table"));
		}
		helper.succeed();
	}

	@GameTest
	public void bigStacksSplitAtTheMaxStackSize(GameTestHelper helper) {
		List<ItemStack> drops = new ArrayList<>(List.of(new ItemStack(Items.CLAY_BALL, 40), new ItemStack(Items.FLINT)));
		helper.assertValueEqual(List.of(64, 64, 64, 48, 6),
				FortuneDrops.multiply(drops, 6).stream().map(ItemStack::getCount).toList(),
				Component.literal("stack sizes of 40 clay balls and 1 flint, ×6"));
		helper.succeed();
	}
}
