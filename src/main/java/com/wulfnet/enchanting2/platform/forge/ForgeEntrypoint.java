package com.wulfnet.enchanting2.platform.forge;

import com.wulfnet.enchanting2.Enchanting2;
import java.lang.reflect.InvocationTargetException;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Forge's entry point for both sides. Forge's {@code @Mod} has no client-only variant, so on a client this also starts
 * {@code ForgeClientEntrypoint} (in the client source set, which this one can't compile against), by name.
 */
@Mod(Enchanting2.MOD_ID)
public final class ForgeEntrypoint {
	private static final String CLIENT_ENTRYPOINT = "com.wulfnet.enchanting2.platform.forge.ForgeClientEntrypoint";

	public ForgeEntrypoint(FMLJavaModLoadingContext context) {
		ForgeBus.set(context.getModBusGroup());
		Enchanting2.init();
		if (FMLEnvironment.dist.isClient()) {
			startClient();
		}
	}

	private static void startClient() {
		try {
			Class.forName(CLIENT_ENTRYPOINT).getMethod("init").invoke(null);
		} catch (InvocationTargetException e) {
			Enchanting2.LOGGER.error("Enchanting 2 client init failed", e.getTargetException());
			throw new IllegalStateException("Enchanting 2 client init failed", e.getTargetException());
		} catch (ReflectiveOperationException e) {
			// The jar always carries the client classes; missing ones mean a broken build, not a server.
			Enchanting2.LOGGER.error("Enchanting 2's client entry point {} can't be started", CLIENT_ENTRYPOINT, e);
			throw new IllegalStateException("Enchanting 2's client entry point can't be started", e);
		}
	}
}
