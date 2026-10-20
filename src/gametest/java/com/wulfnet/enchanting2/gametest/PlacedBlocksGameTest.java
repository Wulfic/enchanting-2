package com.wulfnet.enchanting2.gametest;

import com.wulfnet.enchanting2.fortune.FortuneRule.Origin;
import com.wulfnet.enchanting2.fortune.PlacedBlocks;
import com.wulfnet.enchanting2.fortune.PlacedMarks;
import com.wulfnet.enchanting2.fortune.PlacedMarksCodec;
import com.wulfnet.enchanting2.gametest.mixin.MobAccessor;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.ShulkerBoxDispenseBehavior;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * DESIGN §6's placed-block tracker against the live game (Phase 8C). Positions are relative to the test. Marks are
 * read back through {@link PlacedBlocks#origin}, the call the Fortune drop hook uses. The GameTest datapack adds
 * obsidian and stone (pickaxes) and snow layers (shovels) to the Fortune tags, so the tag-gated hooks have something
 * to mark.
 */
public class PlacedBlocksGameTest {
	private static Origin origin(GameTestHelper helper, BlockPos relative) {
		return PlacedBlocks.origin(helper.getLevel(), helper.absolutePos(relative));
	}

	private static void assertOrigin(GameTestHelper helper, Origin expected, BlockPos relative, String what) {
		helper.assertValueEqual(expected, origin(helper, relative), Component.literal(what + " at " + relative));
	}

	private static void mark(GameTestHelper helper, BlockPos relative) {
		PlacedBlocks.mark(helper.getLevel(), helper.absolutePos(relative));
	}

	private static void floor(GameTestHelper helper, int size) {
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
	}

	/** A player places {@code item} on top of the block at {@code below}, the way a right-click does. */
	private static InteractionResult placeOnTop(GameTestHelper helper, ServerPlayer player, ItemStack item,
			BlockPos below) {
		player.setItemInHand(InteractionHand.MAIN_HAND, item);
		BlockPos absolute = helper.absolutePos(below);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute).add(0, 0.5, 0), Direction.UP, absolute, false);
		return item.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
	}

	@GameTest
	public void playerPlacementMarksAndBreakClears(GameTestHelper helper) {
		floor(helper, 3);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos placed = new BlockPos(1, 1, 1);
		assertOrigin(helper, Origin.NATURAL, placed, "before placing");
		helper.assertTrue(placeOnTop(helper, player, new ItemStack(Items.DIRT), new BlockPos(1, 0, 1)).consumesAction(),
				Component.literal("the dirt wasn't placed"));
		helper.assertBlockPresent(Blocks.DIRT, placed);
		assertOrigin(helper, Origin.PLACED, placed, "hand-placed dirt");
		// Owner, 2026-10-08: every placed block is marked, not only tagged ones.
		helper.assertTrue(placeOnTop(helper, player, new ItemStack(Items.GLASS), new BlockPos(0, 0, 0)).consumesAction(),
				Component.literal("the glass wasn't placed"));
		assertOrigin(helper, Origin.PLACED, new BlockPos(0, 1, 0), "hand-placed glass (in no Fortune tag)");

		helper.assertTrue(player.gameMode.destroyBlock(helper.absolutePos(placed)), Component.literal("break refused"));
		helper.assertBlockNotPresent(Blocks.DIRT, placed);
		assertOrigin(helper, Origin.NATURAL, placed, "after the player broke it");
		helper.succeed();
	}

	@GameTest
	public void dispenserPlacementMarks(GameTestHelper helper) {
		BlockPos dispenserPos = new BlockPos(1, 1, 1);
		helper.setBlock(dispenserPos, Blocks.DISPENSER.defaultBlockState().setValue(DirectionalBlock.FACING, Direction.UP));
		ServerLevel level = helper.getLevel();
		BlockPos absolute = helper.absolutePos(dispenserPos);
		DispenserBlockEntity dispenser = (DispenserBlockEntity) level.getBlockEntity(absolute);
		helper.assertTrue(dispenser != null, Component.literal("no dispenser block entity"));
		new ShulkerBoxDispenseBehavior().dispense(new BlockSource(level, absolute, level.getBlockState(absolute), dispenser),
				new ItemStack(Items.SHULKER_BOX));
		helper.assertBlockPresent(Blocks.SHULKER_BOX, dispenserPos.above());
		assertOrigin(helper, Origin.PLACED, dispenserPos.above(), "dispensed shulker box");
		helper.succeed();
	}

	/** Two placed blocks and a natural one pushed east by one; the piston head takes the first position. */
	@GameTest
	public void pistonPushMovesMarks(GameTestHelper helper) {
		floor(helper, 7);
		helper.setBlock(new BlockPos(1, 1, 1), Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
		for (int x = 2; x <= 4; x++) {
			helper.setBlock(new BlockPos(x, 1, 1), Blocks.DIRT);
		}
		mark(helper, new BlockPos(2, 1, 1));
		mark(helper, new BlockPos(3, 1, 1));
		// A stale mark where the natural block will land.
		mark(helper, new BlockPos(5, 1, 1));
		helper.setBlock(new BlockPos(0, 1, 1), Blocks.REDSTONE_BLOCK);
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(Blocks.DIRT, new BlockPos(5, 1, 1));
			assertOrigin(helper, Origin.NATURAL, new BlockPos(2, 1, 1), "the piston head's position");
			assertOrigin(helper, Origin.PLACED, new BlockPos(3, 1, 1), "pushed placed dirt");
			assertOrigin(helper, Origin.PLACED, new BlockPos(4, 1, 1), "pushed placed dirt");
			assertOrigin(helper, Origin.NATURAL, new BlockPos(5, 1, 1), "pushed natural dirt (stale mark cleared)");
		});
	}

	@GameTest(maxTicks = 100)
	public void stickyPistonPullCarriesTheMarkBack(GameTestHelper helper) {
		floor(helper, 5);
		helper.setBlock(new BlockPos(1, 1, 1),
				Blocks.STICKY_PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST));
		helper.setBlock(new BlockPos(2, 1, 1), Blocks.DIRT);
		mark(helper, new BlockPos(2, 1, 1));
		helper.setBlock(new BlockPos(0, 1, 1), Blocks.REDSTONE_BLOCK);
		helper.runAfterDelay(10, () -> {
			helper.assertBlockPresent(Blocks.DIRT, new BlockPos(3, 1, 1));
			assertOrigin(helper, Origin.PLACED, new BlockPos(3, 1, 1), "pushed");
			helper.setBlock(new BlockPos(0, 1, 1), Blocks.AIR);
			helper.runAfterDelay(10, () -> {
				helper.assertBlockPresent(Blocks.DIRT, new BlockPos(2, 1, 1));
				assertOrigin(helper, Origin.PLACED, new BlockPos(2, 1, 1), "pulled back");
				assertOrigin(helper, Origin.NATURAL, new BlockPos(3, 1, 1), "left behind");
				helper.succeed();
			});
		});
	}

	@GameTest(maxTicks = 100)
	public void placedSandCarriesItsMarkWhereItLands(GameTestHelper helper) {
		floor(helper, 3);
		BlockPos top = new BlockPos(1, 5, 1);
		BlockPos landing = new BlockPos(1, 1, 1);
		helper.setBlock(top, Blocks.SAND);
		mark(helper, top);
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(Blocks.SAND, landing);
			assertOrigin(helper, Origin.PLACED, landing, "landed placed sand");
			assertOrigin(helper, Origin.NATURAL, top, "where it fell from");
		});
	}

	@GameTest(maxTicks = 100)
	public void naturalSandLandsNaturalOverAStaleMark(GameTestHelper helper) {
		floor(helper, 3);
		BlockPos landing = new BlockPos(1, 1, 1);
		mark(helper, landing);
		helper.setBlock(new BlockPos(1, 5, 1), Blocks.SAND);
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(Blocks.SAND, landing);
			assertOrigin(helper, Origin.NATURAL, landing, "landed natural sand");
		});
	}

	@GameTest
	public void endermanSetDownIsMarked(GameTestHelper helper) {
		floor(helper, 5);
		EnderMan enderman = helper.spawn(EntityType.ENDERMAN, new BlockPos(2, 1, 2));
		enderman.setCarriedBlock(Blocks.DIRT.defaultBlockState());
		Goal leave = ((MobAccessor) enderman).e2test$getGoalSelector().getAvailableGoals().stream()
				.map(WrappedGoal::getGoal).filter(goal -> goal.getClass().getSimpleName().equals("EndermanLeaveBlockGoal"))
				.findFirst().orElse(null);
		helper.assertTrue(leave != null, Component.literal("no leave-block goal"));
		// Each tick tries one random spot near the enderman.
		for (int i = 0; i < 500 && enderman.getCarriedBlock() != null; i++) {
			leave.tick();
		}
		helper.assertTrue(enderman.getCarriedBlock() == null, Component.literal("the enderman never set the dirt down"));
		List<BlockPos> dirt = BlockPos.betweenClosedStream(new AABB(helper.absolutePos(new BlockPos(0, 1, 0)))
				.expandTowards(4, 3, 4)).filter(pos -> helper.getLevel().getBlockState(pos).is(Blocks.DIRT))
				.map(BlockPos::immutable).toList();
		helper.assertValueEqual(1, dirt.size(), Component.literal("dirt blocks set down"));
		helper.assertValueEqual(Origin.PLACED, PlacedBlocks.origin(helper.getLevel(), dirt.get(0)),
				Component.literal("dirt the enderman set down"));
		helper.succeed();
	}

	/** Lava next to water: a source makes obsidian (test-tagged, marked), flowing lava makes cobblestone (untagged). */
	@GameTest
	public void generatorOutputIsMarkedOnlyWhenTagged(GameTestHelper helper) {
		floor(helper, 5);
		helper.setBlock(new BlockPos(1, 1, 1), Blocks.WATER);
		helper.setBlock(new BlockPos(2, 1, 1), Blocks.LAVA);
		helper.assertBlockPresent(Blocks.OBSIDIAN, new BlockPos(2, 1, 1));
		assertOrigin(helper, Origin.PLACED, new BlockPos(2, 1, 1), "generated obsidian (tagged in the test datapack)");

		helper.setBlock(new BlockPos(1, 1, 3), Blocks.WATER);
		helper.setBlock(new BlockPos(2, 1, 3), Blocks.LAVA.defaultBlockState().setValue(LiquidBlock.LEVEL, 2));
		helper.assertBlockPresent(Blocks.COBBLESTONE, new BlockPos(2, 1, 3));
		assertOrigin(helper, Origin.NATURAL, new BlockPos(2, 1, 3), "generated cobblestone (in no Fortune tag)");
		helper.succeed();
	}

	/** Lava flowing down onto water makes stone ({@code LavaFluid.spreadTo}); test-tagged, so marked. */
	@GameTest(maxTicks = 100)
	public void lavaOntoWaterStoneIsMarkedWhenTagged(GameTestHelper helper) {
		helper.setBlock(new BlockPos(1, 0, 1), Blocks.GLASS);
		helper.setBlock(new BlockPos(1, 1, 1), Blocks.WATER);
		helper.setBlock(new BlockPos(1, 2, 1), Blocks.LAVA);
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(Blocks.STONE, new BlockPos(1, 1, 1));
			assertOrigin(helper, Origin.PLACED, new BlockPos(1, 1, 1), "stone from lava onto water (tagged in the test datapack)");
		});
	}

	@GameTest(maxTicks = 100)
	public void snowGolemTrailIsMarkedWhenTagged(GameTestHelper helper) {
		floor(helper, 3);
		helper.spawn(EntityType.SNOW_GOLEM, new BlockPos(1, 1, 1));
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(Blocks.SNOW, new BlockPos(1, 1, 1));
			assertOrigin(helper, Origin.PLACED, new BlockPos(1, 1, 1), "snow-golem snow (tagged in the test datapack)");
		});
	}

	/** Through the real chunk serializer: Fabric writes the attachment with our codec under its attachment key. */
	@GameTest
	public void marksAreWrittenWithTheChunk(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		PlacedBlocks.mark(level, pos);
		LevelChunk chunk = level.getChunkAt(pos);
		CompoundTag nbt = SerializableChunkData.copyOf(level, chunk).write();
		Tag stored = nbt.getCompound(AttachmentTarget.NBT_ATTACHMENT_KEY).orElseThrow()
				.get(PlacedBlocks.CHUNK.id().toString());
		helper.assertTrue(stored != null, Component.literal("no placed-block attachment in the chunk NBT: " + nbt.keySet()));
		PlacedMarks read = PlacedMarksCodec.INSTANCE.parse(NbtOps.INSTANCE, stored).getOrThrow();
		helper.assertValueEqual(PlacedBlocks.CHUNK.get(chunk), read, Component.literal("decoded marks"));
		helper.assertTrue(read.isMarked(pos.getX() & 15, pos.getY(), pos.getZ() & 15), Component.literal("mark lost"));
		helper.succeed();
	}

	/** NBT that isn't ours to read decodes to "placed everywhere" and encodes back to the same tag. */
	@GameTest
	public void unreadableNbtFailsClosedAndIsKept(GameTestHelper helper) {
		for (Tag corrupt : List.<Tag>of(StringTag.valueOf("garbage"), new CompoundTag())) {
			PlacedMarks read = PlacedMarksCodec.INSTANCE.parse(NbtOps.INSTANCE, corrupt).getOrThrow();
			helper.assertTrue(read.isUnreadable() && read.isMarked(0, 64, 0), Component.literal("read " + corrupt));
			helper.assertValueEqual(corrupt, PlacedMarksCodec.INSTANCE.encodeStart(NbtOps.INSTANCE, read).getOrThrow(),
					Component.literal("written back"));
		}
		// The same value in a chunk makes every block there count as placed.
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		LevelChunk chunk = level.getChunkAt(pos);
		PlacedMarks before = PlacedBlocks.CHUNK.get(chunk);
		PlacedBlocks.CHUNK.set(chunk, PlacedMarks.unreadable("test"));
		try {
			assertOrigin(helper, Origin.UNREADABLE, new BlockPos(1, 1, 1), "a block in an unreadable chunk");
			PlacedBlocks.clear(level, pos);
			assertOrigin(helper, Origin.UNREADABLE, new BlockPos(1, 1, 1), "after a clear (ignored)");
		} finally {
			PlacedBlocks.CHUNK.set(chunk, before);
		}
		helper.succeed();
	}
}
