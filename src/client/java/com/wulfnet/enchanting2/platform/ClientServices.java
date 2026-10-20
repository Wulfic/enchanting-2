package com.wulfnet.enchanting2.platform;

/** The client half of {@link Services}: the one client class that picks a mod loader. */
public final class ClientServices {
	public static final ClientNetworking NETWORK;
	public static final ClientHooks HOOKS;

	static {
		//? if fabric {
		NETWORK = new com.wulfnet.enchanting2.platform.fabric.FabricClientNetworking();
		HOOKS = new com.wulfnet.enchanting2.platform.fabric.FabricClientHooks();
		//?} else if neoforge {
		/*NETWORK = new com.wulfnet.enchanting2.platform.neoforge.NeoForgeClientNetworking();
		HOOKS = new com.wulfnet.enchanting2.platform.neoforge.NeoForgeClientHooks();
		*///?}
	}

	private ClientServices() {
	}
}
