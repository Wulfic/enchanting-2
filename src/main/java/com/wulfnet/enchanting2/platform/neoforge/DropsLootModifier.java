package com.wulfnet.enchanting2.platform.neoforge;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.wulfnet.enchanting2.platform.ServerEvents;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NeoForge's counterpart of Fabric's {@code LootTableEvents.MODIFY_DROPS}: one global loot modifier, with no condition,
 * that hands every roll to the {@link ServerEvents#onModifyDrops} listeners. Loaded from
 * {@code data/enchanting2/loot_modifiers/drops.json}: NeoForge 26.x reads every file in a {@code loot_modifiers/} folder
 * as a modifier (there is no {@code global_loot_modifiers.json} list any more; one would be logged as an unparsable
 * modifier). NeoForge applies it after the table's own functions.
 */
final class DropsLootModifier extends LootModifier {
	static final String NAME = "drops";
	static final MapCodec<DropsLootModifier> CODEC = RecordCodecBuilder.mapCodec(
			instance -> codecStart(instance).apply(instance, DropsLootModifier::new));
	private static final Logger LOG = LoggerFactory.getLogger(DropsLootModifier.class);

	// NeoForge 26.3 takes one optional condition ("condition"); 26.2 a required array ("conditions"). The JSON is
	// written with an empty "conditions", like all our data in the older format, and gradle/common.gradle's 26.3
	// upgrade drops the empty list there (Phase 13C, 26.2).
	//? if >=26.3 {
	/*private DropsLootModifier(Optional<Holder<LootItemCondition>> condition, int priority) {
		super(condition, priority);
	}
	*///?} else {
	private DropsLootModifier(LootItemCondition[] conditions, int priority) {
		super(conditions, priority);
	}
	//?}

	@Override
	protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
		Identifier id = context.getQueriedLootTableId();
		Optional<Holder.Reference<LootTable>> table = context.getLevel().getServer().reloadableRegistries().lookup()
				.lookupOrThrow(Registries.LOOT_TABLE).get(ResourceKey.create(Registries.LOOT_TABLE, id));
		if (table.isEmpty()) {
			// An inline table (no registry entry) can't be handed on as a Holder; ours only act on block and entity
			// tables, which are always registered.
			LOG.debug("Loot roll of unregistered table {}; Enchanting 2 drop listeners skipped", id);
			return generatedLoot;
		}
		for (ServerEvents.DropsModifier listener : NeoForgeServerEvents.DROPS) {
			listener.modify(table.get(), context, generatedLoot);
		}
		return generatedLoot;
	}

	@Override
	public MapCodec<? extends IGlobalLootModifier> codec() {
		return CODEC;
	}
}
