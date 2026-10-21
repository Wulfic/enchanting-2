package com.wulfnet.enchanting2.platform.forge;

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
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Forge's counterpart of Fabric's {@code LootTableEvents.MODIFY_DROPS}, as NeoForge's: one global loot modifier, with
 * no condition, that hands every roll to the {@link ServerEvents#onModifyDrops} listeners. Forge 26.x still reads the
 * modifiers named in {@code data/forge/loot_modifiers/global_loot_modifiers.json} (here {@code enchanting2:drops}, from
 * {@code data/enchanting2/loot_modifiers/drops.json}), and its codec still takes a required {@code conditions} array.
 * Forge applies it after the table's own functions.
 */
final class DropsLootModifier extends LootModifier {
	static final String NAME = "drops";
	static final MapCodec<DropsLootModifier> CODEC = RecordCodecBuilder.mapCodec(
			instance -> codecStart(instance).apply(instance, DropsLootModifier::new));
	private static final Logger LOG = LoggerFactory.getLogger(DropsLootModifier.class);

	private DropsLootModifier(LootItemCondition[] conditions) {
		super(conditions);
	}

	@Override
	protected ObjectArrayList<ItemStack> doApply(LootTable lootTable, ObjectArrayList<ItemStack> generatedLoot,
			LootContext context) {
		Identifier id = context.getQueriedLootTableId();
		Optional<Holder.Reference<LootTable>> table = context.getLevel().getServer().reloadableRegistries().lookup()
				.lookupOrThrow(Registries.LOOT_TABLE).get(ResourceKey.create(Registries.LOOT_TABLE, id));
		if (table.isEmpty()) {
			// An inline table (no registry entry) can't be handed on as a Holder; ours only act on block and entity
			// tables, which are always registered.
			LOG.debug("Loot roll of unregistered table {}; Enchanting 2 drop listeners skipped", id);
			return generatedLoot;
		}
		for (ServerEvents.DropsModifier listener : ForgeServerEvents.DROPS) {
			listener.modify(table.get(), context, generatedLoot);
		}
		return generatedLoot;
	}

	@Override
	public MapCodec<? extends IGlobalLootModifier> codec() {
		return CODEC;
	}
}
