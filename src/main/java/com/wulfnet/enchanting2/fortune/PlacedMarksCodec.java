package com.wulfnet.enchanting2.fortune;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link PlacedMarks} as {@code {v: 1, sections: [{y, bits: [64 longs]}]}}. Decoding never fails: Fabric drops an
 * attachment whose codec errors, which would turn every placed block in the chunk natural (fail open). Anything this
 * version can't read, a newer format included, decodes to {@link PlacedMarks#unreadable} holding the input, logged at
 * WARN, and encodes back to that same input.
 */
public final class PlacedMarksCodec implements Codec<PlacedMarks> {
	public static final PlacedMarksCodec INSTANCE = new PlacedMarksCodec();
	static final int VERSION = 1;
	/** Section Y bounds: the 4064-block build limit allowed by dimension types, in 16-block sections. */
	static final int MIN_SECTION_Y = -128;
	static final int MAX_SECTION_Y = 127;

	private static final Logger LOGGER = LoggerFactory.getLogger("enchanting2");

	private PlacedMarksCodec() {
	}

	@Override
	public <T> DataResult<Pair<PlacedMarks, T>> decode(DynamicOps<T> ops, T input) {
		PlacedMarks marks;
		try {
			marks = read(ops, input).getOrThrow(IllegalArgumentException::new);
		} catch (RuntimeException e) {
			LOGGER.warn("Unreadable placed-block data ({}). Fortune gives no bonus in this chunk until it's fixed; the "
					+ "data is kept and written back unchanged.", e.getMessage());
			marks = PlacedMarks.unreadable(new Dynamic<>(ops, input));
		}
		return DataResult.success(Pair.of(marks, ops.empty()));
	}

	@Override
	public <T> DataResult<T> encode(PlacedMarks marks, DynamicOps<T> ops, T prefix) {
		if (marks.isUnreadable()) {
			if (!(marks.raw() instanceof Dynamic<?> raw)) {
				return DataResult.error(() -> "unreadable placed-block data without its raw value");
			}
			return DataResult.success(raw.convert(ops).getValue());
		}
		List<T> sections = new ArrayList<>();
		marks.sections().forEach((y, bits) -> sections.add(ops.createMap(Map.of(
				ops.createString("y"), ops.createInt(y),
				ops.createString("bits"), ops.createLongList(LongStream.of(bits))))));
		return ops.mergeToMap(prefix, Map.of(ops.createString("v"), ops.createInt(VERSION),
				ops.createString("sections"), ops.createList(sections.stream())));
	}

	private static <T> DataResult<PlacedMarks> read(DynamicOps<T> ops, T input) {
		Dynamic<T> root = new Dynamic<>(ops, input);
		int version = root.get("v").asNumber().getOrThrow(IllegalArgumentException::new).intValue();
		if (version != VERSION) {
			return DataResult.error(() -> "format version " + version + ", this version reads " + VERSION);
		}
		// Not asList: it reads a missing or non-list value as an empty list, which would be "nothing placed" (fail open).
		T sectionsValue = root.get("sections").result().map(Dynamic::getValue)
				.orElseThrow(() -> new IllegalArgumentException("no sections"));
		List<Dynamic<T>> entries = ops.getStream(sectionsValue).getOrThrow(IllegalArgumentException::new)
				.map(value -> new Dynamic<>(ops, value)).toList();
		Map<Integer, long[]> sections = new HashMap<>();
		for (Dynamic<T> entry : entries) {
			int y = entry.get("y").asNumber().getOrThrow(IllegalArgumentException::new).intValue();
			if (y < MIN_SECTION_Y || y > MAX_SECTION_Y) {
				return DataResult.error(() -> "section y " + y + " out of range");
			}
			long[] bits = entry.get("bits").asLongStream().toArray();
			if (bits.length != PlacedMarks.WORDS) {
				return DataResult.error(() -> "section " + y + " has " + bits.length + " words, expected " + PlacedMarks.WORDS);
			}
			if (sections.put(y, bits) != null) {
				return DataResult.error(() -> "section " + y + " appears twice");
			}
		}
		return DataResult.success(PlacedMarks.ofSections(sections));
	}
}
