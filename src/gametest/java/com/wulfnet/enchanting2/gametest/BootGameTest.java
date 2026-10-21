package com.wulfnet.enchanting2.gametest;

import com.mojang.serialization.Lifecycle;
import com.wulfnet.enchanting2.Enchanting2;
import com.wulfnet.enchanting2.platform.Services;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;

public class BootGameTest {
	@GameTest
	public void modIsLoadedOnTheServer(GameTestHelper helper) {
		helper.assertTrue(Services.LOADER.isModLoaded(Enchanting2.MOD_ID),
				Component.literal("enchanting2 is not loaded on the server"));
		helper.succeed();
	}

	/**
	 * Vanilla puts the "Experimental settings" warning in front of every new world with a registry whose lifecycle
	 * isn't {@code Lifecycle.stable()}, and loads entries from a data pack with no known-pack identity that way. Forge
	 * gives mod packs none (ForgeKnownPacks, 13G). As vanilla, only a registry that isn't stable counts: Forge leaves
	 * code-registered entries with no registration info, in registries that stay stable. The test registries are
	 * skipped: their entries ship only in the GameTest mod.
	 */
	@GameTest
	public void everyEntryOfOursLoadsStable(GameTestHelper helper) {
		List<String> unstable = new ArrayList<>();
		helper.getLevel().registryAccess().registries().forEach(entry -> {
			if (!entry.key().equals(Registries.TEST_INSTANCE) && !entry.key().equals(Registries.TEST_ENVIRONMENT)) {
				collectUnstable(entry.value(), unstable);
			}
		});
		helper.assertTrue(unstable.isEmpty(), Component.literal(unstable.size()
				+ " enchanting2 registry entries aren't Lifecycle.stable() (new worlds would warn): " + unstable));
		helper.succeed();
	}

	private static <T> void collectUnstable(Registry<T> registry, List<String> unstable) {
		if (registry.registryLifecycle() == Lifecycle.stable()) {
			return;
		}
		for (ResourceKey<T> key : registry.registryKeySet()) {
			if (key.identifier().getNamespace().equals(Enchanting2.MOD_ID)) {
				Lifecycle lifecycle = registry.registrationInfo(key).map(RegistrationInfo::lifecycle).orElse(null);
				if (lifecycle != Lifecycle.stable()) {
					unstable.add(key + " " + lifecycle);
				}
			}
		}
	}
}
