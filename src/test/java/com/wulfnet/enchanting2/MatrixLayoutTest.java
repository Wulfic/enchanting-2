package com.wulfnet.enchanting2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The runtime matrix's layout (Phase 12F). {@code verifyAll} link-checks every matrix node settings.gradle includes and
 * GameTests the "claimed" ones, so a {@code matrix/<version>/} directory left out of settings.gradle would never run
 * and nothing would say so. {@code matrix.gradle} refuses bad keys at configuration; this catches the omission.
 */
class MatrixLayoutTest {
	private static final Path MATRIX = Path.of("matrix");
	private static final Pattern VERSIONS = Pattern.compile("versions\\s+((?:'[^']+'\\s*,?\\s*)+)");
	private static final Pattern MATRIX_LIST = Pattern.compile("\\[((?:\\s*'[^']+'\\s*,?)+)\\s*]\\.each \\{ line ->");
	private static final Pattern QUOTED = Pattern.compile("'([^']+)'");

	@Test
	void settingsIncludesEveryMatrixDirectory() throws IOException {
		Set<String> onDisk;
		try (Stream<Path> dirs = Files.list(MATRIX)) {
			onDisk = dirs.filter(dir -> Files.isRegularFile(dir.resolve("gradle.properties")))
					.map(dir -> dir.getFileName().toString()).collect(Collectors.toCollection(TreeSet::new));
		}
		assertFalse(onDisk.isEmpty(), "no matrix/<version>/gradle.properties found");
		assertEquals(onDisk, new TreeSet<>(quotedList(MATRIX_LIST)),
				"settings.gradle's matrix list and the matrix/<version>/ directories differ");
	}

	@Test
	void everyMatrixNodeNamesAVersionNodeJar() throws IOException {
		List<String> versionNodes = quotedList(VERSIONS);
		assertFalse(versionNodes.isEmpty(), "settings.gradle declares no Stonecutter versions");
		for (String node : quotedList(MATRIX_LIST)) {
			Properties props = properties(MATRIX.resolve(node).resolve("gradle.properties"));
			assertEquals(node, props.getProperty("minecraft_version", "").trim(),
					"matrix/" + node + " must run the Minecraft version it is named after");
			String jarNode = props.getProperty("matrix_jar_node", "").trim();
			assertTrue(versionNodes.contains(jarNode),
					"matrix/" + node + "'s matrix_jar_node '" + jarNode + "' is not one of " + versionNodes);
		}
	}

	private static List<String> quotedList(Pattern pattern) throws IOException {
		String settings = Files.readString(Path.of("settings.gradle"), StandardCharsets.UTF_8);
		Matcher list = pattern.matcher(settings);
		assertTrue(list.find(), "settings.gradle has no match for " + pattern);
		Matcher quoted = QUOTED.matcher(list.group(1));
		return quoted.results().map(m -> m.group(1)).toList();
	}

	private static Properties properties(Path file) throws IOException {
		Properties props = new Properties();
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			props.load(reader);
		}
		return props;
	}
}
