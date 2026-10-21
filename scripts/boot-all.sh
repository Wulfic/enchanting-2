#!/usr/bin/env bash
# Phase 13F: boot every row of scripts/boot-matrix.txt through scripts/server_boot_check.py, one at a time, and print
# one table. Exit = the worst row: 0 every row proven · 1 a row found the mod bad · 2 a row proved nothing.
#
#   scripts/boot-all.sh                 # every row (dispatch, before a tag)
#   scripts/boot-all.sh shipped         # tier "shipped" only (every CI push)
#   scripts/boot-all.sh full neoforge   # tier and loader filters; "all" for either means no filter
#
# Needs the jars built (./gradlew assemble). Each row gets a new work dir under build/boot/<run>/ (the check refuses a
# non-empty one); logs stay there as <row>/server.log. Servers use port 25567, so rows never run in parallel.
set -euo pipefail

cd "$(dirname "$0")/.."

TIER=${1:-all}
LOADER=${2:-all}
PYTHON=${PYTHON:-python}
RUN=build/boot/$(date +%Y%m%d-%H%M%S)
VERSION=$(grep -E '^mod_version=' gradle.properties | cut -d= -f2- | tr -d '[:space:]')
if [[ -z "$VERSION" ]]; then
	echo "boot-all: no mod_version in gradle.properties" >&2
	exit 2
fi
case "$TIER" in all|shipped|full) ;; *) echo "boot-all: tier must be all, shipped or full, not '$TIER'" >&2; exit 2 ;; esac
case "$LOADER" in all|fabric|quilt|neoforge|forge) ;; *) echo "boot-all: unknown loader '$LOADER'" >&2; exit 2 ;; esac
mkdir -p "$RUN"

worst=0
table=()
rows=0
while read -r tier loader minecraft loader_version node fabric_api mode; do
	[[ -z "$tier" || "$tier" == \#* ]] && continue
	[[ "$TIER" != all && "$tier" != "$TIER" ]] && continue
	[[ "$LOADER" != all && "$loader" != "$LOADER" ]] && continue
	rows=$((rows + 1))
	id="$loader-$minecraft-$mode"
	# The jar a player downloads: the loader-named jar, never -sources or -gametest.
	jar_loader=$loader
	[[ "$loader" == quilt ]] && jar_loader=fabric
	# Newest first: a widened band leaves the old name's jar behind in build/libs until a clean.
	bands=$(ls -t "versions/$node/build/libs/" 2>/dev/null \
		| sed -nE "s/^enchanting2-${VERSION//./\\.}\+(.+)-${jar_loader}\.jar$/\1/p")
	band=$(head -n1 <<< "$bands")
	if (( $(grep -c . <<< "$bands") > 1 )); then
		echo "boot-all: $id: several $jar_loader jars in versions/$node/build/libs; using the newest (+$band)" >&2
	fi
	jar="versions/$node/build/libs/enchanting2-$VERSION+$band-$jar_loader.jar"
	args=(--loader "$loader" --minecraft "$minecraft" --loader-version "$loader_version" --work "$RUN/$id")
	[[ "$fabric_api" != - ]] && args+=(--fabric-api "$fabric_api")
	if [[ -z "$band" || ! -f "$jar" ]]; then
		echo "boot-all: $id: no enchanting2-$VERSION+<band>-$jar_loader.jar in versions/$node/build/libs (run assemble)" >&2
		code=2
	else
		args+=(--mod "$jar")
		if [[ "$mode" == gametests ]]; then
			# A missing GameTest jar is passed anyway: the check reports it as exit 2.
			args+=(--gametests "versions/$node/build/libs/enchanting2-$VERSION+$band-$jar_loader-gametest.jar")
		else
			args+=(--commands scripts/checks/boot.txt)
		fi
		echo "boot-all: === $id ($jar)"
		set +e
		# stdin from /dev/null: the loop reads the matrix on stdin, and a child that reads it would eat rows.
		"$PYTHON" -I scripts/server_boot_check.py "${args[@]}" < /dev/null 2>&1 | tee "$RUN/$id.out"
		code=${PIPESTATUS[0]}
		set -e
	fi
	case $code in 0) verdict=PASS ;; 1) verdict=FAIL ;; *) verdict="NOTHING PROVEN"; code=2 ;; esac
	(( code > worst )) && worst=$code
	table+=("$(printf '%-9s %-8s %-15s %-16s %-10s %s' "$loader" "$minecraft" "$loader_version" "$node" "$mode" "$verdict")")
done < scripts/boot-matrix.txt

if (( rows == 0 )); then
	echo "boot-all: no rows match tier '$TIER' loader '$LOADER'" >&2
	exit 2
fi
echo
echo "boot-all: $rows row(s), logs in $RUN"
printf '%-9s %-8s %-15s %-16s %-10s %s\n' loader minecraft loader-version jar-node mode verdict
printf '%s\n' "${table[@]}"
exit $worst
