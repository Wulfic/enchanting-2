package com.wulfnet.enchanting2.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

/**
 * Phase 12B: loads the target class of every mixin listed in the given configs. Loading a class applies its mixins,
 * and every config is {@code required} with {@code defaultRequire: 1}, so a target that moved or a method that was
 * renamed throws here instead of on the first player who happens to touch that class. GameTests only reach the classes
 * they use; this reaches all of them, on every version node. The targets are read from each mixin's {@code @Mixin} in
 * its class file (Mixin's own registry is emptied once the configs are selected at startup).
 */
final class MixinTargets {
	private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";

	private MixinTargets() {
	}

	/** {@code "config: mixin -> class: error"} for each target that failed; empty when every one applied. */
	static List<String> loadAll(String... configs) {
		List<String> failures = new ArrayList<>();
		for (String config : configs) {
			try {
				JsonObject json = json(config);
				String pkg = json.get("package").getAsString();
				int listed = 0;
				for (String section : sections()) {
					JsonArray names = json.getAsJsonArray(section);
					if (names == null) {
						continue;
					}
					for (JsonElement name : names) {
						listed++;
						String mixin = pkg + "." + name.getAsString();
						for (String target : targets(mixin)) {
							try {
								Class.forName(target, false, MixinTargets.class.getClassLoader());
							} catch (Throwable failure) {
								failures.add(config + ": " + mixin + " -> " + target + ": " + failure);
							}
						}
					}
				}
				if (listed == 0) {
					failures.add(config + ": lists no mixins");
				}
			} catch (IOException | RuntimeException unreadable) {
				failures.add(config + ": unreadable: " + unreadable);
			}
		}
		return failures;
	}

	/** The config sections Mixin applies in this environment: a dedicated server has no client classes to load. */
	private static List<String> sections() {
		return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT
				? List.of("mixins", "client")
				: List.of("mixins", "server");
	}

	private static JsonObject json(String config) throws IOException {
		try (InputStream in = MixinTargets.class.getClassLoader().getResourceAsStream(config)) {
			if (in == null) {
				throw new IOException("not on the classpath");
			}
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	/** The {@code value} classes and {@code targets} names of {@code mixin}'s {@code @Mixin}, as binary names. */
	private static Set<String> targets(String mixin) throws IOException {
		ClassNode node = new ClassNode();
		try (InputStream in = MixinTargets.class.getClassLoader()
				.getResourceAsStream(mixin.replace('.', '/') + ".class")) {
			if (in == null) {
				throw new IOException(mixin + ".class is not on the classpath");
			}
			new ClassReader(in).accept(node, ClassReader.SKIP_CODE);
		}
		Set<String> targets = new LinkedHashSet<>();
		List<AnnotationNode> annotations = node.invisibleAnnotations == null ? List.of() : node.invisibleAnnotations;
		for (AnnotationNode annotation : annotations) {
			if (!MIXIN.equals(annotation.desc) || annotation.values == null) {
				continue;
			}
			for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
				Object key = annotation.values.get(i);
				if (!(annotation.values.get(i + 1) instanceof List<?> values)) {
					continue;
				}
				for (Object value : values) {
					if ("value".equals(key) && value instanceof Type type) {
						targets.add(type.getClassName());
					} else if ("targets".equals(key) && value instanceof String name) {
						targets.add(name.replace('/', '.'));
					}
				}
			}
		}
		if (targets.isEmpty()) {
			throw new IOException(mixin + " has no @Mixin targets");
		}
		return targets;
	}
}
