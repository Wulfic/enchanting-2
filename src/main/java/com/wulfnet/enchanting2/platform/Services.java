package com.wulfnet.enchanting2.platform;

/**
 * The one class that picks a mod loader (Phase 13, decisions.md 2026-10-20). Each Stonecutter node compiles only its
 * own loader's package ({@code platform/fabric}, {@code platform/neoforge}, {@code platform/forge}); game code reaches
 * the loader through these fields and never imports a loader API. {@code PlatformImportsTest} enforces that.
 */
public final class Services {
	public static final LoaderInfo LOADER;
	public static final ServerEvents EVENTS;
	public static final ItemHooks ITEMS;
	public static final Attachments ATTACHMENTS;
	public static final Networking NETWORK;
	public static final Registration REGISTRY;

	static {
		//? if fabric {
		LOADER = new com.wulfnet.enchanting2.platform.fabric.FabricLoaderInfo();
		EVENTS = new com.wulfnet.enchanting2.platform.fabric.FabricServerEvents();
		ITEMS = new com.wulfnet.enchanting2.platform.fabric.FabricItemHooks();
		ATTACHMENTS = new com.wulfnet.enchanting2.platform.fabric.FabricAttachments();
		NETWORK = new com.wulfnet.enchanting2.platform.fabric.FabricNetworking();
		REGISTRY = new com.wulfnet.enchanting2.platform.fabric.FabricRegistration();
		//?} else if neoforge {
		/*LOADER = new com.wulfnet.enchanting2.platform.neoforge.NeoForgeLoaderInfo();
		EVENTS = new com.wulfnet.enchanting2.platform.neoforge.NeoForgeServerEvents();
		ITEMS = new com.wulfnet.enchanting2.platform.neoforge.NeoForgeItemHooks();
		ATTACHMENTS = new com.wulfnet.enchanting2.platform.neoforge.NeoForgeAttachments();
		NETWORK = new com.wulfnet.enchanting2.platform.neoforge.NeoForgeNetworking();
		REGISTRY = new com.wulfnet.enchanting2.platform.neoforge.NeoForgeRegistration();
		*///?}
	}

	private Services() {
	}
}
