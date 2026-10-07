package com.wulfnet.enchanting2.mixin;

import com.wulfnet.enchanting2.config.CommonConfig;
import com.wulfnet.enchanting2.config.EnchantmentOverrides;
import com.wulfnet.enchanting2.config.ServerConfigHolder;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin implements ServerConfigHolder {
	// Volatile: the join check reads it on a netty thread while /reload replaces it on the server thread.
	@Unique
	private volatile CommonConfig enchanting2$config;
	@Unique
	private volatile EnchantmentOverrides enchanting2$enchantments;

	@Override
	public CommonConfig enchanting2$config() {
		return enchanting2$config;
	}

	@Override
	public void enchanting2$setConfig(CommonConfig config) {
		this.enchanting2$config = config;
	}

	@Override
	public EnchantmentOverrides enchanting2$enchantments() {
		return enchanting2$enchantments;
	}

	@Override
	public void enchanting2$setEnchantments(EnchantmentOverrides enchantments) {
		this.enchanting2$enchantments = enchantments;
	}
}
