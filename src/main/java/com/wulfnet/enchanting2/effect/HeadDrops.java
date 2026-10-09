package com.wulfnet.enchanting2.effect;

import com.mojang.datafixers.util.Pair;
import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.config.ServerConfigs;
import java.util.List;
import java.util.Optional;
import java.util.function.DoubleSupplier;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * DESIGN §9 Beheading (owner, 2026-10-09): a player's direct melee kill with a {@link ModEffects#HEAD_DROP_CHANCE}
 * weapon adds the victim's head to its death drops with that chance. Which head is the loot table
 * {@code enchanting2:beheading/heads} (vanilla's charged-creeper mapping plus player heads), so datapacks can add mobs.
 * Acts on the victim's own death table only, so {@code doMobLoot} and vanilla's drop rules still apply. At most one
 * head: a wither skeleton whose own roll already dropped its skull gets no second one. Any error leaves the drops as
 * vanilla made them.
 */
public final class HeadDrops {
	public static final ResourceKey<LootTable> HEADS = ResourceKey.create(Registries.LOOT_TABLE,
			Identifier.fromNamespaceAndPath(Enchanting2.MOD_ID, "beheading/heads"));

	private HeadDrops() {
	}

	public static void register() {
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			if (context.hasParameter(LootContextParams.THIS_ENTITY)
					&& context.hasParameter(LootContextParams.DAMAGE_SOURCE)) {
				apply(table, context, drops,
						ServerConfigs.get(context.getLevel().getServer()).beheading().playerHeads(),
						context.getRandom()::nextDouble);
			}
		});
	}

	/** The event handler with the config and the chance roll given. Mutates {@code drops}. */
	public static void apply(Holder<LootTable> table, LootContext context, List<ItemStack> drops, boolean playerHeads,
			DoubleSupplier roll) {
		Entity victim = context.getOptionalParameter(LootContextParams.THIS_ENTITY);
		DamageSource source = context.getOptionalParameter(LootContextParams.DAMAGE_SOURCE);
		if (victim == null || source == null) {
			return;
		}
		Optional<ResourceKey<LootTable>> ownTable = victim.getLootTable();
		if (ownTable.isEmpty() || !table.is(ownTable.get())) {
			return;
		}
		if (!source.isDirect() || !(source.getEntity() instanceof Player killer) || killer == victim
				|| (victim instanceof Player && !playerHeads)) {
			return;
		}
		ItemStack weapon = source.getWeaponItem();
		if (weapon == null || weapon.isEmpty()) {
			return;
		}
		ServerLevel level = context.getLevel();
		try {
			Pair<LevelBasedValue, Integer> chance = EnchantmentHelper.getHighestLevel(weapon,
					ModEffects.HEAD_DROP_CHANCE);
			if (chance == null) {
				return;
			}
			float p = chance.getFirst().calculate(chance.getSecond());
			if (!(roll.getAsDouble() < p)) {
				return;
			}
			for (ItemStack head : heads(level, context, victim, source)) {
				if (head.isEmpty() || drops.stream().anyMatch(drop -> drop.is(head.getItem()))) {
					continue;
				}
				Enchanting2.LOGGER.debug("Beheading {} on {}: dropping {}", chance.getSecond(),
						victim.getStringUUID(), head);
				drops.add(head);
			}
		} catch (RuntimeException e) {
			Enchanting2.LOGGER.error("Beheading failed for {} killed by {}; the drops are left as vanilla made them",
					victim.getStringUUID(), killer.getStringUUID(), e);
		}
	}

	private static List<ItemStack> heads(ServerLevel level, LootContext context, Entity victim, DamageSource source) {
		LootTable heads = level.getServer().reloadableRegistries().getLootTable(HEADS);
		if (heads == LootTable.EMPTY) {
			Enchanting2.LOGGER.warn("Beheading rolled for {} but loot table {} is missing (a datapack removed it?); "
					+ "no head dropped", victim.getStringUUID(), HEADS.identifier());
			return List.of();
		}
		Vec3 origin = context.getOptionalParameter(LootContextParams.ORIGIN);
		LootParams params = new LootParams.Builder(level)
				.withParameter(LootContextParams.THIS_ENTITY, victim)
				.withParameter(LootContextParams.ORIGIN, origin != null ? origin : victim.position())
				.withParameter(LootContextParams.DAMAGE_SOURCE, source)
				.withOptionalParameter(LootContextParams.ATTACKING_ENTITY, source.getEntity())
				.withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, source.getDirectEntity())
				.withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER,
						context.getOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER))
				.create(LootContextParamSets.ENTITY);
		return heads.getRandomItems(params);
	}
}
