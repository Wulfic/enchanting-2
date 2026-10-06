#!/usr/bin/env bash
# Boot the dev server (run/server, port 25566), optionally type console commands into it, stop it
# cleanly, and check the log. Exit 0 = booted, mod initialized, no enchanting2 ERROR lines.
#
#   scripts/dev-server-smoke.sh                  # boot + stop
#   scripts/dev-server-smoke.sh commands.txt     # boot, send each line as a console command, stop
#
# Never kills java by name: another mod's server may be running on this machine (port 25565).
set -euo pipefail

cd "$(dirname "$0")/.."

PORT=25566
BOOT_TIMEOUT=${BOOT_TIMEOUT:-300}
COMMANDS_FILE=${1:-}
LOG=run/server/logs/latest.log
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
			[[ -z "$cmd" || "$cmd" == \#* ]] && continue
			echo "$cmd"
			sleep 1
		done < "$COMMANDS_FILE"
		sleep 2
	fi
	echo stop
}

echo "dev-server-smoke: starting runServer on port $PORT (output: $OUT)"
set +e
feed_console | timeout $((BOOT_TIMEOUT + 120)) ./gradlew runServer --console=plain > "$OUT" 2>&1
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
if grep -E '/(ERROR|FATAL)\]' "$OUT" | grep -qi 'enchanting2'; then
	echo "FAIL: enchanting2 logged errors:" >&2
	grep -E '/(ERROR|FATAL)\]' "$OUT" | grep -i 'enchanting2' >&2
	fail=1
fi
(( fail == 0 )) && echo "dev-server-smoke: OK"
exit $fail
