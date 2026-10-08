package com.wulfnet.enchanting2.grindstone;

import com.wulfnet.enchanting2.grindstone.GrindstoneOutcome.Status;
import com.wulfnet.enchanting2.screen.PanelText.Arg;
import com.wulfnet.enchanting2.screen.PanelText.Msg;
import com.wulfnet.enchanting2.screen.PanelText.Row;
import com.wulfnet.enchanting2.screen.PanelText.Tone;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * What the grindstone's picker panel says about the server's panel payload (DESIGN §6, decisions.md 2026-10-07
 * "Grindstone"): a heading per action, one clickable row per pick with its price, and a status line at the bottom. Pure,
 * so every status and action is unit-tested; the client only draws it and sends the clicked row's choice.
 */
public final class GrindstonePanelView {
	public static final String KEY = "enchanting2.grindstone.";
	/** Vanilla's own texts, so the common cases read exactly like the anvil. */
	public static final String VANILLA_COST = "container.repair.cost";
	public static final String VANILLA_TOO_EXPENSIVE = "container.repair.expensive";

	private GrindstonePanelView() {
	}

	/**
	 * One drawn row.
	 *
	 * @param choice   what clicking it picks; empty for headings and the status line
	 * @param selected the server holds this row's choice
	 */
	public record Item(Row row, Optional<GrindstoneChoice> choice, boolean selected) {
		public Item {
			Objects.requireNonNull(row, "row");
			Objects.requireNonNull(choice, "choice");
			if (selected && choice.isEmpty()) {
				throw new IllegalArgumentException("only a pick can be selected");
			}
		}

		static Item text(Row row) {
			return new Item(row, Optional.empty(), false);
		}
	}

	/**
	 * @param playerLevel          the player's XP level, for "insufficient levels"
	 * @param maximumOperationCost the server's {@code maximum_operation_cost}, if it set one
	 */
	public static List<Item> panel(Status status, List<GrindstoneOption> options, Optional<GrindstoneChoice> choice,
			int levelsCharged, int playerLevel, OptionalInt maximumOperationCost) {
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(choice, "choice");
		List<Item> items = new ArrayList<>();
		GrindstoneAction heading = null;
		for (GrindstoneOption option : options) {
			GrindstoneAction action = option.choice().action();
			if (action != heading && action.takesEnchantment()) {
				items.add(Item.text(Row.of(Msg.of(KEY + "heading." + name(action)), Tone.MUTED)));
			}
			heading = action;
			boolean selected = choice.isPresent() && choice.get().equals(option.choice());
			items.add(new Item(row(option, playerLevel), Optional.of(option.choice()), selected));
		}
		Optional<GrindstoneOption> picked = choice.flatMap(c -> options.stream()
				.filter(o -> o.choice().equals(c)).findFirst());
		items.add(Item.text(Row.of(status(status, picked, levelsCharged, playerLevel, maximumOperationCost),
				tone(status, levelsCharged, playerLevel))));
		return items;
	}

	/** One pick: Extract and Cleanse name the enchantment and show the price; Disenchant and Repair are vanilla's. */
	static Row row(GrindstoneOption option, int playerLevel) {
		GrindstoneChoice choice = option.choice();
		if (!choice.action().takesEnchantment()) {
			return new Row(Msg.of(KEY + "row." + name(choice.action())), OptionalLong.empty(), Tone.NORMAL, false);
		}
		Arg enchantment = new Arg.EnchLevel(choice.enchantment().orElseThrow(), option.level());
		return switch (option.status()) {
			case CURSE_NOT_EXTRACTABLE -> new Row(Msg.of(KEY + "row.curse", enchantment), OptionalLong.empty(),
					Tone.MUTED, true);
			case NOTHING_TO_SPLIT -> new Row(Msg.of(KEY + "row.only_entry", enchantment), OptionalLong.empty(),
					Tone.MUTED, true);
			case TOO_EXPENSIVE -> new Row(Msg.of(KEY + "row.enchantment", enchantment), OptionalLong.of(option.cost()),
					Tone.BAD, true);
			case OK -> new Row(Msg.of(KEY + "row.enchantment", enchantment),
					OptionalLong.of(option.free() ? 0 : option.cost()),
					option.free() || playerLevel >= option.cost() ? Tone.NORMAL : Tone.BAD, true);
			default -> throw new IllegalArgumentException(option.status() + " isn't a row status");
		};
	}

	/** The bottom line: what happens if the player takes the result now, or why nothing can be taken. */
	static Msg status(Status status, Optional<GrindstoneOption> picked, int levelsCharged, int playerLevel,
			OptionalInt maximumOperationCost) {
		return switch (status) {
			case NONE -> Msg.of(KEY + "status.empty");
			case STACKED_ITEM -> Msg.of(KEY + "status.stacked");
			case NO_CHOICE -> Msg.of(KEY + "status.pick");
			case NOT_AVAILABLE -> Msg.of(KEY + "status.not_available");
			case CURSE_NOT_EXTRACTABLE -> Msg.of(KEY + "status.curse");
			case NOTHING_TO_SPLIT -> Msg.of(KEY + "status.only_entry");
			case TOO_EXPENSIVE -> picked.isPresent() && maximumOperationCost.isPresent()
					? Msg.of(KEY + "status.too_expensive", new Arg.Num(picked.get().cost()),
							new Arg.Num(maximumOperationCost.getAsInt()))
					: Msg.of(VANILLA_TOO_EXPENSIVE);
			case OK -> ok(picked, levelsCharged, playerLevel);
		};
	}

	private static Msg ok(Optional<GrindstoneOption> picked, int levelsCharged, int playerLevel) {
		if (picked.isPresent() && !picked.get().choice().action().takesEnchantment()) {
			return Msg.of(KEY + "status.gives_xp");
		}
		if (picked.isPresent() && picked.get().free()) {
			return Msg.of(KEY + "status.free_creative");
		}
		if (levelsCharged == 0) {
			return Msg.of(KEY + "status.free");
		}
		if (playerLevel < levelsCharged) {
			return Msg.of(KEY + "status.insufficient", new Arg.Num(levelsCharged), new Arg.Num(playerLevel));
		}
		return Msg.of(VANILLA_COST, new Arg.Num(levelsCharged));
	}

	private static Tone tone(Status status, int levelsCharged, int playerLevel) {
		return switch (status) {
			case NONE, NO_CHOICE -> Tone.MUTED;
			case OK -> playerLevel >= levelsCharged ? Tone.GOOD : Tone.BAD;
			default -> Tone.BAD;
		};
	}

	/** The selected row's index in {@code items}, for the panel's highlight. */
	public static OptionalInt selectedIndex(List<Item> items) {
		for (int i = 0; i < items.size(); i++) {
			if (items.get(i).selected()) {
				return OptionalInt.of(i);
			}
		}
		return OptionalInt.empty();
	}

	static String name(GrindstoneAction action) {
		return action.name().toLowerCase(Locale.ROOT);
	}
}
