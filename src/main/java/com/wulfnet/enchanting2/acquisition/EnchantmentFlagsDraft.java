package com.wulfnet.enchanting2.acquisition;

import com.wulfnet.enchanting2.config.AcquisitionSettings;
import com.wulfnet.enchanting2.config.EnchantmentOverrides;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The config screen's working copy of the four tick boxes per enchantment ({@code enabled}, {@code table}, {@code loot},
 * {@code trade}). {@link #apply()} changes only those keys: every other field and every entry the screen didn't
 * touch stays as it was, and a key is written only when it differs from the default or the file already had it.
 */
public final class EnchantmentFlagsDraft {
	/** Every flag is true unless the file says otherwise. */
	public static final boolean DEFAULT = true;

	public enum Flag {
		ENABLED("enabled"),
		TABLE("table"),
		LOOT("loot"),
		TRADE("trade");

		private final String key;

		Flag(String key) {
			this.key = key;
		}

		/** The {@code enchantments.json5} key, also used for the screen's text. */
		public String key() {
			return key;
		}

		Optional<Boolean> read(AcquisitionSettings a) {
			return switch (this) {
				case ENABLED -> a.enabled();
				case TABLE -> a.table();
				case LOOT -> a.loot();
				case TRADE -> a.trade();
			};
		}

		AcquisitionSettings write(AcquisitionSettings a, Optional<Boolean> value) {
			return switch (this) {
				case ENABLED -> new AcquisitionSettings(value, a.table(), a.loot(), a.trade(), a.lootWeight(),
						a.tradeWeight(), a.treasure());
				case TABLE -> new AcquisitionSettings(a.enabled(), value, a.loot(), a.trade(), a.lootWeight(),
						a.tradeWeight(), a.treasure());
				case LOOT -> new AcquisitionSettings(a.enabled(), a.table(), value, a.trade(), a.lootWeight(),
						a.tradeWeight(), a.treasure());
				case TRADE -> new AcquisitionSettings(a.enabled(), a.table(), a.loot(), value, a.lootWeight(),
						a.tradeWeight(), a.treasure());
			};
		}
	}

	private final EnchantmentOverrides original;
	private final Map<String, EnumMap<Flag, Boolean>> rows = new TreeMap<>();

	/**
	 * @param known the enchantments to list (the registry's, or vanilla's on the title screen); IDs in the file are
	 *              listed too, so an entry for a mod that isn't loaded can still be edited
	 */
	public EnchantmentFlagsDraft(EnchantmentOverrides original, Collection<String> known) {
		this.original = Objects.requireNonNull(original, "original");
		Objects.requireNonNull(known, "known");
		known.forEach(id -> rows.put(id, flagsOf(id)));
		original.entries().keySet().forEach(id -> rows.computeIfAbsent(id, this::flagsOf));
	}

	private EnumMap<Flag, Boolean> flagsOf(String id) {
		AcquisitionSettings settings = settings(id);
		EnumMap<Flag, Boolean> flags = new EnumMap<>(Flag.class);
		for (Flag flag : Flag.values()) {
			flags.put(flag, flag.read(settings).orElse(DEFAULT));
		}
		return flags;
	}

	private AcquisitionSettings settings(String id) {
		EnchantmentOverrides.Entry entry = original.entries().get(id);
		return entry == null ? AcquisitionSettings.NONE : entry.acquisition();
	}

	/** Listed IDs in ID order. */
	public List<String> ids() {
		return List.copyOf(rows.keySet());
	}

	public boolean get(String id, Flag flag) {
		return row(id).get(flag);
	}

	public void set(String id, Flag flag, boolean value) {
		row(id).put(flag, value);
	}

	private EnumMap<Flag, Boolean> row(String id) {
		EnumMap<Flag, Boolean> row = rows.get(id);
		if (row == null) {
			throw new IllegalArgumentException("Not listed: " + id);
		}
		return row;
	}

	/** The original file with this draft's flags applied; equal to the original when nothing changed. */
	public EnchantmentOverrides apply() {
		Map<String, EnchantmentOverrides.Entry> entries = new LinkedHashMap<>(original.entries());
		rows.forEach((id, flags) -> {
			AcquisitionSettings before = settings(id);
			AcquisitionSettings after = before;
			for (Flag flag : Flag.values()) {
				boolean value = flags.get(flag);
				Optional<Boolean> current = flag.read(before);
				if (current.orElse(DEFAULT) != value) {
					after = flag.write(after, Optional.of(value));
				}
			}
			if (!after.equals(before)) {
				EnchantmentOverrides.Entry entry = original.entries().get(id);
				entries.put(id, entry == null ? new EnchantmentOverrides.Entry(after) : entry.withAcquisition(after));
			}
		});
		return new EnchantmentOverrides(entries);
	}
}
