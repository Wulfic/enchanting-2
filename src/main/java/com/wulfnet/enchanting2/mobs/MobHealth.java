package com.wulfnet.enchanting2.mobs;

import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.config.ServerConfigs;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * The mob health multiplier (owner, 2026-10-13; TODO 10m; {@code mobs.health_multiplier}): every {@link Mob}'s max
 * health times the multiplier, bosses included, never players. One {@code max_health} modifier,
 * {@code add_multiplied_total} by {@code multiplier − 1}, put on when a mob enters a level and again on every mob in
 * the world after a config reload.
 *
 * <p>The modifier is saved with the mob (permanent), unlike enchantment modifiers: vanilla loads attributes before
 * health, so a 3x zombie at 30 of 60 comes back at 30 of 60. A transient one would be missing when the saved health is
 * read, clamp it to 20, and heal the mob on every chunk load. The cost: a world opened without the mod keeps its
 * multiplied mobs (DESIGN §2a). A change keeps each mob's share of health: half health stays half.
 */
public final class MobHealth {
	public static final Identifier MODIFIER_ID = Identifier.fromNamespaceAndPath(Enchanting2.MOD_ID,
			"health_multiplier");
	/** Raised from vanilla's 1024 so 100x is really 100x (owner, 2026-10-13): the Wither at 100x is 30,000. */
	public static final double MAX_HEALTH_CEILING = 1_000_000.0;

	private MobHealth() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob) {
				apply(mob, ServerConfigs.get(level.getServer()).mobs().healthMultiplier());
			}
		});
	}

	/** After a config reload: every loaded mob in every level. */
	public static void applyAll(MinecraftServer server) {
		int multiplier = ServerConfigs.get(server).mobs().healthMultiplier();
		int changed = 0;
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity entity : level.getAllEntities()) {
				if (entity instanceof Mob mob && apply(mob, multiplier)) {
					changed++;
				}
			}
		}
		if (changed > 0) {
			Enchanting2.LOGGER.info("Mob health multiplier {}x applied to {} loaded mob(s)", multiplier, changed);
		}
	}

	/**
	 * Sets {@code mob}'s multiplier, keeping its share of health.
	 *
	 * @return whether anything changed (false when it already had this multiplier, or has no max health)
	 */
	public static boolean apply(Mob mob, int multiplier) {
		AttributeInstance maxHealth = mob.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth == null) {
			Enchanting2.LOGGER.warn("{} has no max_health attribute; health multiplier skipped", mob);
			return false;
		}
		double amount = multiplier - 1.0;
		AttributeModifier existing = maxHealth.getModifier(MODIFIER_ID);
		if ((existing == null ? 0.0 : existing.amount()) == amount) {
			return false;
		}
		float share = mob.getMaxHealth() > 0 ? mob.getHealth() / mob.getMaxHealth() : 1.0F;
		maxHealth.removeModifier(MODIFIER_ID);
		if (amount > 0) {
			maxHealth.addPermanentModifier(new AttributeModifier(MODIFIER_ID, amount,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		if (mob.isAlive()) {
			mob.setHealth(share * mob.getMaxHealth());
		}
		return true;
	}
}
