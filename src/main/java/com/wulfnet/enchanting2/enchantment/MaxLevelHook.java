package com.wulfnet.enchanting2.enchantment;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.config.ConfigFiles;
import com.wulfnet.enchanting2.config.EnchantmentOverrides;
import com.wulfnet.enchanting2.config.ServerConfigs;
import com.wulfnet.enchanting2.mixin.EnchantmentBuilderAccessor;
import com.wulfnet.enchanting2.platform.Services;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.OptionalInt;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Applies DESIGN §8 max levels and {@code max_level_override} while enchantments load from datapacks. That happens
 * before any {@code MinecraftServer} exists, so this reads {@code enchantments.json5} itself instead of going through
 * {@code ServerConfigs}. {@code /reload} does not re-run it, so an override needs a restart (TODO 4A.6 probe). Items
 * above a lowered max are never touched; the max only limits new enchanting.
 */
public final class MaxLevelHook {
	private static final Object LOCK = new Object();
	private static Cached cached;

	private record Stamp(long modified, long size) {
	}

	private record Cached(Stamp stamp, EnchantmentOverrides overrides) {
	}

	private MaxLevelHook() {
	}

	public static void register() {
		Services.ITEMS.onEnchantmentLoad((key, builder, items) -> apply(key, builder));
	}

	static Path file() {
		return ServerConfigs.enchantmentsFile();
	}

	private static void apply(ResourceKey<Enchantment> key, Enchantment.Builder builder) {
		String id = key.identifier().toString();
		EnchantmentBuilderAccessor accessor = (EnchantmentBuilderAccessor) builder;
		Enchantment.EnchantmentDefinition d = accessor.enchanting2$getDefinition();
		EnchantmentOverrides.Entry entry = overrides().entries().get(id);
		OptionalInt override = entry == null ? OptionalInt.empty() : entry.maxLevelOverride();
		MaxLevels.Resolution r = MaxLevels.resolve(id, d.maxLevel(), override);

		if (r.why() == MaxLevels.Why.DATAPACK_CHANGED) {
			Enchanting2.LOGGER.info("{} loaded with max level {}, not vanilla's; something else changed it, so DESIGN §8 "
					+ "is not applied. Set max_level_override in {} to choose a level.", id, d.maxLevel(), file());
		} else if (r.why() == MaxLevels.Why.OVERRIDE_CLAMPED) {
			Enchanting2.LOGGER.warn("{} max_level_override {} in {} is past the level where the enchantment stops "
					+ "working, so max level {} is used instead (DESIGN §8).", id, override.getAsInt(), file(), r.max());
		}
		// Fabric keeps a builder only if a builder method that returns the builder ran; a mixin accessor doesn't count.
		// Re-setting the same exclusive set changes nothing and marks it modified. It runs for every entry, changed or
		// not: a modified entry loses its known-pack info, so the server always syncs its own max level and a client's
		// own config or table never shows a different one.
		builder.exclusiveWith(accessor.enchanting2$getExclusiveSet());
		if (r.max() == d.maxLevel()) {
			return;
		}
		accessor.enchanting2$setDefinition(new Enchantment.EnchantmentDefinition(d.supportedItems(), d.primaryItems(),
				d.weight(), r.max(), d.minCost(), d.maxCost(), d.anvilCost(), d.slots()));
		if (r.why() == MaxLevels.Why.OVERRIDE) {
			Enchanting2.LOGGER.info("{} max level {} -> {} (max_level_override)", id, d.maxLevel(), r.max());
		} else {
			Enchanting2.LOGGER.debug("{} max level {} -> {} (DESIGN §8)", id, d.maxLevel(), r.max());
		}
	}

	/**
	 * The overrides file, parsed once per change: MODIFY fires once per enchantment, ~43 times per world load. A file
	 * that can't be read means no overrides (the §8 table still applies), logged once per change.
	 */
	private static EnchantmentOverrides overrides() {
		Path file = file();
		synchronized (LOCK) {
			Stamp stamp = stamp(file);
			if (cached != null && Objects.equals(stamp, cached.stamp())) {
				return cached.overrides();
			}
			ConfigFiles.LoadResult<EnchantmentOverrides> result = ConfigFiles.load(file, EnchantmentOverrides.CODEC);
			if (result.source() == ConfigFiles.Source.UNREADABLE) {
				Enchanting2.LOGGER.warn("{} could not be read, so every max_level_override is ignored for this world "
						+ "load. DESIGN §8 max levels still apply. Fix the file and reopen the world.", file);
			}
			// Stamped after load, which may have just created the file.
			cached = new Cached(stamp(file), result.config());
			return cached.overrides();
		}
	}

	private static Stamp stamp(Path file) {
		try {
			BasicFileAttributes a = Files.readAttributes(file, BasicFileAttributes.class);
			return new Stamp(a.lastModifiedTime().toMillis(), a.size());
		} catch (IOException e) {
			// Missing is normal before the first load creates it; anything else surfaces through ConfigFiles.load.
			// null == null in the cache check, so a file that can't be created is tried once, not once per enchantment.
			Enchanting2.LOGGER.debug("No stamp for {}: {}", file, e.toString());
			return null;
		}
	}
}
