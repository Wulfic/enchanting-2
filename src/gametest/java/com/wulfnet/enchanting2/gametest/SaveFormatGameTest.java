package com.wulfnet.enchanting2.gametest;

import com.wulfnet.enchanting2.combat.LastStand;
import com.wulfnet.enchanting2.effect.SuspendGravity;
import com.wulfnet.enchanting2.fortune.PlacedBlocks;
import com.wulfnet.enchanting2.fortune.PlacedMarks;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/**
 * Phase 12B: a world saved on 1.21.11 and opened on 26.x keeps our data. Every attachment of ours sits under Fabric's
 * {@code fabric:attachments} key in entity and chunk NBT. {@link #entityAttachments} is that subtree exactly as 1.21.11
 * writes it, so each version node asserts both directions: what it writes equals the fixture (the 1.21.11 run proves
 * the fixture is real; the 26.x run that nothing changed), and the fixture reads back into the same values. The chunk
 * case round-trips a marked chunk through vanilla's chunk serializer, which is where Fabric hooks chunk attachments.
 */
public class SaveFormatGameTest {
	private static final long READY_AT = 123_456_789L;
	private static final int GRAVITY_TICKS = 42;

	/** The {@code fabric:attachments} compound 1.21.11 writes for a husk carrying three of our attachments. */
	private static CompoundTag entityAttachments() {
		CompoundTag attachments = new CompoundTag();
		attachments.putLong("enchanting2:last_stand_ready_at", READY_AT);
		attachments.putInt("enchanting2:gravity_ticks", GRAVITY_TICKS);
		attachments.putBoolean("enchanting2:placed_falling", true);
		return attachments;
	}

	@GameTest
	public void entityAttachmentsKeepTheirSavedFormat(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Husk written = husk(helper);
		written.setAttached(LastStand.READY_AT, READY_AT);
		written.setAttached(SuspendGravity.TICKS_LEFT, GRAVITY_TICKS);
		written.setAttached(PlacedBlocks.FALLING, true);
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		written.saveWithoutId(output);
		CompoundTag saved = output.buildResult().getCompoundOrEmpty(AttachmentTarget.NBT_ATTACHMENT_KEY);
		helper.assertValueEqual(entityAttachments(), saved, Component.literal("saved fabric:attachments"));

		CompoundTag fixture = new CompoundTag();
		fixture.put(AttachmentTarget.NBT_ATTACHMENT_KEY, entityAttachments());
		Husk read = husk(helper);
		read.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), fixture));
		helper.assertValueEqual(READY_AT, read.getAttached(LastStand.READY_AT), Component.literal("Last Stand ready at"));
		helper.assertValueEqual(GRAVITY_TICKS, read.getAttached(SuspendGravity.TICKS_LEFT),
				Component.literal("gravity ticks"));
		helper.assertValueEqual(true, read.getAttached(PlacedBlocks.FALLING), Component.literal("placed falling"));
		helper.succeed();
	}

	@GameTest
	public void chunkMarksSurviveTheChunkSerializer(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(BlockPos.ZERO.above());
		PlacedBlocks.mark(level, pos);
		LevelChunk chunk = level.getChunkAt(pos);
		CompoundTag written = SerializableChunkData.copyOf(level, chunk).write();
		helper.assertTrue(written.getCompoundOrEmpty(AttachmentTarget.NBT_ATTACHMENT_KEY)
				.contains("enchanting2:placed_blocks"), Component.literal("chunk NBT has no enchanting2:placed_blocks"));

		SerializableChunkData parsed = SerializableChunkData.parse(level, level.palettedContainerFactory(), written);
		helper.assertTrue(parsed != null, Component.literal("the written chunk didn't parse"));
		ProtoChunk read = parsed.read(level, level.getChunkSource().getPoiManager(),
				level.getChunkSource().chunkMap.storageInfo(), chunk.getPos());
		PlacedMarks marks = read.getAttached(PlacedBlocks.CHUNK);
		helper.assertTrue(marks != null && marks.isMarked(pos.getX() & 15, pos.getY(), pos.getZ() & 15),
				Component.literal("the read chunk lost the mark at " + pos + ": " + marks));
		PlacedBlocks.clear(level, pos);
		helper.succeed();
	}

	private static Husk husk(GameTestHelper helper) {
		Husk husk = EntityType.HUSK.create(helper.getLevel(), EntitySpawnReason.LOAD);
		if (husk == null) {
			throw new AssertionError("no husk");
		}
		return husk;
	}
}
