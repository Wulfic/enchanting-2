#!/usr/bin/env bash
# release.yml's "Collect the jars" step (Phase 12G), a script so it can be dry-run locally after ./gradlew assemble:
#
#   VERSION=1.1.0 COMMIT=$(git rev-parse HEAD) GITHUB_REPOSITORY=Wulfic/enchanting-2 scripts/release-collect.sh
#
# Needs changes.md (the CHANGELOG section; release.yml's meta step writes it). For every versions/<node>/ it takes
# exactly one enchanting2-<VERSION>+<band>.jar and its -sources.jar, checks the jar's own fabric.mod.json says
# <VERSION>+<band>, and writes release-files.txt (one path per line) and release-notes.md (changes + a jar table).
# Refuses (exit 1) on a missing or extra jar, a version mismatch, or no nodes. Reads and writes nothing else.
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

files=()
table="| File | Minecraft | Java |"$'\n'"|---|---|---|"
for props in "${nodes[@]}"; do
	node=$(dirname "$props")
	jars=()
	for f in "$node/build/libs/enchanting2-${VERSION}+"*.jar; do
		case "$f" in
			*-sources.jar | *-gametest.jar) ;;
			*) jars+=("$f") ;;
		esac
	done
	if [[ ${#jars[@]} -ne 1 ]]; then
		echo "::error::$node has ${#jars[@]} jar(s) named enchanting2-${VERSION}+<band>.jar, expected 1: ${jars[*]:-none}" >&2
		exit 1
	fi
	jar=${jars[0]}
	name=$(basename "$jar" .jar)
	band=${name#"enchanting2-${VERSION}+"}
	sources="${jar%.jar}-sources.jar"
	if [[ ! -f "$sources" ]]; then
		echo "::error::$node has no $(basename "$sources")" >&2
		exit 1
	fi
	shipped=$(unzip -p "$jar" fabric.mod.json | grep -o '"version" *: *"[^"]*"' | head -n1 | sed 's/.*: *"\(.*\)"/\1/')
	if [[ "$shipped" != "${VERSION}+${band}" ]]; then
		echo "::error::$jar has version '$shipped' in its fabric.mod.json, expected '${VERSION}+${band}' from its name" >&2
		exit 1
	fi
	files+=("$jar" "$sources")
	table+=$'\n'"| \`$(basename "$jar")\` | ${band/-/ to } | $(prop "$props" java_version)+ |"
	echo "collected $jar ($band)"
done

{
	cat changes.md
	echo
	echo "---"
	echo "**One jar per Minecraft version line.** Pick the one for your version and put it in \`mods/\` on the server **and**"
	echo "every player's client, with [Fabric API](https://modrinth.com/mod/fabric-api) and Fabric Loader $(prop gradle.properties loader_version)+."
	echo
	echo "$table"
	echo
	echo "Full guide: [wiki](https://github.com/${GITHUB_REPOSITORY}/wiki) · Built from \`${COMMIT:0:7}\`."
} > release-notes.md
printf '%s\n' "${files[@]}" > release-files.txt
echo "release-collect: ${#files[@]} files for ${#nodes[@]} band(s); notes in release-notes.md"
