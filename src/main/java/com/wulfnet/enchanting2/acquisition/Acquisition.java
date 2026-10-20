package com.wulfnet.enchanting2.acquisition;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.config.ServerConfigs;
import com.wulfnet.enchanting2.mixin.HolderReferenceAccessor;
import com.wulfnet.enchanting2.platform.Services;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Phase 9 on the running server: finds the rules for an enchantment holder and makes the weighted picks. A holder whose
 * registry has no running server (see {@link AcquisitionHost}) gets vanilla behaviour, logged once.
 */
public final class Acquisition {
	// Only throttles a log line; holds no world state.
	private static final AtomicBoolean WARNED_NO_SERVER = new AtomicBoolean();

	private Acquisition() {
	}

	public static void register() {
		Services.EVENTS.onServerStarting(server -> stamp(server, server));
		Services.EVENTS.onServerStopped(server -> stamp(server, null));
	}

	private static void stamp(MinecraftServer server, MinecraftServer value) {
		Registry<Enchantment> registry = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		if (registry instanceof AcquisitionHost host) {
			host.enchanting2$setAcquisitionServer(value);
		} else {
			// Fail open to vanilla: loot and trades keep working, without the enchantments.json5 flags and weights.
			Enchanting2.LOGGER.error("The enchantment registry is a {}, not a MappedRegistry; enchantments.json5 "
					+ "acquisition settings (enabled, table, loot, trade, weights) won't apply", registry.getClass());
		}
	}

	/** This server's rules, or empty for a holder outside any running server's enchantment registry. */
	public static Optional<AcquisitionRules> rules(Holder<Enchantment> enchantment) {
		MinecraftServer server = null;
		if (enchantment instanceof Holder.Reference<Enchantment> reference
				&& ((HolderReferenceAccessor) reference).enchanting2$owner() instanceof AcquisitionHost host) {
			server = host.enchanting2$acquisitionServer();
		}
		if (server == null) {
			if (WARNED_NO_SERVER.compareAndSet(false, true)) {
				Enchanting2.LOGGER.warn("Enchantment {} isn't from a running server's registry; picking it with vanilla "
						+ "rules (logged once)", enchantment.getRegisteredName());
			}
			return Optional.empty();
		}
		return Optional.of(new AcquisitionRules(ServerConfigs.enchantments(server)));
	}

	public static boolean allows(Holder<Enchantment> enchantment, Source source) {
		return rules(enchantment).map(r -> r.allows(enchantment.getRegisteredName(), source)).orElse(true);
	}

	/** The pick weight for {@code source}: the override, else the registry weight. */
	public static int weight(Holder<Enchantment> enchantment, Source source) {
		int registryWeight = enchantment.value().getWeight();
		return rules(enchantment).map(r -> r.weight(enchantment.getRegisteredName(), source, registryWeight))
				.orElse(registryWeight);
	}

	/** @param tagged whether the vanilla tag the caller checked contains it */
	public static boolean treasure(Holder<Enchantment> enchantment, boolean tagged) {
		return rules(enchantment).map(r -> r.treasure(enchantment.getRegisteredName(), tagged)).orElse(tagged);
	}

	/**
	 * Replaces one of vanilla's uniform picks: drops the candidates {@code source} doesn't allow and weighs the rest.
	 * Outside a running server it returns {@code vanilla} unchanged.
	 *
	 * @return empty when nothing is allowed, which vanilla's callers already handle (no offer, an unenchanted item)
	 */
	public static Optional<Holder<Enchantment>> pick(List<Holder<Enchantment>> candidates, Source source,
			RandomSource random, Supplier<Optional<Holder<Enchantment>>> vanilla) {
		if (candidates.isEmpty() || rules(candidates.getFirst()).isEmpty()) {
			return vanilla.get();
		}
		List<Holder<Enchantment>> allowed = candidates.stream().filter(e -> allows(e, source)).toList();
		return WeightedPick.pick(allowed, e -> weight(e, source), random::nextInt);
	}
}
