package com.wulfnet.enchanting2.gametest;

import com.wulfnet.enchanting2.enchantment.ModEnchantments;
import com.wulfnet.enchanting2.gametest.mixin.EntityInvoker;
import com.wulfnet.enchanting2.movement.DoubleJump;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.vehicle.minecart.Minecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 10cGC: Double Jump's rules (when an air jump is allowed, once per air time) and the jump itself, on a mock
 * player. The client's key handling, with real presses, is {@code DoubleJumpClientGameTest}.
 */
public class DoubleJumpGameTest {
	private static void check(GameTestHelper helper, boolean condition, String message) {
		helper.assertTrue(condition, Component.literal(message));
	}

	private static ItemStack boots(GameTestHelper helper) {
		ItemStack stack = new ItemStack(Items.IRON_BOOTS);
		stack.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
				.getOrThrow(ModEnchantments.DOUBLE_JUMP), 1);
		return stack;
	}

	/** A survival player in mid-air at {@code at}, wearing Double Jump boots, 10 blocks into a fall. */
	private static ServerPlayer falling(GameTestHelper helper, Vec3 at) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.setPos(helper.absoluteVec(at));
		player.setOnGround(false);
		player.setItemSlot(EquipmentSlot.FEET, boots(helper));
		player.fallDistance = 10.0;
		return player;
	}

	@GameTest
	public void oneAirJumpUntilLandingAndItBreaksTheFall(GameTestHelper helper) {
		ServerPlayer player = falling(helper, new Vec3(2.5, 5.0, 2.5));
		player.setDeltaMovement(0.0, -0.8, 0.0);
		check(helper, DoubleJump.canAirJump(player), "an air jump with the boots was refused");
		DoubleJump.jump(player);
		check(helper, player.getDeltaMovement().y > 0.4, "the air jump didn't lift: " + player.getDeltaMovement());
		check(helper, player.fallDistance == 0.0, "the air jump didn't reset the fall: " + player.fallDistance);
		check(helper, !DoubleJump.canAirJump(player), "a second air jump before landing was allowed");
		player.setOnGround(true);
		DoubleJump.tick(player);
		player.setOnGround(false);
		check(helper, DoubleJump.canAirJump(player), "landing didn't ready the air jump again");
		helper.succeed();
	}

	@GameTest
	public void noBootsTheGroundFlightRidingOrWaterRefuseIt(GameTestHelper helper) {
		ServerPlayer bare = falling(helper, new Vec3(1.5, 5.0, 1.5));
		bare.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
		check(helper, !DoubleJump.canAirJump(bare), "an air jump without the boots");

		ServerPlayer grounded = falling(helper, new Vec3(2.5, 5.0, 1.5));
		grounded.setOnGround(true);
		check(helper, !DoubleJump.canAirJump(grounded), "an air jump on the ground");

		ServerPlayer flying = falling(helper, new Vec3(3.5, 5.0, 1.5));
		flying.getAbilities().flying = true;
		check(helper, !DoubleJump.canAirJump(flying), "an air jump while flying");

		ServerPlayer riding = falling(helper, new Vec3(4.5, 5.0, 1.5));
		Minecart cart = helper.spawn(EntityType.MINECART, new BlockPos(4, 5, 1));
		check(helper, riding.startRiding(cart, true, true), "the player didn't get into the minecart");
		check(helper, !DoubleJump.canAirJump(riding), "an air jump while riding");

		helper.setBlock(new BlockPos(5, 2, 4), Blocks.WATER);
		ServerPlayer swimming = falling(helper, new Vec3(5.5, 2.0, 4.5));
		((EntityInvoker) swimming).e2test$updateInWaterStateAndDoFluidPushing();
		check(helper, swimming.isInWater(), "the player isn't in the water");
		check(helper, !DoubleJump.canAirJump(swimming), "an air jump in water");
		helper.succeed();
	}
}
