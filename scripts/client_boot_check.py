"""Run the client world check on NeoForge/Forge nodes and judge it from the game's log.

Phase 13G. The release gate's client rung (create and join a new world) where there are no client GameTests: the
Fabric nodes run `:<node>:runClientGameTest`, the NeoForge and Forge nodes run this.

  python scripts/client_boot_check.py --node 26.3-neoforge [--node 26.3-forge ...] [--checks FILE]
  python scripts/client_boot_check.py --join --node 26.3-forge [--node ...]
  python scripts/client_boot_check.py --self-test

Each node runs `./gradlew :<node>:runClientBootCheck` (sequential, never two games at once): a dev client in a fresh
<node>/build/run/clientBoot where ClientBootCheck (gametest source set) creates a world through vanilla's Create New
World screen, joins it, opens the mod's config screen, saves and quits, logging an "enchanting2 client boot:" line per
stage. The verdict reads <node>/build/run/clientBoot/logs/latest.log against the checks file (default
scripts/checks/client-boot.txt: `@expect <regex>`, `@allow-error <regex>`, `#`; no console commands, a client has no
console): every @expect, no ERROR/FATAL line outside @allow-error, no crash report, and Gradle's run task exiting 0.
The Gradle output is kept at <node>/build/client-boot-gradle.log.

--join: the dedicated-server variant. The node's built jar (run assemble first) boots on a real server of the node's
shipped boot-matrix row through server_boot_check.py (port 25567, scripts/checks/client-join-server.txt, work dir under
build/boot/clientjoin-<time>/), and once that server listens the dev client joins it instead of creating a world
(-PclientBootJoin), judged against scripts/checks/client-join.txt (the default --checks in this mode). The node's
verdict is the worse of the client's and the server's.

Exit 0 = proven · 1 = the mod is bad · 2 = nothing proven (not a NeoForge/Forge node, Gradle failed before the game
wrote a log). The exit is the worst node's.
"""
import argparse
import os
import re
import subprocess
import sys
import tempfile
import time

from server_boot_check import ERROR_LINE, INIT_LINE, NothingProven, log, parse_commands, port_listening
from server_boot_check import PORT as SERVER_PORT

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_CHECKS = os.path.join(ROOT, "scripts", "checks", "client-boot.txt")
JOIN_CHECKS = os.path.join(ROOT, "scripts", "checks", "client-join.txt")
JOIN_SERVER_COMMANDS = os.path.join(ROOT, "scripts", "checks", "client-join-server.txt")
JOIN_ADDRESS = "127.0.0.1:%d" % SERVER_PORT
# The server installs and boots (a first install downloads the loader and Minecraft) before it listens.
SERVER_LISTEN_WAIT = 15 * 60
CRASH_LINE = "---- Minecraft Crash Report ----"
FAILED_LINE = re.compile(r"enchanting2 client boot: FAILED")
# Gradle's own run task timeout is 15 minutes (gradle/client-boot.gradle); this only catches a Gradle that hangs.
TIMEOUT = 20 * 60


def load_checks(path):
    commands, expects, allowed = parse_commands(path)
    if commands:
        raise NothingProven("%s: console commands (%r) have nowhere to go on a client" % (path, commands[0]))
    if not expects:
        raise NothingProven("%s has no @expect: nothing would be proven" % path)
    return expects, allowed


def judge(lines, expects, allowed, gradle_exit, crash_reports):
    """Problems found in a client log (empty = proven)."""
    problems = []
    if not any(INIT_LINE in line for line in lines):
        problems.append('the mod never logged "%s"' % INIT_LINE)
    failed = [line for line in lines if FAILED_LINE.search(line)]
    if failed:
        problems.append("the check failed: %s" % failed[0].strip())
    errors = [line for line in lines
              if ERROR_LINE.search(line) and not any(a.search(line) for a in allowed)]
    if errors:
        problems.append("%d ERROR/FATAL line(s), first: %s" % (len(errors), errors[0].strip()))
    if any(CRASH_LINE in line for line in lines) or crash_reports:
        problems.append("the game crashed (%s)" % (", ".join(crash_reports) or "crash report in the log"))
    for expect in expects:
        if not any(expect.search(line) for line in lines):
            problems.append("no line matches @expect %s" % expect.pattern)
    if gradle_exit != 0:
        problems.append("the run task exited %d" % gradle_exit)
    return problems


def require_node(node):
    """The node's directory; refuses a node this check can't run on."""
    if not re.fullmatch(r"[0-9.]+-(neoforge|forge)", node):
        raise NothingProven("%s is not a NeoForge/Forge node (Fabric nodes run :<node>:runClientGameTest)" % node)
    node_dir = os.path.join(ROOT, "versions", node)
    if not os.path.isdir(node_dir):
        raise NothingProven("no node directory %s" % node_dir)
    return node_dir


def shipped_row(node):
    """(loader, minecraft, loader version) of the node's shipped row in scripts/boot-matrix.txt."""
    with open(os.path.join(ROOT, "scripts", "boot-matrix.txt"), encoding="utf-8") as f:
        for line in f:
            cols = line.split()
            if len(cols) == 7 and cols[0] == "shipped" and cols[4] == node:
                return cols[1], cols[2], cols[3]
    raise NothingProven("no shipped row for %s in scripts/boot-matrix.txt" % node)


def built_jar(node, loader):
    """The node's newest loader-named jar of this mod_version, as boot-all.sh picks it."""
    with open(os.path.join(ROOT, "gradle.properties"), encoding="utf-8") as f:
        version = next((l.split("=", 1)[1].strip() for l in f if l.startswith("mod_version=")), "")
    libs = os.path.join(ROOT, "versions", node, "build", "libs")
    pattern = re.compile(r"enchanting2-" + re.escape(version) + r"\+.+-" + loader + r"\.jar")
    names = os.listdir(libs) if os.path.isdir(libs) else []
    jars = [os.path.join(libs, n) for n in names if pattern.fullmatch(n)]
    if not version or not jars:
        raise NothingProven("no enchanting2-%s+<band>-%s.jar in %s (run :%s:assemble)" % (version, loader, libs, node))
    return max(jars, key=os.path.getmtime)


def join_node(node, expects, allowed):
    """--join: the built jar on a real server, the dev client joining it. (exit code, one-line summary)."""
    require_node(node)
    loader, minecraft, loader_version = shipped_row(node)
    jar = built_jar(node, loader)
    if port_listening(SERVER_PORT):
        raise NothingProven("port %d is already in use; refusing to start the server" % SERVER_PORT)
    work = os.path.join(ROOT, "build", "boot", "clientjoin-" + time.strftime("%Y%m%d-%H%M%S"), node)
    server_out = work + ".out"
    os.makedirs(os.path.dirname(work), exist_ok=True)
    cmd = [sys.executable, "-I", os.path.join(ROOT, "scripts", "server_boot_check.py"), "--loader", loader,
           "--minecraft", minecraft, "--loader-version", loader_version, "--mod", jar, "--work", work,
           "--commands", JOIN_SERVER_COMMANDS]
    log("%s: server: %s (output %s)" % (node, " ".join(cmd), server_out))
    with open(server_out, "w", encoding="utf-8") as out:
        server = subprocess.Popen(cmd, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=out, stderr=subprocess.STDOUT)
        deadline = time.time() + SERVER_LISTEN_WAIT
        while not port_listening(SERVER_PORT):
            if server.poll() is not None:
                raise NothingProven("%s: the server check exited %d before listening; see %s"
                                    % (node, server.returncode, server_out))
            if time.time() > deadline:
                raise NothingProven("%s: nothing on port %d after %d s; the server check (PID %d) is still running"
                                    % (node, SERVER_PORT, SERVER_LISTEN_WAIT, server.pid))
            time.sleep(5)
        log("%s: server listening on %d; the dev client joins it" % (node, SERVER_PORT))
        client_code, client_summary = check_node(node, expects, allowed, join=JOIN_ADDRESS)
        try:
            server_code = server.wait(timeout=TIMEOUT)
        except subprocess.TimeoutExpired:
            raise NothingProven("%s: the server check (PID %d) still running after %d s" % (node, server.pid, TIMEOUT))
    log("%s: server check exit %d (log %s)" % (node, server_code, os.path.join(work, "server.log")))
    code = max(client_code, server_code if server_code in (0, 1) else 2)
    return code, "%s; server check exit %d" % (client_summary, server_code)


def check_node(node, expects, allowed, join=None):
    """(exit code, one-line summary) for one node; join: a server address to join instead of creating a world."""
    node_dir = require_node(node)
    run_dir = os.path.join(node_dir, "build", "run", "clientBoot")
    latest = os.path.join(run_dir, "logs", "latest.log")
    gradle_log = os.path.join(node_dir, "build", "client-boot-gradle.log")
    gradlew = os.path.join(ROOT, "gradlew.bat" if os.name == "nt" else "gradlew")
    cmd = [gradlew, ":%s:runClientBootCheck" % node, "--console=plain"]
    if join:
        cmd.append("-PclientBootJoin=" + join)
    log("%s: %s (Gradle output: %s)" % (node, " ".join(cmd), gradle_log))
    started = time.time()
    with open(gradle_log, "w", encoding="utf-8") as out:
        try:
            gradle_exit = subprocess.run(cmd, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=out,
                                         stderr=subprocess.STDOUT, timeout=TIMEOUT).returncode
        except subprocess.TimeoutExpired:
            raise NothingProven("%s: Gradle still running after %d s; its game may still be open" % (node, TIMEOUT))
    # The run dir is cleared by every run, so a log older than this run is a run that never started the game.
    if not os.path.isfile(latest) or os.path.getmtime(latest) < started:
        raise NothingProven("%s: Gradle exited %d before the game wrote %s; see %s"
                            % (node, gradle_exit, latest, gradle_log))
    with open(latest, encoding="utf-8", errors="replace") as f:
        lines = f.readlines()
    crash_dir = os.path.join(run_dir, "crash-reports")
    crash_reports = sorted(os.listdir(crash_dir)) if os.path.isdir(crash_dir) else []
    problems = judge(lines, expects, allowed, gradle_exit, crash_reports)
    for problem in problems:
        log("%s: FAIL: %s" % (node, problem))
    if problems:
        return 1, "FAIL (%d problem(s); log %s)" % (len(problems), latest)
    matched = [next(line.strip() for line in lines if e.search(line)) for e in expects]
    for line in matched:
        log("%s: matched %s" % (node, line))
    return 0, "PASS (%d @expect, %d s)" % (len(expects), int(time.time() - started))


def self_test():
    """judge() on synthetic logs, both ways. Exit 0 only if every case judged as expected."""
    good = [
        "[12:00:00] [main/INFO] [com.wulfnet.enchanting2.Enchanting2/]: Enchanting 2 initialized",
        "[12:00:01] [Render thread/INFO] [ClientBootCheck/]: enchanting2 client boot: joined world New World: 70 "
        "enchanting2 enchantments, Knockback max level 5, server config received",
        "[12:00:02] [Render thread/INFO] [ClientBootCheck/]: enchanting2 client boot: PASS (world New World saved)",
    ]
    expects = [re.compile(r"enchanting2 client boot: joined world .*Knockback max level 5"),
               re.compile(r"enchanting2 client boot: PASS")]
    allowed = [re.compile(r"known harmless")]
    cases = [
        ("clean log", good, 0, [], 0),
        ("ERROR line", good + ["[12:00:03] [Render thread/ERROR] [x/]: broke"], 0, [], 1),
        ("allowed ERROR", good + ["[12:00:03] [Render thread/ERROR] [x/]: known harmless"], 0, [], 0),
        ("FAILED stage", good + ["[12:00:03] [Render thread/INFO]: enchanting2 client boot: FAILED at JOINING"],
         0, [], 1),
        ("no PASS", [l for l in good if "PASS" not in l], 0, [], 1),
        ("no init line", [l for l in good if INIT_LINE not in l], 0, [], 1),
        ("crash in log", good + [CRASH_LINE], 0, [], 1),
        ("crash report", good, 0, ["crash-2026-10-21_12.00.00-client.txt"], 1),
        ("gradle exit 1", good, 1, [], 1),
    ]
    failures = 0
    for name, lines, gradle_exit, crashes, want in cases:
        got = 1 if judge(lines, expects, allowed, gradle_exit, crashes) else 0
        failures += got != want
        log("self-test %-16s want %d got %d %s" % (name, want, got, "ok" if got == want else "WRONG"))
    with tempfile.TemporaryDirectory() as tmp:
        for name, text, want in [
            ("checks ok", "@expect PASS\n", 0),
            ("checks command", "say hi\n@expect PASS\n", 2),
            ("checks no expect", "# nothing\n", 2),
        ]:
            path = os.path.join(tmp, "checks.txt")
            with open(path, "w", encoding="utf-8") as f:
                f.write(text)
            try:
                load_checks(path)
                got = 0
            except NothingProven:
                got = 2
            failures += got != want
            log("self-test %-16s want %d got %d %s" % (name, want, got, "ok" if got == want else "WRONG"))
    for name, node in [("fabric node", "26.3"), ("unknown node", "9.9-neoforge")]:
        try:
            check_node(node, [], [])
            got = 0
        except NothingProven:
            got = 2
        failures += got != 2
        log("self-test %-16s want 2 got %d %s" % (name, got, "ok" if got == 2 else "WRONG"))
    log("self-test: %s" % ("PASS" if failures == 0 else "%d WRONG" % failures))
    return 0 if failures == 0 else 1


def main():
    p = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    p.add_argument("--self-test", action="store_true")
    p.add_argument("--node", action="append", default=[])
    p.add_argument("--checks")
    p.add_argument("--join", action="store_true")
    a = p.parse_args()
    if a.self_test:
        return self_test()
    if not a.node:
        p.error("give at least one --node (a NeoForge or Forge node, e.g. 26.3-neoforge)")
    try:
        expects, allowed = load_checks(a.checks or (JOIN_CHECKS if a.join else DEFAULT_CHECKS))
    except NothingProven as e:
        log("NOTHING PROVEN (exit 2):", e)
        return 2
    results = []
    for node in a.node:
        try:
            code, summary = join_node(node, expects, allowed) if a.join else check_node(node, expects, allowed)
        except NothingProven as e:
            code, summary = 2, "NOTHING PROVEN: %s" % e
        results.append((node, code, summary))
    log("client boot check:")
    for node, code, summary in results:
        log("  %-16s exit %d  %s" % (node, code, summary))
    worst = max(code for _, code, _ in results)
    log("client boot check: exit %d" % worst)
    return worst


if __name__ == "__main__":
    sys.exit(main())
