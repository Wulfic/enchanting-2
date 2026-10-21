package com.wulfnet.enchanting2.gametest.platform.neoforge;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.wulfnet.enchanting2.gametest.platform.GameTest;
import com.wulfnet.enchanting2.gametest.platform.GameTestDiscovery;
import com.wulfnet.enchanting2.gametest.platform.GameTestDiscovery.TestMethod;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The GameTest mod on NeoForge (Phase 13C): registers each test {@link GameTestDiscovery} finds as a test function,
 * then as a test instance. Only runs when NeoForge enables GameTests (dev and GameTest servers).
 */
@Mod(GameTestDiscovery.MOD_ID)
public final class NeoForgeGameTests {
	private static final String MOD_ID = GameTestDiscovery.MOD_ID;
	private static final Logger LOG = LoggerFactory.getLogger(NeoForgeGameTests.class);
	private static final String EMPTY_ALL_OF = "{\"type\":\"minecraft:all_of\",\"definitions\":[]}";

	private final List<TestMethod> tests;

	public NeoForgeGameTests(IEventBus modBus) {
		if (!GameTestHooks.isGametestEnabled()) {
			tests = List.of();
			return;
		}
		tests = GameTestDiscovery.findTests();
		modBus.addListener(RegisterEvent.class, this::registerFunctions);
		modBus.addListener(RegisterGameTestsEvent.class, this::registerTests);
	}

	private void registerFunctions(RegisterEvent event) {
		event.register(Registries.TEST_FUNCTION, helper -> {
			for (TestMethod test : tests) {
				helper.register(test.id(), test.function());
			}
		});
	}

	private void registerTests(RegisterGameTestsEvent event) {
		// One environment of ours per environment a test names. Every one we use is an empty all_of (vanilla's default
		// and data/enchanting2/test_environment/*): it only groups tests into a batch, so an empty one of our own
		// batches the same. The event offers no lookup of the data-loaded ones; anything else is refused, not dropped.
		Map<String, Holder<TestEnvironmentDefinition<?>>> environments = new HashMap<>();
		for (TestMethod test : tests) {
			String name = test.annotation().environment();
			Holder<TestEnvironmentDefinition<?>> environment = environments.computeIfAbsent(name, n -> {
				requireEmptyAllOf(n);
				Identifier source = Identifier.parse(n);
				return event.registerEnvironment(
						Identifier.fromNamespaceAndPath(MOD_ID, source.getNamespace() + "/" + source.getPath()));
			});
			GameTest a = test.annotation();
			//? if >=26.3 {
			/*TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(environment,
					ResourceKey.create(Registries.DIMENSION, Identifier.parse(a.dimension())), Identifier.parse(a.structure()),
					a.maxTicks(), a.setupTicks(), a.required(), a.rotation(), a.manualOnly(), a.maxAttempts(),
					a.requiredSuccesses(), a.skyAccess(), a.padding());
			*///?} else {
			TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(environment, Identifier.parse(a.structure()),
					a.maxTicks(), a.setupTicks(), a.required(), a.rotation(), a.manualOnly(), a.maxAttempts(),
					a.requiredSuccesses(), a.skyAccess(), a.padding());
			//?}
			event.registerTest(test.id(), new FunctionGameTestInstance(
					ResourceKey.create(Registries.TEST_FUNCTION, test.id()), data));
		}
		LOG.info("Registered {} Enchanting 2 GameTest(s) in {} environment(s)", tests.size(), environments.size());
	}

	/** Refuses an environment whose JSON isn't an empty all_of: mirroring it as one would silently drop its setup. */
	private static void requireEmptyAllOf(String name) {
		Identifier id = Identifier.parse(name);
		String path = "data/" + id.getNamespace() + "/test_environment/" + id.getPath() + ".json";
		JsonElement json;
		// The class loader, not the class: vanilla's environments are in the Minecraft jar, outside this mod's module.
		try (InputStream in = NeoForgeGameTests.class.getClassLoader().getResourceAsStream(path)) {
			if (in == null) {
				throw new IllegalStateException("GameTest environment " + name + " not found at " + path);
			}
			json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new IllegalStateException("GameTest environment " + name + " is unreadable at " + path, e);
		}
		if (!json.equals(JsonParser.parseString(EMPTY_ALL_OF))) {
			throw new IllegalStateException("GameTest environment " + name + " is not an empty all_of (" + json
					+ "); NeoForgeGameTests only mirrors empty ones");
		}
	}
}
