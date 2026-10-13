package com.wulfnet.enchanting2.client;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.screen.PanelText.Arg;
import com.wulfnet.enchanting2.screen.PanelText.Msg;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Turns {@code PanelText} messages into components: enchantment names from the client's registry, numerals. */
public final class PanelTextResolver {
	private static final Logger LOG = LoggerFactory.getLogger(Enchanting2.MOD_ID);
	/** Vanilla ships {@code enchantment.level.1..10}; past that a level is plain digits. */
	private static final int MAX_NUMERAL = 10;
	/** IDs already warned about, so a missing enchantment logs once instead of every frame. */
	private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

	/** @param registries null before the client has a level; names then fall back to the ID */
	record Context(HolderLookup.@Nullable Provider registries, ItemStack target) {
	}

	private PanelTextResolver() {
	}

	/** A message as the player reads it on a screen acting on {@code target}, names from the client's level. */
	public static Component forScreen(Msg msg, ItemStack target) {
		ClientLevel level = Minecraft.getInstance().level;
		return of(msg, new Context(level != null ? level.registryAccess() : null, target));
	}

	static Component of(Msg msg, Context context) {
		Object[] args = msg.args().stream().map(arg -> resolve(arg, context)).toArray();
		return Component.translatable(msg.key(), args);
	}

	private static Component resolve(Arg arg, Context context) {
		return switch (arg) {
			case Arg.Num num -> Component.literal(Long.toString(num.value()));
			case Arg.Ench ench -> enchantment(ench.id(), context).map(h -> h.value().description().copy())
					.orElseGet(() -> Component.literal(ench.id()));
			case Arg.EnchLevel ench -> named(ench.id(), ench.level(), context);
			case Arg.Level level -> numeral(level.level());
			case Arg.TargetItem target -> target(context);
		};
	}

	/** Like a tooltip: the level is left off a level-1 enchantment whose maximum is 1 (Mending, Silk Touch). */
	private static Component named(String id, int level, Context context) {
		Optional<Holder.Reference<Enchantment>> holder = enchantment(id, context);
		MutableComponent name = holder.map(h -> h.value().description().copy())
				.orElseGet(() -> Component.literal(id));
		boolean single = holder.map(h -> h.value().getMaxLevel() == 1).orElse(false);
		return single && level == 1 ? name : name.append(" ").append(numeral(level));
	}

	private static Component numeral(int level) {
		return level >= 1 && level <= MAX_NUMERAL
				? Component.translatable("enchantment.level." + level)
				: Component.literal(Integer.toString(level));
	}

	private static Component target(Context context) {
		return context.target().isEmpty() ? Component.translatable("enchanting2.anvil.target.empty")
				: context.target().getHoverName();
	}

	private static Optional<Holder.Reference<Enchantment>> enchantment(String id, Context context) {
		Identifier identifier = Identifier.tryParse(id);
		if (identifier == null || context.registries() == null) {
			warnOnce(id, "it isn't a valid ID or there is no level yet");
			return Optional.empty();
		}
		Optional<Holder.Reference<Enchantment>> holder = context.registries().lookup(Registries.ENCHANTMENT)
				.flatMap(lookup -> lookup.get(ResourceKey.create(Registries.ENCHANTMENT, identifier)));
		if (holder.isEmpty()) {
			warnOnce(id, "the client's enchantment registry doesn't have it");
		}
		return holder;
	}

	private static void warnOnce(String id, String why) {
		if (WARNED.add(id)) {
			LOG.warn("A panel names enchantment {} by ID: {}", id, why);
		}
	}
}
