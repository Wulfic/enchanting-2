# TODO

The working plan. `plan-work` writes here **before** any code is written — a plan that
lives only in the chat window did not happen. Keep this current as you execute; a stale
TODO is worse than an empty one, because the next session trusts it.

> Long-lived context does **not** belong here. Decisions and gotchas go to
> `.agent/memory/`; this file is the active work list.

---

## Task template

```markdown
## <Task name>

**Goal:** <one sentence — what "done" means>
**Approach:** <chosen approach, and the one-line reason it beat the alternative>
**Not doing:** <explicit out-of-scope list — this is what stops scope creep>
**Blast radius:** <what this destroys or overwrites, and where it comes back from —
                  or "none: additive only">

- [ ] 1. <concrete step — names a real file>
- [ ] 2. <concrete step>
- [ ] 3. <the test that proves it works>

**Rollback:** <the exact command that undoes this — not "revert the change">
```

Rules for steps: each names a real file or command, each is independently verifiable, and
tests are steps rather than an afterthought. More than ~8 steps means it's a Tier 2 task —
split it into stages with commits between them.

`Blast radius:` and `Rollback:` are **mandatory** — "none, additive only" is a valid
answer, a blank field is not. Destructive steps are marked as such and gated through
`guard-destructive` when you reach them; irreversible steps go last.

---

## Active

## Phase 13 — Quilt, NeoForge and Forge (Tier 2)

Owner 2026-10-20: bring the mod to Quilt, NeoForge and Forge. Answers: NeoForge/Forge on **26.x only** (no 1.21.11);
Fabric/Quilt share a jar, NeoForge and Forge get their own; JUnit + **server GameTests on every loader**; a config
menu on Forge/NeoForge from something other than YACL. Decision record: decisions.md 2026-10-20 "Phase 13".

**Goal:** each release ships the four Fabric jars (also claimed for Quilt where proven) plus one NeoForge and one
Forge jar per 26.x band (B1 26.1.2, B2 26.2, B3 26.3). Each new jar passes JUnit and all server GameTests on its own
loader, and every feature exists on every loader, as in Phase 12.

**Facts checked 2026-10-20 (Maven metadata / Modrinth API):**
- NeoForge: 26.1.2.122, 26.2.0.93, 26.3.0.95 stable (26.1.0 / 26.1.1 builds are still beta). Forge: 26.1.2-64.1.5,
  26.2-65.1.5, 26.3-66.0.13. Each 26.1 patch has its own Forge major (62/63/64), so a B1 jar starts out claiming 26.1.2
  only; the matrix can widen it later, as in 12H.
- Quilt: Loader 0.31.0-beta.4 (2026-09-04). Quilted Fabric API stopped at 1.21 (last update 2024-12), and nothing
  official says whether Quilt runs on 26.x. Quilt users run plain Fabric API, so the claim has to be tested.
- Config screens: YACL has NeoForge builds for 26.x and none for Forge. Cloth Config has NeoForge only; its Forge build
  is an unofficial port with no 26.3. Forge Config API Port would move us to TOML specs. SuperMartijn / config-api
  have no Forge 26.3 build. simple-config-lib and configlib-txf have about 3k downloads each and their own config
  models. → **Our own screen on vanilla widgets** for NeoForge + Forge. `ConfigView`, the drafts and `TabRows` are
  already ours; only the drawing goes through YACL.
- Fabric API surface: 28 main/client files (networking 24 imports, attachments 9, lifecycle 4, item-api 4, loot 2, HUD
  2, tooltip 1, entity copy 1, Mod Menu 1). Tests: 39 server GameTests use only `@GameTest`; the 10 client GameTests
  use `FabricClientGameTest` (Fabric-only, nothing equivalent on the new loaders). No access widener. MixinExtras in 39
  files. 49 vanilla classes mixed into, several of which NeoForge/Forge patch (`AnvilMenu`, `EnchantmentHelper`,
  `EnchantmentMenu`, `GrindstoneMenu`, `LivingEntity`, `Player`, `Block`), so injection points can move or double up.

**Approach:**
- One source tree. Loaders become a second Stonecutter axis: nodes such as `26.3-neoforge` and `26.3-forge` next to
  the existing Fabric nodes (which keep their names, so CI, matrix and release paths stay put).
- A **platform seam**: `platform/` holds interfaces (`Networking`, `Attachments`, `Lifecycle`, `LootHooks`,
  `ComponentHooks`, `EnchantmentHooks`, `ClientHooks`, `ConfigScreenEntry`, `GameTestRegistrar`), with one
  implementation package per loader (`platform/fabric`, `platform/neoforge`, `platform/forge`). Each node compiles only
  its own package. Game logic never imports a loader API. This beats Architectury API because it adds no runtime
  dependency, and the surface we need is small (28 files).
- Server GameTests: test classes keep a loader-neutral `@GameTest` (ours), and each loader's registrar hands them to
  Fabric's API, NeoForge's registration event or Forge's. Client GameTests stay on the Fabric nodes; NeoForge/Forge get
  a written `runClient` smoke list.
- Quilt: the Fabric jar, unchanged. A `quilt-<v>` runtime node (like `matrix/`) runs its server GameTests on Quilt
  Loader + Fabric API. Green = claimed in README/mod pages. Red = documented as unsupported, with no workaround code.

**Not doing:** NeoForge/Forge on 1.21.11 (so `AnvilMenuMixin`'s intermediary `method_24922` stays Fabric-only); a
separate Quilt jar or QSL APIs; client GameTests on NeoForge/Forge; replacing YACL on Fabric/Quilt; any config file
format change (json5 stays, so `docs/CONFIG.md` holds for every loader); Architectury API; 26.4 (waits for stable).

**Blast radius:** 13B moves the 28 Fabric-importing files' loader calls behind `platform/`: these are edits, not
deletions, and they come back from git (`a071f7f` is the base). Everything else is additive (new nodes, packages,
CI jobs). No release, tag or publish until 13G, and 13G gets its own confirmation.

**Work on branch `port/loaders`** (PR + squash, as Phase 12). Each stage = one commit, `verifyAll` green before it.

- [ ] 13A — **Spikes, throwaway, nothing merged.** These decide whether the rest holds.
  - [ ] A1. Quilt: run the 1.21.11 and 26.x Fabric jars' server GameTests on Quilt Loader 0.31.0-beta.4 + Fabric API
        (Quilt installer CLI, server, scratchpad). Record pass/fail per band.
  - [ ] A2. NeoForge 26.3: an empty node in this build (Stonecutter loader axis + ModDevGradle next to
        loom-back-compat, or Architectury Loom for both) that loads `enchanting2.mixins.json` with one MixinExtras
        `@WrapOperation`. Choose the build plugin, `/research-docs` first.
  - [ ] A3. Forge 26.3: the same with ForgeGradle. Check that Forge 66 applies mixins **and** ships MixinExtras.
  - **Gate:** if Forge can't apply our mixins, stop and report to the owner (Forge would wait, NeoForge continues).
        The plugin choice goes into decisions.md.
- [ ] 13B — **Platform seam, Fabric only, no behaviour change.** `platform/` interfaces + `platform/fabric`; the 28
      files call the seam; `@GameTest` is ours, registered through `GameTestRegistrar`. Proof: `verifyAll` and every
      node's `runClientGameTest` are as green as on `a071f7f`, with the same test counts (432 JUnit / 339 GameTests).
      Add a JUnit test that fails if any class outside `platform/<loader>/` imports `net.fabricmc` / `net.neoforged` /
      `net.minecraftforge`.
- [ ] 13C — **NeoForge nodes** 26.1.2, 26.2, 26.3: `neoforge.mods.toml`, `platform/neoforge` (payload registration,
      data attachments with `copyOnDeath`, `ModifyDefaultComponentsEvent`, loot events, `PlayerEvent.Clone`,
      `RegisterGuiLayersEvent`, `ItemTooltipEvent`). The enchantment `MODIFY` replacement is a registry-load hook like
      10gA's `RegistryDataLoaderMixin`. Fix mixins that miss on NeoForge's patched targets (mixin `require` errors =
      the list), each with a GameTest already covering it. Proof: JUnit + 339 server GameTests on each node.
- [ ] 13D — **Forge nodes** 26.1.2, 26.2, 26.3: `mods.toml`, `platform/forge` (capabilities for attachments, its
      payload channel, Forge events). Same proof.
- [ ] 13E — **Config screen for NeoForge + Forge** on vanilla widgets (`client/config/`, drawn from the existing
      `ConfigView` / drafts / `TabRows`; every option the YACL screen has, per DESIGN §1). Entry: NeoForge
      `IConfigScreenFactory`, Forge `ConfigScreenFactory`. JUnit on the model (an option missing from the screen fails),
      plus the owner's `runClient` check on both loaders.
- [ ] 13F — **Build, CI, release plumbing:** `verifyAll` covers every node + Quilt nodes; `ci.yml` jobs per loader;
      jar names carry the loader (proposed `enchanting2-1.2.0+26.3-neoforge.jar`; Fabric names unchanged); `release-collect.sh`,
      `ModIdentityTest`, `ReleaseDocsTest`; README / wiki Installation tables per loader; CHANGELOG [Unreleased].
- [ ] 13G — **Release 1.2.0.** Owner confirms first (outward-facing). Tag, ten jars, mod pages list the loaders.

**Rollback:** before merge, delete the branch (`git branch -D port/loaders`, after checking it's merged or unwanted).
After merge, `git revert <squash commit>`. A bad release is fixed by a patch release, never by deleting tags.

## Session 2026-10-19 — cleanup, client GameTest flake, client GameTests in CI (Tier 1 → 2 for CI)

Owner answers 2026-10-19: 26.4 waits for stable (latest release 26.3, snapshot 26.4-snapshot-4); chase the flake now;
remove the stale worktree and delete `port/26x`; headless client GameTests in CI **after** the flake is fixed.

**Not doing:** 26.4 snapshot nodes; owner in-game checks; Modrinth/CurseForge.
**Blast radius:** C1 deletes a scratch checkout (clean, `git status` 0 lines; dbf6006 is a commit on port/26x).
C2 deletes `port/26x` locally and on origin (tip 97e1949, 15 commits not on main; main holds them as squash 9246bc4).
**Rollback:** C1: `git worktree add <path> dbf6006`. C2: `git fetch origin refs/pull/1/head:port/26x` (GitHub keeps
`refs/pull/1/head` = 97e1949 read-only), then `git push origin port/26x`.

- [x] C1. `git worktree remove` the 147c41c6 scratchpad `base/` (dbf6006, clean). Git unregistered it, then failed
      on "Filename too long"; the leftover dir (530 MB, tracked files + build output, no `.git`, no credentials) stays
      for the owner to delete (`rm -rf` is in the deny list).
- [x] C2. `port/26x` deleted locally and on origin; `refs/pull/1/head` = 97e1949 confirmed after.
- [x] F1. Diagnosed + reproduced: Fabric client-gametest 4.3.5 (1.21.11 only; 5+/6.x deleted the class) resets the
      CLIENTBOUND count at `dropAllTasks`, before `ClientPacketListener.close()`, while the server still ticks; a packet
      lost with the channel leaves +1 and the next tick waits 10 s → AssertionError. `NetworkSynchronizerClientGameTest`
      leaks one count by reflection: same two log lines (run 2026-10-19 20:36).
- [x] F2a. build.gradle band A: `-Dfabric.client.gametest.disableNetworkSynchronizer=true` (Fabric docs' workaround).
      The leak test passes with it.
- [x] F2b. Without the synchronizer, fixed `waitTicks(n)` before a cross-side read is a race (it always was on 26.x):
      repeat runs failed ShieldClientGameTest (Last Stand attachment, S2C) and GrindstoneScreenClientGameTest (result
      click, C2S). Fix the class: gametest `Delivery` barrier = Fabric 6.x's design (marker payload with a sequence
      number over the same ordered connection; `toServer` after client input, `toClient` = one tick for the server's
      broadcast, then a server echo), registered from gametest `main` / `client` entrypoints. 34 cross-side waits
      replaced (41 barrier calls) in 8 client tests; waits for client-only effects (hover, scroll, physics) stay.
- [x] F2b'. 26.1.2 then failed TooltipClientGameTest "back up one notch: 0" (wall-clock `STALE_NANOS` race under
      load, gotchas.md 12E entry). `TallTooltips.useClock` (client, test hook beside `offset()`); the test freezes it
      for the scroll section.
- [x] F2c. 1.21.11 client suite green 3 runs in a row (barriers); final code green once on all four nodes.
- [x] F3. `scripts/verify-all.sh` green (the gametest mod's new `main` entrypoint loads in server GameTests too);
      commit `fix:` + push; CI green. Done: e016372, CI run 39354598552 green.
- [x] CI1. ci.yml job `client` (+ `client-nodes`): node list derived from `versions/*/` (a `nodes` job → JSON matrix, like
      the JDK step reads properties), `fail-fast: false`; per node its `java_version` + `gradle_java_version`;
      `apt-get install xvfb` + Mesa (plus `mesa-vulkan-drivers` if a 26.x client wants Vulkan; check the local log);
      `xvfb-run -a ./gradlew :<node>:runClientGameTest`; screenshots + logs uploaded `if: always()`.
      Header comment + AGENTS.md "done" line + state.md updated. Dispatch CI; green on all four, or a documented
      blocker per node (a node that can't render headless stays local-only, said so in ci.yml).
      **Blast radius:** none (additive CI job). **Rollback:** `git revert` the commit.
      Done 2026-10-19 on branch `ci/client-gametests`, dispatch run 39354609061: build, secrets and all four client
      jobs green on the first try (23 screenshots per node, real renders). 26.3 fell back to Vulkan on lavapipe
      (no GLX visual under Xvfb). JDK step moved to `.github/actions/setup-jdks` (release.yml keeps its own copy).

## Phase 12 — Version bands: one jar per band, as many Minecraft versions per jar as hold up (Tier 2)

**Goal:** every release ships one jar per *band*. Band A = 1.21.11 (obfuscated, intermediary names, Java 21). Band B
= every stable 26.x line (26.1.2, 26.2, 26.3 today) in **one** jar (unobfuscated, Java 25). Every feature lands in
both bands in the same commit. The B jar is proven on each 26.x runtime by the full GameTest suite.
**Revised 2026-10-15 by the 12C gate (decisions.md "One band per 26.x line"):** the 26.x lines don't share a jar.
Bands: A = 1.21.11, B1 = 26.1–26.1.2 (proven by the matrix), B2 = 26.2, B3 = 26.3; four jars per release, all from
the one source tree. The matrix stays for versions a jar claims without being built for (hotfixes, a new line).

**Model: mcMMO** (checked 2026-10-14, `mcMMO-Dev/mcMMO` master, 2.3.003). One Maven module, one jar per band of game
versions:
- It compiles against the band's **floor** (`spigot.version` 1.20.5, `api-version: 1.20.5`). The same jar runs on
  1.20.5 through 1.21.x and 26.x. Older bands are separate, frozen releases: 2.2.049 for 1.18–1.20.4, 2.1.202 for
  1.13.2–1.17. The floor moves up now and then "to reduce the maintenance burden".
- Inside a band, differences are handled at **runtime**, not at compile time. `MinecraftGameVersionFactory` parses the
  running version into `MinecraftGameVersion` (`isAtLeast(...)`, unit-tested). `ReflectionUtils` / `adapter/*`
  (e.g. `BiomeAdapter`) look up things that may not exist and degrade to null or empty instead of throwing. New
  content is matched by name, so a version without it simply finds nothing.
- New Minecraft version: test it on the existing jar. Add compat if something broke (2.3.002: "Fixed mcMMO failing to
  start on Spigot 26.3"). A new jar only when the floor moves.

**What carries over, and what doesn't:** mcMMO sits on Bukkit, a stable API that keeps old methods working. We sit
on vanilla internals: 55 mixin classes, plus Fabric API, YACL and Mod Menu. So:
- **No single jar can span both bands.** 1.21.11 classes refer to intermediary names (`class_1234`) and 26.x
  classes to Mojang names. The bytecode itself differs, and so do the Java versions (21 vs 25). Two bands is the floor.
- **Within 26.x, one jar is plausible but not proven.** 26.2 already moved `Minecraft.setScreen` →
  `Minecraft.gui.setScreen` and split registration into `BlockIds` / `ItemIds` (fabricmc.net 2026-06-15). Our
  `setScreen` calls are only in GameTests, but any mixin whose target method or descriptor changed fails to *apply*,
  which crashes at startup. Plain code can branch at runtime; a mixin needs a separate class per version, gated so
  only the matching one applies.
- **The band ends where bridging costs more than a jar.** A split rule written down before we're tempted to bend it:
  if a 26.x line needs more than ~6 adapters/gated mixins, needs reflection on a per-tick hot path, or needs a class we
  subclass to differ, then that line starts a new band (B1 = 26.1.x, B2 = 26.2+), and that is a fine outcome.

**Approach:**
- Stonecutter + `loom-back-compat` for the **band split** (the two compile targets, one tree; as planned 2026-10-13).
- mcMMO-style runtime adapters for **versions inside a band**: compile against the floor, `compat/McVersion` reads
  the version from Fabric Loader, adapters resolve `MethodHandle`s once at startup and fail closed with a log line,
  and an `IMixinConfigPlugin` (`VersionedMixins`) applies a version-gated mixin only where it matches.
- **Test matrix:** a B jar is only "supporting" a version once the server GameTests (and client GameTests locally)
  pass with that jar on that runtime. A runtime-only Gradle node per 26.x line loads the floor-built jar and gametest
  jar (no recompile; unobfuscated, so no remap) and runs `runGameTest`.

**Rejected:**
- A branch per version with cherry-picks: the owner wants every feature everywhere (decisions.md 2026-10-13).
- A jar per 26.x line, the old 12B–12D: three jars and three dependency sets for lines that may share 95%+ of their
  code. It stays the fallback if the matrix shows they don't.
- One "fat" jar nesting a 1.21.11 jar and a 26.x jar (jar-in-jar): it relies on unverified Fabric Loader behaviour for
  nested mods with unmet `depends`, doubles the download, and gives players nothing, since launchers and Modrinth
  already pick a file by version.

**Not doing:** snapshots or pre-releases in a band, NeoForge/Quilt, Modrinth/CurseForge publishing, new features
during the port, widening band A below 1.21.11 (12G, only if the owner asks).
**Blast radius:** the build system, possibly the source layout. All on branch `port/26x`; main untouched until a
stage is green on every node. **Rollback:** `git branch -D port/26x` (gated: unmerged work, so name the branch and
show `git log main..port/26x` first). Nothing is published until 12F.

- [x] 12A **Spike, band A only:** Stonecutter + loom-back-compat with the one 1.21.11 node. Exit: jar contents
      identical to v1.0.1 (class list + `fabric.mod.json` diff empty but the version), JUnit + server GameTests +
      client GameTests green, dev server still on 25566. If the tooling can't run GameTests per node or breaks
      Jankson's jar-in-jar: stop, bring the owner the branch-per-band fallback and its cost.
      Owner 2026-10-14: "1.21.11 is its own band, and 26.x versions will have however few bands as possible" — the
      layout above stands; 1.21.11 stays in step (2026-10-13 ruling, not overturned).
      Versions pinned 2026-10-14 from the Maven metadata: Stonecutter 0.9.8 (2026-08-31), loom-back-compat 0.4.3
      (2026-09-25); Loom stays 1.18.2 for 12A so the build layout is the only variable (1.18.4 is newest, 12B).
      Reference wiring: TeamMidnightDust/CullLeaves (Fabric 1.21.11 + 26.1–26.3 on the same two plugins, Kotlin DSL).
      Baseline: the published `v1.0.1` jar; compare with `jardiff.py` semantics (entry names + content, text entries
      CRLF→LF, because a Windows build and the Linux CI build differ only in line endings: 414 entries, 0 changed).
  - [x] A1. Branch `port/26x` from main (the uncommitted `docs/CURSEFORGE.md` deletion is the owner's; never staged).
  - [x] A2. `settings.gradle`: KikuGie releases repo, plugins `dev.kikugie.stonecutter` 0.9.8 +
        `dev.kikugie.loom-back-compat` 0.4.3, `stonecutter { kotlinController = false; centralScript = 'build.gradle';
        create(rootProject) { versions '1.21.11'; vcsVersion = '1.21.11' } }`. The JDK check keeps working.
  - [x] A3. `stonecutter.gradle` controller (Groovy), `stonecutter.active '1.21.11'`.
  - [x] A4. `versions/1.21.11/gradle.properties`: the per-version keys (minecraft_version, fabric_api_version,
        java_version, modmenu_version, yacl_version, run_root). Root keeps shared ones + `loomx.loom_version`.
  - [x] A5. `build.gradle`: `dev.kikugie.loom-back-compat` instead of `fabric-loom-remap`;
        `loomx.applyMojangMappings()`; run dirs, eula file and test inputs via `rootProject.file`; `Test.workingDir` =
        root (the docs tests read `docs/`, `wiki/`, `CHANGELOG.md`, `gradle.properties` by relative path).
  - [x] A6. `./gradlew build` green (JUnit 423 + server GameTests 336), then `runClientGameTest` (8).
  - [x] A7. Jar diff vs the v1.0.1 jar: empty. Jankson still nested in `META-INF/jars`.
  - [x] A8. `scripts/dev-server-smoke.sh` boots on 25566 from `run/server` and stops.
  - [x] A9. Paths that moved to `versions/1.21.11/build/`: ci.yml (JDKs from every node's file, jar + report
        paths), release.yml (interim: band A's node; 12F rewrites it), README + wiki "Building from Source",
        state.md. `ReleaseDocsTest` green.
  - [x] A10. decisions.md (12A layout), gotchas for anything that bit, state.md; commit on `port/26x`.
  Result 2026-10-14: jar = v1.0.1 entry for entry (414; only `MANIFEST.MF` gains four `Stonecutter-*` lines),
  Jankson nested; 423 JUnit, 336 server GameTests, client GameTests green; smoke server on 25566 from `run/server`.
  The release.yml dry run caught a `prop loader_version` call left on the old one-argument form (fixed).
  **Blast radius (12A):** build files only, on `port/26x`; additive except the moved property keys (they come back
  from main). **Rollback:** `git switch main` (main untouched); then `git branch -D port/26x` gated as above.
- [ ] 12B **Band B floor, 26.1.2 node:** Java 25, `fabric-loom` (no remap), `implementation` instead of `mod*`,
      `jar` instead of `remapJar`, access widener header `official`, Fabric API 26.1 renames (its porting page), vanilla
      changes (NeoForge 26.1 primer). 1.21.11 ↔ 26.1 differences behind Stonecutter conditions; anything used in more
      than ~3 places gets a small adapter class per band instead. Check each mixin against 26.1.2's source, not just
      "it compiles". Add a GameTest that loads a 1.21.11-written chunk/player (attachment codecs read the same).
      Exit: everything green on 26.1.2.
      Owner 2026-10-14: 1.21.11 **stays in step** (not frozen), so every 1.21.11 ↔ 26.1 difference lives in the shared
      source behind a Stonecutter condition or a per-band adapter. No access widener exists, so that item is moot.
      Versions checked 2026-10-14 (Fabric maven, Modrinth): Fabric API 0.155.3+26.1.2, YACL 3.9.7+26.1-fabric,
      Mod Menu 18.0.2 (26.1.x), Loom 1.18.4 (one `loomx.loom_version` for every node, as CullLeaves does).
  - [x] B1. Loom 1.18.2 → 1.18.4, 1.21.11 node only (one variable): `./gradlew build` green, jar diff vs v1.0.1 still
        only the manifest. Commit. Done 2026-10-14: 423 JUnit, 336 server GameTests, client GameTests green; 414
        entries, only `MANIFEST.MF` differs.
  - [x] B2. Per-node resource values: `fabric.mod.json` `minecraft` + `java` and both `*.mixins.json`
        `compatibilityLevel` expanded from node properties (`mc_dependency`, `java_version`) in `processResources`.
        1.21.11 jar unchanged (same expanded text). Commit. Done 2026-10-14: jar diff = manifest only;
        `ModIdentityTest` checks the range is expanded and Java level = the test JVM's (fails with the mixin pattern
        dropped); the pattern list is a task input (gotchas).
  - [x] B3. Node `26.1.2`: settings.gradle `versions '1.21.11', '26.1.2'`, `versions/26.1.2/gradle.properties` (Java 25,
        the versions above, `run_root=run/26.1.2`, `mc_dependency` `>=26.1.2 <26.2-` until 12C widens it).
  - [x] B4. `:26.1.2:compileJava` + `compileClientJava`: write every error class into this file, then fix behind
        `//? if >=26.1` conditions (adapter class when one difference recurs in more than ~3 places). After each fix
        group: both nodes compile.
        Done 2026-10-14 (uncommitted until B6 is green), every source set compiles on both nodes, no new warnings:
        - Global replacements (build.gradle `stonecutter {}`): Fabric `PayloadTypeRegistry` renames; `GuiGraphics` →
          `GuiGraphicsExtractor` (whole word); `graphics.drawString(` → `graphics.text(`; mixin `method =` names
          renderBg/renderLabels/renderTooltip → extractBackground/extractLabels/tooltip; `ClickType` → `ContainerInput`;
          GameTest-only `resizeDisplay` → `resizeGui`, `getClientWorld` → `getClientLevel`.
        - Same code on both: `Level.getRandom()` (26.1 made `random` protected); window size through
          `AbstractContainerScreenAccessor` (`@Mutable` setters: 26.1 made `imageWidth/Height` final).
        - `port/LootTool` (3 callers): loot `TOOL` is an `ItemInstance` on 26.1 (`ItemStackTemplate` from `/loot`).
        - `//?` conditions: `bypassedBy` TagKey → HolderSet; `EntityType.is` → `Entity.is`; `SingleItemRecipe.assemble`
          lost its registry arg; `EnchantmentEvents.MODIFY` → `MODIFY_WITH_LOOKUP` (2 hooks); `extractBackground`
          handlers take the partial tick last (3); `RowsBar.extractRenderState`.
        - Villager trades are data on 26.1 (`VillagerTrade.getOffer(LootContext)` running loot functions):
          `VillagerTradeScopeMixin` wraps it in TRADE scope; `EnchantBookForEmeraldsMixin` keeps the treasure price on
          its `Stream.anyMatch`; `EnchantRandomlyFunctionMixin` uses the current scope (books roll there on 26.1).
          `AcquisitionGameTest` runs the registry's librarian and weaponsmith trades on 26.1.
  - [x] B5. Mixin audit: every target method/descriptor/`@At` read against 26.1.2's decompiled source (`genSources`),
        not just "it compiles"; a mixin that compiles but no longer means the same thing is the dangerous case.
        Done 2026-10-14: 92 (mixin, method) targets diffed structurally between the two decompiled trees (scratchpad
        `mixindiff.py`, locals ignored): 52 same, 30 cosmetic, 2 real (bucket `$3`, lava shape; fixed), 8 new or
        renamed, checked by hand. `handleKeybinds` gained two `consumeClick` calls before our slice (safe).
        `== null` sweep on vanilla results: one hit, the spawn-egg guard (fixed). `MixinAuditGameTest` and
        `MixinAuditClientGameTest` force-load every target per node (mutation: a broken client mixin fails it).
  - [x] B6. GameTests compile + run on 26.1.2 (server, then client locally); fix behind conditions.
        Done 2026-10-14: 8 failures fixed (gotchas.md 2026-10-14, 12B entries); 339 server GameTests and 425 JUnit
        green on both nodes; client GameTests green on both; 26.1.2 screenshots match 1.21.11's windows.
  - [x] B7. GameTest: a player/chunk written by 1.21.11 (attachments: `PlacedMarks`, `last_stand_ready_at`) reads back
        on 26.1.2.
        Done 2026-10-14: `SaveFormatGameTest` pins the `fabric:attachments` subtree 1.21.11 writes (entity: three
        attachments) and asserts both directions on each node; chunk marks round-trip `SerializableChunkData`.
  - [x] B8. ci.yml builds both nodes (JDKs from every node file, already); state.md, decisions.md, gotchas; commit.
        ci.yml needed no change (Java 25 = Gradle's JDK; artifact glob per node); release.yml still refuses two
        nodes until 12F. README + wiki build commands name the node (`runClientGameTest` too).
        **Blast radius (12B):** build files + source conditionals on `port/26x`. **Rollback:** `git revert` the 12B
        commits on `port/26x`, or `git switch main` (main untouched).
- [x] 12C **Runtime matrix (the feasibility gate):** nodes `26.2` and `26.3` run the 26.1.2 jar's server GameTests
      with that line's Minecraft + Fabric API + YACL + Mod Menu. Write every failure into this file: mixin apply
      errors, `NoSuchMethodError`/`NoSuchFieldError`, YACL API breaks, behaviour diffs. Then apply the split rule
      above and record the band layout in decisions.md **before** writing any adapter.
      Owner 2026-10-15: widen band B's range now as a *candidate* (the matrix proves or narrows it); push `port/26x`
      after each green stage; client GameTests run locally on 26.2 and 26.3 this session.
      Versions checked 2026-10-15 (Fabric meta + maven, Modrinth): stable 26.x = 26.1.2, 26.2, 26.3 (26.4 is at
      snapshot-4, out of scope). 26.2: Fabric API 0.161.0+26.2, YACL 3.9.7+26.2-fabric, Mod Menu 20.0.3. 26.3: Fabric
      API 0.163.0+26.3, YACL 3.9.7+26.3-fabric, Mod Menu 21.0.0. Loader 0.19.5 everywhere.
      **Design:** a matrix node is a plain Gradle subproject (`matrix/<line>/`, project `:matrix-<line>`), *not* a
      Stonecutter node, so it never compiles `src/`. It applies loom-back-compat with `loomx.unobfuscated=true`, and
      its run classpath holds exactly the 26.1.2 node's mod jar and a jar of its gametest source set (consumable
      configurations on the node), plus that line's Fabric API / YACL / Mod Menu. Run configs mirror Loom's
      `configureTests` (server: `-Dfabric-api.gametest`; client: `-Dfabric.client.gametest`, `--username Player0`).
      A `matrix/26.1.2` **control** runs the same jars on their own version: if the control isn't 339/339, the harness
      is wrong, not the game. A report check fails the run on zero tests (a harness that loads nothing passes).
  - [x] C1. `versions/26.1.2/gradle.properties`: `minecraft_dependency=>=26.1.2 <26.4-` (candidate; C8 set the
        proven range). `ModIdentityTest` still green.
  - [x] C2. build.gradle: `gametestJar` task + consumable `matrixModJar` / `matrixGametestJar` configurations
        (artifacts only, no dependencies). 1.21.11 jar unchanged.
  - [x] C3. settings.gradle includes `:matrix-26.1.2`, `:matrix-26.2`, `:matrix-26.3`; shared `matrix/matrix.gradle`;
        each `matrix/<line>/gradle.properties` holds that line's versions and `matrix_jar_node=26.1.2`.
  - [x] C4. `matrix.gradle`: runs `gameTest` + `clientGameTest` (run dirs under `matrix/<line>/build/run/`, cleared,
        eula), Java 25 toolchain, JUnit report file + a check that fails on 0 tests or any failure.
  - [x] C5. Control: `:matrix-26.1.2:runGameTest` = 339/339 (Jankson from the jar's META-INF/jars). 26.2: 83/339
        failed. 26.3: crash at startup (`EnderManMixin` target class gone). Findings below.
  - [x] C6. ~~Client on 26.2/26.3~~ moot: neither line can run the 26.1.2 jar (split rule, below).
  - [x] C7. **Static link check** (`matrix/linkcheck/LinkCheck.java`, task `:matrix-<line>:linkCheck`): every class,
        field and method our two jars use in Minecraft / Fabric / YACL / Mod Menu, plus `@Mixin` targets, injector
        `method =` names and `@At` targets, resolved against that line's runtime classpath. Exits 1 on any problem.
        Control on 26.1.2 = 0 problems (a band-A-only mixin, unlisted in band B's json, is skipped). It caught every
        linkage error the 26.2 GameTests hit, plus the ones the tests' own failures masked.
        Result: control 0 problems; 26.2 31 (the 26.2 GameTests' linkage errors all among them); 26.3 48.
  - [x] C8. Owner 2026-10-15: "26.1, 26.1.1 and 26.1.2 are one jar" — prove it: matrix nodes `26.1` and `26.1.1`
        (Fabric API 0.155.3+26.1.2, YACL 3.9.7+26.1, Mod Menu 18.0.2 all declare 26.1–26.1.2), link check + server
        GameTests + client GameTests on each. Green → band B1 range `>=26.1 <26.2-`.
        2026-10-15: range set to `>=26.1 <26.2-`; link check 0 problems and server GameTests 339/339 on 26.1 and 26.1.1
        (log: "Loading Minecraft 26.1"). The 26.2/26.3 matrix nodes were removed (untracked, this session's; findings
        above): those lines get their own nodes. Client GameTests on 26.1 and 26.1.1: green, the same 23 screenshots
        as the 26.1.2 node's run. The run-dir delete guard refuses a path outside `build/run/` (mutation, reverted).
  - [x] C9. Band layout in decisions.md; gotchas; state.md; commit; push `port/26x`.
  **Blast radius (12C):** additive (new subprojects, one jar task, a widened range in an unreleased node).
  **Rollback:** `git revert` the 12C commit on `port/26x`; run dirs are under `matrix/*/build/` (git-ignored).
  **12C findings (2026-10-15)** — the 26.1.2 jar + its GameTest jar on other lines (link check, then GameTests):
  - 26.2, mod side (8): `CriteriaTriggers` / `EnchantedItemTrigger` gone (table Enchant crashes,
    `EnchantmentMenuMixin`); `Options.hideGui` (`LastStandHud`); `BlockEntityType.MOB_SPAWNER` and
    `EntityType.byString` (`CollectedSpawners`); YACL `ScrollableNavigationBar.arrangeElements()` (`TabbedConfigScreen`);
    `Minecraft.getToastManager()` (`ConfigEditing`); `LivingEntity.knockback(DDD)` (`ShieldBash`); data:
    `loot_table/beheading/heads.json` entity predicate `"type"` no longer parses (`entity_sub_predicate_type`).
  - 26.2, test side (~23): every `EntityType.<MOB>` constant (26.2 split registration, like `BlockIds`/`ItemIds`),
    `BlockPos.getCenter()`, `Minecraft.setScreen`/`.screen` (now `Minecraft.gui`), `I18n.exists`, Fabric client
    GameTest `getClientLevel()`.
  - 26.3, mod side (23): all of 26.2's plus GUI rendering (`RenderPipeline`, `RenderPipelines.GUI_TEXTURED`, every
    `blit`/`blitSprite` overload: all three windows, `ScrollBar`, `WindowBackground`, `TabbedConfigScreen`); loot
    `LootContext.getParameter`/`getOptionalParameter` (Fortune, heads, smelting, spawners, `LootTool`);
    `RegistryCodecs`, `RegistryOps$RegistryInfo`; `ServerPlayer.drop(ItemStack,boolean)`; `LivingEntity.hurtMarked`;
    mixin target classes moved: `EnderMan` (+ `EndermanLeaveBlockGoal`), `HoeItem`, `FuelValues`.
  - **Split rule verdict:** 26.2 needs 8 adapters, 26.3 23 (16 of them also differ from 26.2), against a limit of ~6,
    and the GameTest jar can't run on either without as many again. So **each 26.x line is its own band**, compiled
    from the shared source like 26.1.2 (the fallback the plan named). Owner 2026-10-15: support all three lines.
- [ ] ~~12D Adapters inside band B~~ superseded by the 12C verdict: no runtime adapters, no `VersionedMixins`. Each
      line compiles its own jar, differences as in 12B (decisions.md "12B: how band differences are written").
- [x] 12D **Band B2, node 26.2** (as 12B did for 26.1.2): `versions/26.2/` (Fabric API 0.161.0+26.2, YACL
      3.9.7+26.2-fabric, Mod Menu 20.0.3, `run_root=run/26.2`, range `>=26.2 <26.3-`); compile errors → replacements /
      `//? if >=26.2` conditions / `port/` adapters; mixin audit against 26.2's decompiled source; server + client
      GameTests green on all three nodes; `SaveFormatGameTest` reads 26.1.2 saves. Start list = 12C findings (8 mod,
      ~23 test). Exit: `./gradlew build` green on 1.21.11, 26.1.2, 26.2; client GameTests on each.
      **Blast radius:** source conditions + a new node on `port/26x`; 1.21.11 and 26.1.2 jars must stay as they are
      (jar entry diff before/after). **Rollback:** `git revert` the 12D commits.
  - [x] D1. `versions/26.2/gradle.properties` + settings.gradle `versions '1.21.11', '26.1.2', '26.2'`. Check
        Stonecutter's `>=26.1` conditions and replacements now also apply to 26.2 (they should: same direction).
        CI on `port/26x` before 12D (dispatched): green, build + gitleaks.
  - [x] D2. `:26.2:compileJava`, `compileClientJava`, `compileGametestJava`: write every error group here, fix behind
        `//? if >=26.2` (adapter in `port/` past ~3 callers). After each group: all three nodes compile.
        Done 2026-10-15, no new warnings (26.2's = 26.1.2's, all pre-existing in GameTests):
        - Same code everywhere: `CollectedSpawners.mobOf` via `BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier
          .tryParse(id))` (what `EntityType.byString` did); tests `Vec3.atCenterOf(pos)`, `Language.getInstance().has`.
        - `>=26.2` replacements: `advancements.CriteriaTriggers` → `advancements.triggers.`; `EntityType.<CONST>` →
          `EntityTypes.`, `BlockEntityType.<CONST>` → `BlockEntityTypes.` (+ the holder imports);
          `minecraft.getToastManager()` → `minecraft.gui.toastManager()`; tests `client.setScreen(` / `client.screen` →
          `client.gui.`.
        - `//? if >=26.2`: `ShieldBash` knockback takes the player's attack source (the Sulfur Cube reads it);
          `LastStandHud` hidden HUD = `client.gui.hud.isHidden()`; `TabbedConfigScreen` (YACL's bar is a `MenuTabBar`:
          `arrangeElements(int)`, render override `extractWidgetRenderState`).
        - GameTest helper `ClientWorlds.waitForChunksRender` (Fabric moved it to `getConnection()`; 8 callers).
  - [x] D3. Data that differs: `loot_table/beheading/heads.json` (entity predicate). Find how Stonecutter (0.9.8)
        handles per-version resources before choosing (per-node resource dir vs. generated file); record the choice.
        Done: `${entity_type_predicate}` in the 6 predicates, expanded by processResources like `fabric.mod.json`
        (`type` / `minecraft:entity_type`, the key vanilla's `charged_creeper/root.json` uses on 26.2).
        `ModIdentityTest.beheadingPredicatesAreExpandedForThisNode` (fails with the file dropped from the list).
  - [x] D4. Mixin audit: `:26.2:linkCheck`-style pass is free (javap names, `@At` targets), then the 12B structural
        diff of every injected method between 26.1.2 and 26.2 decompiled sources; re-pick javap-chosen targets.
        Done 2026-10-15: `matrix/26.2` (band B2's control) `linkCheck` 0 problems. Body diff (scratch `mixindiff.py`):
        main 59 same / 16 changed, client 11 / 3; no changed line touches an injection target, no `ordinal` moved
        (`MinecraftMixin`'s slice still lands on `keyAttack.consumeClick()`). javap: bucket dispenser still `$3`,
        `AnvilMenu.lambda$onTake$0` still the damage lambda. No code change. Watch: 26.2's lava jump-out check
        (`isInShallowFluid(LAVA)`) in `aiStep`, Lava Walking's GameTests cover it.
  - [x] D5. Server GameTests on 26.2, then client GameTests (one node at a time); `SaveFormatGameTest` both ways.
        Server: `:26.2:build` 426 JUnit + 339/339 GameTests first try (log = the control's expected warnings only).
        Client: green, the same 23 screenshots (tab rows and Last Stand HUD checked by eye). The full build then hit a
        random test bug on 26.1.2 (merged XP orbs undercounted; `SpawnerGameTest` now sums value × count, gotchas).
        `./gradlew build`: 426 JUnit + 339/339 server GameTests on 1.21.11, 26.1.2 and 26.2.
  - [x] D6. Jar diff: 1.21.11 and 26.1.2 jars unchanged by the 26.2 conditions.
        Done (javap without line tables): only `CollectedSpawners` (the shared registry lookup) and `LastStandHud` (a
        local) differ in code; `ShieldBash`, `TabbedConfigScreen` (+ `$RowsBar`) in line numbers; `heads.json` identical. decisions/gotchas/state; commit; push;
        dispatch CI (`gh workflow run ci.yml --ref port/26x`: ci.yml only triggers on main and PRs).
- [x] 12E **Band B3, node 26.3:** same steps (Fabric API 0.163.0+26.3, YACL 3.9.7+26.3-fabric, Mod Menu 21.0.0).
      Biggest port so far: GUI rendering (`RenderPipeline`, `blit`), loot context API, moved mixin targets.
      Range `>=26.3 <26.4-` (26.4 is a snapshot, out of scope). Start list = 12C findings for 26.3 (23 mod-side; 16
      differ from 26.2 too). Exit: `./gradlew build` green on all four nodes; client GameTests on 26.3 (and on any
      node whose code a 26.3 change touched); 1.21.11 / 26.1.2 / 26.2 jars unchanged but for shared-code edits.
      **Blast radius:** source conditions + a new node + `matrix/26.3` control on `port/26x`; additive.
      **Rollback:** `git revert` the 12E commits (main untouched).
  - [x] E1. `versions/26.3/gradle.properties`, settings.gradle `versions ... '26.3'`, `matrix/26.3` control node.
        Check the 26.x direction replacements/conditions also hold for 26.3.
  - [x] E2. `:26.3:compileJava`, `compileClientJava`, `compileGametestJava`: error groups written here, fixed behind
        `>=26.3` replacements / `//? if >=26.3` / `port/` adapters (past ~3 callers). All four nodes compile after each.
        Done 2026-10-15 (28 main, ~16 client, 2 GameTest errors; no new warnings in main/client):
        - Same code everywhere: `InputConstants.MOUSE_BUTTON_LEFT` / `KEY_LCONTROL` for GLFW's (26.3 dropped
          `org.lwjgl.glfw` and renumbered: left button is **1** there, 0 before; 3 mixins + 5 client GameTests);
          `FortuneDrops.silkChangedDrop` takes the already-checked origin (no `getParameter`); the fuel GameTest drives
          a real furnace (hopper rule, fuel slot, burn, with a lit control furnace) instead of `FuelValues`.
        - `>=26.3` replacements: `context.getOptionalParameter(` → `getOptional(`; `RegistryCodecs` package +
          `homogeneousList` → `holderSet`; `.hurtMarked` → `.syncVelocity`; `EnderMan` → `Enderman`;
          `invulnerableTime = 0` → `setInvulnerableTime(0)`; `RenderPipeline` → `com.mojang.renderpearl.api.pipeline`
          (import and the `@At` descriptors).
        - `//? if >=26.3`: `ItemWideningHook` (lookup returns the getter), `KeepOnDeath` drop (`Prediction.SERVER_ONLY`),
          `ShieldDeflect.atShooter` (power `Vec3`), `ProjectileMixin` (two `deflect` overloads: the Vec3 one by
          descriptor), `ShieldsGameTest` deflect call.
  - [x] E3. Mixin targets that moved: `EnderMan` → `Enderman` (rename); `HoeItem` gone (hoes are plain items with a
        `block_transformer` component): `HoeItemMixin` wraps `BlockTransformer.transformBlock` on 26.3; `FuelValues`
        gone (fuel = `cooking_fuel` component): `FuelValuesMixin` before 26.3, `FurnaceMenuFuelMixin` (`isFuel`) +
        `FurnaceBlockEntityFuelMixin` (`getBurnDuration`, `has` in `canPlaceItem`) from 26.3, listed by the
        `fuel_component` template flag. `:matrix-26.3:linkCheck` 0 problems (it caught 5 stale `RenderPipeline`
        descriptors in `@At` strings first).
  - [x] E4. Data: any loot table / predicate that no longer parses on 26.3 (check the server log for load errors).
        Done: 26.3 reshaped loot conditions and functions (one `condition` / `modifier`, dispatched on `type`; damage
        tag predicates `#tag`). Two enchantments failed loudly; `heads.json` and the e2test tables loaded with their
        lists silently dropped (gotchas). processResources upgrades `loot_table/`, `enchantment/`, `predicate/`,
        `item_modifier/` JSON on 26.3+ (decisions.md "12E"); `ModIdentityTest.lootConditionsAreInThisNodesFormat`
        (mutation: upgrade off → red on 26.3).
  - [x] E5. Mixin audit: `:matrix-26.3:linkCheck` 0 problems, then the body diff 26.2 → 26.3 of every injected method.
        Done: body diff (scratch `mixindiff.py`, rewritten) 58 same / 28 changed, each read: real breaks were the three
        `setBlock` → `setBlockAndUpdate` wraps (`LavaFluidMixin`, `EndermanLeaveBlockGoalMixin`,
        `FallingBlockEntityMixin`, conditioned) and `hurtServer`'s cooldown field (`damageCooldownTime`, replacement);
        `deflect` overloads handled in E2. javap: bucket dispenser still `$3`, `AnvilMenu.lambda$onTake$0`, grindstone
        `$2/$3/$4` unchanged; `MinecraftMixin`'s slice still lands on `keyAttack`; `anyMatch` still in `getOffer`.
  - [x] E6. Server GameTests on 26.3, then client GameTests (screenshots checked against 26.2's).
        **Fixed 2026-10-19:** confirmed: the screenshot took 208 ms (gotchas.md 2026-10-19). `waitTick` after both tall
        screenshots; "back up" asserts exactly `bottom - STEP`. Green on all four nodes. Screenshots vs 26.2 / 26.1:
        windows and tooltips match (< 1% pixels, world background aside). 26.3 alone showed a leftover hover (a
        table-row tooltip over Enchant), so `table-rows` and `openAndClose` park the cursor at 0,0 and tick before the
        shot (now 0.06%). Config title shot: 26.2 opens with the first option focused + its description, 26.3 without.
        Vanilla `Screen.setInitialFocus` is identical on both (focus only if the last input was keyboard); an earlier
        test's simulated Ctrl counts as keyboard on 26.2, not on 26.3. Test history, not product: not chased.
        Server: 339/339 on 26.3. **Client: RED, one failure** (2026-10-15, session ended on the weekly token limit):
        `AssertionError: one notch down scrolled it: 0` (tall-tooltip scroll test). Suspect `MouseHandlerMixin`: 26.3's
        `onScroll` became public and checks `handle != 0L && handle == window.handle()`, so Fabric's simulated scroll
        may never reach `Screen.mouseScrolled`; or the scroll sign/units changed with 26.3's new input layer (the
        mouse buttons were renumbered). Log: rerun `./gradlew :26.3:runClientGameTest`; read 26.3 `MouseHandler.onScroll`
        and Fabric's `TestInput.scroll`. Screenshots not yet compared with 26.2's.
        2026-10-19: `MouseHandler.onScroll` still calls `Screen.mouseScrolled` (wrap intact); the anvil/table scroll
        tests pass on 26.3, and the tall-top screenshot shows the tooltip at its top (vanilla 26.3 would put y < 0), so
        placement + scroll hooks both run. Hypothesis: `takeScreenshot` between the hover and the first notch takes
        > `TooltipScroll.STALE_NANOS` (200 ms) on 26.3, so the notch (or the next frame's `y()`) starts over. Test:
        `waitTick` after the screenshot + a temporary `E6DIAG` timing print (remove before commit). 26.2's screenshots
        are gone; compare with `matrix/26.1`'s (same 23, per 12D).
  - [x] E7. `./gradlew build` on all four nodes; jar diff of the other three before/after.
        Done: 427 JUnit + 339/339 server GameTests on 1.21.11, 26.1.2, 26.2, 26.3. Jar diff vs HEAD (baseline built in a
        scratch worktree; scratch `jardiff.py`): on the three older nodes only `FortuneDrops` differs in code (shared
        edit); 9 classes in line numbers; data, mixin configs, client classes byte-identical, so their client GameTests
        weren't rerun.
  - [x] E8. decisions/gotchas/state; commit; push `port/26x`; dispatch CI.
        Owner 2026-10-19: the 12E work was pushed as `a489e0f temp`; leave it (no history rewrite / force-push), commit
        the E6 fix and docs on top, and name a489e0f as the 12E node/mixins/data/tests work in the commit body.
        Done: 6078afd pushed; CI run 39330250047 green (build + gitleaks).
- [x] 12F **Scripts and process:** `scripts/` task that builds every node and runs the matrix with a per-node report.
      CI: every node's build + the matrix's link checks and server GameTests. AGENTS.md and state.md: "done" = green on
      every node and every matrix version.
      Owner 2026-10-19: (1) CI runs `linkCheck` on **every** matrix node, server GameTests only on the versions a jar
      claims without being built for (26.1, 26.1.1; the 26.1.2/26.2/26.3 controls repeat the node builds). (2) Client
      GameTests stay local-only (headless xvfb/Mesa = a later follow-up, not 12F). (3) One aggregate Gradle task that CI
      also calls, so local and CI can't drift, plus a thin `scripts/verify-all.sh` that wraps it and prints the report.
      **Not doing:** client GameTests in CI, release.yml (12G), new matrix versions.
      **Blast radius:** build.gradle / settings.gradle / matrix.gradle tasks, ci.yml, docs; additive. **Rollback:**
      `git revert` the 12F commits on `port/26x`.
  - [x] F1. Read `matrix/matrix.gradle` and the root build. Role needs no new property: a matrix node is its jar's
        **control** iff `minecraft_version == matrix_jar_node`, else **claimed** (derived, can't go stale). The root
        script is `stonecutter.gradle` (`build.gradle` is the per-node central script). Node `runGameTest` (Loom
        `configureTests`) writes no report and has no zero-tests check; the matrix's does.
  - [x] F2a. `gradle/gametest-report.gradle`, applied by `build.gradle` and `matrix.gradle`: the `gameTest` run writes
        `build/gametest-report.xml`; `checkGameTestReport` (moved from matrix.gradle) fails on no report / 0 cases /
        any failure. Version nodes gain the zero-tests guard. Verify: `:26.3:runGameTest` + `:matrix-26.1:runGameTest`
        green and each writes its report; mutation: report check fed an empty suite fails.
        Done 2026-10-19: 339/339 + report on 1.21.11 (FAPI 0.141.6 honours report-file), 26.3, matrix 26.1; an
        empty suite fails `:26.3:checkGameTestReport` ("has no test cases"), the real one passes (mutation, restored).
  - [x] F2b. `verifyAll` in `stonecutter.gradle`: every version node's `build` + every matrix node's `linkCheck` +
        `runGameTest` on claimed matrix nodes. Fails closed at configuration if a matrix node's properties lack
        `minecraft_version` / `matrix_jar_node` or name a jar node that isn't a version node. JUnit `MatrixLayoutTest`:
        settings.gradle's matrix list = `matrix/*/` dirs, each with both keys, jar node in settings' versions.
  - [x] F3. `verifyReport` (root): one line per version node (JUnit n/failed, GameTests n/failed) and matrix node
        (link-check summary line, GameTests if claimed), from the result files; `-PverifySince=<epoch ms>` marks older
        files "stale (didn't run)". Separate task, not a finalizer (a finalizer doesn't run when a dependency fails).
        `scripts/verify-all.sh`: `verifyAll --continue`, then `verifyReport`, exit = verifyAll's.
        Done 2026-10-19. The fail-closed key check moved into `matrix.gradle` (the root copy was unreachable, gotchas);
        mutation: blank / `9.9` / `matrix-26.2` refused. `MatrixLayoutTest` (+ test inputs: it was UP-TO-DATE at first);
        mutation: a stray `matrix/99.9/` turns it red. `scripts/verify-all.sh` local: exit 0, 429 JUnit + 339/339 on
        four nodes, link checks 0 on five, 339/339 on claimed 26.1 / 26.1.1; with a bad option: exit 1, "FAILED".
        CI: `verifyAll --continue`, report into the job summary (`if: always()`), matrix JDKs read too.
  - [x] F4. ci.yml `build` job calls `verifyAll` (keeps the jar artifact upload); header comment updated.
  - [x] F5. AGENTS.md / state.md "done" definition: green on every node + `verifyAll`; README/wiki build docs.
  - [x] F6. Run `verifyAll` locally, commit, push, dispatch CI; CI green.
        Done: 90a9f1c + 436105d pushed; CI run 39333349438 green (verifyAll ran: 4 nodes, 5 link checks, 2 claimed).
- [x] 12G **Release:** `release.yml` builds every node and attaches one jar per band, named from its proven range
      (`+1.21.11`, `+26.1-26.1.2`, `+26.2`, `+26.3`); `ReleaseDocsTest`, CHANGELOG and README list the supported
      versions per jar; wiki Installation page gets the version table. First band release = 1.1.0.
      Owner 2026-10-19: land port/26x on main by **PR + squash-merge**; tag **v1.1.0 as soon as 12G is green** (no
      wait for an in-game check); jar names `enchanting2-1.1.0+<band>.jar` (`+1.21.11`, `+26.1-26.1.2`, `+26.2`, `+26.3`).
      **Not doing:** Modrinth/CurseForge, client GameTests in CI, deleting `port/26x` after the merge (owner's call).
      **Blast radius:** G6–G8 are outward-facing (main, a public tag, a public release). release.yml never replaces a
      release; a bad release is fixed by a new version, not by deleting. **Rollback:** before the tag: `git revert` the
      squash commit on main. After: publish 1.1.1; the release itself stays (deleting it = owner, explicitly).
  - [x] G1. Band label derived in build.gradle: the node's `minecraft_version` + every `matrix/*/` whose
        `matrix_jar_node` is this node; label = lowest, or `lowest-highest`. `version = mod_version+label` (jar name
        and fabric.mod.json). Test (ModIdentityTest, node passed as a test system property): the shipped version is
        `mod_version+label` computed in Java from the same files, and `minecraft_dependency` admits every version in
        the label (forms `~X` and `>=A <B-`; any other form fails the test, fail closed).
  - [x] G2. release.yml: every node. JDKs read like ci.yml; `verifyAll` instead of `build`; exactly one mod jar +
        one sources jar per node (count = nodes, else refuse); title "Enchanting 2 <v>"; notes end with a jar → Minecraft
        table. Dry-run the meta/collect shell locally with a fake tag and GITHUB_OUTPUT.
  - [x] G3. CHANGELOG `[1.1.0] - 2026-10-19` (Added: 26.1–26.3, one jar per line), `mod_version=1.1.0`; README +
        wiki Installation: the jar table; ReleaseDocsTest: README's table lists every node's jar label.
        Done 2026-10-19: jars `enchanting2-1.1.0+{1.21.11,26.1-26.1.2,26.2,26.3}.jar`, fabric.mod.json version the
        same. Test helper `Bands` (shared by ModIdentityTest + ReleaseDocsTest). Collection moved to
        `scripts/release-collect.sh` (dry-run locally: 8 files + notes table; refuses a mislabeled jar and a second
        jar, exit 1). Meta step dry-run: v1.0.1 passes, v9.9.9 refused. Mutations: unbanded version → ModIdentityTest
        red; README without the 26.3 row → ReleaseDocsTest red. README + `versions/*/gradle.properties` are test inputs.
  - [x] G4. `scripts/verify-all.sh` green; client GameTests not needed (no client code); commit; push; CI green.
  - [x] G5. PR port/26x → main (gh), CI on the PR green, squash-merge (one `feat:` commit).
  - [x] G6. Tag `v1.1.0` on main's squash commit, push the tag; release.yml green; the release has 4 + 4 jars with the
        right names; download one and check its fabric.mod.json version + depends.
  - [x] G7. state.md / decisions.md; 12H stays as process.
  Result 2026-10-19: verify-all 432 JUnit + 339/339 on four nodes, matrix clean; PR #1 CI green; squash-merged as
  9246bc4; CI + Wiki green on main; annotated tag v1.1.0 → release.yml green; release "Enchanting 2 1.1.0" (Latest)
  has the 4 jars + 4 sources jars; the downloaded +26.1-26.1.2 jar says version 1.1.0+26.1-26.1.2, minecraft
  >=26.1 <26.2-, java >=25.
- [ ] 12H **New Minecraft versions from here on (process, not code):** a new version gets a matrix node on the nearest
      band's jar: `linkCheck` first (seconds), then server + client GameTests. Green → widen that band's range, no new
      jar. Red → a new node (band). Dropping a band (mcMMO "raising the floor") is the owner's call; it becomes a frozen
      release, like mcMMO's 2.2.049.

**Risks:** rendering and `GuiGraphics` changes in the three window screens (26.2 was "primarily rendering"); YACL's
API across its per-version builds; enchantment component or registry changes in `MODIFY` hooks and `LevelCeilings`;
GameTest API changes in the gametest source set (it must also compile against the floor); 26.4 when it goes stable.

## Client GameTest flake

- [x] "Network synchronizer in invalid state": solved 2026-10-19 (session section above, F1–F2c; gotchas.md
      2026-10-19). Not our packets: Fabric 4.3.5's own race at world close.

## Owner checks (open)

In-game checks only the owner can do, collected from Phases 1–11f. Each one was GameTested where a test could reach it;
these cover look, feel and real clients. Superseded checks (the old side panels, `anvil.show_preview_panel`) are gone.

- [ ] **1.0.1 jar with Elytra Slot (11f):** put `enchanting2-1.0.1.jar` in the Prism instance and hold jump with the
      Flight elytra in Elytra Slot's slot (thrust, Speed, the durability bar).
- [ ] **Basics (1.6, 2B.9, 2C.7, 4A.7, 3B.6):** `runClient` to the title screen; a singleplayer world opened to LAN,
      quit, reopened. A **vanilla** client joining the dev server (`localhost:25566`) gets a disconnect screen naming
      Enchanting 2. Config screen from the title screen and in a world: an SP edit persists and makes a `.bak`, a LAN
      guest sees it read-only, a vanilla server shows the note. A client-side `minecraft:fortune: {
      max_level_override: 1 }` against the dev server still shows Fortune V in the creative books tab (then remove it).
      The XP bar at level ~1,000,000.
- [ ] **Vanilla servers:** the vanilla anvil, grindstone and table screens stay vanilla there.
- [ ] **Windows (10l, 10o, 11b, 11c):** table, anvil and grindstone windows (look, wheel and thumb scrolling, a long
      breakdown, grindstone hover tooltips, the price label, the ready Enchant button reads as clickable); armor and
      offhand slots in the table; Mending's row price and its tooltip note; an anvil after many uses; with a recipe
      viewer installed.
- [ ] **Config screen:** tab rows (11d); "Fortune" and its warning tooltip; "Enchantments" (tick boxes, names, the
      remote "kept on the server" label); "Enchantment effects"; the Silk Touch spawners toggle; max level / cost
      fields and their notes, a raised max after a restart (Efficiency 10); curse discount fields and the Weight cap;
      the "Display" tooltip toggles; the "Last Stand in PvP" toggle.
- [ ] **Tooltips:** Ctrl descriptions condensed, as "Name: description"; with Enchantment Descriptions installed,
      only one set of lines; the scroll line on a too-tall tooltip (also in the creative tab); the new names (curses in
      red).
- [ ] **Feel, by enchantment:** Fortune V axe on a tree and on a placed log · Health V hearts · Vision at night (no
      flicker, gone when removed) · TrueShot arrow · a collected spawner's tooltip · Infinity bucket bar and pouring ·
      Step Assist · Sure Footing III on soul sand and powder snow · Weight I vs V (50% cap) · Calm Gaze, Phantom Ward,
      Clarity I vs III (Warden) · Lava Walking (lake, flowing edge, sneak to sink, getting out, a fall) · Snow Walker
      on powder snow · Spiked V, Bulwark III, Steadfast III, Deflect III (skeleton, blaze, ghast) · Steady Stance,
      Shield Bash III, a plain shield can't attack while raised · Magnetic (full inventory spills, a sword kill),
      Ender Link · Keep on Death (with keepInventory, with Vanishing) · Area Mining (ores stay, wall vs floor,
      sneaking, a shovel) · Replant (wheat, cocoa) · Tiller III · Quick Strike V (with Vampirism) · Last Stand (save,
      sound, HUD, with a totem) · Speed V vs plain over a long flat, a rocket, no rubber-banding · Flight I / III / V
      (is I too close to V?) · Double Jump (land, cliff, none in water or on a ladder, elytra on the second press).

## Owner housekeeping (outside the code)

- [ ] Rotate the GitHub PAT, the Context7 key and every `.env` password that was on GitHub (11e). The private
      `enchanting-2-archive` still has them; delete it once rotated.
- [x] `docs/CURSEFORGE.md`: deletion committed (owner 2026-10-14). Restore: `git checkout 026622e -- docs/CURSEFORGE.md`.

---

## Done

Phases 0–11f, 2026-10-06 → 2026-10-14. Released as v1.0.0 and v1.0.1. The step-by-step notes, test counts and
mutation checks of every finished phase were trimmed from this file on 2026-10-14. Recover them with
`git show fd3504c:TODO.md`. The *why* of each phase is in `.agent/memory/decisions.md`.

| Phase | What |
|---|---|
| 0–2 | Design resolved; scaffold; config system (core, server sync, client screen) |
| 3–5 | Uncapped XP curve; rules engine (max levels, conflict families); cost model |
| 6, 6b, 7 | Anvil, grindstone, enchanting table (server logic, payloads, screens) |
| 8–9 | Conflict effects + Fortune on all tools; acquisition flags, weights, treasure |
| 10a–10o | New enchantments and curses, applicability, descriptions, level ceilings, balance, windowed screens |
| 11–11f | Docs, owner fixes, public repo + CI + releases, Elytra Slot compat |
| — | Destructive-action doctrine (AGENTS.md, `guard-destructive`, `.claude/settings.json`) |
