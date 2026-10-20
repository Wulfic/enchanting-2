package com.wulfnet.enchanting2;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * The Phase 12G band of each version node, derived the way gradle/common.gradle names the jars: the node's own
 * {@code minecraft_version} plus every {@code matrix/<version>/} whose {@code matrix_jar_node} is that node, written as
 * the lowest version or "lowest-highest", then (13F) {@code -<loader>}. Read from the repo files (tests run with the
 * repo root as working directory).
 */
final class Bands {
	private Bands() {
	}

	/** Every Minecraft version the node's jar is built for or proven on by the matrix, in version order. */
	static TreeSet<String> versions(String node, String ownVersion) throws IOException {
		TreeSet<String> versions = new TreeSet<>(Bands::compare);
		versions.add(ownVersion);
		try (Stream<Path> dirs = Files.list(Path.of("matrix"))) {
			for (Path file : dirs.map(dir -> dir.resolve("gradle.properties")).filter(Files::isRegularFile).toList()) {
				Properties values = properties(file);
				if (values.getProperty("matrix_jar_node", "").trim().equals(node)) {
					versions.add(values.getProperty("minecraft_version", "").trim());
				}
			}
		}
		return versions;
	}

	static String label(TreeSet<String> versions) {
		return versions.size() == 1 ? versions.first() : versions.first() + "-" + versions.last();
	}

	/**
	 * The loader a node builds for, from its name: {@code -neoforge} / {@code -forge} nodes, every other node Fabric (the
	 * rule ci.yml's client-node list uses too). ModIdentityTest checks it against the build script's own loader.
	 */
	static String loader(String node) {
		if (node.endsWith("-neoforge")) {
			return "neoforge";
		}
		return node.endsWith("-forge") ? "forge" : "fabric";
	}

	/** Node name → {@code <band>-<loader>}, the part of the version after {@code +}, for every {@code versions/<node>/}. */
	static Map<String, String> all() throws IOException {
		Map<String, String> bands = new TreeMap<>();
		try (Stream<Path> dirs = Files.list(Path.of("versions"))) {
			for (Path dir : dirs.filter(d -> Files.isRegularFile(d.resolve("gradle.properties"))).toList()) {
				String node = dir.getFileName().toString();
				String own = properties(dir.resolve("gradle.properties")).getProperty("minecraft_version", "").trim();
				bands.put(node, label(versions(node, own)) + "-" + loader(node));
			}
		}
		return bands;
	}

	/** Release versions only (digits and dots), compared part by part; anything else throws. */
	static int compare(String a, String b) {
		int[] x = Arrays.stream(a.split("\\.")).mapToInt(Integer::parseInt).toArray();
		int[] y = Arrays.stream(b.split("\\.")).mapToInt(Integer::parseInt).toArray();
		return Integer.signum(Arrays.compare(x, y));
	}

	static Properties properties(Path file) throws IOException {
		Properties values = new Properties();
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			values.load(reader);
		}
		return values;
	}
}
