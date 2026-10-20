package com.wulfnet.enchanting2.gametest.platform.neoforge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wulfnet.enchanting2.gametest.platform.GameTest;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
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
 * The GameTest mod on NeoForge (Phase 13C), what Fabric's gametest API does for the Fabric nodes: finds every
 * {@link GameTest} method in the classes listed under {@code fabric-gametest} in this mod's {@code fabric.mod.json} (the
 * one list both loaders read), registers each as a test function, then as a test instance. Test IDs are Fabric's:
 * {@code <mod id>:<snake_case of Class_method>}. Only runs when NeoForge enables GameTests (dev and GameTest servers).
 */
@Mod(NeoForgeGameTests.MOD_ID)
public final class NeoForgeGameTests {
	static final String MOD_ID = "enchanting2_gametest";
	private static final Logger LOG = LoggerFactory.getLogger(NeoForgeGameTests.class);
	private static final String EMPTY_ALL_OF = "{\"type\":\"minecraft:all_of\",\"definitions\":[]}";

	private final List<TestMethod> tests;

	public NeoForgeGameTests(IEventBus modBus) {
		if (!GameTestHooks.isGametestEnabled()) {
			tests = List.of();
			return;
		}
		tests = findTests();
		LOG.info("Enchanting 2 GameTests: {} test method(s) in {} class(es)", tests.size(),
				tests.stream().map(t -> t.method().getDeclaringClass()).distinct().count());
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

	private static List<TestMethod> findTests() {
		List<TestMethod> found = new ArrayList<>();
		for (String className : testClassNames()) {
			Object instance;
			try {
				instance = Class.forName(className).getDeclaredConstructor().newInstance();
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException("GameTest class " + className + " can't be created", e);
			}
			int before = found.size();
			for (Class<?> c = instance.getClass(); c != null; c = c.getSuperclass()) {
				for (Method method : c.getDeclaredMethods()) {
					GameTest annotation = method.getAnnotation(GameTest.class);
					if (annotation != null) {
						validate(method);
						found.add(new TestMethod(instance, method, annotation));
					}
				}
			}
			if (found.size() == before) {
				// Fabric only warns; here a listed class with no tests means the import swap missed it, so fail.
				throw new IllegalStateException("GameTest class " + className + " has no @" + GameTest.class.getName()
						+ " methods; was its import swapped (build.neoforge.gradle)?");
			}
		}
		return found;
	}

	/** The {@code fabric-gametest} entry points in this mod's fabric.mod.json, the list Fabric's runner reads too. */
	private static List<String> testClassNames() {
		try (InputStream in = NeoForgeGameTests.class.getResourceAsStream("/fabric.mod.json")) {
			if (in == null) {
				throw new IllegalStateException("the GameTest mod's fabric.mod.json (the test class list) is missing");
			}
			JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			JsonArray entries = root.getAsJsonObject("entrypoints").getAsJsonArray("fabric-gametest");
			List<String> names = new ArrayList<>();
			entries.forEach(entry -> names.add(entry.getAsString()));
			if (names.isEmpty()) {
				throw new IllegalStateException("fabric.mod.json lists no fabric-gametest classes");
			}
			return names;
		} catch (IOException e) {
			throw new IllegalStateException("the GameTest mod's fabric.mod.json is unreadable", e);
		}
	}

	private static void validate(Method method) {
		List<String> issues = new ArrayList<>();
		if (method.getParameterCount() != 1 || method.getParameterTypes()[0] != GameTestHelper.class) {
			issues.add("must take one GameTestHelper");
		}
		if (!Modifier.isPublic(method.getModifiers())) {
			issues.add("must be public");
		}
		if (Modifier.isStatic(method.getModifiers())) {
			issues.add("must not be static");
		}
		if (method.getReturnType() != void.class) {
			issues.add("must return void");
		}
		if (!issues.isEmpty()) {
			throw new IllegalStateException("GameTest " + method.getDeclaringClass().getName() + "#" + method.getName()
					+ ": " + String.join(", ", issues));
		}
	}

	private record TestMethod(Object instance, Method method, GameTest annotation) {
		Identifier id() {
			String name = (instance.getClass().getSimpleName() + "_" + method.getName())
					.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
			return Identifier.fromNamespaceAndPath(MOD_ID, name);
		}

		Consumer<GameTestHelper> function() {
			return helper -> {
				try {
					method.invoke(instance, helper);
				} catch (InvocationTargetException e) {
					// The test's own failure (GameTestAssertException and friends) reaches the runner unwrapped.
					if (e.getTargetException() instanceof RuntimeException failure) {
						throw failure;
					}
					LOG.error("GameTest {} threw", id(), e.getTargetException());
					throw new IllegalStateException("GameTest " + id() + " threw " + e.getTargetException(), e);
				} catch (IllegalAccessException e) {
					LOG.error("GameTest {} can't be invoked", id(), e);
					throw new IllegalStateException("GameTest " + id() + " can't be invoked", e);
				}
			};
		}
	}
}
