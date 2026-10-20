package com.wulfnet.enchanting2.spawner;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.port.LootTool;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * DESIGN §6 "Silk Touch and spawners" (owner, 2026-10-10 and 2026-10-12): with {@code silk_touch.collect_spawners} on,
 * a Silk Touch break of a mob spawner drops it as an item carrying its mob type only, and gives no XP. The item holds
 * a minimal {@code block_entity_data} (so vanilla's tooltip and op placement work) and our {@link #STAMP}. Vanilla
 * copies spawner data only for operators; for anyone else a stamped item gets its mob back through
 * {@link SpawnerBlockEntity#setEntityId}, the spawn-egg path, and only for a type that has a spawn egg. So a forged
 * item grants nothing a spawn egg doesn't already, and every other field in the data is ignored.
 */
public final class CollectedSpawners {
	/** Set on a spawner item our drop made. Placement restores the mob only from stamped items. */
	public static final DataComponentType<Unit> STAMP = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			Identifier.fromNamespaceAndPath(Enchanting2.MOD_ID, "collected_spawner"),
			DataComponentType.<Unit>builder().persistent(Unit.CODEC).networkSynchronized(StreamCodec.unit(Unit.INSTANCE))
					.build());

	private CollectedSpawners() {
	}

	/** Loads the class, which registers {@link #STAMP}. Call once from mod init. */
	public static void register() {
		Enchanting2.LOGGER.debug("Registered item component {}", BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(STAMP));
	}

	/** Whether breaking a spawner with {@code tool} collects it: the option on and Silk Touch on the tool. */
	public static boolean collects(boolean collectSpawners, ItemStack tool, HolderLookup.Provider registries) {
		if (!collectSpawners || tool == null || tool.isEmpty()) {
			return false;
		}
		return EnchantmentHelper.getItemEnchantmentLevel(
				registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), tool) > 0;
	}

	/**
	 * Adds the spawner item to the broken spawner's own (empty in vanilla) loot. Vanilla only rolls block loot for a
	 * survival break with the correct tool, so those rules hold here too. Mutates {@code drops}. Any error leaves
	 * them as they were.
	 */
	public static void addDrop(Holder<LootTable> table, LootContext context, List<ItemStack> drops,
			boolean collectSpawners) {
		BlockState state = context.getOptionalParameter(LootContextParams.BLOCK_STATE);
		if (state == null || !state.is(Blocks.SPAWNER)) {
			return;
		}
		Optional<ResourceKey<LootTable>> ownTable = state.getBlock().getLootTable();
		if (ownTable.isEmpty() || !table.is(ownTable.get())) {
			return;
		}
		ServerLevel level = context.getLevel();
		if (!collects(collectSpawners, LootTool.of(context), level.registryAccess())) {
			return;
		}
		BlockEntity blockEntity = context.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
		if (!(blockEntity instanceof SpawnerBlockEntity spawner)) {
			Enchanting2.LOGGER.warn("Silk-touched spawner at {} has no spawner block entity ({}); nothing dropped",
					context.getOptionalParameter(LootContextParams.ORIGIN), blockEntity);
			return;
		}
		try {
			ItemStack item = item(mobOf(spawner, level.registryAccess()));
			Enchanting2.LOGGER.debug("Silk Touch collected the spawner at {}: {}", spawner.getBlockPos(), item);
			drops.add(item);
		} catch (RuntimeException e) {
			Enchanting2.LOGGER.error("Collecting the spawner at {} failed; nothing dropped", spawner.getBlockPos(), e);
		}
	}

	/** The stamped spawner item for {@code mob}, or a plain spawner item for a spawner with no mob. */
	public static ItemStack item(Optional<EntityType<?>> mob) {
		ItemStack stack = new ItemStack(Items.SPAWNER);
		mob.ifPresent(type -> {
			CompoundTag entity = new CompoundTag();
			entity.putString("id", EntityType.getKey(type).toString());
			CompoundTag spawnData = new CompoundTag();
			spawnData.put("entity", entity);
			CompoundTag data = new CompoundTag();
			data.put("SpawnData", spawnData);
			stack.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(BlockEntityType.MOB_SPAWNER, data));
			stack.set(STAMP, Unit.INSTANCE);
		});
		return stack;
	}

	/** The mob the spawner will spawn next, read the way it saves. */
	public static Optional<EntityType<?>> mobOf(SpawnerBlockEntity spawner, HolderLookup.Provider registries) {
		return mobOf(spawner.saveWithoutMetadata(registries));
	}

	/**
	 * {@code SpawnData.entity.id} of saved spawner data, if it names a known entity type. Looked up in the registry, as
	 * {@code EntityType.byString} did before 26.2 removed it.
	 */
	static Optional<EntityType<?>> mobOf(CompoundTag spawnerData) {
		return spawnerData.getCompound("SpawnData").flatMap(data -> data.getCompound("entity"))
				.flatMap(entity -> entity.getString("id")).map(Identifier::tryParse)
				.flatMap(BuiltInRegistries.ENTITY_TYPE::getOptional);
	}

	/**
	 * Called after vanilla's {@code BlockItem.updateCustomBlockEntityTag} refused the item's data (the op gate). For a
	 * stamped spawner item, sets the placed spawner's mob as a spawn egg would. Returns whether it did.
	 */
	public static boolean restore(Level level, Player player, BlockPos pos, ItemStack stack) {
		if (!(level instanceof ServerLevel) || !stack.has(STAMP)) {
			return false;
		}
		TypedEntityData<BlockEntityType<?>> data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
		if (data == null || data.type() != BlockEntityType.MOB_SPAWNER
				|| !(level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner)) {
			return false;
		}
		Optional<EntityType<?>> mob = mobOf(data.copyTagWithoutId());
		// The guard against forged items. 26.1's byId returns an Optional (any item whose default entity data names the
		// type), so a leftover "== null" there compiles and lets every type through: it must be a spawn egg (Phase 12B).
		//? if >=26.1 {
		/*if (mob.isEmpty() || SpawnEggItem.byId(mob.get()).filter(egg -> egg.value() instanceof SpawnEggItem).isEmpty()) {
		*///?} else
		if (mob.isEmpty() || SpawnEggItem.byId(mob.get()) == null) {
			Enchanting2.LOGGER.warn("{} placed a stamped spawner at {} naming {}, which has no spawn egg; left empty",
					player == null ? "Something" : player.getName().getString(), pos,
					data.copyTagWithoutId().getCompound("SpawnData"));
			return false;
		}
		try {
			spawner.setEntityId(mob.get(), level.getRandom());
			spawner.setChanged();
			BlockState state = level.getBlockState(pos);
			level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
		} catch (RuntimeException e) {
			Enchanting2.LOGGER.error("Restoring a collected spawner's mob {} at {} failed; it may be left empty",
					EntityType.getKey(mob.get()), pos, e);
			return false;
		}
		Enchanting2.LOGGER.debug("Restored a collected spawner's mob {} at {}", EntityType.getKey(mob.get()), pos);
		return true;
	}
}
