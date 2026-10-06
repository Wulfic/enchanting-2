package com.wulfnet.enchanting2.experience;

import com.wulfnet.enchanting2.config.ExperienceSection;
import com.wulfnet.enchanting2.config.ServerConfigs;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/**
 * Which curve a player uses. A player in a {@link ServerLevel} uses that server's config. A client-side player uses
 * the client source, which the client entrypoint points at the server's synced copy. Until then, and on a dedicated
 * server where it is never set, the source is the vanilla curve, so a client on a vanilla server draws a vanilla bar.
 */
public final class PlayerExperience {
	private static volatile Supplier<ExperienceSection> clientSource = () -> ExperienceCurve.VANILLA;

	private PlayerExperience() {
	}

	public static ExperienceSection section(Player player) {
		if (player.level() instanceof ServerLevel level) {
			return ServerConfigs.get(level.getServer()).experience();
		}
		return clientSource.get();
	}

	public static void setClientSource(Supplier<ExperienceSection> source) {
		clientSource = Objects.requireNonNull(source, "source");
	}
}
