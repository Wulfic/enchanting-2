#!/usr/bin/env bash
# Boot the dev server of one version node (port 25566), optionally type console commands into it, stop it
# cleanly, and check the log. Exit 0 = booted, mod initialized, no enchanting2 ERROR lines.
#
#   scripts/dev-server-smoke.sh                  # boot + stop (node 1.21.11, run/server)
#   scripts/dev-server-smoke.sh commands.txt     # boot, send each line as a console command, stop
#   NODE=26.1.2 scripts/dev-server-smoke.sh      # another node (versions/<node>/, its run_root)
#
# One node per run: a bare `./gradlew runServer` would start every node's server, all on the same port.
#
# Lines in the commands file:
#   <command>          typed into the server console
#   @sh <shell>        run with bash from the repo root between console commands (dev-only; output to stderr)
#   @expect <regex>    after the run, the server output must contain a line matching this (grep -E)
#   @allow-error <re>  an enchanting2 ERROR line matching this is expected, not a failure
#   # comment, blank   ignored
#
# Never kills java by name: another mod's server may be running on this machine (port 25565).
set -euo pipefail

cd "$(dirname "$0")/.."

PORT=25566
BOOT_TIMEOUT=${BOOT_TIMEOUT:-300}
COMMANDS_FILE=${1:-}
NODE=${NODE:-1.21.11}
NODE_PROPS="versions/$NODE/gradle.properties"
if [[ ! -f "$NODE_PROPS" ]]; then
	echo "no version node '$NODE' ($NODE_PROPS missing); nodes: $(ls versions)" >&2
	exit 2
fi
RUN_ROOT=$(grep -E '^run_root=' "$NODE_PROPS" | head -n1 | cut -d= -f2- | tr -d '[:space:]')
if [[ -z "$RUN_ROOT" ]]; then
	echo "$NODE_PROPS has no run_root" >&2
	exit 2
fi
LOG=$RUN_ROOT/server/logs/latest.log
OUT=build/dev-server-smoke.out
MARKER=build/dev-server-smoke.marker

if [[ -n "$COMMANDS_FILE" && ! -f "$COMMANDS_FILE" ]]; then
	echo "commands file not found: $COMMANDS_FILE" >&2
	exit 2
fi

if netstat -an 2>/dev/null | grep -qE "[:.]${PORT}[[:space:]].*LISTEN"; then
	echo "port $PORT is already in use; refusing to start a second dev server" >&2
	exit 2
fi

mkdir -p build
touch "$MARKER"

# Feeds the server's stdin: waits for this run's "Done (" line, sends the commands, then "stop".
feed_console() {
	local waited=0
	until [[ -f "$LOG" && "$LOG" -nt "$MARKER" ]] && grep -q 'Done (' "$LOG"; do
		# The server died before finishing startup: Gradle is done, so nobody is reading stdin.
		if grep -qE '^BUILD (SUCCESSFUL|FAILED)' "$OUT" 2>/dev/null; then
			echo "dev-server-smoke: server exited before 'Done ('" >&2
			return
		fi
		if (( waited >= BOOT_TIMEOUT )); then
			echo "dev-server-smoke: no 'Done (' after ${BOOT_TIMEOUT}s; sending stop" >&2
			echo stop
			return
		fi
		sleep 2
		waited=$((waited + 2))
	done
	if [[ -n "$COMMANDS_FILE" ]]; then
		while IFS= read -r cmd || [[ -n "$cmd" ]]; do
			[[ -z "$cmd" || "$cmd" == \#* || "$cmd" == @expect\ * || "$cmd" == @allow-error\ * ]] && continue
			if [[ "$cmd" == @sh\ * ]]; then
				echo "dev-server-smoke: \$ ${cmd#@sh }" >&2
				bash -c "${cmd#@sh }" >&2 || echo "dev-server-smoke: @sh step failed: ${cmd#@sh }" >&2
				continue
			fi
			echo "$cmd"
			sleep 1
		done < "$COMMANDS_FILE"
		sleep 2
	fi
	echo stop
}

echo "dev-server-smoke: starting :$NODE:runServer on port $PORT (output: $OUT)"
set +e
feed_console | timeout $((BOOT_TIMEOUT + 120)) ./gradlew ":$NODE:runServer" --console=plain > "$OUT" 2>&1
status=$?
set -e

if (( status != 0 )); then
	echo "dev-server-smoke: runServer exited with $status (124 = timed out). Tail of $OUT:" >&2
	tail -40 "$OUT" >&2
	exit 1
fi

fail=0
grep -q 'Done (' "$OUT" || { echo "FAIL: server never finished starting" >&2; fail=1; }
grep -q 'Enchanting 2 initialized' "$OUT" || { echo "FAIL: mod did not initialize" >&2; fail=1; }
allowed=()
expected=()
if [[ -n "$COMMANDS_FILE" ]]; then
	while IFS= read -r line || [[ -n "$line" ]]; do
		case "$line" in
			@allow-error\ *) allowed+=("${line#@allow-error }") ;;
			@expect\ *) expected+=("${line#@expect }") ;;
		esac
	done < "$COMMANDS_FILE"
fi
errors=$(grep -E '/(ERROR|FATAL)\]' "$OUT" | grep -i 'enchanting2' || true)
for re in "${allowed[@]}"; do
	errors=$(grep -vE -- "$re" <<< "$errors" || true)
done
if [[ -n "$errors" ]]; then
	echo "FAIL: enchanting2 logged errors:" >&2
	echo "$errors" >&2
	fail=1
fi
for re in "${expected[@]}"; do
	grep -qE -- "$re" "$OUT" || { echo "FAIL: expected a line matching: $re" >&2; fail=1; }
done
(( fail == 0 )) && echo "dev-server-smoke: OK"
exit $fail
