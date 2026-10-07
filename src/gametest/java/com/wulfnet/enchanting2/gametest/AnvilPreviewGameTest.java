package com.wulfnet.enchanting2.gametest;

import com.wulfnet.enchanting2.anvil.AnvilOutcome.Entry;
import com.wulfnet.enchanting2.anvil.AnvilOutcome.Status;
import com.wulfnet.enchanting2.anvil.TransferRules.Fate;
import com.wulfnet.enchanting2.cost.CostBreakdown;
import com.wulfnet.enchanting2.cost.CostBreakdown.Kind;
import com.wulfnet.enchanting2.cost.CostBreakdown.Line;
import com.wulfnet.enchanting2.enchantment.EnchantRules.Verdict;
import com.wulfnet.enchanting2.network.AnvilPreviewHolder;
import com.wulfnet.enchanting2.network.AnvilPreviewPayload;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

/** Stage 6C: the anvil preview payload's wire format, and when the server builds a new one. */
public class AnvilPreviewGameTest {
	private static AnvilPreviewPayload roundTrip(GameTestHelper helper, AnvilPreviewPayload sent) {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		AnvilPreviewPayload.CODEC.encode(buf, sent);
		AnvilPreviewPayload received = AnvilPreviewPayload.CODEC.decode(buf);
		helper.assertTrue(buf.readableBytes() == 0, Component.literal("decode left bytes behind"));
		return received;
	}

	private static AnvilPreviewPayload preview(AnvilMenu menu) {
		AnvilPreviewPayload preview = ((AnvilPreviewHolder) menu).enchanting2$lastPreview();
		if (preview == null) {
			throw new IllegalStateException("the server-side menu built no preview after its slots were set");
		}
		return preview;
	}

	/** One entry per verdict, cycling through the fates, so every value of both enums crosses the wire. */
	private static List<Entry> everyVerdictAndFate() {
		List<Entry> entries = new ArrayList<>();
		Verdict[] verdicts = Verdict.values();
		Fate[] fates = Fate.values();
		for (int i = 0; i < Math.max(verdicts.length, fates.length); i++) {
			Verdict verdict = verdicts[i % verdicts.length];
			Optional<String> with = verdict == Verdict.CONFLICT ? Optional.of("minecraft:silk_touch") : Optional.empty();
			entries.add(new Entry("minecraft:fortune", i + 1, verdict, with, fates[i % fates.length]));
		}
		return entries;
	}

	/** Every line kind; 40,000 total, past the short the vanilla cost slot syncs as. */
	@GameTest
	public void previewRoundTripsPastShortRange(GameTestHelper helper) {
		List<Line> lines = List.of(
				new Line(Kind.ENCHANT, "minecraft:sharpness", 0, 5, 39_994),
				new Line(Kind.SURCHARGE, "minecraft:sharpness", 2, 7, 6),
				new Line(Kind.CURSE_DISCOUNT, "", 30, 25, -3),
				new Line(Kind.RENAME, "", 0, 0, 1),
				new Line(Kind.REPAIR, "", 0, 4, 2));
		CostBreakdown breakdown = new CostBreakdown(lines, 39_997, 40_000, 0, false, false);
		for (Status status : Status.values()) {
			AnvilPreviewPayload sent = new AnvilPreviewPayload(7, status, Optional.of(breakdown), everyVerdictAndFate());
			helper.assertValueEqual(sent, roundTrip(helper, sent), Component.literal("payload with " + status));
		}
		AnvilPreviewPayload bare = new AnvilPreviewPayload(127, Status.NONE, Optional.empty(), List.of());
		helper.assertValueEqual(bare, roundTrip(helper, bare), Component.literal("empty payload"));
		helper.succeed();
	}

	/** A saturated total stays exactly {@code Integer.MAX_VALUE}; its line keeps its full {@code long}. */
	@GameTest
	public void previewRoundTripsSaturatedTotals(GameTestHelper helper) {
		CostBreakdown breakdown = new CostBreakdown(
				List.of(new Line(Kind.ENCHANT, "minecraft:efficiency", 0, 255, Long.MAX_VALUE)),
				Integer.MAX_VALUE, Integer.MAX_VALUE, 0, true, true);
		AnvilPreviewPayload sent = new AnvilPreviewPayload(1, Status.TOO_EXPENSIVE, Optional.of(breakdown), List.of());
		AnvilPreviewPayload received = roundTrip(helper, sent);
		helper.assertValueEqual(sent, received, Component.literal("saturated payload"));
		helper.assertValueEqual(Integer.MAX_VALUE, received.breakdown().orElseThrow().total(),
				Component.literal("total"));
		helper.succeed();
	}

	/** Oversize previews fail where they are built (server thread, logged), not in Netty. */
	@GameTest
	public void oversizePreviewIsRefusedAtConstruction(GameTestHelper helper) {
		Entry entry = new Entry("minecraft:fortune", 1, Verdict.OK, Optional.empty(), Fate.APPLY);
		List<Entry> tooMany = Collections.nCopies(AnvilPreviewPayload.MAX_ENTRIES + 1, entry);
		Entry longId = new Entry("x".repeat(AnvilPreviewPayload.MAX_ID_CHARS + 1), 1, Verdict.OK, Optional.empty(),
				Fate.APPLY);
		Entry longConflict = new Entry("minecraft:fortune", 1, Verdict.CONFLICT,
				Optional.of("x".repeat(AnvilPreviewPayload.MAX_ID_CHARS + 1)), Fate.KEEP_ON_BOOK);
		List<Line> manyLines = Collections.nCopies(AnvilPreviewPayload.MAX_LINES + 1, new Line(Kind.RENAME, "", 0, 0, 1));
		CostBreakdown tooManyLines = new CostBreakdown(manyLines, 0, manyLines.size(), 0, false, false);
		List<Runnable> cases = List.of(
				() -> new AnvilPreviewPayload(1, Status.OK, Optional.empty(), tooMany),
				() -> new AnvilPreviewPayload(1, Status.OK, Optional.empty(), List.of(longId)),
				() -> new AnvilPreviewPayload(1, Status.OK, Optional.empty(), List.of(longConflict)),
				() -> new AnvilPreviewPayload(1, Status.OK, Optional.of(tooManyLines), List.of()));
		for (int i = 0; i < cases.size(); i++) {
			try {
				cases.get(i).run();
				helper.fail("oversize case " + i + " was built");
				return;
			} catch (IllegalArgumentException expected) {
				// The refusal is the point.
			}
		}
		helper.succeed();
	}

	/** An ordinal past the end of an enum is a decode error, not an out-of-bounds or a wrong value. */
	@GameTest
	public void unknownStatusOrdinalFailsDecode(GameTestHelper helper) {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		ByteBufCodecs.VAR_INT.encode(buf, 1);
		ByteBufCodecs.VAR_INT.encode(buf, Status.values().length);
		try {
			AnvilPreviewPayload.CODEC.decode(buf);
		} catch (RuntimeException expected) {
			helper.succeed();
			return;
		}
		helper.fail("a status ordinal past the end decoded");
	}

	/** Totals that don't match their lines fail to decode: {@code CostBreakdown}'s check runs on the client too. */
	@GameTest
	public void forgedTotalFailsDecode(GameTestHelper helper) {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		ByteBufCodecs.VAR_INT.encode(buf, 1); // container
		ByteBufCodecs.VAR_INT.encode(buf, Status.OK.ordinal());
		ByteBufCodecs.BOOL.encode(buf, true); // breakdown present
		ByteBufCodecs.VAR_INT.encode(buf, 1); // one line
		ByteBufCodecs.VAR_INT.encode(buf, Kind.RENAME.ordinal());
		ByteBufCodecs.STRING_UTF8.encode(buf, "");
		ByteBufCodecs.VAR_INT.encode(buf, 0);
		ByteBufCodecs.VAR_INT.encode(buf, 0);
		ByteBufCodecs.VAR_LONG.encode(buf, 1L);
		ByteBufCodecs.VAR_INT.encode(buf, 0); // enchantCost
		ByteBufCodecs.VAR_INT.encode(buf, 5); // total: the line says 1
		ByteBufCodecs.VAR_INT.encode(buf, 0); // lapis
		ByteBufCodecs.BOOL.encode(buf, false);
		ByteBufCodecs.BOOL.encode(buf, false);
		ByteBufCodecs.VAR_INT.encode(buf, 0); // no entries
		try {
			AnvilPreviewPayload.CODEC.decode(buf);
		} catch (RuntimeException expected) {
			helper.succeed();
			return;
		}
		helper.fail("a breakdown whose total disagrees with its lines decoded");
	}

	/**
	 * A rename keystroke reruns {@code createResult} but changes no price or entry, so the menu keeps the same preview
	 * object: nothing new is sent. Adding a book changes it.
	 */
	@GameTest
	public void renameKeystrokeKeepsThePreviewAndABookChangesIt(GameTestHelper helper) {
		ServerPlayer player = AnvilGameTest.player(helper, 100);
		AnvilMenu menu = AnvilGameTest.anvil(player, new ItemStack(Items.DIAMOND_SWORD), ItemStack.EMPTY);
		menu.setItemName("A");
		AnvilPreviewPayload first = preview(menu);
		helper.assertValueEqual(Status.OK, first.status(), Component.literal("rename status"));
		helper.assertValueEqual(menu.containerId, first.containerId(), Component.literal("container ID"));
		helper.assertValueEqual(AnvilPreviewPayload.of(menu.containerId, AnvilGameTest.outcome(menu)), first,
				Component.literal("preview matches the outcome"));

		menu.setItemName("AB");
		// The outcome was recomputed (its result carries the new name), the preview was not replaced.
		helper.assertValueEqual("AB", AnvilGameTest.outcome(menu).result().getHoverName().getString(),
				Component.literal("recomputed result name"));
		helper.assertTrue(first == preview(menu), Component.literal("a rename keystroke built a new preview"));

		menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(AnvilGameTest.book(helper, Map.of(Enchantments.SHARPNESS, 1)));
		AnvilPreviewPayload withBook = preview(menu);
		helper.assertTrue(!withBook.equals(first), Component.literal("adding a book kept the old preview"));
		helper.assertValueEqual(1, withBook.entries().size(), Component.literal("book entries"));
		helper.assertValueEqual(Fate.APPLY, withBook.entries().getFirst().fate(), Component.literal("fate"));
		helper.succeed();
	}
}
