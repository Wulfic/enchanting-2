"""Boot a built jar of the mod on a real loader server and check, from the log, that it is loaded and working.

Phase 13F. The release gate's built-jar rung, and the proof behind NeoForge/Forge claims GameTests can't reach
(production NeoForge/Forge servers never run GameTests). Adapted from the-continent's tools/server_smoke.py.

  python scripts/server_boot_check.py --loader {fabric,quilt,neoforge,forge} --minecraft VERSION
      --loader-version VERSION --mod JAR [--mod JAR ...] --work EMPTY_DIR
      [--fabric-api VERSION] [--commands FILE] [--gametests JAR] [--java PATH] [--timeout SECONDS]
  python scripts/server_boot_check.py --self-test

Installs the loader's server into WORK (refused unless empty or missing) from official Maven/meta, adds the mods
(plus Fabric API from Maven with --fabric-api), writes server.properties with server-port 25567 before the first start,
starts it, types the commands file into the console once it reaches "Done (", then "stop".

The commands file takes dev-server-smoke.sh's directives: a console command per line; `@expect <regex>` (some log
line must match); `@allow-error <regex>` (an ERROR/FATAL line matching it is not a failure); `@sh sleep N` (wait);
`#` comments. Any other `@sh` is dev-only and refused here.

--gametests JAR (Fabric/Quilt, 26.x): runs the server GameTests instead of commands. Adds fabric-gametest-api-v1 (the
version the fabric-api POM names; the player jar doesn't ship it), -Dfabric-api.gametest and an absolute report path,
and puts the server in WORK/build/run/gameTest (ConfigWiringGameTest refuses any other game dir). The server runs the
tests and exits by itself; the verdict reads the report.

The verdict comes from the log, never the server's exit code: "Done (", "Enchanting 2 initialized", no ERROR/FATAL
line outside @allow-error, every @expect, and a clean exit after "stop". The log is kept at WORK/server.log.
Exit 0 = proven · 1 = the mod is bad · 2 = nothing proven (download, installer, port, a server that won't stop).
Never kills a process: a server that ignores "stop" is reported with its PID and exit 2.
"""
import argparse
import os
import re
import socket
import subprocess
import sys
import tempfile
import threading
import time
import urllib.request
import xml.etree.ElementTree as ET

PORT = 25567
ERROR_LINE = re.compile(r"(/|\[)(ERROR|FATAL)\]")
INIT_LINE = "Enchanting 2 initialized"
DONE_LINE = re.compile(r"Done \(.*For help")
SILENCE = 180
EXPECT_WAIT = 60
STOP_WAIT = 180
USER_AGENT = "enchanting2-server-boot-check/1"
FABRIC_MAVEN = "https://maven.fabricmc.net/net/fabricmc/"


class NothingProven(Exception):
    """Exit 2: the environment failed before the mod could be judged."""


def log(*parts):
    print("boot-check:", *parts, flush=True)


def fetch(url):
    # Forge's Maven answers Python's default user agent with 403.
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            return response.read()
    except OSError as e:
        raise NothingProven("downloading %s: %s" % (url, e))


def download(url, path):
    log("downloading", url)
    data = fetch(url)
    with open(path, "wb") as out:
        out.write(data)


def run(cmd, cwd):
    log("running", " ".join(cmd))
    try:
        result = subprocess.run(cmd, cwd=cwd, stdin=subprocess.DEVNULL, capture_output=True, text=True,
                                errors="replace")
    except OSError as e:
        raise NothingProven("%s: %s" % (cmd[0], e))
    if result.returncode != 0:
        print(result.stdout[-4000:], result.stderr[-4000:])
        raise NothingProven("%s exited with %d" % (" ".join(cmd[:3]), result.returncode))


def install(loader, minecraft, version, work, java):
    """Installs the server into work and returns the command that starts it (JVM options go after the java path)."""
    if loader == "fabric":
        import json
        installers = json.loads(fetch("https://meta.fabricmc.net/v2/versions/installer"))
        installer = next(i["version"] for i in installers if i["stable"])
        download("https://meta.fabricmc.net/v2/versions/loader/%s/%s/%s/server/jar" % (minecraft, version, installer),
                 os.path.join(work, "server.jar"))
        return ["-jar", "server.jar", "nogui"]
    if loader == "quilt":
        repo = "https://maven.quiltmc.org/repository/release/org/quiltmc/quilt-installer/"
        release = re.search(r"<release>([^<]+)</release>", fetch(repo + "maven-metadata.xml").decode("utf-8"))
        if release is None:
            raise NothingProven("no <release> in the Quilt installer's maven metadata")
        installer = os.path.join(work, "quilt-installer.jar")
        download("%s%s/quilt-installer-%s.jar" % (repo, release.group(1), release.group(1)), installer)
        run([java, "-jar", installer, "install", "server", minecraft, version, "--download-server",
             "--install-dir=" + work], work)
        return ["-jar", "quilt-server-launch.jar", "nogui"]
    if loader == "neoforge":
        installer = os.path.join(work, "installer.jar")
        download("https://maven.neoforged.net/releases/net/neoforged/neoforge/%s/neoforge-%s-installer.jar"
                 % (version, version), installer)
        run([java, "-jar", installer, "--install-server", work], work)
        args = "win_args.txt" if os.name == "nt" else "unix_args.txt"
        return ["@libraries/net/neoforged/neoforge/%s/%s" % (version, args), "nogui"]
    if loader == "forge":
        full = "%s-%s" % (minecraft, version)
        installer = os.path.join(work, "installer.jar")
        download("https://maven.minecraftforge.net/net/minecraftforge/forge/%s/forge-%s-installer.jar" % (full, full),
                 installer)
        run([java, "-jar", installer, "--installServer", work], work)
        shim = "forge-%s-shim.jar" % full
        if os.path.exists(os.path.join(work, shim)):
            return ["-jar", shim, "nogui"]
        args = "win_args.txt" if os.name == "nt" else "unix_args.txt"
        return ["@libraries/net/minecraftforge/forge/%s/%s" % (full, args), "nogui"]
    raise NothingProven("unknown loader " + loader)


def gametest_api_version(fabric_api):
    """fabric-gametest-api-v1's version, as the fabric-api POM pins it."""
    pom = fetch("%sfabric-api/fabric-api/%s/fabric-api-%s.pom" % (FABRIC_MAVEN, fabric_api, fabric_api)).decode()
    match = re.search(r"<artifactId>fabric-gametest-api-v1</artifactId>\s*<version>([^<]+)</version>", pom)
    if match is None:
        raise NothingProven("fabric-api %s's POM names no fabric-gametest-api-v1" % fabric_api)
    return match.group(1)


def parse_commands(path):
    """(commands, expects, allowed): commands are console lines or ('sleep', seconds)."""
    commands, expects, allowed = [], [], []
    with open(path, encoding="utf-8") as f:
        for raw in f:
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            if line.startswith("@expect "):
                expects.append(re.compile(line[len("@expect "):]))
            elif line.startswith("@allow-error "):
                allowed.append(re.compile(line[len("@allow-error "):]))
            elif line.startswith("@sh "):
                sleep = re.fullmatch(r"sleep (\d+)", line[len("@sh "):])
                if sleep is None:
                    raise NothingProven("%s: '%s' is dev-only (only '@sh sleep N' runs here)" % (path, line))
                commands.append(("sleep", int(sleep.group(1))))
            elif line.startswith("@"):
                raise NothingProven("%s: unknown directive '%s'" % (path, line))
            else:
                commands.append(line)
    return commands, expects, allowed


def report_problems(report):
    """Problems with a GameTest report (empty = every test ran and passed), and a one-line summary."""
    if not os.path.isfile(report):
        return ["no GameTest report at %s: the tests didn't run" % report], "no report"
    cases = list(ET.parse(report).getroot().iter("testcase"))
    failed = [c.get("name") for c in cases if c.find("failure") is not None or c.find("error") is not None]
    summary = "%d GameTests ran, %d failed" % (len(cases), len(failed))
    problems = []
    if not cases:
        problems.append("the GameTest report has no test cases: the GameTest jar didn't load")
    if failed:
        problems.append("%d GameTests failed: %s" % (len(failed), ", ".join(failed[:20])))
    return problems, summary


def judge(lines, expects, allowed, stopped_cleanly):
    """Problems found in a server log (empty = proven)."""
    problems = []
    if not any(DONE_LINE.search(line) for line in lines):
        problems.append('the server never reached "Done ("')
    if not any(INIT_LINE in line for line in lines):
        problems.append('the mod never logged "%s"' % INIT_LINE)
    errors = [line for line in lines
              if ERROR_LINE.search(line) and not any(a.search(line) for a in allowed)]
    if errors:
        problems.append("%d ERROR/FATAL line(s), first: %s" % (len(errors), errors[0]))
    for expect in expects:
        if not any(expect.search(line) for line in lines):
            problems.append("no line matches @expect %s" % expect.pattern)
    if not stopped_cleanly:
        problems.append("the server did not exit cleanly after stop")
    return problems


def port_listening(port):
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.settimeout(1)
        return s.connect_ex(("127.0.0.1", port)) == 0


def boot(a):
    for jar in a.mod + ([a.gametests] if a.gametests else []):
        if not os.path.isfile(jar):
            raise NothingProven("no such jar " + jar)
    if a.gametests and a.loader not in ("fabric", "quilt"):
        raise NothingProven("--gametests runs on fabric/quilt only; production %s servers never run GameTests" % a.loader)
    if a.gametests and not a.fabric_api:
        raise NothingProven("--gametests needs --fabric-api (fabric-gametest-api-v1's version comes from its POM)")
    if a.gametests and a.commands:
        raise NothingProven("--gametests and --commands are separate runs")
    commands, expects, allowed = parse_commands(a.commands) if a.commands else ([], [], [])

    work = os.path.abspath(a.work)
    if os.path.exists(work) and os.listdir(work):
        raise NothingProven("work dir %s is not empty; give a new one (this never overwrites)" % work)
    # ConfigWiringGameTest checks for this game dir; plain boots use it too, so both runs look the same.
    server_dir = os.path.join(work, "build", "run", "gameTest") if a.gametests else work
    mods = os.path.join(server_dir, "mods")
    os.makedirs(mods, exist_ok=True)
    for jar in a.mod + ([a.gametests] if a.gametests else []):
        with open(jar, "rb") as src, open(os.path.join(mods, os.path.basename(jar)), "wb") as dst:
            dst.write(src.read())
    if a.fabric_api:
        name = "fabric-api-%s.jar" % a.fabric_api
        download("%sfabric-api/fabric-api/%s/%s" % (FABRIC_MAVEN, a.fabric_api, name), os.path.join(mods, name))
    if a.gametests:
        version = gametest_api_version(a.fabric_api)
        name = "fabric-gametest-api-v1-%s.jar" % version
        download("%sfabric-gametest-api-v1/%s/%s" % (FABRIC_MAVEN + "fabric-api/", version, name),
                 os.path.join(mods, name))

    if port_listening(PORT):
        raise NothingProven("port %d is already in use; refusing to start a second server" % PORT)
    # 26.3 defaults white-list to true; the join check's client must get in (loopback, offline mode).
    with open(os.path.join(server_dir, "server.properties"), "w") as f:
        f.write("server-ip=127.0.0.1\nserver-port=%d\nonline-mode=false\nsync-chunk-writes=false\n"
                "spawn-protection=0\nlevel-seed=13\nwhite-list=false\nenforce-whitelist=false\n" % PORT)
    with open(os.path.join(server_dir, "eula.txt"), "w") as f:
        f.write("eula=true\n")  # This automated test server only, as the dev server and GameTests do.

    launch = install(a.loader, a.minecraft, a.loader_version, server_dir, a.java)
    jvm = ["-Xmx3G"]
    report = os.path.join(server_dir, "gametest-report.xml")
    if a.gametests:
        jvm += ["-Dfabric-api.gametest", "-Dfabric-api.gametest.report-file=" + report]
    command = [a.java] + jvm + launch
    log("starting", " ".join(command), "in", server_dir)
    try:
        server = subprocess.Popen(command, cwd=server_dir, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                  stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace")
    except OSError as e:
        raise NothingProven("starting %s: %s" % (a.java, e))

    lines = []
    last_output = [time.time()]
    done = threading.Event()

    def save_log():
        with open(os.path.join(work, "server.log"), "w", encoding="utf-8") as f:
            f.write("\n".join(lines) + "\n")

    def pump():
        for line in server.stdout:
            last_output[0] = time.time()
            lines.append(line.rstrip("\n"))
            if DONE_LINE.search(line):
                done.set()

    threading.Thread(target=pump, daemon=True).start()

    def send(text):
        """False once the server is gone, as it is after a crash at startup."""
        try:
            server.stdin.write(text + "\n")
            server.stdin.flush()
            return True
        except OSError:
            return False

    def wait_until(check, seconds):
        """Waits for check(); gives up once the server exits or logs nothing for SILENCE seconds."""
        deadline = time.time() + seconds
        while time.time() < deadline:
            if check():
                return True
            if server.poll() is not None:
                return check()
            if time.time() - last_output[0] > SILENCE:
                log("no output for %d s; giving up" % SILENCE)
                return False
            time.sleep(1)
        return False

    started = time.time()
    stopped_cleanly = False
    if a.gametests:
        # The GameTest server runs every test and exits by itself; it never prints "For help" and never reads stdin.
        if not wait_until(lambda: server.poll() is not None, a.timeout):
            print("\n".join(lines[-40:]))
            save_log()
            raise NothingProven("the GameTest server is still running after %d s (PID %d, not killed)"
                                % (a.timeout, server.pid))
        problems, summary = report_problems(report)
        log(summary)
        if not any(INIT_LINE in line for line in lines):
            problems.append('the mod never logged "%s"' % INIT_LINE)
    else:
        if wait_until(done.is_set, a.timeout):
            log("reached Done after %d s" % (time.time() - started))
            for cmd in commands:
                if isinstance(cmd, tuple):
                    time.sleep(cmd[1])
                elif not send(cmd):
                    break
                else:
                    time.sleep(1)
            wait_until(lambda: all(any(e.search(line) for line in lines) for e in expects), EXPECT_WAIT)
        send("stop")
        try:
            code = server.wait(STOP_WAIT)
        except subprocess.TimeoutExpired:
            print("\n".join(lines[-40:]))
            save_log()
            raise NothingProven("the server did not exit within %d s of stop (PID %d, not killed)"
                                % (STOP_WAIT, server.pid))
        stopped_cleanly = code == 0 and any("Stopping server" in line for line in lines)
        problems = judge(lines, expects, allowed, stopped_cleanly)
    save_log()

    if problems:
        print("\n".join(lines[-60:]))
        for problem in problems:
            log("FAIL:", problem)
        return 1
    for expect in expects:
        log("matched", next(line for line in lines if expect.search(line)).strip())
    log("PASS: %s %s on Minecraft %s (%s)" % (a.loader, a.loader_version, a.minecraft,
                                             "GameTests" if a.gametests else "boot"))
    return 0


def self_test():
    """The verdict functions on synthetic logs, both ways. Exit 0 only if every case judged as expected."""
    good = [
        "[12:00:00] [main/INFO]: Loading Minecraft",
        "[12:00:01] [Server thread/INFO] (enchanting2) Enchanting 2 initialized",
        '[12:00:02] [Server thread/INFO]: Done (3.1s)! For help, type "help"',
        "[12:00:03] [Server thread/INFO]: Applied enchantment Knockback V to Zombie",
        "[12:00:04] [Server thread/INFO]: Stopping server",
    ]
    expects = [re.compile(r"Applied enchantment .*Knockback.* to")]
    allowed = [re.compile(r"known harmless")]
    cases = [
        ("clean log", good, True, 0),
        ("ERROR line", good + ["[12:00:05] [Server thread/ERROR]: something broke"], True, 1),
        ("FATAL in brackets", good + ["[12:00:05] [main/FATAL] [mixin] boom"], True, 1),
        ("allowed ERROR", good + ["[12:00:05] [Server thread/ERROR]: known harmless"], True, 0),
        ("no init line", [l for l in good if INIT_LINE not in l], True, 1),
        ("no Done", [l for l in good if "Done (" not in l], True, 1),
        ("expect missing", [l for l in good if "Applied" not in l], True, 1),
        ("unclean stop", good, False, 1),
    ]
    failures = 0
    for name, lines, stopped, want in cases:
        got = 1 if judge(lines, expects, allowed, stopped) else 0
        verdict = "ok" if got == want else "WRONG"
        failures += got != want
        log("self-test %-18s want %d got %d %s" % (name, want, got, verdict))
    with tempfile.TemporaryDirectory() as tmp:
        report = os.path.join(tmp, "r.xml")
        for name, xml, want in [
            ("report pass", '<testsuite><testcase name="a"/><testcase name="b"/></testsuite>', 0),
            ("report failure", '<testsuite><testcase name="a"><failure/></testcase></testsuite>', 1),
            ("report empty", "<testsuite/>", 1),
        ]:
            with open(report, "w") as f:
                f.write(xml)
            got = 1 if report_problems(report)[0] else 0
            failures += got != want
            log("self-test %-18s want %d got %d %s" % (name, want, got, "ok" if got == want else "WRONG"))
        got = 1 if report_problems(os.path.join(tmp, "missing.xml"))[0] else 0
        failures += got != 1
        log("self-test %-18s want 1 got %d %s" % ("report missing", got, "ok" if got == 1 else "WRONG"))
    log("self-test: %s" % ("PASS" if failures == 0 else "%d WRONG" % failures))
    return 0 if failures == 0 else 1


def main():
    p = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    p.add_argument("--self-test", action="store_true")
    p.add_argument("--loader", choices=["fabric", "quilt", "neoforge", "forge"])
    p.add_argument("--minecraft")
    p.add_argument("--loader-version")
    p.add_argument("--mod", action="append", default=[])
    p.add_argument("--work")
    p.add_argument("--fabric-api")
    p.add_argument("--commands")
    p.add_argument("--gametests")
    p.add_argument("--java", default="java")
    p.add_argument("--timeout", type=int, default=900)
    a = p.parse_args()
    if a.self_test:
        return self_test()
    missing = [n for n in ("loader", "minecraft", "loader_version", "work") if getattr(a, n) is None]
    if missing or not a.mod:
        p.error("required: --loader --minecraft --loader-version --mod --work (missing: %s)"
                % ", ".join(missing + ([] if a.mod else ["mod"])))
    try:
        return boot(a)
    except NothingProven as e:
        log("NOTHING PROVEN (exit 2):", e)
        return 2


if __name__ == "__main__":
    sys.exit(main())
