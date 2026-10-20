package com.wulfnet.enchanting2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * What the release and wiki workflows rely on (TODO 11e). {@code release.yml} refuses a tag whose CHANGELOG section is
 * missing, and {@code wiki.yml} publishes {@code wiki/} plus pages copied from {@code docs/}; both would fail only after
 * a push, so the same rules are checked here, before one.
 */
class ReleaseDocsTest {
	private static final Path WIKI = Path.of("wiki");
	private static final Path WIKI_WORKFLOW = Path.of(".github", "workflows", "wiki.yml");
	/** A Markdown link target: {@code ](target)}. */
	private static final Pattern LINK = Pattern.compile("\\]\\(([^)\\s]+)\\)");
	/** One {@code "docs/X.md:Page"} entry of wiki.yml's {@code pages} list. */
	private static final Pattern GENERATED = Pattern.compile("\"(docs/[A-Za-z0-9_-]+\\.md):([A-Za-z0-9-]+)\"");

	@Test
	void changelogHasADatedSectionForTheModVersion() throws IOException {
		String version = modVersion();
		String changelog = read(Path.of("CHANGELOG.md"));
		Matcher heading = Pattern.compile("^## \\[" + Pattern.quote(version) + "\\] - \\d{4}-\\d{2}-\\d{2}$",
				Pattern.MULTILINE).matcher(changelog);
		assertTrue(heading.find(), "CHANGELOG.md has no '## [" + version + "] - YYYY-MM-DD' heading; release.yml "
				+ "uses that section as the release notes and refuses the tag without it");
		int next = changelog.indexOf("\n## [", heading.end());
		String body = changelog.substring(heading.end(), next < 0 ? changelog.length() : next);
		assertFalse(body.isBlank(), "CHANGELOG.md's " + version + " section is empty");
	}

	/**
	 * Phase 12G: a release has one jar per band and (13F) loader ({@code enchanting2-<version>+<band>-<loader>.jar}), so
	 * the README's and the wiki's "which jar" tables must name exactly the current ones: a new band or loader node (or a
	 * matrix version widening a band) that the tables miss would send players looking for a file that isn't there, or
	 * leave a stale one listed.
	 */
	@Test
	void installTablesListExactlyTheCurrentBands() throws IOException {
		Set<String> bands = new TreeSet<>(Bands.all().values());
		assertFalse(bands.isEmpty(), "no version nodes under versions/");
		// Any name, not just well-formed ones: a stale loader-less "+26.3.jar" or a typo shows up in the mismatch.
		Pattern jarName = Pattern.compile("enchanting2-<version>\\+([^`\\s|]+?)\\.jar");
		for (Path doc : List.of(Path.of("README.md"), WIKI.resolve("Installation.md"))) {
			Set<String> listed = new TreeSet<>();
			Matcher m = jarName.matcher(read(doc));
			while (m.find()) {
				listed.add(m.group(1));
			}
			assertEquals(bands, listed, doc + " lists jars for " + listed + "; this build makes " + bands);
		}
	}

	@Test
	void licenseFileAndModMetadataAgree() throws IOException {
		assertTrue(read(Path.of("LICENSE")).startsWith("MIT License\n"), "LICENSE is not the MIT license");
		String modJson = read(Path.of("src", "main", "resources", "fabric.mod.json"));
		assertTrue(modJson.contains("\"license\": \"MIT\""), "fabric.mod.json's license is not MIT");
	}

	@Test
	void everyWikiLinkResolves() throws IOException {
		Map<String, Path> generated = generatedPages();
		Set<String> pages = new TreeSet<>(generated.keySet());
		for (Path page : wikiPages()) {
			pages.add(page.getFileName().toString().replaceFirst("\\.md$", ""));
		}
		assertTrue(pages.contains("Home"), "wiki/Home.md is missing; it is the wiki's front page");

		List<String> broken = new ArrayList<>();
		for (Path page : wikiPages()) {
			for (String target : links(read(page))) {
				String name = target.replaceFirst("#.*$", "");
				if (!name.isEmpty() && !pages.contains(name)) {
					broken.add(page.getFileName() + " -> " + target);
				}
			}
		}
		assertEquals(List.of(), broken, "wiki links to pages that don't exist (known: " + pages + ")");
	}

	/** wiki.yml rewrites a copied doc's link to another copied doc; any other relative link would break on the wiki. */
	@Test
	void copiedDocsOnlyLinkToOtherCopiedDocsOrTheWeb() throws IOException {
		Map<String, Path> generated = generatedPages();
		assertFalse(generated.isEmpty(), "wiki.yml lists no docs/ pages to copy; the pattern no longer matches it");
		Set<String> copied = new TreeSet<>();
		for (Path source : generated.values()) {
			assertTrue(Files.isRegularFile(source), "wiki.yml copies " + source + ", which doesn't exist");
			copied.add(source.getFileName().toString());
		}

		List<String> broken = new ArrayList<>();
		for (Path source : generated.values()) {
			for (String target : links(read(source))) {
				if (!target.startsWith("#") && !copied.contains(target.replaceFirst("#.*$", ""))) {
					broken.add(source + " -> " + target);
				}
			}
		}
		assertEquals(List.of(), broken, "copied docs may only link to " + copied + ", an anchor, or a full URL");
	}

	/** Relative link targets: no scheme, not an image. */
	private static List<String> links(String markdown) {
		List<String> out = new ArrayList<>();
		Matcher m = LINK.matcher(markdown);
		while (m.find()) {
			String target = m.group(1);
			if (!target.contains("://") && !target.startsWith("mailto:") && !target.matches("(?i).*\\.(png|jpe?g|gif)$")) {
				out.add(target);
			}
		}
		return out;
	}

	private static Map<String, Path> generatedPages() throws IOException {
		Map<String, Path> out = new LinkedHashMap<>();
		Matcher m = GENERATED.matcher(read(WIKI_WORKFLOW));
		while (m.find()) {
			out.put(m.group(2), Path.of(m.group(1)));
		}
		return out;
	}

	private static List<Path> wikiPages() throws IOException {
		assertTrue(Files.isDirectory(WIKI), "wiki/ is missing");
		try (Stream<Path> files = Files.list(WIKI)) {
			return files.filter(p -> p.toString().endsWith(".md")).sorted().toList();
		}
	}

	private static String modVersion() throws IOException {
		Matcher m = Pattern.compile("^mod_version=(.+)$", Pattern.MULTILINE).matcher(read(Path.of("gradle.properties")));
		assertTrue(m.find(), "gradle.properties has no mod_version");
		return m.group(1).strip();
	}

	private static String read(Path path) throws IOException {
		return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
	}
}
