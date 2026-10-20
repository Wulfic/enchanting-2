package com.wulfnet.enchanting2.gametest.platform;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import net.minecraft.world.level.block.Rotation;

/**
 * The server GameTest annotation on loaders without Fabric's (Phase 13C; decisions.md 2026-10-20 "13B: server GameTests
 * keep Fabric's @GameTest"). Fabric nodes compile the tests against Fabric's own; on the other nodes a Stonecutter
 * replacement swaps that one import for this class, and the loader's registrar reads it. Same attributes and defaults
 * as Fabric's {@code net.fabricmc.fabric.api.gametest.v1.GameTest}, except the structure: Fabric's empty template isn't
 * there, so ours (the same 8x8x8 of air, {@code scripts/make-empty-structure.py}) is.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface GameTest {
	String environment() default "minecraft:default";

	/** The level the test runs in; 26.3 added it to TestData (and Fabric's annotation). Ignored before 26.3. */
	String dimension() default "minecraft:overworld";

	String structure() default "enchanting2_gametest:empty";

	int maxTicks() default 20;

	int setupTicks() default 0;

	boolean required() default true;

	Rotation rotation() default Rotation.NONE;

	boolean manualOnly() default false;

	int maxAttempts() default 1;

	int requiredSuccesses() default 1;

	boolean skyAccess() default false;

	int padding() default 1;
}
