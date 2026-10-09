package com.wulfnet.enchanting2.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.wulfnet.enchanting2.acquisition.AcquisitionScope;
import com.wulfnet.enchanting2.acquisition.Source;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.VillagerTrades;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Phase 9: villager offers that enchant gear ({@code #on_traded_equipment}, or an enchantment provider) reach the same
 * {@code selectEnchantment} as loot. Running them in a TRADE scope makes that roll use the trade flag and weight.
 */
@Mixin({VillagerTrades.EnchantedItemForEmeralds.class, VillagerTrades.ItemsForEmeralds.class,
		VillagerTrades.ItemsAndEmeraldsToItems.class})
abstract class VillagerTradeScopeMixin {
	@WrapMethod(method = "getOffer")
	private MerchantOffer enchanting2$tradeScope(ServerLevel level, Entity trader, RandomSource random,
			Operation<MerchantOffer> original) {
		return AcquisitionScope.within(Source.TRADE, () -> original.call(level, trader, random));
	}
}
