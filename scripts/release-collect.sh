#!/usr/bin/env bash
# release.yml's "Collect the jars" step (Phase 12G), a script so it can be dry-run locally after ./gradlew assemble:
#
#   VERSION=1.1.0 COMMIT=$(git rev-parse HEAD) GITHUB_REPOSITORY=Wulfic/enchanting-2 scripts/release-collect.sh
#
# Needs changes.md (the CHANGELOG section; release.yml's meta step writes it). For every versions/<node>/ it takes
# exactly one enchanting2-<VERSION>+<band>-<loader>.jar (Phase 13F) and its -sources.jar, checks the loader is the
# node's (named by its -neoforge / -forge suffix, else fabric) and that the jar's own metadata (fabric.mod.json, or
# META-INF/neoforge.mods.toml / mods.toml) says <VERSION>+<band>-<loader>, and writes release-files.txt (one path per
# line) and release-notes.md (changes + a jar table). Refuses (exit 1) on a missing or extra jar, a wrong loader, a
# version mismatch, or no nodes. Reads and writes nothing else.
set -euo pipefail

cd "$(dirname "$0")/.."

: "${VERSION:?set VERSION (mod_version)}"
: "${COMMIT:?set COMMIT (the tagged commit)}"
: "${GITHUB_REPOSITORY:?set GITHUB_REPOSITORY (owner/repo)}"
if [[ ! -s changes.md ]]; then
	echo "::error::changes.md is missing or empty; release.yml's meta step writes it from CHANGELOG.md" >&2
	exit 1
fi

prop() { grep -E "^$2=" "$1" | head -n1 | cut -d= -f2- | tr -d '[:space:]'; }
shopt -s nullglob

nodes=(versions/*/gradle.properties)
if [[ ${#nodes[@]} -eq 0 ]]; then
	echo "::error::no versions/<node>/gradle.properties found" >&2
	exit 1
fi

# A node's loader, by the rule Bands.loader (src/test) checks against each build script.
node_loader() {
	case "$1" in
		*-neoforge) echo neoforge ;;
		*-forge) echo forge ;;
		*) echo fabric ;;
	esac
}

# The version a jar ships in its own metadata, read the way its loader reads it.
shipped_version() {
	case "$2" in
		fabric) unzip -p "$1" fabric.mod.json | grep -o '"version" *: *"[^"]*"' | head -n1 | sed 's/.*: *"\(.*\)"/\1/' ;;
		neoforge | forge)
			local toml=META-INF/neoforge.mods.toml
			[[ "$2" == forge ]] && toml=META-INF/mods.toml
			# The first version = "..." after [[mods]] (dependencies use versionRange).
			unzip -p "$1" "$toml" | awk '/^\[\[mods\]\]/ { m = 1; next } /^\[/ { m = 0 } m && /^version *=/ { print; exit }' \
				| sed 's/^version *= *"\(.*\)".*/\1/' ;;
	esac
}

files=()
table="| File | Loader | Minecraft | Java |"$'\n'"|---|---|---|---|"
loaders=()
for props in "${nodes[@]}"; do
	node=$(dirname "$props")
	jars=()
	for f in "$node/build/libs/enchanting2-${VERSION}+"*.jar; do
		case "$f" in
			# -slim: Forge's jar before JarJar nests Jankson in it (build.forge.gradle); the shipped one has no classifier.
			*-sources.jar | *-gametest.jar | *-slim.jar) ;;
			*) jars+=("$f") ;;
		esac
	done
	if [[ ${#jars[@]} -ne 1 ]]; then
		echo "::error::$node has ${#jars[@]} jar(s) named enchanting2-${VERSION}+<band>-<loader>.jar, expected 1: ${jars[*]:-none}" >&2
		exit 1
	fi
	jar=${jars[0]}
	name=$(basename "$jar" .jar)
	suffix=${name#"enchanting2-${VERSION}+"}
	loader=${suffix##*-}
	band=${suffix%-*}
	expected=$(node_loader "$(basename "$node")")
	if [[ "$loader" != "$expected" || "$band" == "$suffix" ]]; then
		echo "::error::$jar should end in -${expected}.jar (the loader of $node), as enchanting2-${VERSION}+<band>-${expected}.jar" >&2
		exit 1
	fi
	sources="${jar%.jar}-sources.jar"
	if [[ ! -f "$sources" ]]; then
		echo "::error::$node has no $(basename "$sources")" >&2
		exit 1
	fi
	shipped=$(shipped_version "$jar" "$loader")
	if [[ "$shipped" != "${VERSION}+${suffix}" ]]; then
		echo "::error::$jar ships version '$shipped' in its $loader metadata, expected '${VERSION}+${suffix}' from its name" >&2
		exit 1
	fi
	files+=("$jar" "$sources")
	loaders+=("$loader")
	table+=$'\n'"| \`$(basename "$jar")\` | ${loader} | ${band/-/ to } | $(prop "$props" java_version)+ |"
	echo "collected $jar ($band, $loader)"
done

# What each loader in this release needs next to the jar.
needs=""
for loader in $(printf '%s\n' "${loaders[@]}" | sort -u); do
	case "$loader" in
		fabric) needs+=$'\n'"- **Fabric:** [Fabric API](https://modrinth.com/mod/fabric-api) and Fabric Loader $(prop gradle.properties loader_version)+." ;;
		neoforge) needs+=$'\n'"- **NeoForge:** nothing else." ;;
		forge) needs+=$'\n'"- **Forge:** nothing else." ;;
	esac
done

{
	cat changes.md
	echo
	echo "---"
	echo "**One jar per Minecraft version line and loader.** Pick the one for your version and loader and put it in"
	echo "\`mods/\` on the server **and** every player's client, with:"
	echo "$needs"
	echo
	echo "$table"
	echo
	echo "Full guide: [wiki](https://github.com/${GITHUB_REPOSITORY}/wiki) · Built from \`${COMMIT:0:7}\`."
} > release-notes.md
printf '%s\n' "${files[@]}" > release-files.txt
echo "release-collect: ${#files[@]} files for ${#nodes[@]} band(s); notes in release-notes.md"
