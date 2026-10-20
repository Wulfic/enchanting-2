#!/usr/bin/env bash
# Everything "done" needs, on every node (Phase 12F; AGENTS.md "Definition of Done"), then one line per node:
#
#   scripts/verify-all.sh                 ./gradlew verifyAll --continue, then ./gradlew verifyReport
#   scripts/verify-all.sh --offline       extra arguments go to the verifyAll run
#
# verifyAll = every version node's build (JUnit + server GameTests), every matrix node's linkCheck, and the server
# GameTests of each matrix version a jar claims without being built for. --continue runs every node even after one
# fails, so the report shows all of them. Exit status = verifyAll's; the report never turns a failure into a pass.
#
# Not included: client GameTests (they open a game window). Run ./gradlew :<node>:runClientGameTest, one node at a time;
# CI runs them headless on every node (ci.yml `client`).
set -uo pipefail

cd "$(dirname "$0")/.."

# Results older than this weren't rewritten by this run; verifyReport marks them with *.
start_ms=$(( $(date +%s) * 1000 ))

./gradlew verifyAll --continue "$@"
status=$?

if ! ./gradlew verifyReport -q -PverifySince="$start_ms"; then
	echo "verify-all: verifyReport failed; the verifyAll result above (exit $status) still stands" >&2
fi

if [[ $status -ne 0 ]]; then
	echo "verify-all: FAILED (verifyAll exit $status)" >&2
else
	echo "verify-all: passed"
fi
exit "$status"
