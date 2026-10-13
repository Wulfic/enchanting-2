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

# Enchanting 2 — v1 roadmap (Tier 2)

Spec: [docs/DESIGN.md](docs/DESIGN.md). Open questions Q1–Q14 are all resolved. Each phase is
one or more reviewable commits, and the next phase doesn't start until the current one
builds green with tests. Before a phase starts, expand its steps into concrete file-level
steps here.

**Cross-cutting (owner, 2026-10-06):** the mod must be safe to add to or remove from an existing
world, and it must work in singleplayer (integrated server, LAN) and on dedicated servers. Every
phase's GameTests and dev-server checks cover this. See DESIGN §2a.

**Not doing (v1):** other loaders or MC versions, vanilla-client support, Mending repair
changes, full per-enchantment GUI editing. See DESIGN §10.

**Blast radius (whole roadmap):** additive only. The repo has no code yet. The only edits to
existing files are `.gitignore` (add Gradle/IDE ignores) and `README.md`. Mod config files
written at runtime live in a dev run directory (`run/`, gitignored).

**Rollback (whole roadmap):** each phase is its own commit range, so `git revert
<first>^..<last>` undoes it. Nothing outside the repo is touched until Phase 11 (publishing),
which is gated separately.

## Phase 0 — Resolve the plan

- [x] 0.1 Turn the rough plan into `docs/DESIGN.md` and flag its contradictions as OPEN
- [x] 0.2 Owner answered Q1–Q11 → folded into DESIGN.md
- [x] 0.3 Choices recorded in `.agent/memory/decisions.md`
- [x] 0.4 Q12 resolved: lapis at the table only; the anvil never costs lapis

## Phase 1 — Project scaffold
**Goal:** `./gradlew build` produces a jar that loads in a dev client and a dedicated server
and logs one line.
**Pinned 2026-10-06** (fabric meta, maven-metadata, Modrinth, FabricMC/fabric-example-mod@1.21.11):
MC 1.21.11 · Java 21 · Gradle 9.7.1 · Loom 1.18.2 (**plugin id `net.fabricmc.fabric-loom-remap`**) ·
Loader 0.19.5 · Fabric API 0.141.6+1.21.11 · Mod Menu 17.0.1 · YACL 3.8.1+1.21.11-fabric ·
Jankson 1.2.3 · JUnit 5.11.4. Add `fabric-loader-junit` when a unit test first needs MC classes
(plain JUnit hits an IllegalAccessError there; see ../mcMMO-Singleplayer build.gradle).
**Blast radius:** additive. Only existing file edited is `.gitignore` (append). Wrapper files come
from the example mod; the wrapper jar is checked against Gradle's published SHA-256.
**Rollback:** `git revert <phase-1 commits>`; before commit, delete the new untracked files listed below.

- [x] 1.1 Versions verified (above) → `gradle.properties`
- [x] 1.2 `settings.gradle`, `build.gradle`, `gradle.properties`, `gradlew`, `gradlew.bat`,
      `gradle/wrapper/*` (jar SHA-256 verified). Toolchain from `java_version=21`.
- [x] 1.3 `src/main/resources/fabric.mod.json`, `enchanting2.mixins.json`,
      `src/client/resources/enchanting2.client.mixins.json`,
      `assets/enchanting2/icon.png` (128×128 from `Logo/`, via ffmpeg)
- [x] 1.4 `src/main/java/com/wulfnet/enchanting2/Enchanting2.java` (logs one line),
      `src/client/java/.../Enchanting2Client.java`; `.gitignore` adds `.gradle/ build/ run/ out/ *.iml`
- [x] 1.5 JUnit: `src/test/java/.../ModIdentityTest.java` (fails if the mod ID constant and
      fabric.mod.json disagree). GameTest source set via `fabricApi.configureTests`
      (`src/gametest/`, mod `enchanting2-gametest`), one trivial server GameTest.
- [~] 1.6 `build` green: 3 JUnit + 4 GameTests. The GameTest run boots a dedicated server (KnotServer)
      with the mod loaded and logs "Enchanting 2 initialized", which covers the server side.
      **Open:** `runClient` to the title screen. The owner runs it (it opens a window on their desktop).
      Also: create a singleplayer world, open to LAN, quit, reopen (integrated-server lifecycle).
- [~] 1.7 DEFERRED (owner, 2026-10-06): no CI for now. Commits go straight to main and get pushed.
      Revisit `.github/workflows/build.yml` later.
- [x] 1.8 Vanilla registry checked against DESIGN §8, from `data/minecraft/enchantment/*.json`
      in the 1.21.11 jar. All 43 IDs and vanilla maxes match §8, including `minecraft:lunge`
      (effect `post_piercing_attack`). The Data Attachment API (v1.8.48 in FAPI 0.141.6) supports
      persistent chunk attachments (`SerializableChunkDataMixin`). Findings → decisions.md.
  - [x] 1.8a `src/gametest/.../VanillaEnchantmentIdsGameTest`: every ID in the §8 table resolves
        in the live registry. This guards the `EnchantmentEvents.MODIFY` keys used in Phase 4.
- [x] 1.9 Dev server (owner request: "use a dev server for testing the mod properly")
  - [x] 1.9a `build.gradle` `loom.runs`: client → `run/client`, server → `run/server` with
        `--nogui --port 25566`, so it never touches 25565 (the owner's other instance)
  - [x] 1.9b `acceptDevServerEula` task: writes `run/server/eula.txt` **only if absent**. Same EULA
        acceptance as `fabricApi.configureTests { eula = true }`.
  - [x] 1.9c `scripts/dev-server-smoke.sh`: refuses if 25566 is in use. Boots `runServer`, waits for a
        `latest.log` newer than its start marker that contains `Done (`. Asserts the
        "Enchanting 2 initialized" line and no ERROR from enchanting2, pipes `stop`, and times out
        with a non-zero exit. Never kills java by name.
- [x] 1.10 Add/remove safety probe on the dev server (owner requirement, see the new DESIGN §2a).
      Throwaway datapack `probe:test_ench` → chest with a sword carrying it → stop → move the
      datapack out of `world/datapacks` → boot → `data get block` on the chest. Records whether
      vanilla **deletes** items whose enchantment registry entry has vanished. The answer
      decides how §9's new enchantments are stored. The probe world is throwaway (`run/server/world`).
      **Result:** items survive. Only the unknown enchantment is dropped (WARN). See gotchas.md and DESIGN §2a.
      Still to check: player inventory, at the 1.6 `runClient` check.

## Phase 2 — Config system (Tier 2: three stages, one commit each)
**Goal:** validated JSON5 config, owned per `MinecraftServer`, reloaded on `/reload`, synced
server→client, editable from Mod Menu by the SP/LAN host only.
**Owner answers (2026-10-06):** in-game editing is for the SP/LAN host only. On a dedicated server
the screen is read-only, ops included, and there's no C2S write packet. A running server picks up
file edits on vanilla `/reload`. Removal losing `enchanting2:` enchantments is accepted and documented.
**Approach:** pure-Java core (`config/`, no MC imports) so the JUnit tests run without
`fabric-loader-junit`. Then thin server wiring, then the client screen.
Each later phase adds **its own** config section and keys along with the code that reads them.
Phase 2 ships only the framework plus the `experience` section (DESIGN §3, consumed in Phase 3).
**Not doing:** keys for Phases 4–10 (they land with their behavior. `silk_fortune_mode` → Phase 8,
registry "requires restart" hints → Phase 4). No `client.json5` until a phase needs a client-only key.
No op editing over the network. No file watcher. No migration code (schema 1 is the first).
**Blast radius:** the code writes files in `config/enchanting2/`, so its guards are part of the work:
- the loader writes **only when the file is absent**. It never rewrites an existing file, including
  invalid or future-version ones.
- the GUI save (SP host) copies the current file to `<name>.json5.bak`, checks the copy byte for byte,
  then writes a temp file and does an atomic move. It refuses to save over a `schema_version` newer
  than it understands.
- an unparseable file on `/reload` keeps the previous in-memory config (WARN). It doesn't silently
  fall back to defaults mid-game.
Repo-side: additive. The only edits to existing files are `build.gradle`, `gradle.properties`,
`fabric.mod.json`, the mixin json and `Enchanting2*.java`.
**Rollback:** `git revert <stage commit>` per stage. Runtime: the `.bak` restores the previous config
(`mv run/client/config/enchanting2/common.json5.bak run/client/config/enchanting2/common.json5`).

### Stage 2A — Core (pure Java, no MC classes) → commit  ✅ 2026-10-06
- [x] 2A.1 `build.gradle`: `implementation` + `include` `blue.endless:jankson:1.2.3` (Maven Central, checked
      maven-metadata). The jar nests `META-INF/jars/jankson-1.2.3.jar`.
- [x] 2A.2 `config/ConfigIssue`, `config/ConfigReader` (`readInt`/`readBool`/`section`/`reject`, recursive
      unknown-key report). `enumValue` left out: no enum key exists yet (it arrives with `silk_fortune_mode`, Phase 8).
- [x] 2A.3 `config/ExperienceSection` (4 keys; the two undefined ones were dropped by the owner), `config/CommonConfig`,
      `config/ConfigCodec` (one interface per file, so `ConfigFiles` owns schema_version and disk access).
- [x] 2A.4 `config/EnchantmentOverrides` (`overrides: { "ns:path": {} }`, namespace required, `unknownIds(Predicate)`).
- [x] 2A.5 `config/ConfigFiles`: `load` → `LoadResult(config, issues, Source FILE|CREATED|CREATE_FAILED|UNREADABLE,
      futureSchema)`, `writeDefaultsIfAbsent`, `saveWithBackup` → `SaveOutcome`. Save also refuses an unparseable
      file (fail closed). Load/save refuse a path whose name isn't the codec's file.
- [x] 2A.6 `ConfigParseTest` (20) + `ConfigFilesTest` (12): every case listed in the plan, plus BOM, null, wrong-type
      section, create failure, and wrong-file-name refusal (no disk change).

### Stage 2B — Server wiring → commit  ✅ 2026-10-06
**Owner (2026-10-06):** clients on a different Enchanting 2 network protocol are disconnected with a message
naming both protocol numbers (not an exact mod-version match, and not "no check").
**Researched (Fabric networking 5.1.6 sources, mapped MC jar):** `ServerConfigurationConnectionEvents.CONFIGURE` +
`ServerConfigurationNetworking.canSend(handler, type)` → `handler.disconnect(...)` is the documented pattern. It runs
on netty threads, so the holder field is `volatile` and the config is an immutable record.
**Scope change:** a minimal client receiver moves here from 2C.2. Without one, `canSend` is false for every modded
client and they would all be disconnected. The resolver and the screen stay in 2C.
- [x] 2B.1 `network/NetworkProtocol` (pure Java): `VERSION = 1`. Every payload ID is `enchanting2:v<N>/<name>`.
      `classify(Collection<String> clientChannels)` → MATCH / VANILLA (no `enchanting2:` channel) /
      MISMATCH(client versions), plus the disconnect text. The text is literal, not translatable: a vanilla client has
      no lang file for us. JUnit: `NetworkProtocolTest`.
- [x] 2B.2 `config/ServerConfigHolder` duck interface + `mixin/MinecraftServerMixin` (a `volatile` field) +
      `config/ServerConfigs` (`get(server)`, never null: an unset holder logs an ERROR once and returns defaults).
- [x] 2B.3 Folded into `config/ServerConfigs` (one class, no separate lifecycle class): `SERVER_STARTING` → `ConfigFiles.load(configDir/enchanting2/common.json5)`.
      `END_DATA_PACK_RELOAD` (success only) → reload. UNREADABLE keeps the previous config + WARN. Anything else
      replaces it (bad keys default, per the 2A tests). Then re-send to every player who `canSend`.
- [x] 2B.4 `network/ConfigSyncPayload` (`enchanting2:v1/config_sync`): strict-JSON text, `stringUtf8(262144)`.
      Registered for configuration S2C and play S2C. `ConfigFiles.renderStrict` / `parseSynced` for the text.
- [x] 2B.5 `network/ServerNetworking`: `CONFIGURE` → classify → disconnect, or send the config in the configuration
      phase. A client therefore holds the config before play starts. It is sent again at play `JOIN`, so a `/reload`
      during a player's configuration phase can't leave them stale.
- [x] 2B.6 Client: `client/ClientConfigState` (server copy, cleared on disconnect) + configuration and play receivers
      in `Enchanting2Client`. A payload that fails to parse logs a WARN and keeps the previous copy.
- [x] 2B.7 GameTests (`ConfigWiringGameTest`): the holder is set and equals `ConfigFiles.load` of the file. The
      payload round-trips through `FriendlyByteBuf`, and oversize text fails decode. One sequence on the shared
      file: bad value + `/reload` → that key defaults and a marker key changes. Unparseable + `/reload` → the
      marker is kept. The test then restores the original text. The file lives in the GameTest run dir, which
      `clearRunDirectory` wipes.
- [x] 2B.8 Dev server: `scripts/dev-server-smoke.sh` gains a `@sh <cmd>` line directive.
      `scripts/checks/config-reload.txt`: first boot writes defaults. A bad value + `reload` → a WARN names the key.
      Garbage + `reload` → an ERROR, and the previous config is kept. Then the file is restored. It touches only
      `run/server/config/enchanting2/common.json5` (generated, reproducible: delete it and boot).
      Also `@expect <regex>` and `@allow-error <regex>`. Passed 2026-10-06; the file was restored byte for byte.
- [ ] 2B.9 Owner (with 1.6/2C.7, in `runClient`): join the dev server (`localhost:25566`) with a **vanilla** client →
      the disconnect screen names Enchanting 2. The join check is unit-tested (`NetworkProtocolTest`), but the live
      kick needs a real vanilla client, which the GameTest harness can't provide.
**Blast radius:** additive code. Runtime writes go only to `config/enchanting2/common.json5`, and only if absent (2A guards).
Test and dev-check overwrites stay inside throwaway run dirs.
**Rollback:** `git revert <2B commit>`. Dev run dir: `rm run/server/config/enchanting2/common.json5`, then boot to regenerate.

### Stage 2C — Client → commit  ✅ 2026-10-06 (except 2C.7, owner)
**Owner (2026-10-06):** the title screen (no world) edits the local file. All SP worlds share one
`config/enchanting2/common.json5`, with no per-world override.
**Researched (jars from the repos, javap):** Mod Menu 17.0.1 at `maven.terraformersmc.com/releases` (`ModMenuApi`,
`ConfigScreenFactory`). YACL `3.8.1+1.21.11-fabric` at `maven.isxander.dev/releases`, mod id
`yet_another_config_lib_v3`. It nests its libraries (`META-INF/jars`), and only its `dsl`/`config.v3` packages use
Kotlin, so `transitive = false` is safe if we use the Java builder API only.
**Not doing:** per-world config, a C2S write path, a fallback screen without YACL, client GameTests for the screen.
**Blast radius:** additive code. The screen's save writes `config/enchanting2/common.json5` only through
`ConfigFiles.saveWithBackup` (2A guards: `.bak` verified first, refuses unparseable/future files).
`modLocalRuntime` also puts YACL on the dev-server and GameTest classpath (environment `*`). Mod Menu is client-only.
**Rollback:** `git revert <2C commit>`. Runtime: `mv <config>/enchanting2/common.json5.bak <config>/enchanting2/common.json5`.
- [x] 2C.1 `build.gradle`: the two repos, `modCompileOnly` + `modLocalRuntime` for both, `transitive = false`.
      `fabric.mod.json`: `suggests` both, `modmenu` entrypoint.
- [x] 2C.2 `config/ConfigView` (pure Java, main set so plain JUnit reaches it): `resolve(Connection NONE|HOSTING|REMOTE,
      Supplier<LoadResult> localFile, Supplier<CommonConfig> hostedLive, Optional<CommonConfig> serverCopy)` →
      `Mode` + values. NONE → the file (editable). HOSTING → the live server values (editable). A file that doesn't
      parse or is from a newer schema locks NONE/HOSTING read-only with a reason. REMOTE → the server copy (read-only),
      or VANILLA_SERVER if none. REMOTE never calls `localFile`.
- [x] 2C.3 JUnit `ConfigViewTest`: every mode. The REMOTE suppliers throw, which proves a remote session never reads or
      creates the local file. That covers the old 2.6 ("a client-side edit can't change server behavior") together
      with there being no C2S config payload.
- [x] 2C.4 `client/ConfigEditing` (MC side): detect the connection, `save(CommonConfig)` → `saveWithBackup`. When
      hosting, `server.execute(() -> ServerConfigs.reload(server))` re-reads the file and re-syncs LAN guests (the
      2B-tested path). A non-SAVED outcome → a `SystemToast` + the existing ConfigFiles log line.
- [x] 2C.5 `client/ModMenuIntegration` (a factory only when `isModLoaded("yet_another_config_lib_v3")`, otherwise
      Mod Menu's default no-screen) → `client/ConfigScreen` (YACL builder, `available(false)` when read-only, a label
      with the mode's reason, ranges from `ExperienceSection` constants). `assets/enchanting2/lang/en_us.json`.
- [x] 2C.6 `./gradlew build` green (56 JUnit incl. `ConfigViewTest` 6 + `ConfigLangKeysTest` 2, 8 GameTests).
      `scripts/dev-server-smoke.sh scripts/checks/config-reload.txt` passes with YACL loaded on the server, and the file was
      restored byte for byte. The REMOTE tests were mutation-checked: making REMOTE read the file fails 2 of 6.
- [ ] 2C.7 Owner check in `runClient` (folded into the 1.6 check): the screen opens from the title screen and in a
      world. An SP edit persists and makes a `.bak`. A LAN guest sees read-only. A vanilla server shows the vanilla note.

## Phase 3 — Experience curve (two stages, one commit each)
**Goal:** DESIGN §3 acceptance list passes.
**Owner (2026-10-06):** there is no admin level cap. The only limit is `Integer.MAX_VALUE`, and a level never wraps
negative. The `maximum_level` key (added in 2A) is removed.
**Audit (javap of the mapped 1.21.11 jar, 2026-10-06):** all curve math goes through `Player#getXpNeededForNextLevel`.
Its callers: `Player#giveExperiencePoints` (orbs, bottles, grindstone, `/xp add points`), `ServerPlayer#setExperiencePoints`
(`/xp set points`), `/xp query points` (`floor(progress × needed)`), and the client `ExperienceBarRenderer` (it draws the
bar only if the result is `> 0`). Everything else uses levels only and needs no change: `giveExperienceLevels` (anvil,
`/xp add levels`; already `IntMath.saturatedAdd`, so it never wraps), `setExperienceLevels`, `onEnchantmentPerformed`,
`getBaseExperienceReward` (death drop `min(7L, 100)`, stays vanilla per DESIGN), `restoreFrom`/`PlayerList.respawn`
(keepInventory copies the three fields), the XP packet, `Gui`, `EnchantmentMenu`, `AnvilMenu`, `PlayerPredicate`.
`totalExperience` is the score, not curve state. **Correction (3B.7):** vanilla does `clamp(total + amount, 0, MAX)` with
an `int` add, so at MAX it wraps negative and resets to 0. It doesn't clamp at MAX.
**Vanilla bug fixed on the way:** `112 + 9(L − 30)` overflows `int` near L ≈ 238.6M. The needed points go negative, and
the client hides the bar. The service clamps to `[1, Integer.MAX_VALUE]`.
**Approach:** one `@Inject(HEAD, cancellable)` on `getXpNeededForNextLevel` (not an `@Overwrite`, so other mods' injects
still apply). The config comes from the player's side: a `ServerLevel` reads `ServerConfigs.get(server)`; a client player
reads the server's synced copy, or the vanilla curve when there is none (vanilla server).
**Not doing:** rewriting `giveExperiencePoints`' float math, *unless* the lossless round-trip GameTest (3B.4) fails. In
that case it becomes its own written sub-step. No death-drop change. No `totalExperience` change. No admin cap.
**Blast radius:** additive code, plus removing one config key. A `common.json5` that still has `maximum_level` loads with an
unknown-key WARN and is never rewritten (2A guard). Nothing was released, so only dev run dirs have such files.
Player XP itself is never rewritten: the mixin changes only how many points the *next* level costs. Adding or removing the
mod keeps every stored level (DESIGN §2a).
**Rollback:** `git revert <3A commit> <3B commit>`.

### Stage 3A — Drop `maximum_level`, pure curve → commit  ✅ 2026-10-06
- [x] 3A.1 `config/ExperienceSection`: remove `maximumLevel` (3 components). `client/ConfigScreen`, `lang/en_us.json`.
      Tests that used `maximum_level` as an arbitrary key (`ConfigParseTest`, `ConfigFilesTest`, `ConfigViewTest`,
      `ConfigWiringGameTest`) switch to `flatten_after_level`/`fixed_points_per_level`, keeping each test's intent.
      DESIGN §3 + decisions.md entry.
- [x] 3A.2 `experience/ExperienceCurve` (pure, main set, no MC imports): `vanillaPointsToNext(level)` (long),
      `pointsToNext(section, level)` → int clamped to `[1, MAX]`, `totalPoints(section, level, pointsIntoLevel)`
      (saturating long), `fromTotal(section, total)` → `(level, pointsIntoLevel)` (binary search; total is monotonic).
- [x] 3A.3 `ExperienceCurveTest`: vanilla boundaries 0/15/16/29/30/31 against the javap'd formula; 30→31, 31→32, 49→50,
      99→100, 9999→10000 = 112; total at 30 = 1395, at L = `1395 + 112(L − 30)`; `flatten_after_level: 0` and
      `enabled: false`; overflow (vanilla at `Integer.MAX_VALUE` stays positive; totals use exact long math, never negative);
      round-trip `fromTotal(totalPoints(x)) == x` over a spread of levels including both sides of 30 and near MAX.
      Done: 12 tests, sums brute-force checked past the clamp level. Mutation: dropping the clamp fails 4.

### Stage 3B — Wiring → commit  ✅ 2026-10-06 (except the owner HUD check in 3B.6)
- [x] 3B.1 `experience/PlayerExperience` (main, MC side): `section(Player)` (server holder or client source) and
      `setClientSource(Supplier<ExperienceSection>)`. Default client source = the vanilla curve.
- [x] 3B.2 `mixin/PlayerMixin` (`getXpNeededForNextLevel` → `ExperienceCurve.pointsToNext`), registered in
      `enchanting2.mixins.json`. `Enchanting2Client` sets the client source from `ClientConfigState.serverCopy()`.
- [x] 3B.3 GameTests `ExperienceGameTest` (mock `ServerPlayer`, default config): needed = 112 at 30/31/49/99/9999;
      `/xp set points` path (`setExperiencePoints`) and query (`floor(progress × needed)`) above 30; points across the 30
      boundary land on the right level + points; spending levels from 40 → 25; level near `Integer.MAX_VALUE` + points →
      `MAX`, never negative, needed > 0.
      The 30 crossing failed (29 + 157 points → 30 + **49**, expected 50) until 3B.7. It's float drift, same root
      cause as 3B.4.
      Also: `ConfigWiringGameTest`'s reload test moved to its own environment `enchanting2:config_reload`, so it runs
      in its own batch and never changes the live config under these tests.
- [x] 3B.4 Add-then-remove N points is lossless (level and floor points equal before and after), from mid-level above
      and below 30. If it fails because of float progress, stop and plan the exact-math sub-step.
      **Failed: 3 of 20 cases lose 1 point** (fixed by 3B.7). +/-10000 at 20+10 → 20+9. +/-500 and +/-10000 at 1000+111 → 1000+110.
- [x] 3B.5 Death: `restoreFrom(old, false)` with keepInventory on (level/progress copied) and off (reset to 0). The
      gamerule is restored in `finally`. Save/reload: `saveWithoutId` → `load` into a fresh player at level 2,000,000,000
      with progress → identical.
- [x] 3B.7 **Exact XP via TAIL snap (owner chose TAIL snap over HEAD cancel / waive, 2026-10-06).** Vanilla has the
      same drift: 20+10 is all vanilla curve, and 29→30 uses vanilla's own 107/112. The cause: progress is a float,
      and `floor(progress × needed)` reads `p/n` back as `p − ε`.
      The vanilla body still runs, so other mods' injects into `giveExperiencePoints` keep working.
      - [x] a. `ExperienceCurve` (pure): `readPoints(progress, n)` = `floor(progress × n + 0.25)` clamped to `[0, n−1]`.
        It reads both stored forms, vanilla's `p/n` and ours. `storedProgress(p, n)` = the smallest float `f` with `f × n ≥ p`
        (first tried `(p + 0.5)/n`: exact, but the bar sat half a point ahead, ~13 px at level 0).
        `exactProgressFits(n)` is `n ≤ 2^21`. JUnit: for every n in 1..2000 and sampled n up to 2^21, for every p,
        vanilla `floor(stored × n)` gives `p`, and `readPoints` of both forms gives `p`. Above 2^21 it reports false.
      - [x] b. `PlayerMixin`, HEAD of `giveExperiencePoints`: record the exact total
        (`totalPoints(level) + readPoints`), or −1 when n doesn't fit. TAIL: skip if −1 or `amount == 0`. Target =
        `fromTotal(max(0, before + amount))`. If the target n fits, apply the level delta via `giveExperienceLevels(Δ)`
        (sound plus resync) and store `storedProgress`. If progress changed on a `ServerPlayer`, set `lastSentExp = −1`.
        Vanilla only syncs when `totalExperience` changes, so without this the client goes stale while the total is
        clamped at 0.
      - [x] c. `ServerPlayerMixin`: `@Accessor lastSentExp` (get/set). `setExperiencePoints` at `RETURN` (catches the
        early return too) stores `storedProgress(clamp(points, 0, n−1), n)` when n fits.
      - [x] d. GameTests: 3B.3 crossing + 3B.4 go green with the asserts unchanged. New: a legacy `p/n` progress at 20+10
        gets +1 → 20+11. With `totalExperience` clamped at 0, removing points sets `lastSentExp` to −1. (At MAX it
        wraps to 0; see gotchas.)
      - [x] e. DESIGN §3 Implementation bullet. decisions.md entry. gotchas: the `totalExperience` sync gap.
      Fallback: no snap when n > 2^21 (vanilla curve past level ~233k only; config caps the fixed cost at 1,000,000).
      Not doing: replacing the vanilla body; touching `increaseScore` / the `totalExperience` clamp.
- [x] 3B.6 `./gradlew build` green (73 JUnit + 18 GameTests). state.md, gotchas if any. Owner check added to the 1.6 `runClient` list: HUD at level
      ~1,000,000 shows the bar and the right number.

### Stage 3C — Score counters never wrap → commit  ✅ 2026-10-06
**Owner (2026-10-06):** saturating add. **javap:** there are two counters. `totalExperience` is
`clamp(total + amount, 0, MAX)` with an `int` add, so MAX + n resets to 0. The death-screen number is `getScore()`, and
`increaseScore` is a plain `int` add with no clamp at all, so it wraps negative. Neither feeds the curve.
- [x] 3C.1 `PlayerMixin`: `@ModifyArg` on the `Mth.clamp` call in `giveExperiencePoints` (index 0), with MixinExtras
      `@Local(argsOnly = true) int amount`. It returns `clamp((long) totalExperience + amount, 0, MAX)`, so the vanilla clamp
      still runs. `increaseScore` HEAD inject: if `(long) score + amount` is outside the `int` range, `setScore` the saturated
      value and cancel. Otherwise vanilla runs. A negative score stays vanilla (`/xp add -n` can make it negative).
- [x] 3C.2 GameTests: total MAX − 2 + 5 → MAX. Total MAX + 5 → MAX. Total 3 − 10 → 0. Score MAX − 2 + 5 → MAX.
      Score `MIN_VALUE + 2` − 5 → `MIN_VALUE`. Score 10 + 5 → 15 (vanilla path untouched). Both tests failed before the fix (total 0; score −2147483646). Build: 73 JUnit + 20 GameTests.
- [x] 3C.3 gotchas.md (correct the "score" naming in the 3B.7 entry), DESIGN §3 bullet, build green, commit.
**Blast radius:** two injects that only change a result that would otherwise wrap. No stored data is rewritten.
**Rollback:** `git revert <3C commit>`.

## Phase 4 — Enchantment rules engine (four stages, one commit each)
**Goal:** applicability is mandatory, conflict families are configurable, max levels are
overridden, and duplicate IDs can't occur.
**Owner (2026-10-06):** lowering a max level never touches existing items. An item already above the new max keeps
its level and its effect, which is vanilla behavior. The max only limits new enchanting and combining (`EnchantRules`).
**Owner (2026-10-06, Phase 4 start):** (1) conflict relaxation lives **only in `EnchantRules`**. Registry `exclusive_set`s
stay vanilla. (2) The damage family relaxes **all six** members of vanilla's `exclusive_set/damage` tag. (3) Lunge goes
to **5**, as in §8. See decisions.md.
**Facts (FAPI 0.141.6 source, 1.21.11 jar):** `MODIFY` runs inside `ResourceManagerRegistryLoadTask` before tags are
bound, once per enchantment loaded from resources. Fabric keeps our edit only if a builder method *returning the builder*
ran (`EnchantmentBuilderMixin.didModify`). A modified entry has its known-pack info cleared, so the server sends its
version to clients and a client's own config never touches the server's max levels. `Enchantment.Builder.definition` is
`final` and there's no setter, so we need an `@Mutable @Accessor`. Vanilla exclusive-set tags: armor, boots, bow, crossbow,
damage (sharpness, smite, bane, impaling, density, breach), mining, and riptide (loyalty, channeling; on Riptide only).
**Not doing:** effect resolution for combined families (Phase 8), `silk_fortune_mode` (Phase 8), per-enchantment
disable/weights (Phase 9), anvil/table wiring (Phases 6–7), reason text on screens (6.4), any registry `exclusive_set`
edit, touching items above a lowered max, or GUI editing of `enchantments.json5` (§10).
**Blast radius:** additive code. Two config files gain optional keys: `enchantments.json5` gets `max_level_override`, and
`common.json5` gets `conflicts`. Existing files without them load with defaults and are never rewritten (2A guard), and no
schema bump is needed. `VanillaEnchantmentIdsGameTest` changes its *expected* levels to the v1 column (the test stays;
it is not deleted). Adding or removing the mod: max levels revert to vanilla on removal, and items keep their levels (§2a).
**Rollback:** `git revert` the stage's commit. Nothing outside the repo and dev run dirs is touched.

### Stage 4A — Max levels via `MODIFY` → commit  ✅ 2026-10-07
**Owner (2026-10-07):** Lure and Riptide stay at vanilla 3 in 4A. 4D raises them only after its GameTest passes.
**Checked (fabric-item-api-v1 11.6.0, the version on our classpath):** `MODIFY` is the only modify event and it isn't
deprecated. Newer item-api versions (14.x) deprecate it for `MODIFY_WITH_LOOKUP`; see gotchas.md before upgrading FAPI.
- [x] 4A.1 `config/EnchantmentOverrides.Entry(OptionalInt maxLevelOverride)`: reads/writes `max_level_override`, int
      `1..255` (vanilla's codec range). Bad values → issue + absent. Tests in `ConfigParseTest` (valid, 0, 256, string,
      round-trip).
- [x] 4A.2 `enchantment/MaxLevels` (pure): the §8 table as `id → (vanillaMax, v1Max)`. `resolve(id, loadedMax,
      override)` → `(max, Why)`: an override always wins. A table entry applies only when `loadedMax == vanillaMax`;
      a datapack that already changed it is respected (logged). Any other ID keeps its value. JUnit: every §8 row, override
      above/below, datapack-changed, unknown ID.
- [x] 4A.3 `mixin/EnchantmentBuilderAccessor`: `@Mutable @Accessor("definition")` setter and an `exclusiveSet` getter, with
      `enchanting2$` names so they can't clash with Fabric's accessor on the same class.
- [x] 4A.4 `enchantment/MaxLevelHook`: registers `MODIFY` in `onInitialize`. It reads `enchantments.json5` via
      `ConfigFiles.load`, cached on (mtime, size) so 43 calls parse once and each change logs once. It replaces the
      definition with the new `maxLevel`, then re-calls `exclusiveWith(sameSet)` so Fabric keeps the edit (comment says
      why). An unreadable file means the v1 table only, with a WARN naming the file and the consequence.
- [x] 4A.5 GameTest: `VanillaEnchantmentIdsGameTest` expects the v1 column from the live registry (Fortune 5, Depth
      Strider 3, Quick Charge 4, Lunge 5, Silk 1). Reverting 4A.4 must fail it.
- [x] 4A.6 (was 4.0) Dev-server probe on 25566: set `minecraft:fortune` override 2, `/reload`, then `/enchant` Fortune 3
      (vanilla rejects above max). Restart and repeat. Record the outcome in gotchas.md. Fix DESIGN §2 and the
      `enchantments.json5` comment ("restart" vs "/reload").
      **Result:** `/reload` does not re-run `MODIFY`; a restart does (`scripts/checks/max-level-reload.txt`, gotchas.md).
      The file was restored byte for byte. The /enchant step was replaced by the hook's INFO line (no player on the dev
      server). Mutation: dropping `register()` or the `exclusiveWith` re-call each fails the v1 GameTest.
      Build: 83 JUnit (+10: ConfigParseTest 4, MaxLevelsTest 6) + 20 GameTests.
- [ ] 4A.7 Owner (with the 1.6 `runClient` check): put `minecraft:fortune: { max_level_override: 1 }` in the **client's**
      `run/client/config/enchanting2/enchantments.json5`, join the dev server (no override) → the creative Enchanted Books
      tab shows Fortune V. Then remove the client override. This proves the server's max level is the one synced
      (decisions.md 2026-10-07).

### Stage 4B — Conflict families (config) → commit  ✅ 2026-10-07
- [x] 4B.1 `enchantment/ConflictFamily` (pure enum): `SILK_TOUCH_FORTUNE`, `MENDING_INFINITY`, `PROTECTION`, `DAMAGE`
      (all six), `RIPTIDE_LOYALTY_CHANNELING`, `MULTISHOT_PIERCING`, `DEPTH_STRIDER_FROST_WALKER`, each with its member
      IDs. `relaxes(a, b, enabled)` is true only when both IDs are in the same enabled family.
      Families are disjoint (an ID is in at most one), checked by a test.
- [x] 4B.2 `config/AllowTogetherSection` in `CommonConfig`: `allow_together.<family>: true` (default; false = vanilla
      exclusive). **Owner (2026-10-07): `allow_together`, not `conflicts`, because `conflicts.x: true` reads as "they conflict".**
      Synced (the payload renders the whole file), and `/reload` applies it. A "Conflicts" screen category with one tick box
      per family. Lang keys `allow_together.<family>` + `.desc`, with `ConfigLangKeysTest` checking every family.
- [x] 4B.3 JUnit: defaults, parse/round-trip, missing section → defaults, a non-bool value → issue + default, unknown key →
      issue; `relaxes` symmetric, false across families, false when the family is off, false for third-party IDs.
- [x] 4B.4 decisions.md (key name), DESIGN §2/§4 key spelling, build green, commit.
      Mutation: `relaxes` ignoring `allowed` fails `ConflictFamilyTest`; a reader ignoring the file fails 4 parse tests.
      Build: 97 JUnit (+14) + 20 GameTests. Nothing reads the toggles until 4C.

### Stage 4C — `EnchantRules` → commit  ✅ 2026-10-07
**Owner (2026-10-07):** (1) books obey conflicts; only applicability waits until the book is applied. (2) An enchantment
already on the item can be upgraded even if it conflicts with something else there (grandfathered); only a *newly added*
enchantment is conflict-checked. (3) Merging keeps the higher level; no vanilla equal-levels +1. See decisions.md.
**Checked (javap 1.21.11, FAPI item-api 11.6.0 source):** `ItemStack#canBeEnchantedWith(holder, ACCEPTABLE)` fires
`ALLOW_ENCHANTING`, then `FabricItem` → `Enchantment#canEnchant` (supported_items). `Enchantment.areCompatible(a, b)` is
false for `a == b` and checks both exclusive sets. `ItemEnchantments.Mutable#upgrade` merges with `Integer::max` (capped 255).
**Approach:** one MC-side class (it needs `Holder`/`ItemStack`), stateless static methods, the config passed in as an
`AllowTogetherSection` so it's testable without a server holder. Tested by GameTests (no `fabric-loader-junit` yet).
**Not doing:** costs (Phase 5), anvil/table/grindstone wiring (6–7), reason text (6.4), disabled enchantments (9), reading
the stored/applied component off a stack (callers pass `existing`).
**Blast radius:** additive code, plus test-only data in the GameTest mod (`e2test:` enchantment, one tag append that only
exists on the GameTest server). Nothing on disk or in a world is rewritten.
**Rollback:** `git revert <4C commit>`.
- [x] 4C.1 `enchantment/EnchantRules.check(target, holder, level, existing, allowTogether) → Result(verdict,
      Optional conflictsWith, currentLevel)`. Order: `INVALID_LEVEL` (<1), `ABOVE_MAX` (> registry max),
      `NOT_APPLICABLE` (`canBeEnchantedWith(ACCEPTABLE)`; skipped for `BOOK`/`ENCHANTED_BOOK`), `ALREADY_AT_LEVEL`
      (current level ≥ requested; feeds DESIGN §5's "Already at maximum level"), then `CONFLICT(other)` **only when the
      ID is not on the item yet**. Compatible = `areCompatible || ConflictFamily.relaxes`. Several conflicts → the
      lowest ID, so the message is deterministic. (A by-key match was dropped: the item and network codecs always
      resolve to the registry's own `Holder.Reference`, so it was a branch no test could reach.)
- [x] 4C.2 `EnchantRules.withEnchantment(existing, holder, level)` → `ItemEnchantments`: vanilla `Mutable#upgrade`
      (one entry per holder, higher level). Every other entry is kept. `level < 1` throws.
- [x] 4C.3 GameTest data: `data/e2test/enchantment/third_party.json` (mining tools) + append it to
      `minecraft:tags/enchantment/exclusive_set/mining` (`replace: false`).
- [x] 4C.4 `EnchantRulesGameTest` (8 tests): Efficiency on a helmet / Protection on a pickaxe → NOT_APPLICABLE; Silk+Fortune OK
      relaxed, CONFLICT(silk) off; Fortune 6 → ABOVE_MAX; 0 → INVALID_LEVEL; a book takes Protection + Efficiency but
      refuses Silk+Fortune when off; Fortune V on the item with the request III → ALREADY_AT_LEVEL; Silk+Fortune III item,
      family off, Fortune V → OK (grandfathered), Unbreaking → OK; `third_party` vs Fortune → CONFLICT even relaxed
      (not a family member); merge keeps the higher level, keeps unrelated + third-party entries, rejects level 0.
- [x] 4C.5 Mutation checks: dropping the relax OR, the book skip, or the grandfather gate each fails exactly its test.
      The grandfather mutation showed the gate also stops an enchantment conflicting with itself (`areCompatible(a, a)`
      is false). Build: 97 JUnit + 28 GameTests.

### Stage 4D — Lure 5 / Riptide 5, safe caps on overrides → commit  ✅ 2026-10-07
**Owner (2026-10-07):** a `max_level_override` past a known break level is **clamped** to the last safe level (WARN),
not loaded as written. See decisions.md.
- [x] 4D.1 Arithmetic (javap 1.21.11, vanilla enchantment JSON):
      **Lure:** `lure.json` `fishing_time_reduction` = 5 s × level. `FishingRodItem` → `lureSpeed = (int)(s × 20)` = 100 ×
      level ticks. `FishingHook.catchingFish`: when `timeUntilLured ≤ 0` it re-rolls `Mth.nextInt(100, 600)` (inclusive)
      `− lureSpeed`; ≤ 0 means re-roll again next tick, > 0 counts down to the fish. Lure V (500): 100/501 ≈ 20% of rolls
      land in 1..100, so fish bite (faster than III). **Lure VI (600): every roll is ≤ 0 → never bites. Cap 5.**
      **Riptide:** `riptide.json` strength = 1.5 + 0.75(L − 1) → L3 3.0, L5 4.5, L7 6.0, L8 6.75. `TridentItem.releaseUsing`
      pushes the look vector (unit) × strength. `handleMovePlayer` rejects when `dist² − serverVelocity² > 100 × packets`
      (300 fall-flying; skipped for the SP owner, or when gamerule `playerMovementCheck` is off). Worst single launch,
      assuming no velocity credit: launched along a terminal fall (3.92 b/t): L5 (4.5 + 3.92)² = 70.9 ✓; L7 9.92² = 98.4
      ✓; L8 10.67² = 113.8 ✗. **Riptide V passes. Cap 7.** (Chained launches in rain exceed this even at vanilla III, so
      the velocity credit is what keeps vanilla working; the cap uses the stricter no-credit bound.)
      `trident_sound` has 3 entries; `pickHighestLevel` uses `list.get(min(level, size) − 1)`, so IV/V reuse riptide_3.
      **Quick Charge** (cap only, v1 is already 4): charge = 1.25 s − 0.25 s × level → IV = 5 ticks, **V = 0. Cap 4.**
- [x] 4D.2 `MaxLevels`: `SAFE_MAX` = lure 5, riptide 7, quick_charge 4. `resolve` clamps an override above it →
      `Why.OVERRIDE_CLAMPED` (the table and datapack-changed values are never clamped: the table is ≤ the cap, and a
      datapack that changes the max may also have changed the effect). JUnit: clamp, at-cap, below-cap, datapack value
      above the cap untouched.
- [x] 4D.3 `MaxLevelHook`: WARN for `OVERRIDE_CLAMPED` naming the file, requested and applied level, and the reason.
      Logged before the unchanged-max early return. Dev server (`scripts/checks/max-level-clamp.txt`): Lure 9 → 5 and
      Riptide 255 → 7 WARN, Quick Charge 4 loads as written; the overrides file was restored byte for byte.
- [x] 4D.4 `MechanicLimitsGameTest` (+ a GameTest-only mixin config with a `FishingHook` accessor):
      Lure: registry reduction ticks at V = 500 and VI = 600. A real hook in a water pool (`skyAccess`) with Lure V's speed
      gets a fish (`nibble > 0`) within the time limit; a hook with Lure VI's speed never does (`timeUntilLured ≤ 0`,
      `timeUntilHooked == 0`, `nibble == 0` after 200 ticks).
      Quick Charge: `CrossbowItem.getChargeDuration` IV > 0, V == 0.
      Riptide: mock player in water, trident with Riptide V, `releaseUsing` (no exception, sound path included) → velocity
      gain ≈ registry strength 4.5, and the no-credit bound holds at 5 and 7 and fails at 8.
- [x] 4D.5 Only after 4D.4 is green: add `lure` and `riptide` rows (3 → 5) to `MaxLevels.TABLE`,
      `VanillaEnchantmentIdsGameTest` expects 5, `MaxLevelsTest` row list, DESIGN §8 (asterisks resolved + caps).
- [x] 4D.6 Mutations, each failing its test: no clamp (JUnit); Lure cap 6 (no fish in 1200 ticks at Lure VI); Lure
      cap 4 (a Lure V hook bites at tick 8); Riptide cap 8 ((6.75 + 3.92)² = 113.8); Quick Charge cap 5 (0 charge ticks).
      The GameTests read `SAFE_MAX`, so they pin the caps themselves. Build: 101 JUnit + 33 GameTests.
**Not doing:** clamping datapack-set maxes, caps derived from live effect data, a chained-Riptide test, Depth Strider
(useless above 3, not broken).
**Blast radius:** additive code + a GameTest-only mixin. An existing `enchantments.json5` override above a cap now loads
clamped (WARN), never rewritten. **Rollback:** `git revert <4D commit>`.

## Phase 5 — Cost model (three stages, one commit each)
**Goal:** a pure cost calculator (no MC classes) that reproduces every DESIGN §5 / §5a example exactly, with its
config keys parsed and validated. Nothing calls it until Phase 6 (same as `EnchantRules`).
**Owner (2026-10-07, Phase 5 start):** (1) repair defaults to vanilla minus the prior-work penalty: 1 level per material
item, 2 levels to combine two items, both configurable. (2) `treasure_multiplier` and `curse_multiplier` are **dropped**.
(3) `FIXED` upgrade = `cost_per_level` once, however many levels are added. (4) Cleanse repays **that curse's own**
`discount_percent` (min 30), not the marginal drop under the cap. See decisions.md.
**Facts (from the §5 table):** "Mending alone = 2" and "Mending + Eff I = 5" only hold if Mending is `base_cost 2`,
`cost_per_level 0`, surcharge 2. The surcharge is data (`other_levels_surcharge`, per enchantment, default 0), not a
Mending branch. Lifetime surcharge of X = `s_X × O_final` when levels only rise, which is why build order can't matter.
**Agent calls (recorded in decisions.md):** a curse's own line costs 0 (its value is the discount); curses don't count
in `O`. A curse with no entry discounts 10% (Binding/Vanishing's value). `maximum_operation_cost N` refuses totals
**above** N (−1 = off, default), and creative bypasses it as vanilla does. Arithmetic is `long` and saturates at
`Integer.MAX_VALUE` (3C precedent). Package `cost/` (not `enchanting/`, which would sit next to `enchantment/`).
**Not doing:** loading `enchantments.json5` onto the running server or syncing it (Phase 6, with its first caller);
reading stacks or tags (callers pass ID→level maps and an `isCurse` predicate); table tiers/shelves (7); reason text (6.4);
GUI editing of per-enchantment cost fields (file-only, §10); new curses (Phase 10).
**Blast radius:** additive code. `common.json5` gains optional `costs` and `curses` sections; `enchantments.json5`
entries gain optional cost keys. Existing files load with defaults and are never rewritten (2A guard), so no schema bump.
**Rollback:** `git revert` the stage's commit. Nothing outside the repo is touched.

### Stage 5A — Config keys → commit  ✅ 2026-10-07
- [x] 5A.1 `ConfigReader.readOptionalEnum(key, Class<E>)` (lower-case names; bad value → issue + empty). JUnit.
- [x] 5A.2 `config/CostsSection` in `CommonConfig.costs`: `rename_cost` 1, `repair_cost_per_material` 1,
      `repair_combine_cost` 2, `maximum_operation_cost` −1 (−1..1,000,000), `creative_free` true. Ranges public.
- [x] 5A.3 `config/CursesSection` in `CommonConfig.curses`: `max_discount_percent` 45 (0..95), `removal_min_cost` 30.
- [x] 5A.4 `EnchantmentOverrides.Entry` gains optional `base_cost`, `cost_per_level` (0..10,000; keeps sums inside `long`),
      `upgrade_cost_mode` (`result_level|level_difference|fixed|free`), `other_levels_surcharge` (0..100),
      `discount_percent` (0..100, curses only). Comment text in `write`.
- [x] 5A.5 `cost/Pricing` record + `cost/PricingTable` (pure): `DEFAULT` 0/1/LEVEL_DIFFERENCE/0/10% (the 10% is
      Binding's, Vanishing's and any entry-less curse's, so no curse rows), one `BUILT_IN` row: `minecraft:mending`
      2/0/surcharge 2. Per-field override from an `Entry`. `PricingTableTest` (7): defaults, each field alone, Mending
      partly overridden, a max-level-only entry changes no price, record bounds.
- [x] 5A.6 Config screen: "Costs" and "Curses" categories with lang keys + `.desc`; `ConfigLangKeysTest` covers them;
      `ConfigScreen` rebuilds `CommonConfig` with the new sections.
- [x] 5A.7 `ConfigParseTest`: defaults, round-trip, missing sections → defaults, each out-of-range/wrong-type → issue +
      default, unknown enum → issue. Mutation: a reader that ignores the file fails them. Build green, commit.
      **Done:** `ConfigParseTest` +9, `ConfigLangKeysTest` +1 (keys taken from what each section writes). Mutations,
      each caught: costs/curses reader returns defaults (5 tests), `upgrade_cost_mode` never read (3), `PricingTable`
      ignores overrides (2), a missing `.desc` (1). Build: 118 JUnit + 33 GameTests.

### Stage 5B — `CostModel` → commit  ✅ 2026-10-07
- [x] 5B.1 `cost/CostRequest(station TABLE|ANVIL, before, after, renamed, repairMaterials, combineRepair,
      creative)`; `before`/`after` are ID→level maps. Validation: a level drops or an ID disappears → throw (removal is
      the grindstone's job, priced separately); rename/repair off the anvil → throw. (No GRINDSTONE station: Extract
      and Cleanse are their own methods in 5C, so it would be dead.) Maps are copied sorted, so lines are ordered.
- [x] 5B.2 `cost/CostBreakdown`: ordered lines (`ENCHANT id from→to amount`, `SURCHARGE id O_in→O_out amount`,
      `RENAME`, `REPAIR`), `enchantCost`, `total`, `lapis`, `tooExpensive`, `free` (creative). Totals equal the sum of
      lines (asserted in the constructor).
- [x] 5B.3 `cost/CostModel.price(request, pricing, isCurse, costs)`: base only when newly added; levels by mode
      (new = result level always; upgrade by mode); surcharge `s × (O_out − O_in)` or `s × O_out` when newly added;
      an unchanged ID contributes nothing (ALREADY_AT_LEVEL is free). Lapis = `min(enchantCost, 64)` at TABLE, else 0.
- [x] 5B.4 `CostModelTest`, one test per §5 table row (Mending 2; Mending + Eff I..V 5/8/11/14/17; Eff V then Mending
      5 + 12; Mending then Eff V 2 + 15; Mending + Eff V + Unb III + Fort III 35; Eff III→V with Mending 6), plus rename
      only = 1, material repair ×3 = 3, combine = 2, creative → total 0 with lines kept, `maximum_operation_cost`
      0/at/above, creative ignores it, ANVIL lapis 0, TABLE lapis capped at 64, each upgrade mode, saturation at extremes.
- [x] 5B.5 Property test (seeded `Random`, 1,000 cases): random final item, random split into ops with random
      intermediate levels → summed cost equals one-shot cost under default pricing. Mutation: surcharge on added levels
      only (the rejected design) must fail it. Build green, commit.
      **Done:** `CostModelTest` (23) + `CostModelPropertyTest` (1,000 seeded cases; half built-in pricing, half random
      base/per-level/surcharge with LEVEL_DIFFERENCE, the only order-independent upgrade mode). Mutations, each caught:
      surcharge only on same-op levels (property + Eff-V-then-Mending), lapis at the anvil, curses counted in `O`,
      creative refused as too expensive, base cost re-charged on upgrade (also the property). 142 JUnit + 33 GameTests.

### Stage 5C — Curses: discount, Cleanse, Extract → commit  ✅ 2026-10-07
- [x] 5C.1 `CostModel`: discount = Σ curse `discount_percent` on the **result**, capped at `max_discount_percent`;
      `enchantCost' = ceil(enchantCost × (100 − d) / 100)`; a `CURSE_DISCOUNT` line (negative). Never on rename/repair;
      lapis follows the discounted cost.
- [x] 5C.2 `CostModel.cleanseCost(current, curseId, …)` = `max(removal_min_cost, ceil(pct × undiscounted build cost of
      current non-curse enchantments))`; `extractCost(id, level, …)` = the price of adding it to a blank item (Mending 2).
      A curse ID passed to `extractCost` throws (§6: curses can't be extracted).
- [x] 5C.3 Tests: §5a example (Mending + Eff V + Weight-like 15% curse → 15; cleanse → 30); cap at 45 with four 15%
      curses; rounding up; rename + repair untouched; TABLE lapis = discounted cost; extract Eff V = 5, Mending = 2.
- [x] 5C.4 Property (seeded, 1,000 cases): curse → enchant → cleanse every curse ≥ enchanting the same item uncursed.
      Mutation: a flat `removal_min_cost` cleanse (the 2026-10-06 rejected design) must fail it. (The marginal-drop rule
      would *not*: its drops telescope to the capped discount, see decisions.md correction.) A unit test pins own-% vs
      marginal instead: four 15% curses at the 45% cap, cleanse one → 15% of the build cost.
- [x] 5C.5a (done with the plan) DESIGN §5/§5a: multipliers dropped, FIXED defined, repair defaults, Mending's data,
      curse line 0, unknown-curse 10%, cleanse own %. decisions.md 2026-10-07 "Cost model gaps".
- [x] 5C.5 state.md, build green, commit.
      **Done:** `price` takes `CursesSection`; `CostBreakdown.Line.amount` is `long` (a 95% discount on a cost past int
      range is itself past int range) and `CURSE_DISCOUNT` is the only negative kind. `CurseCostTest` (13, incl. a
      1,000-case property with random prices, modes, caps and minimums, comparing against min(same steps clean, clean
      at once), which bounds every upgrade mode). `CostModelPropertyTest` now runs with discount 0: §5a is per operation
      and rounds up, so it is deliberately not order-independent. Mutations, each caught: flat-minimum cleanse
      (property + 2), marginal cleanse (own-% unit test + 1; the property passes, as the correction predicts),
      discount rounding down (8), no cap (3), cleanse ignoring prices/surcharge (3). 155 JUnit + 33 GameTests.
      **For Phase 6b:** `cleanseCost`/`extractCost` don't know about creative; the grindstone applies `creative_free`.

## Phase 6 — Anvil (four stages, one commit each)
**Goal:** the server's anvil runs on `EnchantRules` + `CostModel` (DESIGN §6). No 40 cap, no prior-work penalty, and
book modes that never lose an enchantment unless the admin picks `strip_invalid` and the preview says so first. The
screen shows the breakdown and the reasons.
**Owner (2026-10-07, Phase 6 start):** (1) Invalid-book modes: `reject_all` = no result unless every entry applies;
`apply_valid` (default) = valid entries apply and **the book stays in the right slot holding only what was skipped**;
`strip_invalid` = the book is consumed and skipped entries are destroyed, marked "will be lost" in the preview. (2)
Item + item: `reject_all`/`apply_valid` **refuse** when a sacrifice enchantment would be lost (there's no leftover to
return); `strip_invalid` allows it, previewed. A lower level of an ID the target already has higher ("dominated") is
never counted as lost. (3) Prior work: the `repair_cost` component is **never read and never written**, so removing
the mod hands items back their pre-mod penalty. No config key. (4) Preview is a **side panel** next to the anvil window.
The cost label shows the total or the blocking reason.
**Facts (decompiled 1.21.11 `AnvilMenu`/`AnvilScreen`):** `createResult` runs on both sides, so a client on our server
must not compute its own result, or it desyncs from the server's slot tracking. The cost `DataSlot` is synced as a
**short** (`ClientboundContainerSetDataPacket`), so a cost above 32,767 wraps on the client and the real total has to
travel in our payload. `mayPickup` needs `cost > 0`, which would block free results (creative, curse-only books, `FREE`).
`AnvilScreen.renderLabels` hard-codes "Too Expensive!" at 40. Vanilla lets creative players skip applicability.
**Agent calls (decisions.md 2026-10-07 "Anvil"):** the server computes the preview and sends it (S2C). The client gets
no pricing table, so there's one source of truth and a forged client can't misprice. `NetworkProtocol.VERSION` → 2: a
v1 client would run vanilla's client-side result on a v2 server. Creative players obey `EnchantRules` (vanilla's bypass
is dropped; `/enchant` exists). Enchanting needs a single item in the left slot (vanilla blocks stacks via its 40 cap);
renaming a stack still works. Book entries are checked in ID order against the result so far, so the outcome is
deterministic. `reject_all` consumes dominated entries like item + item does.
**Not doing:** grindstone (6b); table (7); a plain `minecraft:book` in the left slot (the table makes books, 7.4); the
`enabled` flag (9); vanilla +1 merging (decisions.md 2026-10-07); syncing `enchantments.json5` to clients.
**Blast radius:** with the mod on the server, every anvil operation goes through our code (server side). A modded
client on a vanilla server keeps vanilla (the client defers only when the server sent our config). Items: no new
components; `repair_cost` untouched. `common.json5` gains an optional `anvil` section (defaults, no schema bump). A v1
client is disconnected with the existing mismatch message.
**Rollback:** `git revert` the stage's commit. Nothing outside the repo is touched.

### Stage 6A — Config and pure transfer rules → commit  ✅ 2026-10-07
- [x] 6A.1 `config/AnvilSection` in `CommonConfig.anvil`: `invalid_book_mode` (`reject_all|apply_valid|strip_invalid`,
      default `apply_valid`) via `readOptionalEnum`. Screen category "Anvil" + lang keys + `.desc`.
- [x] 6A.2 `anvil/TransferRules` (pure): `decide(Source BOOK|ITEM, InvalidBookMode, EnchantRules.Verdict)` →
      `APPLY | KEEP_ON_BOOK | CONSUME_DOMINATED | LOSE | REFUSE`. `TransferRulesTest`: the whole 2 × 3 × 6 table,
      one assertion per cell, plus "no `LOSE` outside `strip_invalid`".
- [x] 6A.3 `enchantments.json5` on the running server: `ServerConfigHolder` also holds the `EnchantmentOverrides`
      (not a `PricingTable`: `config` must not import `cost`, which already imports `config`). `ServerConfigs`
      loads it at `SERVER_STARTING` and re-reads it on `/reload` (unparseable → keep previous, same policy as
      `common.json5`). `MaxLevelHook` keeps its own read (it runs before the server exists).
- [x] 6A.4 Tests: `ConfigParseTest` (+anvil defaults, bad enum → issue + default), `ConfigLangKeysTest` (+Anvil),
      `ConfigWiringGameTest` (pricing reloads on `/reload`). Mutation: `decide` returns `APPLY` for CONFLICT. Build green,
      commit.
      **Done:** `ConfigParseTest` +4, `ConfigLangKeysTest` +1 (and the section-keys test now walks `anvil`),
      `TransferRulesTest` 6 (every cell + invariants: `LOSE` only under strip, `KEEP_ON_BOOK` only from books, `APPLY` iff
      OK). The existing reload GameTest now also edits `enchantments.json5` (marker, garbage keeps previous, restore) in the
      same sequence, since two tests editing config files would race. `MaxLevelHook.file()` now delegates to
      `ServerConfigs.enchantmentsFile()`. Mutation: CONFLICT → APPLY fails 3 of 6. 166 JUnit + 33 GameTests.

### Stage 6B — Server anvil → commit  ✅ 2026-10-07
- [x] 6B.1 `anvil/AnvilOutcome` (MC): result stack, right-slot-after-take stack (leftover book / shrunk materials /
      empty), materials used, `CostBreakdown`, applied entries, skipped entries (ID, level, verdict, conflictsWith,
      fate), status (`OK | NOTHING_TO_DO | REFUSED_WOULD_LOSE | REFUSED_INVALID | TOO_EXPENSIVE | STACKED_ITEM`).
- [x] 6B.2 `anvil/AnvilCalculator.compute(left, right, name, creative, CommonConfig, PricingTable)`: rename only;
      material repair (vanilla 25% steps, `repair_cost_per_material` each); book (entries via `EnchantRules.check` in ID
      order + `TransferRules`); item + item (vanilla durability sum + 12%, `repair_combine_cost`, then entries as
      ITEM). Curses via `#minecraft:curse`. `REPAIR_COST` is copied as is, never changed.
- [x] 6B.3 `mixin/AnvilMenuMixin`: `createResult` HEAD, cancellable. Server: compute, set result, `cost` (int, exact
      server-side), `repairItemCountCost`, keep the outcome in an `@Unique` field. Client: cancel without touching the
      result slot when `AnvilBridge.serverHandlesAnvil()` (set by client init from `ClientConfigState.serverCopy()`),
      else vanilla. `mayPickup`: result present and (total is 0 or level ≥ total). `onTake`: charge `total` (0 when free,
      so creative with `creative_free false` pays), then put the outcome's right-slot stack back.
- [x] 6B.4 GameTests (real `AnvilMenu` + mock `ServerPlayer`, click the result slot): 3-entry book on a pickaxe under
      each mode; item + item under each mode (Silk + Fortune exclusive); material repair ×3 consumes 3 and costs 3;
      combine repair = 2 + enchant; rename only = 1, renaming a stack of 16; insufficient levels → take refused,
      nothing consumed; curse-only book costs 0 and can be taken; creative free / not free; `maximum_operation_cost`;
      cost 45 (past the old 40 cap) taken; `repair_cost` unchanged; a stack of 2 swords + book → `STACKED_ITEM`.
- [x] 6B.5 **Conservation property** (GameTest, seeded, 300 cases): random item, random book or sacrifice, random mode
      → every input (ID, level) is on the result or the right-slot stack at ≥ that level, unless it's dominated or the
      outcome lists it as `LOSE`; nothing appears on both. Mutation: drop the leftover write in `onTake` must fail it.
      Build green, commit.
      **Done:** `AnvilGameTest` (21, real `AnvilMenu` + mock player + result-slot click). Beyond the plan: a book that
      gives nothing (e.g. renaming with it in the slot) goes back untouched in every mode; a stack in the *right* slot
      is also `STACKED_ITEM` (taking would otherwise empty the whole slot); `rightAfterTake` covers material leftovers
      too, so the mixin has one right-slot path; `AnvilCalculator` failures are logged and fail closed (no result).
      The mock player is hard-wired creative (gotchas.md), so tests clear `instabuild`. Property: 300 seeded cases, and
      it asserts at least 60 reach a take. Mutation (leftover write dropped) fails 4, the property among them.
      **Not tested here (client side):** `AnvilBridge` deferral and the client `mayPickup` prediction; owner `runClient`
      check in 6D.4. 166 JUnit + 54 GameTests.

### Stage 6C — Preview payload → commit  ✅ 2026-10-07
**Refined at 6C start (2026-10-07):** `CostBreakdown.total` is an `int` saturated at `Integer.MAX_VALUE`, so "exact
`long` total" is an exact `int` (VarInt): the short wrap is the problem, not the width. Line amounts stay `long`. The
codec round-trip is a GameTest, not JUnit: `ByteBufCodecs` is a Minecraft class and JUnit here has no MC bootstrap
(same as `ConfigSyncPayload`). Enums travel as ordinals (bounds-checked on decode); reordering one is a wire change and
needs a `VERSION` bump. List and string sizes are checked in the record's constructor, so an oversize preview fails on
the server thread (logged, not sent) instead of inside Netty. Dedup is by value (`equals`) per menu; the last preview is
kept even when the client can't receive it, so the GameTest can see it (the mock player has no channels).
- [x] 6C.1 `network/AnvilPreviewPayload` (S2C play): container ID, status, optional price (lines, total, free), entries
      (ID, level, verdict, conflicts-with, fate). `of(containerId, AnvilOutcome)`. `VERSION` → 2. Registered in
      `registerPayloads`.
- [x] 6C.2 `AnvilMenuMixin` builds it after each server `createResult`; when it differs from the last one for that menu,
      keeps it and sends it if `canSend`. Failure to build or send is logged; the anvil still works.
- [x] 6C.3 `NetworkProtocolTest` pins v2 (a v1 client is a `MISMATCH` naming 1). GameTests: codec round-trip incl. a
      total of 40,000, `Integer.MAX_VALUE` and a `Long.MAX_VALUE` line, every enum value; oversize list refused; a
      rename keystroke that doesn't change the outcome keeps the same preview; adding a book changes it. Build green,
      commit.
      **Done:** the payload carries the whole `CostBreakdown`, so decoding re-runs its totals check (a forged total fails
      to decode, tested). `AnvilPreviewHolder` lives in `network` (it already imports `anvil`). An oversize preview falls
      back to status-only, logged, so the panel never shows a stale price. `AnvilPreviewGameTest` 6, `NetworkProtocolTest`
      +1. Mutation (dedup check removed) fails the rename test. Nothing on the client receives it yet (6D.1), so
      `canSend` is false and nothing is actually sent until then. 167 JUnit + 60 GameTests.

### Stage 6D — Anvil screen → commit  ✅ 2026-10-07
**Owner (2026-10-07, 6D start; decisions.md "Anvil screen"):** `client.json5` now, the toggle click saves it (GUI
writer, `.bak`); tab off the window's left edge; no room on either side → shift the window; client GameTests on.
**Facts (decompiled 1.21.11):** `AnvilScreen.subInit` places the rename box from `(width - imageWidth) / 2`, not
`leftPos`; `renderLabels` runs translated to `leftPos/topPos`; `AnvilScreen` declares `renderBg` and `subInit` but not
`render`; `hasClickedOutside` makes a click outside the window throw the carried item. Client GameTest API on the
classpath: 4.3.5.
**Not doing:** vanilla-server screen changes (none: no panel, no tab); grouping digits; hover tooltips on panel rows;
syncing anything new to the client.
**Blast radius:** client only, plus one new file `config/enchanting2/client.json5` (created on first launch, written
only on a toggle click or a config-screen save, `.bak` first). Vanilla servers: unchanged screen. **Rollback:** `git
revert`; a leftover `client.json5` is harmless (nothing else reads it) and can be deleted by hand.
- [x] 6D.1 `config/ClientConfig` (pure codec, `client.json5`, `anvil.show_preview_panel` default true) +
      `client/ClientSettings` (load at client init, `setShowPanel` saves through `saveWithBackup`; a refused or failed
      save keeps the session value, toast). `ConfigScreen` "Display" category, always editable, saved with the screen.
      Tests: `ConfigParseTest`/`ConfigFilesTest` (defaults, bad value → issue, round trip), `ConfigLangKeysTest`.
- [x] 6D.2 `anvil/AnvilPreviewView` (pure): label (total / free / too expensive with the max / insufficient levels /
      refused reason / stacked / nothing to do / none) + short form, and panel rows (lines with amounts, skipped
      entries with reason + fate, total) as lang keys with typed args. `anvil/AnvilPanelLayout` (pure): fixed panel
      width; left, else right, else shifted window; hidden → centered. JUnit: every status, verdict, fate and line kind;
      layout at a range of screen widths (panel and window on screen, never overlapping, shift only when needed).
- [x] 6D.3 `client/AnvilPreviewState`: receiver (makes `canSend` true), latest payload per container ID, cleared on
      screen close, config INIT and disconnect. `AnvilBridge` client preview source → `mayPickup` prediction uses the
      exact total.
- [x] 6D.4 `mixin/client/AnvilScreenMixin`: `getCost()` → 0 in `renderLabels` when the server is ours, our label at
      TAIL; `subInit` TAIL applies the layout (`leftPos` + rename box) and adds the tab; panel drawn in `renderBg` TAIL;
      `hasClickedOutside` false over panel and tab. Duck interface for the client GameTest.
- [x] 6D.5 Lang keys for every status, verdict, fate, line kind, label and the tab; `AnvilLangKeysTest` walks every key
      `AnvilPreviewView` can produce.
- [x] 6D.6 Client GameTest (`enableClientGameTests`): singleplayer world, real anvil, sword + book → panel rows and
      label; `rename_cost` 40,000 + `/reload` → label shows 40000 (short slot wraps); too few levels → insufficient
      label; tab click → panel hidden, window centered, `client.json5` says false; tiny window → window shifted, panel
      on screen. Screenshots kept for the owner.
- [x] 6D.7 DESIGN §2 (client.json5) and §6 Anvil (modes, item + item, prior work untouched, panel). state.md. Owner
      `runClient` list: panel look, vanilla-server fallback. Mutations: label ignores the preview; outside-click guard
      removed. Build green, commit.
      **Done:** the first client launch crashed in YACL's own entrypoint (its nested quilt parsers aren't loaded in the
      dev runtime; pre-existing since 2C, gotchas.md); fixed with dev-only `localRuntime` deps. The screenshots showed
      the compact label running over "Inventory" at 40,000, so a label is now a longest-first list of forms ending in
      "Cost: N", and the client test asserts the label clears "Inventory". Mutations: preview ignored → "a priced
      preview arrived" fails; outside-click guard removed → "the carried diamonds were thrown". 195 JUnit + 60
      GameTests + 1 client GameTest (`./gradlew runClientGameTest`; `build` does not run it). Screenshots:
      `build/run/clientGameTest/screenshots/`. **Not automated (owner):** the config screen's Display toggle, and a
      modded client on a vanilla server keeping the vanilla anvil screen.

## Phase 6b — Grindstone (three stages, one commit each)  ✅ 2026-10-07 (except the owner `runClient` checks)
**Goal:** the grindstone offers Extract (one non-curse enchantment onto a plain book, moved, never copied or lost),
Cleanse (one curse, §5a repay cost) and vanilla Disenchant / two-item Repair, each as an explicit pick from a side
panel. The server decides and charges everything (DESIGN §6).
**Owner (2026-10-07, 6b start):** (1) The picker is a **side panel** beside the window, placed like the anvil's
(left, else right, else shift the window). It's a control, so it has no hide tab. (2) **The result slot is empty
until the player picks** an action, vanilla Disenchant included. (3) `maximum_operation_cost` caps Extract and
Cleanse too (Cleanse's minimum of 30 means N < 30 blocks Cleanse; the panel says why).
**Facts (decompiled 1.21.11 `GrindstoneMenu`/`GrindstoneScreen`):** both input slots are anonymous `Slot`s
(`GrindstoneMenu$2`, `$3`) whose `mayPlace` takes only damageable or enchanted items, **so a plain book can't be placed
today** (`$1` is the `SimpleContainer`, verified with `javap`). The result slot (`$4`) `onTake` awards XP orbs from
the inputs' non-curse `getMinCost` and **empties both inputs**. `computeResult` refuses when either input has a count > 1. Disenchant writes `REPAIR_COST` (vanilla's own
reset). There is no cost `DataSlot` and no level check. `createResult` runs on both sides via `slotsChanged`.
`GrindstoneScreen.renderBg` blits at `(width - imageWidth) / 2`, not `leftPos`, and draws the error sprite whenever an
input is present and the result is empty.
**Agent calls (decisions.md "Grindstone"):**
- Either orientation works: the source item in one slot, a plain book (any count, one consumed) in the other.
- The selection is server state per menu, set only by a validated C2S choice. It's cleared when the source stack
  changes (item or components). It survives a change in the book count only.
- Extract and Cleanse replace vanilla's `onTake` (no XP orbs, so Cleanse can't be an XP faucet; `REPAIR_COST`
  untouched, like the anvil). Disenchant and Repair keep vanilla's body.
- A book holding nothing but the picked entry is refused (`NOTHING_TO_SPLIT`): that would just move it onto another
  book for XP. A book with that entry plus a curse can be split (it separates the curse).
- Creative follows the anvil: it bypasses the cap, and `creative_free` makes it free.
- The error sprite shows only for a refusal, not while waiting for a pick.
- `NetworkProtocol.VERSION` → 3: a v2 client would compute vanilla grindstone results locally on a v3 server.
- No new config keys.
**Not doing:** extracting onto an already enchanted book (merge); extracting curses; cleansing several curses in one
take; changing vanilla Disenchant XP or Repair math; a grindstone for vanilla servers (a modded client there keeps
vanilla: no book placement, no panel); `enabled` flags (Phase 9).
**Blast radius:** with the mod on the server, every grindstone take goes through our menu code. Items gain no new
components. Extract and Cleanse never touch `REPAIR_COST`. Disenchant and Repair behave exactly as vanilla, just behind a
pick. A v2 client is disconnected with the existing mismatch message. Client: the grindstone screen only, and only when
the server is ours.
**Rollback:** `git revert` the stage's commit. Nothing outside the repo is touched. A book left in a grindstone slot
when the mod is removed is returned by vanilla's `removed()` like any other item.

### Stage 6bA — Calculator (server logic, no menu wiring) → commit  ✅ 2026-10-07
- [x] 6bA.1 `grindstone/GrindstoneAction` (`EXTRACT | CLEANSE | DISENCHANT | REPAIR`), `grindstone/GrindstoneChoice`
      (action + enchantment ID, empty for DISENCHANT/REPAIR), `grindstone/GrindstoneOption` (choice, level, levels
      charged, status), `grindstone/GrindstoneOutcome` (status, result, slot 0 and 1 after the take, levels charged,
      `vanillaTake` for DISENCHANT/REPAIR). Status: `OK | NO_CHOICE | CURSE_NOT_EXTRACTABLE | NOTHING_TO_SPLIT |
      STACKED_ITEM | TOO_EXPENSIVE | NOT_AVAILABLE | FAILED`.
- [x] 6bA.2 `grindstone/GrindstoneCalculator.options(a, b, creative, config, pricing)`: the list the panel shows, in a
      stable order (action, then enchantment ID). `compute(a, b, choice, creative, config, pricing)`: the outcome for a
      choice; a choice not among the OK options gives `NOT_AVAILABLE` and no result. Extract uses
      `CostModel.extractCost` and Cleanse `CostModel.cleanseCost`; DISENCHANT/REPAIR results are vanilla's (copied
      logic: `removeNonCursesFrom`, `mergeItems`, `REPAIR_COST` included). Never mutates its inputs. Failures are logged
      and fail closed.
- [x] 6bA.3 `GrindstoneCalculatorGameTest` (needs MC item stacks, like `AnvilGameTest`): every action in both
      orientations; Extract result = one entry, source keeps the rest, book count − 1; multi-enchantment book split;
      single-entry book → `NOTHING_TO_SPLIT`; curse-only rows refused; Cleanse = max(30, own % × undiscounted); cap →
      `TOO_EXPENSIVE`, creative bypasses; `creative_free` → 0; stacked source → `STACKED_ITEM`; Disenchant/Repair
      results equal vanilla's for the same inputs. Mutation: Extract leaves the entry on the source → fails. Build
      green, commit.
      **Done:** one `GrindstoneCalculator.compute(slot0, slot1, choice, creative, config, pricing, vanillaResult)` returns
      the rows *and* the pick's result (`GrindstoneOutcome.options`), so the panel and the result never come from two
      calls. **Changed from the plan:** Disenchant/Repair call vanilla's own `computeResult` through
      `mixin/GrindstoneMenuInvoker` instead of a copy: exact vanilla, other mods' changes kept, nothing to drift.
      `FAILED` became `NONE` (as the anvil), and `NONE` covers inputs the grindstone does nothing with. A curse-only
      item gets no Disenchant row (vanilla's "result" there only resets prior work). Plain books never Repair or
      Disenchant. Rows: Cleanse before Disenchant (action order), Extract rows by ID, curses listed as refused.
      `GrindstoneCalculatorGameTest` 20. Mutation (Extract copies) fails 4. 195 JUnit + 80 GameTests.

### Stage 6bB — Server menu + payloads → commit  ✅ 2026-10-07
- [x] 6bB.1 `network/GrindstoneChoicePayload` (C2S: container ID, choice or none) and
      `network/GrindstoneOptionsPayload` (S2C: container ID, options, selected choice, outcome status, levels
      charged). Sizes checked in the constructor, enums as bounds-checked ordinals. `VERSION` → 3. Registered in
      `registerPayloads`.
- [x] 6bB.2 Server receiver: ignore and log (debug) unless `player.containerMenu` is a grindstone with that container
      ID. Store the choice in the menu, recompute.
- [x] 6bB.3 `mixin/GrindstoneMenuMixin`: `createResult` HEAD. On the server: options + outcome for the stored choice,
      set result, broadcast, send the options payload when changed (dedup like the anvil). On a client of our server:
      cancel. Clear the choice when the source stack changes. Duck `GrindstoneMenuAccess` for tests.
- [x] 6bB.4 `mixin/GrindstoneInputSlotMixin` (`GrindstoneMenu$2`, `$3`): `mayPlace` also accepts `Items.BOOK` on the
      server, and on a client of our server (`GrindstoneBridge`, like `AnvilBridge`).
      `mixin/GrindstoneResultSlotMixin` (`$4`): `mayPickup` = OK and (free or levels ≥ charge); client prediction from
      the latest options payload. `onTake` HEAD: Extract/Cleanse charge, write both input slots from the outcome, play
      level event 1042, cancel. DISENCHANT/REPAIR fall through to vanilla.
- [x] 6bB.5 `GrindstoneGameTest` (real `GrindstoneMenu` + mock player, click the result slot): nothing before a pick;
      Extract moves the entry exactly once (result has it, source doesn't, every other entry unchanged, one book
      consumed, levels − cost, no XP orbs); either orientation; Cleanse charges the repay cost, removes only that curse,
      no orbs; Disenchant after a pick = vanilla (orbs spawned); insufficient levels → take refused, nothing changed;
      shift-click with a full inventory → nothing changed; forged choices (wrong container ID, enchantment not on the
      item, a curse as Extract, Cleanse of a non-curse) → no result; source swapped after a pick → choice cleared;
      book count changed → choice kept; payload codec round trip incl. every enum value and an oversize list refused.
- [x] 6bB.6 **Conservation property** (seeded, 300 cases): random enchanted item or book, random plain-book count,
      random orientation, random choice → after a take, the (ID, level) multiset over both inputs + result equals the
      one before, minus exactly the cleansed curse (Cleanse) and never anything for Extract. Asserts ≥ 60 cases reach a
      take. Mutations: don't remove the entry from the source (copy) → fails; don't write the book slot → fails.
      `NetworkProtocolTest` pins v3. Build green, commit.
      **Done:** the slots get their menu through a duck (`GrindstoneSlotOwner`, set in the menu's constructor), not the
      compiler's synthetic outer field, whose name differs by mapping. `$4` doesn't declare `mayPickup`, so the mixin
      adds the override. Every vanilla take path (pickup, shift-click, hotbar swap, throw, collect) calls `mayPickup`
      first (decompiled `doClick`), so the level check covers them all. The C2S handler is
      `ServerNetworking.handleGrindstoneChoice`, tested directly with forged IDs and picks. A malformed choice is a
      decode error. `ordinal()`/ID codecs moved to `network/PayloadCodecs`, shared with the anvil payload. On a client of
      our server `onTake` is cancelled (the server writes the inputs and syncs them). `GrindstoneGameTest` 16 (incl.
      orbs counted at a real `ContainerLevelAccess`, a full inventory shift-click, codecs, the property: 300 cases, ≥ 60
      Extract/Cleanse takes). Mutations: book slot not written → 3 fail; Extract copies → 7 fail; both include the
      property. Nothing on the client receives the panel or sends picks yet (6bC), so on a real server the grindstone
      shows nothing to pick until then. 195 JUnit + 96 GameTests.

### Stage 6bC — Grindstone screen → commit  ✅ 2026-10-07
- [x] 6bC.1 Generalize the layout: `anvil/AnvilPanelLayout` → `screen/SidePanelLayout` with a tab width parameter
      (anvil 14, grindstone 0); update `AnvilScreenMixin` and the test (pure rename + parameter; existing cases unchanged).
- [x] 6bC.2 `grindstone/GrindstonePanelView` (pure): rows (action header, enchantment + level + cost, refused reason),
      the selected row, and a footer (cost / free / too expensive with the max / insufficient levels / pick an action).
      JUnit for every status and action. `GrindstoneLangKeysTest` walks every key it can produce.
- [x] 6bC.3 `client/GrindstoneOptionsState` (receiver; cleared on close, config INIT, disconnect) + `GrindstoneBridge`
      client sources (server is ours, levels charged).
- [x] 6bC.4 `mixin/client/GrindstoneScreenMixin`: `init` TAIL applies the layout to `leftPos`; `renderBg` blits at
      `leftPos` (vanilla uses the centered value) and shows the error sprite only on a refusal; panel drawn at TAIL; a
      click on a row sends the choice (clicking the selected row clears it); `hasClickedOutside` false over the panel.
- [x] 6bC.5 Lang keys (en_us) for actions, statuses and footer forms.
- [x] 6bC.6 Client GameTest: singleplayer, real grindstone, sword (Sharpness + Unbreaking) + 3 books → panel rows;
      click Sharpness → result is a Sharpness book; take → sword keeps Unbreaking, 2 books left; cursed item → Cleanse
      row with its cost; tiny window → panel on screen. Screenshots.
- [x] 6bC.7 DESIGN §6 (grindstone modes as built), state.md, decisions.md. Owner `runClient` list: panel look, a
      modded client on a vanilla server keeping the vanilla grindstone. Mutation: row click sends nothing → client test
      fails. Build + `runClientGameTest` green, commit.
      **Done:** wider than 6bC.1 planned: the shared screen pieces moved out of `anvil` so the grindstone doesn't
      import anvil classes. `screen/SidePanelLayout` (tab width a parameter), `screen/PanelText` (`Tone`, `Arg`, `Msg`,
      `Row`), `client/SidePanel` (was `AnvilPreviewPanel`; adds a selected row, hover and `rowAt`) and
      `client/PanelTextResolver` (was `AnvilPreviewText`). Anvil behaviour unchanged (its client test still passes).
      `GrindstoneScreen` declares neither `init` nor `mouseClicked`, so the mixin adds both overrides; `renderBg` is
      shifted with a pose translation (vanilla blits at the centred X, not `leftPos`). The red X is a v2
      `@WrapWithCondition`. The panel eats its own clicks, so a carried item is never thrown from it. Tests:
      `GrindstonePanelViewTest` 7, `GrindstoneLangKeysTest` 3, `SidePanelLayoutTest` +1 (no tab),
      `GrindstoneScreenClientGameTest` (narrow shift, rows, click → server pick → result → take moves Sharpness and
      charges 5, Cleanse row at 30, panel click keeps carried diamonds, close clears). Mutation: the row click sends
      nothing → "the click picked Sharpness on the server" fails. Screenshots `0004`–`0007` in
      `build/run/clientGameTest/screenshots/`. 206 JUnit + 96 GameTests + 2 client GameTests.
      **Not automated (owner):** the panel's look in a normal session, and a modded client on a vanilla server keeping
      the vanilla grindstone (no book placement, no panel).

## Phase 7 — Enchanting table (four stages, one commit each)  ✅ 2026-10-07 (except the owner `runClient` checks)
**Goal:** the table lists every offered enchantment applicable to the item in slot 0, the player picks any number of
them, each at a chosen level, and sees the per-line XP cost, the lapis and the totals before committing. The server
validates, prices and charges everything (DESIGN §5, §6). No randomness.
**Owner (2026-10-07, Phase 7 start):** (1) **Layout:** keep the vanilla window (item and lapis slots, book animation).
The three-offer area becomes the totals and an Enchant button. The list is a scrollable side panel placed like the
anvil's and grindstone's. (2) **Enchanted items and enchanted books are accepted.** The player can add enchantments and
raise levels. Upgrades are priced by `upgrade_cost_mode`. (3) **Treasure at the table: Mending and Frost Walker
only**, at full power. Soul Speed, Swift Sneak and Wind Burst stay loot-only. (4) **Curses now:** Binding and Vanishing
are opt-in picks with the §5a negative line.
**Facts (decompiled 1.21.11 `EnchantmentMenu`/`EnchantmentScreen`/`EnchantingTableBlock`):** `javap`: `$1` is the
`SimpleContainer`, `$2` slot 0 (max stack 1, accepts anything), `$3` slot 1 (lapis only). No slot mixin is planned.
A menu built with `ContainerLevelAccess.NULL` (another mod's portable table) never runs `access.execute`, so it shows
nothing and can't enchant (fail closed). `slotsChanged` counts shelves with the public statics `EnchantingTableBlock.BOOKSHELF_OFFSETS` +
`isValidBookShelf(level, pos, offset)` and rolls the three offers. It only runs for `isEnchantable()` items, which
excludes anything already enchanted. `clickMenuButton(player, 0..2)` is vanilla's C2S. It charges only 1–3 levels (the
cost is a requirement, not a charge), consumes 1–3 lapis, transmutes a plain book into `ENCHANTED_BOOK`, awards
`Stats.ENCHANT_ITEM`, triggers `ENCHANTED_ITEM`, rerolls the seed and plays `ENCHANTMENT_TABLE_USE`. `costs[]`,
`enchantClue[]`, `levelClue[]` and the seed are `DataSlot`s. The screen draws the three 108×19 rows at
`(width − imageWidth)/2 + 60, … + 14 + 19n` (centred X again, not `leftPos`). Its `mouseClicked` hit-tests the same
rectangles. Vanilla offers come from `#minecraft:in_enchanting_table` (= `#non_treasure`). `#treasure` = Binding,
Vanishing, Swift Sneak, Soul Speed, Frost Walker, Mending, Wind Burst.
**Agent calls (decisions.md "Enchanting table"):**
- **Pool:** the enchantment tag `enchanting2:offered_at_table` = `#minecraft:in_enchanting_table` + Mending + Frost
  Walker + Binding + Vanishing. Mods already put their table enchantments in the vanilla tag, so they're included.
  Datapacks can extend the tag. Phase 9's per-enchantment flags will override it per ID.
- **Full power:** `table.treasure_requires_full_power` (new `table` section, default true) gates `#minecraft:treasure`
  minus `#minecraft:curse`. A curse is a discount, not power, so it's never gated.
- **Applicability = `supported_items`** (`EnchantRules`, as at the anvil), not vanilla's narrower `primary_items`.
  DESIGN §4 says applicability comes from `supported_items`. A narrower table would just send players to the anvil.
- **Power:** `tier = 1 + floor(shelves × 4 / 15)`, `maxSelectable = ceil(maxLevel × tier / 5)` (DESIGN §6). Shelves
  are recounted on every compute and again at commit, so breaking a shelf mid-menu can't sell a level it no longer
  allows.
- **Selection** = an ID → level map held by the server per menu, set only by a C2S payload. It's cleared when slot 0
  changes (item or components) and kept when only the lapis changes. **All or nothing:** a selection with any invalid
  entry produces no enchant. The panel names the entry and why. The server never applies a subset the player didn't
  pick. Entries are checked in ID order against the result so far, so the later one of a conflicting pair gets
  `CONFLICT`.
- **Commit** is our own C2S `TableEnchantPayload(containerId, selection, levels, lapis)`. The server recomputes, and
  refuses (logged, panel resent) when the selection or either number differs from what it would charge now. The
  player is never charged a price they weren't shown, even after a `/reload` or a broken shelf. It also avoids any
  ordering dependency between a selection packet and vanilla's button packet. Vanilla `clickMenuButton` and
  `slotsChanged` are cancelled for menus we handle.
- **Charge:** `giveExperienceLevels(−levels)` (as at the anvil and grindstone), lapis consumed from slot 1. Stat,
  advancement trigger, seed reroll and sound kept as vanilla. Creative follows the anvil (`creative_free`, no cap).
- `NetworkProtocol.VERSION` → 4: a v3 client on a v4 server would wait for vanilla offers that never come.
**Not doing:** per-enchantment table flags or weights (Phase 9); new curses (10e); lowering or removing enchantments at
the table; random or "surprise" offers; a vanilla-server table for a modded client (it keeps vanilla's screen);
changing shelf placement rules or the 15-shelf maximum.
**Blast radius:** with the mod on the server, every table menu runs our code, and vanilla's random offers never appear.
Items get ordinary `ENCHANTMENTS` / `STORED_ENCHANTMENTS`, so removing the mod leaves them working (levels above
vanilla max keep their stored level, as decided in Phase 4). A v3 client is disconnected with the existing message.
Client: the table screen only, and only when the server is ours. Nothing is deleted or overwritten. Items in the slots
go back through vanilla's `removed()` → `clearContainer`.
**Rollback:** `git revert` the stage's commit. Nothing outside the repo is touched.

### Stage 7A — Rules, pool, calculator (server logic, no menu wiring) → commit  ✅ 2026-10-07
- [x] 7A.1 `config/TableSection` (`treasure_requires_full_power`, default true) in `CommonConfig` (+ write/read, synced
      like the others). `ConfigParseTest`/`ConfigViewTest` cases; config screen toggle + lang key
      (`ConfigLangKeysTest`).
- [x] 7A.2 `table/TablePower` (pure): `tier(shelves)`, `maxSelectable(maxLevel, tier)`, shelves clamped to 0..15.
      JUnit: every shelf count 0–15 → DESIGN's tier table; max 1/3/4/5 × tier 1–5; 15 shelves unlocks every max,
      including a 255 override.
- [x] 7A.3 `data/enchanting2/tags/enchantment/offered_at_table.json` + `table/TablePool` (the tag key, `isGated(holder,
      config)`). GameTest: Mending, Frost Walker, Binding, Vanishing and every `#in_enchanting_table` member are in;
      Soul Speed, Swift Sneak and Wind Burst aren't.
- [x] 7A.4 `table/TableSelection` (sorted ID → level map, size ≤ the pool, levels 1..255), `table/TableRow` (ID, current
      level, max selectable, max level, selected level or 0, status), `table/TableOutcome` (status, rows,
      `CostBreakdown`, result stack, levels, lapis). Status: `OK | NONE | NOTHING_TO_ENCHANT | NO_SELECTION | INVALID_PICK
      | TOO_EXPENSIVE | INSUFFICIENT_LEVELS | INSUFFICIENT_LAPIS`. Row status: `OK | NEEDS_POWER | NEEDS_FULL_POWER |
      ALREADY_AT_LEVEL | CONFLICT (+ with) | ABOVE_MAX`.
- [x] 7A.5 `table/TableCalculator.compute(target, lapisCount, shelves, selection, playerLevels, creative, config,
      pricing, pool)`: rows = pool members `EnchantRules` doesn't call `NOT_APPLICABLE` (every member for a book), in ID
      order. Unselected rows are checked against the item plus the current selection, so they show a conflict before
      they're picked. The result is a copy with `EnchantRules.withEnchantment` per pick. A plain book is transmuted to
      `ENCHANTED_BOOK`, and an enchanted book gains stored entries. Priced with `CostRequest.table`. Never mutates its
      inputs. Failures are logged and fail closed (`NONE`).
- [x] 7A.6 `TableCalculatorGameTest`: sword rows match `supported_items`; book lists the whole pool; tier gating at 0/4/
      8/12/15 shelves; Mending/Frost Walker need 15 (and don't with the toggle off); curses ungated; curse pick → negative
      line, lapis follows the discounted cost; Mending + Eff V = 17 and lapis 17; enchanted pickaxe Eff III → V = 2;
      Mending in two visits = one visit (DESIGN table); upgrade on an item past the tier's max → `NEEDS_POWER`, existing
      level untouched; forged picks (not in pool, not applicable, above max, above tier, already at level, conflict
      with the family exclusive) → `INVALID_PICK`, no result; cap → `TOO_EXPENSIVE`, creative bypasses; `creative_free`
      → 0 levels and 0 lapis; never removes or lowers an existing entry (third-party entry kept). Mutation: skip the
      tier check → fails. Build green, commit.
      **Done:** `TableOutcome` carries the shelf count (the tier is derived) and an `Invalid` (the first blocking pick
      in ID order: ID, level, reason, conflict). `NOT_OFFERED` is an `Invalid` reason only: a row is offered by
      definition, and the constructor refuses it. Its cost is present exactly from `TOO_EXPENSIVE` on (`priced()`), so
      the panel can show "requires X (you have Y)". Row statuses add `MAXED` (unpicked, at or past the max). The
      calculator takes the player's levels for `INSUFFICIENT_LEVELS`. Added to the plan: a stack of more than one item,
      and a plain `BOOK` that holds `ENCHANTMENTS` (only a command makes one; enchanting it would leave entries in both
      components) → `NONE`, fail closed. Shelf counts above 15 are clamped (vanilla's offsets cover 32 positions). The
      7A.3 pool checks live in `TableCalculatorGameTest` (17 tests). Mutation (tier check skipped) → 2 fail. 213 JUnit
      (+5 `TablePowerTest`, +2 table config parse) + 113 GameTests.

### Stage 7B — Server menu + payloads → commit  ✅ 2026-10-07
- [x] 7B.1 `network/TableSelectPayload` (C2S: container ID, selection), `network/TableEnchantPayload` (C2S: container
      ID, selection, levels, lapis), `network/TablePreviewPayload` (S2C: container ID, status, rows, breakdown lines,
      totals, shelves, tier). Sizes checked in the constructor, shared codecs in `PayloadCodecs`. `VERSION` → 4.
      Registered in `registerPayloads`.
- [x] 7B.2 `mixin/EnchantmentMenuMixin`: player captured at `<init>`; `slotsChanged` HEAD → on the server, count shelves
      through `access`, compute, send the preview when changed (dedup as the grindstone), cancel; on a client of our
      server, cancel. `clickMenuButton` HEAD → return false for menus we handle (logged at debug). Duck
      `table/TableMenuAccess` (`select`, `enchant`, outcome, last preview) for the handlers and tests.
      `table/TableBridge` (client sources: server is ours, latest preview).
- [x] 7B.3 `ServerNetworking.handleTableSelect` / `handleTableEnchant`: ignore unless `player.containerMenu` is a table
      with that ID. Enchant recomputes. It refuses unless status `OK` and the payload's selection, levels and lapis equal
      the recomputed ones. It then writes slot 0, consumes lapis, charges levels, awards the stat and trigger, plays the
      sound, clears the selection and recomputes.
- [x] 7B.4 `TableGameTest` (real table + shelves placed in the structure, real `EnchantmentMenu`, mock player with
      `instabuild` off): no vanilla offers (`costs[]` stay 0); select → preview; enchant → exact levels and lapis
      charged, item has exactly the picks; plain book → enchanted book with several entries; enchanted book gains
      entries; upgrade; forged payloads (wrong container ID, inapplicable, above max, above tier, not in pool, Soul
      Speed, conflict, stale levels, stale lapis, a selection different from the stored one) → nothing charged or
      changed; insufficient levels or lapis → refused; shelf broken after select → the enchant is refused and the
      preview resent; slot 0 swapped → selection cleared; lapis count changed → kept; vanilla `clickMenuButton(0)` →
      false, nothing changed; codec round trips including every enum and an oversize selection refused. **Property**
      (seeded, 300): random item/book, existing enchantments, shelves, selection → after an OK enchant, every prior
      (ID, level) is still present at ≥ its level and the charge equals the preview. Mutations: skip the stale-cost check
      → fails; charge before validating → fails. `NetworkProtocolTest` pins v4. Build green, commit.
      **Done:** the preview carries the selection as the server holds it (the client echoes it in the Enchant payload)
      and enforces `TableOutcome`'s invariants (an OK or priced status always has a cost), so a malformed packet can't
      crash the client. The breakdown codec moved from `AnvilPreviewPayload` to `PayloadCodecs` (anvil wire format
      unchanged). Shelves are counted through `access.evaluate`, so a `ContainerLevelAccess.NULL` menu is `NONE` and
      can't enchant. The enchant compares selection + levels + lapis, **not the status**. A player who gained levels
      since a panel that said "insufficient" can enchant at the shown price, and the server's own check decides. A
      refused enchant force-resends the panel. Vanilla's seed reroll is dropped (nothing reads the seed). The
      `ENCHANTED_ITEM` trigger gets the levels charged. Slot 1 counts as 0 lapis unless it holds lapis. `TableGameTest`
      12 (incl. a corner shelf blocked by stone → 14, the property: 300 cases, ≥ 60 enchants). Mutations: stale-number
      check dropped → 1 fails; payload levels charged before validating → 6 fail (incl. the property). 213 JUnit + 125
      GameTests + 2 client GameTests.

### Stage 7C — Panel model and scrolling → commit  ✅ 2026-10-07
- [x] 7C.1 `table/TablePanelView` (pure): a row per offered enchantment (name, current → selected level, its line cost
      or the reason it can't be picked), surcharge / discount / total lines, footer (cost and lapis, too expensive with
      the max, insufficient levels or lapis, invalid pick named, pick something). JUnit for every status.
      `TableLangKeysTest` walks every key it can produce. Lang keys (en_us).
- [x] 7C.2 `client/SidePanel`: vertical scrolling (wheel, clamped, kept across rebuilds while the item is unchanged)
      and per-row hit zones (`<` / `>` level arrows, the row body). Anvil and grindstone panels unchanged (a list that
      fits never scrolls). `screen/SidePanelLayout` takes the panel width (the table panel is wider for names + level
      + cost); JUnit for the new width.
- [x] 7C.3 `client/TablePreviewState` (receiver; cleared on close, config INIT, disconnect), `client/TableScreenAccess`.
      Build green, commit.
      **Done:** `TablePanelView` has `body` (one item per row, plus a reason line under every row that can't be picked,
      or whose pick is refused), `footer` (surcharge and curse lines, totals, status), `ready` and `click`. **`ready` is
      re-derived from the cost and the client's own levels and lapis**, so a stale server status never greys out a
      button that would work. A body click picks at the highest level the table sells, or unpicks; the stepper stays
      within `current + 1 .. maxSelectable`. Reasons name the shelves needed (`TablePower.shelvesFor`: 0/4/8/12/15,
      checked against the definition for every max and level up to 255). `PanelText` gained `Stepper` and `Zone`.
      `SidePanel.drawList`: a scrolling body under the title (scrollbar, 2 lines per wheel notch, clamped on draw), a
      pinned footer, several selected rows, steppers, `hitAt`/`arrowCenterX`; the existing `draw` is unchanged (anvil
      and grindstone client tests pass). `SidePanelLayout.compute` takes a width (`TablePanelView.PANEL_WIDTH` 160).
      `TableScreenAccess` moved to 7D with the screen it describes. Tests: `TablePanelViewTest` 10,
      `TableLangKeysTest` 3 (its own coverage check caught that the walk never reached "insufficient lapis"),
      `TablePowerTest` +2, `SidePanelLayoutTest` +1. 228 JUnit + 125 GameTests + 2 client GameTests.

### Stage 7D — Enchanting table screen → commit  ✅ 2026-10-07
- [x] 7D.1 `mixin/client/EnchantmentScreenMixin`: `init` TAIL applies the layout to `leftPos`; `renderBg` shifted to
      `leftPos` (pose translation, as the grindstone); vanilla's three offer rows suppressed. In their 108×57 area: XP
      and lapis totals, the status line, and an Enchant button drawn with vanilla's own `enchantment_slot` sprites
      (normal / highlighted / disabled). Panel at TAIL. Row body click toggles a pick (default level = the highest
      selectable), arrows step the level, and each change sends `TableSelectPayload`. Enchant sends
      `TableEnchantPayload` with the shown numbers: the cost's levels and lapis, enabled for a priced status when the
      local player's levels and the lapis slot cover them. The panel's `INSUFFICIENT_LEVELS` can be stale, see 7B.
      Vanilla `mouseClicked` offer hit-test is skipped.
      `hasClickedOutside` is false over the panel.
- [x] 7D.2 Client GameTest: singleplayer, real table with 15 shelves, diamond sword + lapis → rows; pick Sharpness V
      and Mending → totals shown; Enchant → sword has both, levels and lapis charged; plain book with two picks →
      enchanted book; scrolling on a book reaches the last row; tiny window → panel on screen; a panel click keeps a
      carried item. Screenshots.
- [x] 7D.3 DESIGN §6 (table as built), state.md, decisions.md. Owner `runClient` list: panel look, a modded client on a
      vanilla server keeping the vanilla table. Mutation: the Enchant button sends nothing → client test fails. Build +
      `runClientGameTest` green, commit.
      **Done:** **changed from the plan:** no pose translation. The book is a picture-in-picture element at absolute
      coordinates, so `renderBg`'s own X (local 5) is set to `leftPos` instead (gotchas.md). `EnchantmentScreen`
      declares `init` and `mouseClicked` (injected) but not `mouseScrolled`, `hasClickedOutside` or `removed`
      (overridden). Vanilla's offer sprites are suppressed with a `@WrapWithCondition`. The book opens when the panel
      has rows (`@ModifyVariable` on `tickBook`'s boolean). `TableScreenAccess` (layout, panel and window text, row Y,
      arrow X, scroll). `TableScreenClientGameTest`: a real table + 15 shelves 3 blocks from the player; narrow window
      shifts; sword rows; clicks pick Sharpness V and Mending on the server; window shows 17 levels and 17 lapis;
      Enchant → sword has both, 17 levels and 17 lapis taken (vanilla's "Enchanter" advancement fires); stepper V → IV
      → V, re-click unpicks; plain book with Aqua Affinity + Efficiency, its list scrolls to the last row, enchanted
      book; a panel click keeps carried diamonds; close clears. Mutation: Enchant sends nothing → "the sword has
      Sharpness V and Mending" fails. Screenshots `0008`–`0012`. 228 JUnit + 125 GameTests + 3 client GameTests.
      **Not automated (owner):** the look in a normal session (is the ready Enchant button clearly clickable?), and a
      modded client on a vanilla server keeping the vanilla table.

## Phase 8 — Conflict effect resolution + Fortune on all tools (four stages, one commit each)  ✅ 2026-10-08 (except the owner `runClient` check)
**Goal:** every relaxed conflict family does what DESIGN §4's table says when combined, and Fortune multiplies the drops
of natural blocks in the `fortune_multiplies/<tool>` tags, never of a block a player placed or moved (DESIGN §6).
**Owner (2026-10-08, Phase 8 start):** (1) **Every drop** of a tagged natural block is multiplied: Fortune V gravel
gives ~3.1× flint, clay ~3.1× clay balls, snow blocks snowballs, mushroom blocks mushrooms. (2) **Every hand-placed
block** is marked (mcMMO behavior), not only tagged ones, so a datapack or `/reload` adding a block to a tag later
can't pay out on blocks already placed. (3) **Any Riptide trident** throws normally out of water and rain (Riptide-only
ones too), and launches in water or rain. (4) **SILK_WINS:** a tool with Silk Touch + Fortune still multiplies a tagged
block **unless Silk changed the drop** (logs, sand, dirt, moss → multiplied; clay, snow, mushroom blocks, gravel at
Fortune III+ → one silk-touched block).
**Facts (1.21.11 `javap`, FAPI 0.141.6 sources):**
- `TridentItem.use` and `releaseUsing` each call `EnchantmentHelper.getTridentSpinAttackStrength(stack, entity)` once,
  then `> 0 && !player.isInWaterOrRain()` → FAIL / return false. With a strength of 0, `releaseUsing` takes the
  normal throw branch (Loyalty and Channeling are on the thrown entity's stack).
- `ServerPlayerGameMode.destroyBlock`: `playerWillDestroy` → `level.removeBlock` → `Block.destroy` →
  `playerDestroy` (loot). The block is gone before its loot is rolled, so a mark must be cleared **after** the loot,
  not on removal.
- Fabric loot v3 `LootTableEvents.MODIFY_DROPS(Holder<LootTable>, LootContext, List<ItemStack>)` wraps
  `LootTable.getRandomItemsRaw`, so it fires for every table roll, nested `loot_table` entries included.
- Fabric attachments: a decode error is logged ("Skipping invalid attachments") and **the attachment is dropped**. To
  fail closed, our codec must never fail. It decodes bad data into an `UNREADABLE` value that keeps the raw tag and
  writes it back verbatim. An unknown type (mod removed, then added back) is also dropped, so that chunk's marks are
  lost and its placed blocks count as natural once (the same one-time exploit as pre-install blocks, DESIGN §2a).
- Fortune itself has no effect components. Its bonus lives in loot tables (`apply_bonus`), decisions.md Phase 1.8.
**Agent calls (decisions.md "Phase 8"):**
- **8.2 needs no effect code.** In 1.21 every damage, protection, ammo, projectile and movement effect is a data
  component evaluated per enchantment and summed. Our families only relax `EnchantRules`. The stage adds GameTests that
  prove each combination works, and that the vanilla EPF clamp (`CombatRules`, 20) still limits Protection stacking.
- **Trident:** one `@WrapOperation` on both `getTridentSpinAttackStrength` calls in `TridentItem`, returning 0 when
  the user is a player not `isInWaterOrRain()`. It keys off the item, not the `allow_together` toggle (a grandfathered
  or `/enchant`ed trident behaves the same). It runs on both sides, so the client predicts the same thing.
- **`silk_fortune_mode`** goes in a new `fortune` section of `common.json5` (`SILK_WINS` default |
  `FORTUNE_MULTIPLIES_SILK`). Its config-screen entry carries the duplication warning in its tooltip.
- **Which tool kind:** the tool must be in `#minecraft:axes` / `#minecraft:shovels` / `#minecraft:hoes` /
  `#minecraft:pickaxes` and the block in the matching `enchanting2:fortune_multiplies/<kind>` tag. A tool in several
  kinds (a modded paxel) gets the union.
- **`FORTUNE_MULTIPLIES_SILK`** multiplies the silk-touched drop of a block in its tool's `fortune_multiplies` tag or
  in `enchanting2:fortune_multiplies_silk` (default `#c:ores`). Same tracker, same exclusions.
- **"Silk changed the drop"** is decided by rolling the block's own loot table once more, with a copy of the tool
  without Silk Touch, and comparing item types. The re-roll goes through a re-entrancy guard so our own handler doesn't
  act on it. Gravel at Fortune I–II is the one default-tag block where the answer varies roll to roll (vanilla's flint
  chance), which is acceptable.
- **Only the block's own table:** the handler acts only when the context has `BLOCK_STATE`, `ORIGIN` and `TOOL`, and
  the table is `state.getBlock().getLootTable()`. It's a no-op for chests, mobs and nested tables.
- **Ancient debris** is excluded in code by ID, whatever the tags say.
- **Storage:** per chunk, a map of section Y → 4096-bit set (≤ 512 bytes per section). A dense build is bounded by
  the chunk, unlike a set of packed longs.
- **Marking:** `BlockItem.place` success (players **and** dispensers) → mark. Piston `moveBlocks` → three passes (read
  every source mark, clear them all, write at the destinations). `FallingBlockEntity.fall` moves the mark from the
  position to the entity (a persistent entity attachment), and landing writes it back. Endermen placing a carried
  block → mark (it may be a placed block, so fail closed). Lava/water generator output and snow-golem snow → mark
  only when the block is in a `fortune_multiplies` tag (DESIGN §6). Grown blocks (saplings, bone meal, moss spread)
  stay natural. A player break clears the mark in `PlayerBlockBreakEvents.AFTER` (after the loot). Other removals
  (explosions, fire, water) leave a stale mark, which fails closed (one natural block there gets no bonus once).
- In-place conversions (dirt → path, farmland, mud; log → stripped) keep the mark, because a mark belongs to the
  position.
**Not doing:** Fortune on pickaxe blocks beyond vanilla; a per-block multiplier config; a server command to inspect or
clear marks (a later debug tool if needed); migrating mcMMO's single-file store; changing vanilla's crop, ore or leaf
Fortune; tracking blocks moved by other mods' movers (Create contraptions etc.), which count as natural.
**Blast radius:** server only (no client code beyond the config-screen toggle and the shared trident mixin). Every
block break with a Fortune tool on a tagged block can drop more items. Every block placement now writes a chunk
attachment (the chunk is marked dirty, which it already is after a placement). Chunk NBT gains `fabric:attachments` →
`enchanting2:placed_blocks`. Removing the mod: Fabric WARNs once per chunk load about an unknown attachment and drops
it on the next save, and the world otherwise loads unchanged. Nothing existing is deleted or overwritten.
**Rollback:** `git revert` the stage's commit. Dev worlds in `run/` (gitignored, disposable) may hold attachments.
Nothing outside the repo is touched.

### Stage 8A — Conflict effects: config, trident, family GameTests → commit  ✅ 2026-10-08
- [x] 8A.1 `config/FortuneSection` (`silk_fortune_mode`: `SILK_WINS` | `FORTUNE_MULTIPLIES_SILK`, default `SILK_WINS`)
      in `CommonConfig` (read/write, synced like the others). An unknown value → default + `ConfigIssue`.
      `ConfigParseTest`/`ConfigViewTest` cases. Config-screen cycling entry, its tooltip names the ore-duplication
      loop, lang keys (`ConfigLangKeysTest`).
- [x] 8A.2 `mixin/TridentItemMixin`: `@WrapOperation` both `getTridentSpinAttackStrength` calls → 0 for a player not in
      water or rain. GameTests (mock player, `EntityInvoker` water refresh per gotchas.md): Riptide + Loyalty on land →
      a `ThrownTrident` with Loyalty, no spin; Riptide alone on land → thrown; Riptide + Loyalty in water → spin, no
      projectile; plain Loyalty trident unchanged. Mutation: drop the wrap → the land tests fail.
- [x] 8A.3 `ConflictEffectsGameTest` (8.2): Sharpness V + Smite V vs a zombie deals the sum of both bonuses (vs each
      alone); Bane + Sharpness vs a spider likewise; Density + Breach on a mace both apply; Protection IV + Blast
      Protection IV + Fire Protection IV + Projectile Protection IV still clamp at EPF 20 (check `CombatRules` by
      `javap` first); Mending + Infinity: a bow with both fires without consuming the arrow and is repaired by an
      orb; Multishot + Piercing fires three arrows each with pierce level; Depth Strider + Frost Walker: water freezes
      and the movement attribute applies. Each test fails if the family toggle is off and the item is built through
      `EnchantRules` (so the test proves the combination came from us, not `/enchant`).
- [x] 8A.4 Build green (JUnit + GameTests), update DESIGN §4 if anything differed, commit.
      **Done:** the enum is `fortune/SilkFortuneMode`, and the screen has its own "Fortune" category. The trident tests
      live in `ConflictEffectsGameTest` (10 tests: 3 trident, 7 family). Every item is built through `EnchantRules`,
      and each test first asserts the same build is a `CONFLICT` with its family off. Levels come from the live
      registry max. `CombatRules.getDamageAfterMagicAbsorb` clamps EPF to 0..20 (`javap`). With four protections on
      every piece the explosion EPF is above 20, and 10 damage → 2. Impaling: no vanilla item takes it with another
      damage member (DESIGN §4 note). Frost Walker needed `setOnGround(true)` on the never-ticked mock player.
      Mutation (trident mixin unregistered) → exactly the two land tests fail ("use refused the trident"). 232
      JUnit (+4), 135 GameTests (+10).

### Stage 8B — MC-free core: marks and the Fortune rule → commit  ✅ 2026-10-08
- [x] 8B.1 `fortune/PlacedMarks`: per chunk, section Y → `long[64]` bitset over local (x, y, z). `mark`, `clear`,
      `isMarked`, `isEmpty`, `count`. Three-pass `move(from[], to[])` (read all, clear all, write all), so a pushed
      column keeps every mark. `UNREADABLE` state: `isMarked` → true everywhere, mutations are no-ops, the raw data is
      kept. Defensive copies, no MC types (positions as packed longs or ints).
- [x] 8B.2 `fortune/PlacedMarksCodec` (versioned `{v:1, sections:[{y, bits}]}`): decode never fails. Wrong version,
      wrong array length, duplicate Y, or a non-map → `UNREADABLE(raw)` + a WARN with the reason. Encoding an
      `UNREADABLE` writes the raw value back unchanged. An empty set encodes to nothing (no attachment written).
- [x] 8B.3 `fortune/FortuneRule` (pure): `multiplier(level, random)` = vanilla ore formula `1 + max(0, nextInt(level+2)
      − 1)`. `decide(fortuneLevel, hasSilk, mode, silkChangedDrop, tagged, silkTagged, natural, excluded)` →
      `MULTIPLY | NONE` with a reason enum for the debug log.
- [x] 8B.4 JUnit: every bit of a section round-trips; the boundary sections (y −64, 319); move of a 12-block column
      by one (overlapping from/to) keeps 12 marks; clear of an unmarked position is a no-op; codec round-trip; each
      corrupt input → `UNREADABLE`, marked everywhere, re-encodes byte-identical; `decide` truth table (silk × mode ×
      changed × natural × excluded); multiplier distribution at Fortune V within bounds (seeded random, mean 22/7 ≈ 3.14).
      Mutation: single-pass move → the column test fails. Build green, commit.
      **Done:** `PlacedMarks` is immutable (a chunk attachment is only saved when it's set again), with copy-on-write
      per section. `moved` takes packed local positions (`pack(x, y, z)`). The codec is a hand-written DFU `Codec`
      tested with `JsonOps`. It reads `sections` through `ops.getStream`, because `Dynamic.asList` turns a non-list into
      an empty list (fail open). Section Y is bounded −128..127. `decide` takes the origin and the Silk re-roll as
      suppliers. Order: Fortune → excluded → tags → origin → re-roll, so the re-roll only runs for a natural, tagged
      block on a Silk tool under SILK_WINS (tested). Fortune V averages 22/7, not 2.9 (gotchas.md). Mutations: a
      single-pass move and the `asList` read → exactly one test fails each. 256 JUnit (+24), 135 GameTests.

### Stage 8C — Persistence and marking hooks → commit  ✅ 2026-10-08
- [x] 8C.1 `fortune/PlacedBlocks` (MC side): chunk `AttachmentType<PlacedMarks>` `enchanting2:placed_blocks`
      (persistent, 8B codec, not synced), entity `AttachmentType<Boolean>` `enchanting2:placed_falling`. API:
      `mark(level, pos)`, `clear(level, pos)`, `isNatural(level, pos)` (`UNREADABLE` → false, logged at WARN once per
      chunk per load), `move(level, from[], to[])`. An emptied set removes the attachment.
- [x] 8C.2 Hooks: `BlockItem.place` success → mark (mixin at `RETURN`, server side only); `PistonBaseBlock.moveBlocks` →
      three passes via `PlacedBlocks.move` (read the to-move and to-destroy lists vanilla builds; check targets by
      `javap` first); `FallingBlockEntity.fall` → move to the entity, its landing `setBlock` → write back
      (destroyed-on-landing → nothing); enderman `LeaveBlockGoal` place → mark; fluid generator and snow golem
      placements → mark if tagged; `PlayerBlockBreakEvents.AFTER` → clear.
- [x] 8C.3 `PlacedBlocksGameTest`: place by player → marked; by dispenser → marked; player break → cleared; piston push
      and pull of a 3-block column → marks at the new positions, none left behind; sticky pull; sand placed in the air →
      falls → marked where it lands; natural sand falling → not marked; enderman place → marked; cobble generator
      output not tagged → not marked (and marked with a test tag); explosion leaves a stale mark (documented behavior).
      Persistence: encode the attachment through the chunk codec path and back (or a dev-server smoke run in
      `scripts/checks/` that places, restarts, and checks). Build green, commit.
      **Done:** **correction to the plan:** Fabric's `PlayerBlockBreakEvents.AFTER` fires at `Block.destroy`, *before*
      `playerDestroy` rolls the loot (gotchas.md). The clear is our own `ServerPlayerGameModeMixin` at `destroyBlock`
      RETURN. A piston push can cross a chunk border, so the three passes moved out of `PlacedMarks` into MC-free
      `MarkMoves` over a `Store` (`PlacedBlocks.move` adapts it). Pass 2 also clears every destination, so a natural
      block pushed onto a stale mark arrives natural. Landing likewise *sets* the landing position (placed or natural).
      Mixins: `BlockItemMixin` (wraps `placeBlock`, so players and dispensers), `PistonBaseBlockMixin` (wraps `resolve`
      in `moveBlocks` only), `FallingBlockEntityMixin` (`fall` RETURN + the one landing `setBlock` in `tick`),
      `EndermanLeaveBlockGoalMixin`, `LiquidBlockMixin` (both generator `setBlockAndUpdate`s), `LavaFluidMixin`
      (`spreadTo` stone), `SnowGolemMixin`, `ServerPlayerGameModeMixin`. Tags (8D.1) were created here, since the
      tag-gated hooks need them (`FortuneTags`: tool kind = vanilla item tag, block tag per kind, plus
      `fortune_multiplies_silk` = optional `#c:ores`). The GameTest datapack adds obsidian and stone (pickaxes) and snow
      layers (shovels). Persistence: `SerializableChunkData.copyOf(...).write()` carries the attachment under Fabric's
      key and decodes back with `NbtOps`. `read()` was skipped, because it runs `PoiManager` consistency checks on a
      live test chunk. Corrupt NBT → `UNREADABLE`, written back as-is. 12 GameTests (enderman via its leave goal from
      `goalSelector`, GameTest `MobAccessor`). Mutations: all eight mixins off → the 9 hook tests fail and the 2
      persistence tests pass; the break-clear and lava mixins off → exactly those 2 fail. 259 JUnit (+3), 147 GameTests.

### Stage 8D — Fortune drops → commit  ✅ 2026-10-08
- [x] 8D.1 Tags (done in 8C): `data/enchanting2/tags/block/fortune_multiplies/{axes,shovels,hoes,pickaxes}.json` per DESIGN §6's
      table (pickaxes empty), `fortune_multiplies_silk.json` = `#c:ores`. Lang/README note.
- [x] 8D.2 `fortune/FortuneDrops` on `LootTableEvents.MODIFY_DROPS`: guard (block table, params present, Fortune > 0,
      not re-entrant) → tool kinds → tags → silk handling per mode (re-roll for "silk changed") → `isNatural` →
      `FortuneRule` → multiply every stack (split at max stack size). Errors are logged and leave the drops unchanged.
      DEBUG log per decision.
- [x] 8D.3 `FortuneDropsGameTest`: natural log + Fortune V axe → more than one log over N breaks (seeded, or the
      expected-value bound); placed log → exactly 1; piston-pushed placed log → 1; sand placed, fallen, mined → 1;
      natural gravel Fortune V → flint multiplied; clay → clay balls multiplied; wrong tool kind (Fortune hoe on a log) →
      1; Fortune 0 → vanilla; SILK_WINS + Silk on a log → multiplied, on clay → 1 clay block; FORTUNE_MULTIPLIES_SILK +
      Silk on natural diamond ore → multiplied ore, placed → 1; ancient debris in a test tag → never; an `UNREADABLE`
      chunk → no bonus + WARN; a chest or mob loot roll untouched. Mutation: skip `isNatural` → the placed tests fail.
- [x] 8D.4 DESIGN §6 updated to match (owner answers, tag list, silk rule). Build green, commit. state.md, decisions.md.
      **Done:** `FortuneDrops.apply(table, context, drops, mode)` is public. The event passes the live mode, and the
      FORTUNE_MULTIPLIES_SILK GameTests pass it directly (parallel GameTests must not flip the server config). The
      Silk-free re-roll goes through `LootTable.getRandomItems` behind a `ThreadLocal` guard, because loot also rolls
      on worldgen threads. Each "multiplies" test rolls 20 times (an all-×1 run has odds (2/7)^20). The place-and-mine
      test runs a survival mock player: place a log, break it with a Fortune V axe, ten times → exactly 10 logs. The
      GameTest datapack adds ancient debris to pickaxes, so the hard exclusion is tested against a tag that includes it.
      Not done: a piston-pushed or fallen placed block mined → 1. 8C proves the marks follow the block, and 8D proves
      a mark gates the drop, so that test would repeat both. Mutations: origin forced NATURAL → exactly the 4 placed
      and unreadable tests fail. Clearing in Fabric's `AFTER` instead (the plan's original design) → only the
      place-and-mine loop fails (30 logs, gotchas.md). My own test bug on the first run: 1 flint × 6 = 6, not 4.
      259 JUnit, 159 GameTests (+12).

## Phase 9 — Acquisition: flags, weights, treasure, config screen (three stages, one commit each)
**Goal:** every per-enchantment acquisition setting in DESIGN §5/§6 (`enabled`, `table`/`loot`/`trade` flags, loot and
trade weights, `treasure`) works for every random source, and the config screen edits enable plus the three flags.
**Owner (2026-10-09, Phase 9 start):** (1) **Disabled = acquisition only.** It disappears from the table, loot, trades
and mob/villager gear. Existing items keep it and it keeps working; the anvil and grindstone still move it. (2)
**Weighted by default.** Librarian books and `enchant_randomly` loot (both uniform in vanilla) use the registry weight
unless `trade_weight` / `loot_weight` is set. (3) **Levels up to the new max.** Loot and trades roll levels exactly as
vanilla does against the raised max (librarians can sell Protection V). (4) **`treasure` overrides our rules only:** the
table's 15-shelf gate and the librarian's doubled price. Vanilla tags stay untouched. (5) **GUI in Phase 9:** the config
screen edits enable and the three source flags.
**Facts (1.21.11 `javap`):**
- Every weighted random pick goes through `EnchantmentHelper.selectEnchantment`. It calls
  `getAvailableEnchantmentResults` and then `WeightedRandom.getRandomItem(random, list, EnchantmentInstance::weight)`
  twice (first pick, then each extra pick after `filterCompatibleEnchantments`). Callers: `enchantItem` (loot
  `enchant_with_levels`, villager `EnchantedItemForEmeralds` on `#on_traded_equipment`), the `EnchantmentsByCost[WithDifficulty]`
  providers (`enchantItemFromProvider`: mob spawn gear in `Mob`/`Pillager`/`Vindicator`/`EnderMan`/`SkeletonTrapGoal`,
  villager `ItemsForEmeralds`/`ItemsAndEmeraldsToItems`) and vanilla's `EnchantmentMenu` (already replaced by ours).
- Two uniform picks: `EnchantRandomlyFunction.run` → `Util.getRandomSafe(list, random)` over its options (the **whole
  registry** when it has none, filtered by `canEnchant`), and `VillagerTrades$EnchantBookForEmeralds.getOffer` →
  `Registry.getRandomElementOf(#tradeable, random)`, level `Mth.nextInt(min, max)`, price doubled when
  `holder.is(#double_trade_price)`, capped at 64. An empty pick returns no offer.
- `SingleEnchantment` providers and `set_enchantments` name the enchantment in data: fixed grants, not random picks.
- `selectEnchantment` has no level or server. A registry holder's private `owner` is the `MappedRegistry` that made it
  (`createStandAlone(this, key)`), and each server builds its own enchantment registry.
- Registry `weight` codec range is 1..1024.
**Agent calls (decisions.md "Phase 9"):**
- **Config:** all keys go in `enchantments.json5` entries, all optional (absent = built-in): `enabled`, `table`, `loot`,
  `trade` (bool, default true), `loot_weight`, `trade_weight` (1..1024, default the registry weight), `treasure` (bool,
  default the `#treasure` / `#double_trade_price` tag). No schema bump: the keys are additive. Flags only **remove** from
  a source. Adding an enchantment to a source is the tags' job (`#tradeable`, `#on_random_loot`, `offered_at_table`, ...).
- **Sources:** TABLE = our table pool. LOOT = loot tables (`enchant_randomly`, `enchant_with_levels`, fishing, chests,
  bartering) and mob spawn gear. TRADE = librarian books and villager-sold enchanted gear. The table isn't random, so it
  has a flag and no weight.
- **Config in `selectEnchantment`:** the server stamps itself on its enchantment registry at `SERVER_STARTING`
  (`MappedRegistry` duck) and clears it at `SERVER_STOPPED`. The hook reads the holder's `owner` (accessor), then
  that server's live `enchantments.json5`. It's per-server and covers callers from other mods, with no static world
  state. An unstamped registry (a client copy) → vanilla behaviour, WARN once.
- **Trade vs loot inside `selectEnchantment`:** `@WrapMethod` on the three villager `getOffer`s opens a TRADE scope
  (thread-local, restored in `finally`). Outside one, the source is LOOT.
- **Fail-safe:** a pick with no allowed candidate returns empty, which vanilla already handles (no offer; the loot item
  stays unenchanted with vanilla's WARN).
- **GUI:** an "Enchantments" category, one collapsed group per enchantment (translated name, ID as fallback) with four
  tick boxes. Title screen: §8's vanilla IDs plus IDs in the file. Hosting: the server's registry plus the file. A
  remote server shows a label and no values, because `enchantments.json5` isn't synced (decisions.md 2026-10-07
  "Anvil"). A save changes only those four keys, writes a key only when it differs from the default or was already
  set, and keeps every other field and unknown ID. Same backed-up writer, same host-only rule as `common.json5`.
**Not doing:** syncing `enchantments.json5` to clients; GUI for weights, `treasure`, costs or max levels (file-only; a
later stage per §10); removing a disabled enchantment from existing items; filtering fixed grants (`set_enchantments`,
`SingleEnchantment` providers); `/enchant` and the creative tab; capping loot or trade levels (owner); a global
uniform/weighted toggle; adding an enchantment to a source from config (tags do that); vanilla's `EnchantmentMenu`
offers (unused); re-rolling trades villagers already have (they're saved on the villager).
**Blast radius:** on a modded server every newly rolled enchanted loot item, mob spawn item and villager offer goes
through our filter, and librarian / `enchant_randomly` odds change from uniform to weighted. Existing items and existing
villager offers are untouched. No world data is written. `enchantments.json5` gains optional keys. The config screen now
also writes `enchantments.json5` (`.bak` first, host or title screen only). Removing the mod leaves vanilla items only.
**Rollback:** `git revert` the stage's commit. Dev files in `run/` (gitignored) may hold new keys, which an older build
reports as unknown keys and ignores. Nothing outside the repo is touched.

### Stage 9A — Config keys and pure rules (no MC classes) → commit  ✅ 2026-10-09
- [x] 9A.1 `EnchantmentOverrides.Entry`: `enabled`, `table`, `loot`, `trade` (`Optional<Boolean>`), `loot_weight`,
      `trade_weight` (`OptionalInt`, 1..1024), `treasure` (`Optional<Boolean>`). Read (`ConfigReader.readOptionalBool`,
      new), write only the set keys, file comment. Bad value → `ConfigIssue`, key treated as absent.
- [x] 9A.2 `acquisition/Source` (TABLE, LOOT, TRADE) and `acquisition/AcquisitionRules` over `EnchantmentOverrides`:
      `allows(id, source)` (= enabled && flag), `weight(id, source, registryWeight)` (TABLE throws),
      `treasure(id, tagged)`.
- [x] 9A.3 `acquisition/WeightedPick.pick(list, weight, nextInt)`: vanilla's walk. Empty or zero total → empty.
- [x] 9A.4 JUnit: parse, round trip, every range edge, unknown-ID kept; rules truth table; pick distribution (fixed seed,
      bounds) and never picking an excluded item. Mutation: `allows` ignoring `enabled` → a test fails.
      Mutation run: exactly `disabledClosesEverySourceEvenWhenItsFlagSaysTrue` failed. 275 JUnit (+16), 159 GameTests.

### Stage 9B — Every random source on the server → commit  ✅ 2026-10-09
- [x] 9B.1 `acquisition/Acquisition`: register the stamp (`mixin/MappedRegistryMixin` + `AcquisitionHost` duck,
      `mixin/HolderReferenceAccessor`), `rules(holder)`, WARN once when unstamped. `AcquisitionScope` (TRADE).
- [x] 9B.2 `mixin/EnchantmentHelperMixin` on `selectEnchantment`: filter `getAvailableEnchantmentResults` by the scope's
      source, and both `WeightedRandom.getRandomItem` calls weigh by `rules.weight`.
- [x] 9B.3 `mixin/EnchantRandomlyFunctionMixin`: `getRandomSafe` → LOOT filter + `WeightedPick` by loot weight.
- [x] 9B.4 `mixin/EnchantBookForEmeraldsMixin`: `getRandomElementOf` → TRADE filter + trade weight; the
      `#double_trade_price` check → `rules.treasure(id, tagged)`.
- [x] 9B.5 `mixin/VillagerTradeScopeMixin`: `@WrapMethod` TRADE scope on `EnchantedItemForEmeralds`, `ItemsForEmeralds`,
      `ItemsAndEmeraldsToItems` `getOffer`.
- [x] 9B.6 Table: `TablePool.members` filters TABLE; `needsFullPower` uses `rules.treasure`. `EnchantmentMenuMixin` and
      `TableCalculator` take the rules.
- [x] 9B.7 GameTests (`AcquisitionGameTest`, gametest loot tables `e2test:enchant_randomly_book`,
      `e2test:enchant_with_levels_book`): for librarian books, villager gear, `enchant_randomly`, `enchant_with_levels`
      and mob spawn gear, a control run where X appears within N seeded rolls, then `enabled: false` and the source's own
      flag → X never appears, and the other source's flag → X still appears. `trade_weight 1024` Mending > 50% of
      librarian books vs < 15% default. `treasure` doubles the librarian price on the same seed. Table: `table: false` /
      `enabled: false` → not in the pool; `treasure: false` Mending pickable at 0 shelves; `treasure: true` Sharpness
      needs 15. Mutations: drop each mixin's filter → that source's test fails.
      Added `weightsReachSelectEnchantmentBySource` (Looting on a sword: loot_weight moves `enchant_with_levels` only,
      trade_weight moves villager gear only), since no other test reached the weight wrap in `selectEnchantment`.
      Mutation run A (filter in `selectEnchantment`, its weight wrap, `enchant_randomly` pick, librarian pick, treasure
      price, all at once): all 9 acquisition tests failed, each on its own assertion. Run B (trade scope reports LOOT):
      exactly the villager-flags and weights tests failed. The two table tests assert the opposite of the default
      behaviour `treasureNeedsFullPowerAndCursesDont` already pins, so they weren't mutation-run separately. My own
      test bug on the first full run: villager gear rolls at power 5-19, where Looting often isn't a candidate, so
      trade_weight 1024 gives 209/400, not > 3/4. 275 JUnit, 170 GameTests (+11).

### Stage 9C — Config screen "Enchantments" category → commit  ✅ 2026-10-09 (except the owner `runClient` look)
- [x] 9C.1 `ConfigView` generic over the file (`ConfigView<T>`), plus the remote "kept on the server" mode for
      `enchantments.json5`. Existing `ConfigViewTest` cases pass unchanged; new cases for the enchantments view.
- [x] 9C.2 `acquisition/EnchantmentFlagsDraft` (main source set, pure): rows from an ID set, `apply(overrides)` changes
      only the four keys. JUnit: no change → equal; one toggle → one key; cost-only entry keeps its costs; unknown ID kept;
      a toggle back to the default on an absent key leaves it absent.
- [x] 9C.3 `ConfigScreen` category + `ConfigEditing.saveEnchantments` (re-checks editable, backed-up write, host reload).
- [x] 9C.4 Lang keys (en_us) + `ConfigLangKeysTest` coverage.
- [x] 9C.5 `./gradlew runClientGameTest`: the existing client tests stay green. Owner `runClient`: the category's look.
      Added `ConfigScreenClientGameTest`: the screen builds on the title screen (vanilla IDs, `EDIT_FILE`) and in a
      hosted world (registry IDs incl. `e2test:third_party`, `EDIT_HOSTED`), and a save through `ConfigEditing`
      reaches the running server, then the previous file is saved back.
      Done differently from the plan: the title-screen list is `enchantment/VanillaEnchantments.KEYS` (pinned to the
      registry by a new `VanillaEnchantmentIdsGameTest` case), because `MaxLevels.TABLE` only holds the changed rows.
      The mode texts take the file name as `%1$s`, so both categories share them (a lang test forbids `json5` in them).
      My own test bug on the first client run: the save's `server.execute` re-read only runs when the server ticks, and
      `computeOnServer` doesn't tick it (gotchas.md). 286 JUnit (+11), 171 GameTests (+1), 4 client GameTests (+1).

## Phase 10 — New enchantments (DESIGN §9), by route
- [x] 10a Data-only (D): Withering, Poisoning, Freezing, Attack Speed, Critical Strike,
      Toughness, Health, Swiftness, Reach, XP Boost, Cushion. JSON + lang + tags +
      GameTest each. Plan and stages below. (Vision moved to 10b, owner 2026-10-09.)
- [x] 10b Custom effect types (E): Vampirism, TrueShot Aura, Smelting, Beheading, Vision. Plan and stages below.
- [ ] 10c Mixin/network (M): Quick Strike, Magnetic, Ender Link, Keep on Death, Double Jump,
      Lava Walking, Snow Walker, Flight, Speed, Area Mining, Replant, Tiller, Last Stand, Deflect, Shield Bash,
      Steadfast, Spiked, Bulwark, Riposte, Steady Stance (Spiked and Bulwark are E-route; built here with the shields),
      Calm Gaze, Clarity, Phantom Ward, Sustenance (helmet), Endurance (leggings).
      Each gets its own written sub-plan here first, covering edge cases from §9 and dupe tests.
      Every stage that adds an enchantment or changes a price updates docs/ENCHANTMENTS.md (status, cost).
      Lava Walking, Snow Walker (owner, 2026-10-10): boots, max level 1, priced 10 (10f). Sneak to sink for both;
      mobs wearing the boots get the effect. Standing on lava still burns (vanilla's lava contact: set on fire +
      lava damage, so Fire Resistance is what makes it safe). Snow Walker is the leather-boots effect on any boots,
      freeze immunity included. Water Walking dropped (owner: Frost Walker already covers water).
      Speed (owner, 2026-10-10): elytra, +10% flight speed per level, costs 6 per level (its stage adds
      `PricingTable` row `Pricing(0, 6, LEVEL_DIFFERENCE, 0, 10)`; JUnit Speed V = 30, II → V = 18). Max 5, and it
      scales firework-rocket boosts as well as gliding (owner).
      Shields (owner, 2026-10-10): Steadfast (III, axe-disable −33%/level), Spiked (V), Bulwark (III), Riposte (V,
      +3%/level on the next melee hit after a block), Steady Stance (I, priced 10). DESIGN §9 rows. Spiked's damage
      and Bulwark's cooldown are numbers for their sub-plan. Quick Guard, Parry, Arrow Catcher not picked.
      Helmet and leggings (owner, 2026-10-10; DESIGN §9 rows): Sustenance 4%/level, Endurance 5%/level (sprint only).
      Hard Hat, Leaping, Shadow Step not picked. Each stage writes its enchantment's `.desc` lang line (10h's
      completeness test fails otherwise).
      Sure Footing on powder snow (owner, 2026-10-12): 10i ships soul sand and honey; a mixin here scales powder snow's
      stuck slowdown by the same level / 3.
- [x] 10f Prices (owner, 2026-10-10; DESIGN §5): every single-level non-curse enchantment except Mending costs
      10 XP levels, and Frost Walker costs 2 per level. One commit:  ✅ 2026-10-12 (no GameTest asserted a price of 1
      for these, so none moved; JUnit fails 8 cases with `BUILT_IN` reverted)
      - `cost/PricingTable.BUILT_IN`: `minecraft:frost_walker` → `Pricing(0, 2, LEVEL_DIFFERENCE, 0, 10)`. JUnit:
        Frost Walker V = 10, III → V = 4.
      - `cost/PricingTable.BUILT_IN`: a `Pricing(10, 0, LEVEL_DIFFERENCE, 0, 10)` row for `minecraft:aqua_affinity`,
        `silk_touch`, `flame`, `infinity`, `channeling`, `multishot`, and `enchanting2:smelting`, `true_shot_aura`,
        `vision`. Each 10c single-level enchantment (Magnetic, Ender Link, Keep on Death,
        Double Jump, Area Mining, Replant, Lava Walking, Snow Walker, Steady Stance, Calm Gaze, Phantom Ward, Step
        Assist, and any later level-1 one) adds its
        row in its own stage (owner: all of them, built or not).
      - JUnit: `PricingTable.builtIn()` gives 10 for each row and a file override still wins; `CostModel` with
        Silk Touch alone = 10, Silk Touch + Eff V = 15, Mending + Silk Touch = 14; Mending and curses unchanged.
      - Existing tests that price these at 1 (table, anvil, grindstone GameTests) move to 10. Each is updated, never
        deleted.
      - A single-level enchantment whose max is raised by `max_level_override` costs 10 for any level
        (`cost_per_level 0`). The admin sets `cost_per_level` to change that.
- [x] 10g Applicability and vanilla extensions (owner, 2026-10-10; DESIGN §4, §6). Three stages below, one commit
      each: axes and crossbow widening, Silk Touch spawners, infinite buckets.
- [x] 10h Enchantment descriptions on Ctrl (owner, 2026-10-10; DESIGN §6 "Enchantment descriptions in tooltips").
      Client-only, one commit:
      - `en_us.json`: an `enchantment.<ns>.<path>.desc` line for all 43 vanilla enchantments and the 16 built ones,
        from docs/ENCHANTMENTS.md with this mod's numbers.
      - Client: Fabric `ItemTooltipCallback`; under each visible enchantment line (item and stored enchantments) add
        its description while Ctrl is down, else the hint line. Check how 1.21.11 reads modifier keys first (the
        input API changed in 1.21.9). Off when the Enchantment Descriptions mod is loaded (confirm its mod ID).
      - `client.json5` `tooltips.enchantment_descriptions`, `tooltips.show_hint` (both true) + "Display" toggles.
      - JUnit: line builder (key derivation, missing key, hidden enchantment, Ctrl off). JUnit lang-completeness test
        over the vanilla + `enchanting2:` enchantment IDs. Client GameTest screenshot with Ctrl held on a Sharpness
        V book and a Vision helmet.
      How (2026-10-12, javap 1.21.11): `Minecraft.hasControlDown()` still exists (no new input API needed);
      `Language.getInstance().has(key)` for a missing key; Fabric `ItemTooltipCallback.getTooltip(stack, context,
      flag, lines)`. Vanilla writes each enchantment as `Enchantment.getFullname(holder, level)`, so a description goes
      right after the line equal to that (a hidden enchantment has no line, so gets nothing).
      - `tooltip/DescriptionPlan` (main, MC-free): ids in tooltip order + "has key" + Ctrl + the two settings → which
        ids get a description and whether one hint line goes after the last enchantment line. Hint only when at least
        one shown enchantment has a description.
      - `client/EnchantmentDescriptions` (client): the callback, reads item and stored enchantments, inserts dark-gray
        indented lines. Off when `enchdesc` (Darkhax's Enchantment Descriptions) is loaded.
      - 62 `.desc` lines: 43 vanilla + 19 ours (Step Assist, Anchored, Sure Footing already have one).
      - JUnit: `DescriptionPlanTest`; `EnchantmentDescriptionLangTest` over `VanillaEnchantments.ids()` + every
        `data/enchanting2/enchantment/*.json`; `ClientConfig` round trip. Client GameTest: Ctrl held → a Sharpness V
        book's and a Vision helmet's tooltips have their description; released → the hint.
      ✅ 2026-10-12. Descriptions wrap at 200 GUI px (Silk Touch's is ~95 characters; the client GameTest checks it
      wraps). Mod ID `enchdesc` from Darkhax's docs (its config file name), not from a jar: owner, confirm with a real
      jar if you run it. Mutation: unregistering the hook fails the client GameTest.
- [x] 10i Data-only armor additions (D, owner 2026-10-10): Step Assist (`step_height`), Anchored
      (`knockback_resistance`), Sure Footing (`movement_efficiency`), the 10a pattern: JSON + lang (+ `.desc`) + tags
      + GameTest each, Step Assist's 10-price row. One commit:
      - Measured (javap, 2026-10-12): `movement_efficiency` is read only by `LivingEntity.getBlockSpeedFactor`
        (`lerp(eff, factor, 1)`): soul sand and honey (0.4), never powder snow (`makeStuckInBlock`). Owner
        (2026-10-12): 1/3 per level (`fraction` level / 3: I 0.6, II 0.8, III 1.0 speed); powder snow → 10c.
      - Owner (2026-10-12): Anchored = `knockback_resistance` only, 0.1/level (V = 0.5), not explosions.
      - `step_assist.json` (+0.4 `step_height`, I), `anchored.json`, `sure_footing.json`: slot `legs`,
        `#minecraft:enchantable/leg_armor`, weight 2, costs like Toughness/Vision. `ModEnchantments` keys,
        `non_treasure` + `tooltip_order` tags, lang name + `.desc`, `PricingTable` `step_assist` SINGLE_LEVEL row.
      - GameTests (`NewEnchantmentsGameTest`): registered at max (I, V, III), every source, supported on leggings only;
        step height 1.0 with Step Assist; knockback resistance 0.1 and 0.5; a player on soul sand gets block speed
        factor 0.6 / 1.0 at Sure Footing I / III (0.4 without). JUnit: Step Assist prices 10.
      - docs/ENCHANTMENTS.md rows → Built, DESIGN §9 rows.
      ✅ 2026-10-12. Also a registry-wide GameTest: every `minecraft:`/`enchanting2:` single-level non-curse
      enchantment except Mending prices 10, so 10c's single-level ones can't skip their row. Mutation run (denominator 4,
      Anchored base 0.2, no Step Assist row) fails 3 GameTests + 1 JUnit.
- [x] 10j Configurability (owner, 2026-10-10; DESIGN §1 principle, §8 "Raising max levels"). Two commits:
      - 10jA Ceilings (server, no GUI): a `LevelCeilings` table (MC-free) of hard ceilings (clamp + WARN, today's
        `MaxLevels.SAFE_MAX` moves in) and soft-ceiling notes (lang keys). Check each candidate before it goes in:
        arithmetic in this file or a GameTest (Piercing at 128 and 255, Swiftness and Speed at high levels against
        moved-too-quickly, Thorns VII always triggering, Protection past the 20 cap, Fortune 255 on one block for
        tick time). JUnit: every `minecraft:` and `enchanting2:` ID has a row (no ceiling / soft / hard), so a new
        enchantment can't skip it; an override above a hard ceiling clamps; 255 is accepted for a no-ceiling row.
        GameTest: Efficiency 100 applies at the anvil, costs 100 and mines instantly.
      - 10jB Config screen: the "Enchantments" category gets, per enchantment, max level (1 to its hard ceiling or
        255, "default (N)" reset), `base_cost` and `cost_per_level`, with the ceiling note as the field's description
        and "applies on restart" beside max level. Writes `enchantments.json5` through `saveWithBackup`. Read-only on a
        remote server like the rest. Client GameTest screenshot of an expanded entry.
      - From here on, each stage that adds an option or enchantment also adds its screen entry and its ceiling row
        (the JUnit above fails otherwise).
      **10jA findings and table (2026-10-12; javap 1.21.11 + vanilla JSON; owner answers in decisions.md):**
      - Hard (clamp + WARN): Lure 5, Riptide 7, Quick Charge 4 (4D); **Piercing 127** (`AbstractArrow` stores
        `(byte) getPiercingCount`, 128 wraps to −128 = no piercing); **Lunge 13** (push 0.458/level) and **Wind Burst 14**
        (launch 1.5 + 0.35(L−1) from IV) by Riptide's rule: last level with (push + 3.92 terminal fall)² ≤ 100
        (0.458×13 = 5.95 ✓, ×14 = 6.41 ✗; WB XIV 6.05 ✓, XV 6.40 ✗). 1.21.11's post-impulse grace skips only "moved
        wrongly", not "moved too quickly".
      - Soft (note, field still accepts): Protection 20, Fire/Blast/Projectile Protection 10, Feather Falling 7, Cushion 5
        (1/2/2/2/3/4 points per level per piece vs vanilla's 20-point cap); Thorns 7 (15%); Breach 7 (15%); Depth Strider
        3 and Sure Footing 3 (efficiency attributes max 1); Swift Sneak 5 (0.3 + 0.15L ≥ 1); Frost Walker 14 (radius
        clamped at 16 = 3 + 13); Anchored 10; Critical Strike 20; Vampirism 25; Beheading 50; Reach 122 (both ranges
        max 64: entity 3 + 0.5L); Toughness 40 (armor toughness max 20, 0.5/level per piece); single-level ones whose
        effect doesn't scale: Aqua Affinity, Silk Touch, Flame, Infinity, Channeling, Mending, Binding, Vanishing,
        Smelting, TrueShot Aura, Vision, Step Assist (1).
      - None (note where useful): Efficiency (instant mining long before 100), Fortune and Looting (one stack per 64
        items; hundreds per break or kill can lag), Multishot (owner: no ceiling; 2L + 1 arrows a shot), Health (max
        health caps at 1024), everything else.
      - Swiftness needs no cap: 255 while sprinting ≈ 4 b/t, far under the 10 b/t check. Speed is 10c's.
      - Code: `enchantment/LevelCeilings` (MC-free: kind, level, note key) replaces `MaxLevels.SAFE_MAX`;
        `MaxLevels.resolve` clamps to it and to 255. Notes in en_us `enchanting2.ceiling.<ns>.<path>`.
      - JUnit: every vanilla (`VanillaEnchantments.ids()`) and shipped `enchanting2:` ID has a row; every soft/hard row
        has a note; an override above a hard ceiling clamps; 255 accepted for a no-ceiling row; 256 clamps to 255.
      - GameTests: Piercing 127 vs 128 arrow pierce level; Lunge and Wind Burst pushes measured from the registry effect
        on a mock player, bound holds at the cap and fails at cap + 1; attribute maxima (reach 64, toughness 20,
        knockback resistance 1, the two efficiencies 1, sneaking speed 1); Frost Walker radius 16 at XIV and XV;
        every soft-1 row's effects contain no level-scaling value (would catch a Multishot-style miss); Efficiency C
        mines obsidian instantly; a level-100 enchantment (`e2test:high_level`, max 255, GameTest datapack) applies at
        the anvil and costs 100. (Raising Efficiency's own max needs a startup config the GameTest run can't have.)
      ✅ 10jA 2026-10-12. Corrections found by the GameTests (gotchas.md): Aqua Affinity scales (soft 25, submerged
      mining speed max 20), Efficiency is capped (soft 32, mining efficiency max 1024; stone instant from VI, obsidian
      never). Mutation run (six ceilings off by one) fails 6 GameTests + 2 JUnit.
      **10jB plan (2026-10-12):**
      - `config/EnchantmentNumbersDraft` (MC-free, beside `EnchantmentFlagsDraft`): max level, base cost, cost per
        level per ID; each shows `override.orElse(default)`. `apply(base)`: a field left at what the file gave is
        untouched; moved back to its default → the override is cleared ("default (N)" reset); else set. An entry left
        with nothing is dropped. Applied after the flags draft, so both edit one `enchantments.json5`.
      - Defaults: max = `MaxLevels.resolve(id, the JSON max_level, none)` from `enchantment/DefaultMaxLevels`, which
        reads `/data/<ns>/enchantment/<path>.json` off the classpath (vanilla's from the game jar, ours from the mod);
        another mod's falls back to the registry's max in a world. Costs = `PricingTable.builtIn().get(id)`.
      - Screen: three integer fields per group: max level 1..(hard ceiling or 255) with the ceiling note + "applies
        on restart" as its description; base cost and cost per level 0..10000. Read-only like the flags on a remote
        server. `ConfigScreen.build(parent)` returns the YACL object so the client test can inspect it.
      - JUnit: draft (untouched, reset clears, set, entry dropped, flags kept), `DefaultMaxLevels` (Sharpness 5, Piercing
        5, Lure 5, Vision 1, unknown empty). Client GameTest: Piercing's max field is 1..127 default 5 with its note;
        Sharpness 1..255; a screenshot of the category with Piercing's group expanded.
      ✅ 10jB 2026-10-12. No expanded-group screenshot (YACL fixes `collapsed` at build); the client GameTest checks the
      built options instead (Piercing 1..127 default 5 with its note, Sharpness 1..255, Vision base cost 10). Mutation:
      dropping the ceiling from the range fails it.
- [x] 10e Curses (§5a): Fragility, Weight, Greed, Fumbling (D); Hunger (M). Curse tag +
      `discount_percent` data; table shows curses as optional picks with a negative cost line.
      Max level 5 for all five (owner, 2026-10-10). Per level (owner, 2026-10-12): the effect scales linearly (§5a's
      table is level I) and the discount is 15% at I, +5% per level (V = 35%), still capped by
      `curses.max_discount_percent`.
      Per curse (owner, 2026-10-12, replaces "linear" above; decisions.md "10e curse scaling"): Greed −10% XP/level;
      Fumbling −15% break speed/level; Weight −10% speed/level per piece, pieces add, total capped at 50%; Fragility ×2
      at I +0.5/level (V ×4); Hunger +10% exhaustion/level, armor only, pieces add uncapped. Plan and stages below.
- [x] 10k Owner tweaks (owner, 2026-10-12). One commit:  ✅ 2026-10-12 (the three Fortune GameTests fail with the tags
      reverted; the table client GameTest now checks the drawn order interleaves)
      - Fortune default tags: shovels + `grass_block`, `podzol`, `coarse_dirt`, `rooted_dirt`, `mycelium`; axes +
        `minecraft:bamboo`; hoes + `#minecraft:leaves`. GameTest: a natural grass block under Fortune V gives more
        than one dirt over a seeded run, a placed one exactly one; natural leaves with a Fortune hoe multiply.
        DESIGN §6 table + docs/ENCHANTMENTS.md Fortune row.
      - Table panel rows sorted by translated name (client, `TablePanelView`), JUnit on the sort, DESIGN §7.
- [ ] 10d Balance pass: every candidate moves from REVIEW to INCLUDE/DEFER/REMOVE

### Phase 10c — Mixin/network enchantments (owner order, 2026-10-12; decisions.md "10c order and eight design answers")
**Order:** A helmet/leggings hooks → B boots (Lava Walking, Snow Walker) → C seven shields → D drops (Magnetic, Ender
Link, Keep on Death) → E tools (Area Mining, Replant, Tiller) → F Quick Strike, Last Stand → G Speed, Flight, Double
Jump. Each stage writes its plan here before code; each enchantment gets JSON + lang name + `.desc` + `non_treasure` and
`tooltip_order` tags + a `LevelCeilings` row + a `PricingTable` row if single-level + docs/ENCHANTMENTS.md + DESIGN §9.
**Rollback:** one commit per stage, `git revert <sha>`. Stage A writes no world data (all hooks read equipment live;
Phantom Ward's provocation is a transient field).

### Stage 10cA — Calm Gaze, Phantom Ward, Clarity, Sustenance, Endurance, Sure Footing on powder snow → commit  ✅ 2026-10-12
**Not doing:** Calm Gaze on other gaze checks (only `EnderMan.isBeingStaredBy` reads the disguise; javap); Phantom Ward
on insomnia spawning (owner); Clarity on any effect but the three in its JSON; Sustenance/Endurance on mobs (no food);
Sure Footing on powder snow's vertical stuck factor (only x/z, so sinking/climbing stays vanilla) or freezing.
**Hooks (javap 1.21.11):**
- Calm Gaze: `EnderMan.isBeingStaredBy(Player)` (package-private; first line tests
  `LivingEntity.PLAYER_NOT_WEARING_DISGUISE_ITEM`). HEAD, cancellable: false when the player's equipment carries
  `enchanting2:gaze_disguise` (unit component). Server only by nature.
- Phantom Ward: `Phantom.canAttack(ServerLevel, LivingEntity, TargetingConditions)` is re-checked by all three phantom
  goals (target pick, attack strategy, sweep), so HEAD there: a target whose equipment carries
  `enchanting2:ignored_by_phantoms` is refused unless provoked. Provoked = `@Unique UUID` on the phantom, set when
  `getLastHurtByMob() == target` (vanilla's 100-tick memory) and kept while `getTarget() == target`; a check against
  that target while it isn't the current target clears it (vanilla dropped it).
- Clarity: `LivingEntity.addEffect(MobEffectInstance, Entity)` and `forceAddEffect(...)`, server only (the client
  receives the already-shortened instance through `forceAddEffect`; shortening there too would double it). Component
  `enchanting2:shorten_effects` `{effects: HolderSet<MobEffect>, fraction: LevelBasedValue}`; fractions for the incoming
  effect summed over equipment, clamped [0, 1]. ≥ 1 → refused (infinite too); else `withScaledDuration(1 − f)` (infinite
  stays infinite). Clarity JSON: blindness, darkness, nausea, `fraction` level / 3.
- Sustenance: `enchanting2:exhaustion_reduction` (LevelBasedValue, summed, clamped [0, 1]) in the existing
  `causeFoodExhaustion` hook: `amount × (1 + hunger) × (1 − reduction)`. 0.04/level, helmet.
- Endurance: `enchanting2:sprint_exhaustion_reduction`, 0.05/level, leggings. `ServerPlayerMixin`, `@ModifyArg` on the
  first `causeFoodExhaustion` after `isSprinting()` (`@Slice`) in `checkMovementStatistics` (0.1/m sprint) and
  `jumpFromGround` (0.2 sprint-jump). It then still goes through Hunger and Sustenance, so all three multiply.
- Sure Footing on powder snow: `PowderSnowBlock.entityInside` → `@ModifyArg` on `Entity.makeStuckInBlock`'s Vec3
  (0.9, 1.5, 0.9): x and z become `lerp(movement_efficiency, 0.9, 1)` for a LivingEntity, the same formula as
  `getBlockSpeedFactor`. Runs on both sides (the client moves the player); `movement_efficiency` is a synced attribute
  (javap: `setSyncable(true)`). Soul Speed's own `movement_efficiency` is only added on soul-speed blocks, so it never
  reaches powder snow.
**Data:** `calm_gaze.json`, `phantom_ward.json` (I, head), `clarity.json` (III, head), `sustenance.json` (V, head),
`endurance.json` (V, legs); weight 2, costs like Vision/Toughness. `ModEnchantments` keys, `ModEffects` four component
types, tags, lang names + `.desc` (Sure Footing's `.desc` gains powder snow). `PricingTable` 10-rows for Calm Gaze and
Phantom Ward. `LevelCeilings`: Calm Gaze, Phantom Ward soft 1; Clarity soft 3 (immune); Sustenance soft 25, Endurance
soft 20 (no drain at all).
**Tests (GameTest unless noted):**
- Calm Gaze: a mock player looking straight at an enderman's eyes is "staring" bare-headed and not with the helmet.
- Phantom Ward: `canAttack` true bare-headed, false warded; after the warded player hurts the phantom, true; still true
  while it's the target; after `setTarget(null)` and the memory expired, false again. Another (unwarded) player stays
  attackable.
- Clarity: Blindness 300 ticks → I 200, II 100, III refused; Darkness infinite → I infinite, III refused; Speed 300
  untouched at III; a zombie wearing Clarity III is immune (any wearer).
- Sustenance V: one causeFoodExhaustion(1.0) → 0.8; with Hunger V on four pieces → 3.0 × 0.8 = 2.4.
- Endurance V: sprint 1 m on a mock player through `checkMovementStatistics` → exhaustion 0.075 (0.1 × 0.75); walking
  unchanged; sprint-jump 0.15 vs 0.2.
- Sure Footing III in powder snow: stuck multiplier x/z 1.0, y 1.5; I → 0.9333; none → 0.9.
- JUnit: the reduction math (clamp, multiply with Hunger), `PricingTable` 10s, ceiling rows (existing completeness tests
  fail without them), lang completeness (existing).
- Mutation run before commit: each hook off must fail its test.
- ✅ 2026-10-12: 358 JUnit + 273 GameTests + client GameTests green (`WornEnchantmentsGameTest`, `WornMathTest`).
  Mutation run (the three new mixins unregistered, Clarity/Sustenance/Endurance hooks passing through) fails exactly the
  six new GameTests. Phantom Ward is tested on zombies: the mock player is creative, which `TargetingConditions`
  never targets. Clarity rounds the shortened duration (vanilla's `withScaledDuration` floors, and the float 2/3 made
  Clarity II on 300 ticks 99). Shared code: `effect/WornEffects` (moved `CurseEffects.sum` there), `effect/WornMath`.
  Owner `runClient`: the five names and `.desc` lines, an enderman you stare at with a Calm Gaze helmet, a night
  without sleep in a Phantom Ward helmet, a Warden's Darkness with Clarity I vs III, sprinting through powder snow
  in Sure Footing III leggings.

### Stage 10cB — Lava Walking, Snow Walker (boots) → commit  ✅ 2026-10-12
**Not doing:** fire immunity or protecting the boots (standing on lava burns, owner); a slowdown or fall cushion on lava
(owner, 2026-10-12: a floor); walking on lava under other lava (only the top surface, vanilla's rule); Snow Walker
changing powder snow's look or the climb (the climb comes free: `onClimbable` asks the same method); Lava Walking for
striders (they already walk on lava).
**Hooks (javap 1.21.11):**
- Lava Walking: `LiquidBlock.getCollisionShape` gives `SHAPE_STABLE` (half a block) when the context `isAbove` it, the
  block is a source (`LEVEL` 0) and `CollisionContext.canStandOnFluid(above, fluid)`, which asks
  `LivingEntity.canStandOnFluid(fluid)` (false in `LivingEntity`, Strider's override says lava) and that the block
  above isn't the same fluid. `LivingEntity.shouldTravelInFluid` asks the same method, so true there also means ground
  speed instead of lava's ×0.5 drag (owner: normal speed). `@ModifyReturnValue` on `LivingEntity.canStandOnFluid`: true
  for lava when the entity wears `enchanting2:lava_walker` (unit component), isn't `isDescending()` (sneaking) and its
  lava height ≤ 0.5 (so a walker who sank swims back up with lava physics and lands on the surface once above it).
  Both sides (the client moves its own player; the enchantment registry, effects included, syncs). Flowing lava
  (`LEVEL` ≠ 0): **agent decision** — also stands, through `@ModifyExpressionValue` on the `LEVEL` read in
  `getCollisionShape` for a lava walker, since sinking at every flow edge reads as broken; recorded in decisions.md.
- Standing at +0.5 puts the feet inside the lava block (source surface ≈ 0.89), so vanilla's `LavaFluid.entityInside`
  burns them unaided (javap: `LAVA_IGNITE` + `lavaHurt`). The design guess "box above the lava, burn applied
  explicitly" is wrong for this shape; the GameTest proves the burn.
- Fall damage: landing goes through `checkFallDamage` → `Block.fallOn` on the lava block → `causeFallDamage` (Feather
  Falling applies). `baseTick` halves `fallDistance` in lava, but on the landing tick the entity was still above it.
- Snow Walker: `PowderSnowBlock.canEntityWalkOnPowderSnow(Entity)` (static: `#powder_snow_walkable_mobs` or leather
  boots on FEET) → `@ModifyReturnValue`: also true for a LivingEntity wearing `enchanting2:powder_snow_walker`.
  The collision shape already handles sneaking (`isDescending`) and the >2.5 fall case. Freezing:
  `LivingEntity.canFreeze()` (`#freeze_immune_wearables` on any armor slot) → `@ModifyReturnValue`: false when worn.
**Data:** `lava_walking.json`, `snow_walker.json` (I, feet, `#minecraft:enchantable/foot_armor`, weight 2, costs like
Calm Gaze). `ModEnchantments` keys, `ModEffects` two unit components, `non_treasure` + `tooltip_order` tags, lang names
+ `.desc` + ceiling notes, `PricingTable` 10-rows, `LevelCeilings` soft 1 each, `NewEnchantmentsGameTest` rows.
docs/ENCHANTMENTS.md boots rows, DESIGN §9 rows.
**Tests (GameTest):**
- Lava Walking: a zombie in Lava Walking boots placed over a source lava pool comes to rest standing at lava y + 0.5,
  on fire (burning, hurt); one without boots sinks. A player-like sneaking check: `canStandOnFluid` true standing,
  false sneaking (mock player `setShiftKeyDown`). Flowing lava stands too. A zombie dropped from 10 blocks onto lava in
  the boots takes fall damage (health lost before any fire tick > 0, ~7); `travel` speed: `shouldTravelInFluid` false
  on the surface.
- Snow Walker: `canEntityWalkOnPowderSnow` false bare, true in iron boots with Snow Walker; a zombie with it placed
  on powder snow stays on top; `canFreeze` false with it, true without.
- Mutation run before commit: each hook off must fail its test.
**Rollback:** one commit, `git revert <sha>`. No world data written.
- ✅ 2026-10-12: 358 JUnit + 279 GameTests (`BootsGameTest`, six) + client GameTests green. Tested on husks (no
  daylight burn, goals cleared, 100 health). Mutations: the stand hook and the powder snow hook off fail five tests; the
  flow hook, the freeze hook and the sneak check off fail exactly their three; the sunk-depth check off fails the
  "sunk walker swims" step. Changed from the plan: the fall test drops 5.5 blocks, not 10 (the 8-high test structure
  has a ceiling); `AcquisitionGameTest`'s librarian test rolls until both price cases are seen 5 times (cap 1000)
  instead of a fixed 100, since two new tradeable books shifted every seed's pick.
  Owner `runClient`: walking across a lava lake and its flowing edge in Lava Walking boots (speed, burn, sneak to
  sink, jumping out after sinking), a fall onto lava, powder snow in Snow Walker iron boots (walk, climb, sneak, no
  freeze).

### Stage 10cC — the seven shields, in three commits (owner answers: decisions.md 2026-10-12 "Shield Bash, Deflect...")
**Split:** CA server-side block hooks (Steadfast, Spiked, Bulwark, Riposte) → CB Deflect (projectile flight and
ownership) → CC Steady Stance + Shield Bash (client input). Each writes its detail here before code.
**Not doing (all three):** shields at the enchanting table (vanilla shields have no `enchantable` component; these come
on books + anvil, as Unbreaking does on shields today); Quick Guard, Parry, Arrow Catcher (owner: not picked).
**Items:** new tag `enchanting2:enchantable/shield` = `minecraft:shield` + optional `#c:tools/shield` (modded shields
carry `blocks_attacks`, so the hooks work on them). Slots `hand`. Every hook reads the blocking stack itself, not
equipment, so a second shield in the other hand never adds.
**Rollback:** one commit per sub-stage, `git revert <sha>`. CA registers one mob effect (`enchanting2:bulwark`); a world
saved with it running drops the unknown effect on load without the mod, nothing else.

#### 10cCA — Steadfast, Spiked, Bulwark, Riposte  ✅ 2026-10-12
**Hooks (javap 1.21.11):** `LivingEntity.applyItemBlocking(ServerLevel, DamageSource, float)` resolves the blocked
amount, then always calls `BlocksAttacks.hurtBlockingItem(level, stack, this, hand, blocked)` (which may break the
shield), then for blocked > 0, a non-projectile source and a LivingEntity direct attacker, `blockUsingItem` (vanilla's
0.5 knockback; `Player` adds the axe's `BlocksAttacks.disable`, which calls `stopUsingItem`). So the blocking stack is
only safe to read before `hurtBlockingItem`:
- `@WrapOperation` on that `hurtBlockingItem` call: read the stack's components (`EnchantmentHelper.runIterationOnItem`,
  the stack only), call the original, then `shield/ShieldEffects.afterBlock(self, level, source, blocked, values)`:
  - Spiked (`enchanting2:block_retaliation`, LevelBasedValue damage, linear 1/level): blocked > 0, source not
    `#is_projectile`, direct entity a LivingEntity and not the thorns type → that attacker takes the damage from
    `damageSources().thorns(self)` (owner: melee only; no extra shield durability). Thorns-typed hits never trigger it,
    so two Spiked shields can't loop.
  - Bulwark (`enchanting2:block_absorption` {amount LevelBasedValue 2/level, duration 100, cooldown 200}): blocked > 0
    and the game time ≥ the blocker's `@Unique` ready tick → mob effect `enchanting2:bulwark` at amplifier
    `round(amount) − 1`, hidden (no icon or particles; the yellow hearts show it). The effect is vanilla's Absorption
    at 1 health per amplifier step: `MAX_ABSORPTION` +1 per (amplifier + 1), absorption raised to that on start, gone
    when it runs out or ends. Ready tick = now + cooldown (owner: 5 s, every 10 s).
  - Riposte (`enchanting2:riposte` {damage_bonus LevelBasedValue 0.03/level, window 40}): blocked > 0 → the blocker's
    `@Unique` bonus and expiry tick are set (a later block overwrites them).
- Riposte's hit: `LivingEntity.hurtServer` HEAD, `@ModifyVariable` on the amount: when the source's entity is its
  direct entity, the type is `player_attack`/`mob_attack`/`mob_attack_no_aggro`, and that attacker has a live
  riposte, amount × (1 + bonus) and the riposte is used up (owner: next melee hit within 2 s). Read through a duck
  interface on the attacker (`shield/RiposteHolder`). Thorns and projectiles never use it.
- Steadfast (`enchanting2:shield_disable_reduction`, LevelBasedValue fraction level / 3): `BlocksAttacks.disable`
  HEAD, `@ModifyVariable` on the seconds: × (1 − sum for that stack, clamped [0, 1]); vanilla skips the cooldown at 0
  ticks, so III never disables (owner).
**Data:** `steadfast.json` (III), `spiked.json` (V), `bulwark.json` (III), `riposte.json` (V); weight 2; costs like
Clarity (min 5 / +8, max 25 / +8); `#enchanting2:enchantable/shield`; `ModEnchantments`, `ModEffects` four
components, tags, lang names + `.desc` + `effect.enchanting2.bulwark`, `LevelCeilings`: Steadfast soft 3, the rest
none. No `PricingTable` rows (multi-level).
**Tests (GameTest; a no-AI zombie raising a shield with `startUsingItem(OFF_HAND)`, facing the attacker, stands in for
a player; attackers have 0 armor):**
- Steadfast: `disable(level, mockPlayer, 5, shield)` cooldown ticks: bare 100, I 66, II 33, III none.
- Spiked V: a blocked mob attack takes the attacker from 20 to 15; a blocked arrow hurts nobody; a bare shield hurts
  nobody; a thorns-typed blocked hit doesn't retaliate.
- Bulwark II: a blocked hit gives 4 absorption; a second within 10 s gives nothing; at 101 ticks the absorption and the
  effect are gone; after 200 ticks a block grants again.
- Riposte V: block, then the blocker's mob attack for 10 on an armorless target deals 11.5; the next deals 10; a
  riposte 41 ticks old deals 10; a thorns hit doesn't use it.
- JUnit: the bulwark amplifier rounding; the Steadfast clamp. Mutation run: each hook off fails its test.
- ✅ 2026-10-12: 361 JUnit (`ShieldMathTest`) + 283 GameTests (`ShieldsGameTest`, four) + client GameTests green.
  Husks, not zombies (zombies burn in the test level's daylight and lost health on their own). Steadfast I is 67 ticks,
  not 66: vanilla rounds `disableBlockingForTicks`. Mutations (two runs): the Steadfast hook, the thorns guard, the
  Bulwark cooldown, the Riposte expiry, the Riposte hit hook, the Spiked call and the Bulwark grant each fail exactly
  their test. Owner `runClient`: the four names and descriptions on a shield's tooltip; blocking a zombie with Spiked V
  (it flinches and loses health), Bulwark III's yellow hearts and that they go after 5 s; an axe-wielding vindicator
  against Steadfast III.

#### 10cCB — Deflect  ✅ 2026-10-12
**Hook (javap 1.21.11):** before any projectile hits an entity, `Projectile.hitTargetOrDeflectSelf` asks
`target.deflection(projectile)`; anything but `NONE` runs `projectile.deflect(d, target, its owner, false)` instead of
the hit (no damage, no discard), the path breezes use. So `@ModifyReturnValue` on `Entity.deflection`, server only:
our `ShieldDeflect.AT_SHOOTER` when the target is a LivingEntity blocking (`getItemBlockingWith`) with a shield whose
`enchanting2:deflect_chance` (LevelBasedValue, level / 3) wins a roll, the projectile is in the entity type tag
`enchanting2:deflectable`, hasn't rolled before (`@Unique` flag on `Projectile`, set on the first roll whether it wins
or not: at most once per projectile, so two Deflect shields can't ping-pong), and vanilla would block it (not a
piercing arrow, not `bypassed_by`, inside the shield's blocking angle, via `BlocksAttacks.resolveBlockedDamage` with an
arrow or mob-projectile source).
- `AT_SHOOTER`: same speed, aimed at the shooter's chest, with the arrow's drop over that distance added (its
  `getGravity()`; 0 for fireballs); no shooter alive in that level → straight back. Rotation and `needsSync` like
  vanilla's `REVERSE`.
- `deflect` sets the owner back to the old one after the deflection runs, so `@Inject` at `Projectile.deflect` RETURN:
  for `AT_SHOOTER` on the server, `setOwner(blocker)` and arrows `pickup = DISALLOWED` (owner: kill credit, not picked
  up).
- The deflection is a block: the shield's block sound (`BlocksAttacks.onBlocked`) and 1 durability for a player's
  shield (`hurtAndBreak`, Unbreaking applies). **Agent decisions** (decisions.md): the default tag holds arrows,
  spectral arrows, blaze and ghast fireballs, wither skulls, shulker bullets and llama spit; **not tridents** (owning a
  thrower's Loyalty trident would steal it) and nothing harmless (pearls, potions, eggs, snowballs, fishing hooks,
  wind charges). A deflected projectile isn't a blocked hit, so Bulwark and Riposte don't fire on it.
**Data:** `deflect.json` (III, weight 2, Clarity costs), lang, `.desc`, tags, `LevelCeilings` soft 3 (III = always),
`#enchanting2:deflectable`.
**Tests (GameTest, husk blocker raising a Deflect III shield, a no-AI skeleton 6 blocks in front):**
- an arrow the skeleton "shot" at the blocker turns back: owner the blocker, pickup disallowed, heading at the
  skeleton, blocker unhurt; it hits the skeleton, whose last attacker is the blocker. A plain shield: the arrow keeps
  its owner (vanilla bounce).
- `deflection()` directly: a second call on the same arrow is `NONE`; a trident and a fishing-hook-like untagged
  projectile `NONE`; an arrow from behind the blocker `NONE`; Deflect I over 300 fresh arrows wins 60..140 times.
- Mutation run: each condition and the owner hook off fails a test.
- ✅ 2026-10-12: 361 JUnit + 289 GameTests (six Deflect tests in `ShieldsGameTest`) + client GameTests green. Default tag
  without shulker bullets (they home back onto their target, so a reversed one would turn around). The roll test was
  split into one test per guard so a mutation run shows each one. Mutations: the owner hook, the once-per-projectile
  flag, the blocking-angle check, the tag check, the aim and the chance each fail exactly their test (one run); the
  pickup line fails the end-to-end test (second run). Owner `runClient`: a skeleton shooting at a Deflect III shield
  (its arrows come back and hit it; none to pick up), a blaze's fireballs, a ghast's fireball ("Return to Sender").
#### 10cCC — Steady Stance, Shield Bash  ✅ 2026-10-12
**Hooks (javap 1.21.11):**
- Steady Stance: the use slowdown is the used item's `use_effects.speed_multiplier` (shield 0.2), read only by the
  client's `LocalPlayer.itemUseSpeedMultiplier` in `modifyInput` (mobs aren't slowed by it; the server doesn't check
  it beyond its loose moved-too-fast limit). Client `@ModifyReturnValue` there: 1.0 when the used item carries
  `enchanting2:steady_stance` (unit). **Agent decision:** walking speed only; blocking still stops sprinting
  (`isSlowDueToUsingItem` reads `can_sprint`), since sprint-blocking is more than "no slowdown".
- Shield Bash, client: `Minecraft.handleKeybinds` swallows attack clicks while an item is in use (a
  `keyAttack.consumeClick()` loop right after `releaseUsingItem`). Client `@WrapOperation` on that call (sliced from
  `releaseUsingItem`): a click while using an item that carries `enchanting2:shield_bash`, with the crosshair on an
  entity, runs vanilla's `startAttack()` (the normal attack packet). Other clicks stay swallowed (no block mining).
- Shield Bash, server: nothing refuses an attack from a player using an item (javap: no `isUsingItem` in
  `ServerGamePacketListenerImpl` or `Player.attack`). `@WrapMethod` on `Player.attack`: if the player is using a
  shield with the component and the hit landed (the target's last-hurt-by timestamp moved to now, by this player), the
  target gets `knockback(0.5 × level)` along the player's facing (Knockback I per level, as vanilla's attack applies
  it) and Slowness IV for 10 ticks × level; the shield gets the cooldown (60 ticks) and the player stops using it, as
  an axe's disable does. Component `enchanting2:shield_bash` {knockback, slowness (ticks), cooldown}.
**Data:** `steady_stance.json` (I, `PricingTable` 10 row, soft 1), `shield_bash.json` (III, none), the shield tag,
weight 2, lang + `.desc`, tags.
**Tests:**
- Server GameTest: a mock player using a Shield Bash III shield attacks a husk: Slowness IV, 30 ticks; more knockback
  than the same attack with a plain raised shield; shield on cooldown for 60 ticks; no longer using it. A shield not
  raised: no bash.
- Client GameTest (new `ShieldClientGameTest`, real input): raise a plain shield (hold use) and click attack on a husk
  in front: nothing hits it. With Steady Stance + Shield Bash: `itemUseSpeedMultiplier` is 1.0 (gametest client
  invoker), the click lands a bash (Slowness on the server's husk) and the shield goes down on cooldown.
- Mutation run: each hook off fails a test.
- ✅ 2026-10-12: 361 JUnit + 291 GameTests (two new in `ShieldsGameTest`) + 6 client GameTests (new
  `ShieldClientGameTest`: real "use" and "attack" input on a cow) green. The "landed" check also accepts this player
  becoming the last attacker (a mob hit on its first tick keeps stamp 0). Mutations (two server runs, two client runs):
  the `Player.attack` hook, the Steady Stance rule, the bash knockback, the `LocalPlayer` hook and the click hook each
  fail their test. Phase 10cC complete. Owner `runClient`: walking with a Steady Stance shield up; bashing a zombie
  with Shield Bash III (knockback, slow, shield down 3 s), and that a plain shield still can't attack while raised.

### Stage 10cD — drops: Magnetic, Ender Link (DA), Keep on Death (DB); one commit each
**Owner (decisions.md 2026-10-12):** drops from blocks broken and mobs killed with the item; Magnetic also sends XP to
the player (Mending applies); Ender Link items only; overflow drops normally.

#### 10cDA — Magnetic, Ender Link  ✅ 2026-10-12
**Hooks (javap 1.21.11):** a server-thread redirect context (`drops/DropRedirect`, a stack so nesting restores) set
while a player's tool produces drops, read where items and XP spawn:
- Blocks: `Block.dropResources(state, level, pos, blockEntity, entity, tool)` (`@WrapMethod`; the breaking path via
  `playerDestroy`) → context when `entity` is a server player and `tool` carries `enchanting2:drops_to_inventory`
  or `drops_to_ender_chest`. Items: `Block.popResource(level, pos, stack)` (static `@WrapMethod`), which every
  `getDrops` stack goes through after Fortune and Smelting.
- Mobs: `LivingEntity.dropAllDeathLoot(level, source)` (`@WrapMethod`) → context when `source.getEntity()` is a
  server player and `source.getWeaponItem()` carries one (a bow's arrow carries the bow). It runs the loot table,
  `dropCustomDeathLoot` (held/worn gear), `dropEquipment` and `dropExperience`; their items all funnel into
  `Entity.spawnAtLocation(level, stack, Vec3)` (the other overloads call it; javap), `@WrapMethod` there.
- XP: `ExperienceOrb.award(level, pos, amount)` (static `@WrapMethod`; blocks' `popExperience`, Smelting's furnace
  XP and `dropExperience` all use it) → with `enchanting2:experience_to_player` in the context, the orbs spawn at the
  player instead, so they're picked up next tick and Mending applies.
- Redirect: Ender Link first (`getEnderChestInventory().addItem`), then Magnetic (`getInventory().add` on a copy);
  whatever doesn't fit continues to vanilla's spawn. Nothing is created: only the remainder spawns.
**Conflict:** DESIGN §9's "conflicts with Magnetic" loses to §4 (owner: conflicts default to allow together; none of
ours declares an `exclusive_set`, guarded by `ModEnchantmentDataTest`, which caught a first try at an exclusive set).
Both can share a tool: the ender chest fills first, then the inventory. Open question for the owner (state.md).
**Not doing:** container contents spilled when a chest is broken (they drop in `removeBlock`, outside the tool's
drops); a player victim's inventory (players drop through `Inventory.dropAll`, not death loot); pickup events/sounds
(no Fabric event; per-block pickup sounds would spam).
**Data:** `magnetic.json`, `ender_link.json` (I, `#enchanting2:enchantable/tool_or_weapon`, mainhand, weight 2,
costs like Calm Gaze), `PricingTable` 10 rows, `LevelCeilings` soft 1, tags, lang + `.desc`.
**Tests (GameTest, survival mock player):** Magnetic pickaxe on stone → cobblestone in the inventory, none on the
ground; on coal ore the XP orbs appear at the player, not the block; a full inventory → the cobblestone drops. Ender
Link pickaxe → cobblestone in the ender chest, the coal-ore XP at the block. A Magnetic sword kill of a husk wearing a
helmet (drop chance 2) → the helmet in the inventory and the XP at the player; a plain sword → on the ground. Both on
one pickaxe → the ender chest first, then the inventory. Mutation run: each hook off fails a test.
- ✅ 2026-10-12: 361 JUnit + 296 GameTests (`DropsGameTest`, five) + 6 client GameTests green. Mutations (three runs):
  each redirect context, the `popResource`, `spawnAtLocation` and XP hooks, and the ender-chest-first order fail their
  tests. A mock player's sword damage never applies (it isn't ticked), so the kill test's husk has 0.01 health.
  `TableScreenClientGameTest`'s stepper step re-scrolls the picked row into view: a sword's table list now scrolls,
  and the pick's footer pushed Sharpness out of the window (owner check: should the panel keep a picked row in view?).
  Owner `runClient`: mining with Magnetic (items in the inventory, XP straight to you, a full inventory spilling),
  Ender Link into the ender chest, a Magnetic sword kill.

#### 10cDB — Keep on Death  ✅ 2026-10-12
**Owner (decisions.md "10c order and eight design answers"):** free, forever: back in its slot on respawn, enchantment
kept; which items take it stays configurable.
**Hooks (javap 1.21.11):** `Player.dropEquipment` (only without keepInventory) runs `destroyVanishingCursedItems`, then
`Inventory.dropAll` (the 36 main slots, then `EntityEquipment.dropAll`: armor, off hand). `@WrapMethod` on
`Inventory.dropAll`: take every Keep on Death stack out of its slot (all `getContainerSize()` slots, equipment
included), run vanilla, put them back. They stay in the dead player's inventory, so a logout on the death screen saves
them. On respawn, Fabric's `ServerPlayerEvents.COPY_FROM` (end of `restoreFrom`) with `alive` false moves each one into
the same slot of the new player, only where that slot is empty (with keepInventory vanilla already copied everything,
so nothing doubles), and clears the old slot.
- Curse of Vanishing still wins: it's destroyed before `dropAll`.
- Which items: `supported_items` `#enchanting2:enchantable/keep_on_death` = `#minecraft:enchantable/durability` by
  default (datapacks change it; **agent reading** of "configurable", recorded; owner to confirm whether it needs a
  config-screen list too). Component `enchanting2:keep_on_death` (unit), slots any.
**Not doing:** graves mods (if one empties the inventory first, there's nothing left for us); mob equipment.
**Data:** `keep_on_death.json` (I, weight 2, Calm Gaze costs), the tag, `PricingTable` 10 row, `LevelCeilings` soft
1, tags, lang + `.desc`.
**Tests (GameTest, survival mock player):** a Keep on Death pickaxe in slot 3 and helmet worn, plain dirt in slot 4,
and a Keep on Death + Vanishing sword: after `die()` the dirt is on the ground, the pickaxe and helmet are still in
their slots and not on the ground, the sword is gone; a second mock player `restoreFrom(dead, false)` gets the pickaxe
in slot 3 and the helmet on its head, and the dead one's slots are empty. `restoreFrom(alive, true)` (the
keepInventory/end-portal path) leaves exactly one pickaxe. Mutation run: each hook off fails a test.
- ✅ 2026-10-12: 361 JUnit + 299 GameTests (`KeepOnDeathGameTest`, three) + 6 client GameTests green. Changed from the
  plan: a slot something else took on respawn sends the kept item anywhere in the inventory, then to the ground,
  instead of leaving it on the discarded old player (tested). keepInventory is simulated by hand (`replaceWith`
  before `restoreFrom`): the gamerule is shared by every concurrent test. Mutations (three runs): the `dropAll` wrapper,
  the `COPY_FROM` registration, and the already-copied skip each fail; the skip first survived a count-only check
  (`Inventory.add` moves a shared stack, it doesn't double it), so the test also checks the slot. Phase 10cD complete.
  Owner `runClient`: die with a Keep on Death pickaxe and armor (back in place on respawn), with keepInventory on (no
  doubles), and with Curse of Vanishing on the same item (gone).

### Stage 10cE — tools: Area Mining (EA), Replant (EB), Tiller (EC); one commit each

#### 10cEA — Area Mining  ✅ 2026-10-13
**DESIGN §9:** pickaxe, shovel; 3×3 facing the hit face; sneak for one block; only blocks no harder than the centre
and mineable by the tool; each extra block fires the break event (claims) and costs durability; Fortune, Silk Touch,
Smelting and Magnetic apply per block.
**Hooks (javap 1.21.11):** `ServerPlayerGameMode.destroyBlock(pos)` checks the tool, block-entity permission and
adventure rules, runs `playerWillDestroy` (Fabric's `PlayerBlockBreakEvents.BEFORE` cancels there), `removeBlock`,
`mineBlock` (durability) and `playerDestroy` (drops: Fortune, Silk, Smelting, Magnetic's redirect). `@WrapMethod` on
it: if the main hand carries `enchanting2:area_mining` (unit), the player isn't sneaking and this isn't one of our own
extra breaks (a thread-local flag), read the centre's hardness before the break, run vanilla, and if it broke, call
`destroyBlock` for each of the 8 neighbours in the face's plane that is no harder (`getDestroySpeed` ≥ 0 and ≤ the
centre's) and `isCorrectToolForDrops` with the tool, stopping if the tool breaks. The face: the one the client sent with
its last `handleBlockBreakAction` for that block (`@Inject` HEAD stores face + pos on the game mode); with none (another
mod's break, a test), the axis the player is looking along.
**Data:** `area_mining.json` (I, new item tag `enchanting2:enchantable/area_mining` = `#minecraft:pickaxes` +
`#minecraft:shovels`, mainhand, weight 2, Calm Gaze costs), `PricingTable` 10 row, `LevelCeilings` soft 1, tags,
lang + `.desc`.
**Tests (GameTest, survival mock player):** looking down at a stone 3×3 with an obsidian and a dirt in it: the 7 stones
go (7 cobblestone, 7 durability), obsidian (harder) and dirt (not a pickaxe block) stay; sneaking or a plain pickaxe
breaks one; looking north at a wall breaks the 3×3 in that wall; a neighbour a `PlayerBlockBreakEvents.BEFORE`
listener refuses (keyed to that test's position) stays. Mutation run.
- ✅ 2026-10-13: 361 JUnit + 303 GameTests (`AreaMiningGameTest`, four) + 6 client GameTests green. Mutations (four
  runs): the hook, the sneak check, the hardness check, the face/look axis and the tool check each fail their test (the
  sneak check needed its own run: with the hook off it can't show). Owner `runClient`: mining stone with ores in it
  (the ores stay), a wall vs a floor, sneaking, a shovel in dirt.

#### 10cEB — Replant  ✅ 2026-10-13
**Hooks:** inside the existing `Block.dropResources` wrapper (inside Magnetic's redirect), `tool/Replant.around`: with
`enchanting2:replant` on the tool, a mature crop in the block tag `enchanting2:replantable` (wheat, carrots, potatoes,
beetroots, nether wart, cocoa; mature = its `age` property at its highest), the crop's clone item is the seed;
`Block.popResource` hands each stack to `Replant.takeSeed` before the redirect, which takes one; after the drops, if a
seed was taken, the crop goes back at age 0 (`setValue`, so cocoa keeps its facing) when the spot is air and it can
survive, else the seed drops. Never creates a seed.
- ✅ 2026-10-13: 361 JUnit + 306 GameTests (`ReplantGameTest`, three: wheat, carrots, immature, plain hoe; cocoa with
  Magnetic, 1 bean replanted and 2 in the inventory; the seed rule with test-chosen drops) + client GameTests green.
  Mutations (three runs): the wrapper call, the "seed taken" check, the maturity check, the order before Magnetic, and
  the seed take each fail their test. Owner `runClient`: harvesting a wheat field with Replant, cocoa on a jungle tree.
#### 10cEC — Tiller (detail before code)

### Phase 10e — Curses (three stages, one commit each)
**Goal:** five new curses, each with a GameTest that fails if its effect breaks, and a per-level curse discount.
**Not doing:** Hunger on held items (owner: armor only); changing Binding/Vanishing (stay level 1, 10%); curses on
mob spawn gear (vanilla's `on_mob_spawn_equipment` has none); a cap on Hunger (owner); the table selling curse levels
past its power tier (curses keep the generic `ceil(max × tier / 5)`; only treasure gating skips them, as today).
**Rollback:** each stage is one commit; `git revert <sha>`. No world data is written: Weight's modifier is transient
and recomputed on the next equipment change, so removing the mod leaves nothing behind.

### Stage 10eA — Per-level discount → commit  ✅ 2026-10-12
- `cost/Pricing` + `discountPerLevel` (0..100); `discountAt(level)` = `min(100, percent + perLevel × (level − 1))`. A
  5-arg constructor keeps every existing row (per-level 0).
- `config/EnchantmentOverrides.Entry` + `discount_per_level` (0..100), read and written like `discount_percent`.
  `PricingTable.get` merges it.
- `CostModel.price` sums `discountAt(level)` over the result's curses; `cleanseCost` repays the removed curse's
  `discountAt(its level on the item)`.
- `PricingTable.BUILT_IN`: `CURSE_15` row `Pricing(0, 1, LEVEL_DIFFERENCE, 0, 15, 5)` for the five new IDs (a row for
  an enchantment not yet in the registry is never read).
- Config screen: curse entries get two more fields (discount at level I, per level), through
  `EnchantmentNumbersDraft` (`Field.DISCOUNT_PERCENT`, `DISCOUNT_PER_LEVEL`). Curse = `#minecraft:curse` from the world's
  registry, else every `data/minecraft/tags/enchantment/curse.json` on the classpath (`enchantment/DefaultCurses`).
- JUnit: `discountAt` (I 15, V 35, clamp 100); a curse at V alone on Eff V (5) → `ceil(5 × 0.65)` = 4; two curses at V
  hit the 45 cap; Cleanse of a curse at III repays 25%; a file `discount_per_level` wins; config round trip; draft
  fields; `DefaultCurses` has Binding and Vanishing.
- ✅ 2026-10-12: 348 JUnit + 261 GameTests + client GameTests green. The client GameTest checks Binding's group has both
  discount fields (default 10) and Sharpness's has none. Mutations: flat percent in `CostModel` fails 3 JUnit; discount
  fields on every entry fails the client GameTest. Gotcha: `I18n.get` runs `String.format`, so a name with "(%)" comes
  back as a format error; the client test matches names through `Component.translatable(...).getString()`.

### Stage 10eB — Fragility, Greed, Fumbling (data) → commit  ✅ 2026-10-12
- `fragility.json`: `minecraft:item_damage` multiply, linear 2.0 / +0.5; `#minecraft:enchantable/durability`; slot any.
  `EnchantmentHelperMixin` rounds `processDurabilityChange` at random too (×2.5 on 1 damage would truncate to 2). A whole
  value draws no roll, so Unbreaking alone keeps vanilla's sequence.
- `greed.json`: `mob_experience` + `block_experience` multiply, linear 0.9 / −0.1;
  `#enchanting2:enchantable/tool_or_weapon` (XP Boost's); mainhand. Rounded by the existing mixin; multiplies with XP
  Boost.
- `fumbling.json`: `minecraft:block_break_speed` `add_multiplied_total`, linear −0.15 / −0.15;
  `#minecraft:enchantable/mining`; mainhand.
- All: max 5, weight 1, vanilla curse costs (min 25, max 50). Tags like Binding/Vanishing: `#minecraft:curse`,
  `treasure`, `on_random_loot`, `tradeable`, `tooltip_order` (after ours), and `enchanting2:offered_at_table`. Lang
  name ("Curse of Fragility") + `.desc`.
- `LevelCeilings`: Greed soft 10 (factor 0 at X); Fumbling soft 7 (VII = −105%, can't mine; the attribute floors at
  0); Fragility none.
- GameTests: each registered at max V, a curse, offered at the table; Fragility II averages 2.5 damage per use over a
  seeded run (fails with truncation), V gives 4; Greed III on a 10-XP kill gives 7; Fumbling II block break speed 0.7.
- ✅ 2026-10-12: 348 JUnit + 265 GameTests (`CursesGameTest`). Changed from the plan: Greed is soft 10, not hard (vanilla
  awards nothing for a negative amount, and X already gives 0, tested); Fumbling soft 7, not 6 (VII is −105%, floored
  at 0, tested). `ModEnchantments.CURSES` splits the source-tag checks (JUnit + GameTest); the lang test compares
  against `ModEnchantments.ids()` instead of a pinned 19. Mutation: no durability rounding fails Fragility II (mean 2.0).

### Stage 10eC — Weight, Hunger (code) + docs → commit  ✅ 2026-10-12
- Effect components: `enchanting2:movement_penalty` and `enchanting2:exhaustion` (both `LevelBasedValue`), summed over
  equipped items whose slot matches (`runIterationOnEquipment`).
- Weight: `weight.json` (`#minecraft:enchantable/armor`, slots armor, penalty linear 0.1 / 0.1). `LivingEntityMixin`
  at `handleEquipmentChanges` TAIL (server only, runs only when gear changes): one transient `movement_speed` modifier
  `enchanting2:curse.weight`, `add_multiplied_base`, `−min(sum, curses.weight_max_slowdown_percent / 100)`; removed at
  0. New `CursesSection` key `weight_max_slowdown_percent` (default 50, 0..100) + config screen field.
- Hunger: `hunger.json` (`#minecraft:enchantable/armor`, slots armor, exhaustion linear 0.1 / 0.1). `PlayerMixin`
  scales `causeFoodExhaustion`'s amount by `1 + sum`.
- MC-free `curse/CurseMath` (penalty cap, exhaustion factor) with JUnit.
- `LevelCeilings`: Weight none (the total stops at the cap); Hunger none.
- GameTests: Weight I on one piece → speed ×0.9; III + III → ×0.5 (capped, not 0.4); taking a piece off restores it;
  a zombie wearing Weight is slowed; Hunger V on four pieces → exhaustion ×3.0; no curse → unchanged.
- docs/ENCHANTMENTS.md curse rows → Built with the per-level numbers; DESIGN §5a table and per-level text.
- Owner `runClient`: the curse names in red, a Weight set's walking feel, the new screen fields.
- ✅ 2026-10-12: 352 JUnit + 267 GameTests + client GameTests green. Weight is tested on a ticking zombie (the real
  `handleEquipmentChanges` hook): I alone −0.1, Weight V held ignored, III + III capped at −0.5, back after a save and
  load, gone with the armor off. Hunger on a mock player (`FoodDataAccessor`, invulnerable off): I 1.1, held ignored,
  four at V 3.0. Mutation: both hooks off fails both tests. Weight's cap applies at the next gear change, not live.

### Phase 10g — Applicability and vanilla extensions (three stages, one commit each)
**Goal:** the owner's 2026-10-10 additions to vanilla enchantments work, each with a GameTest that fails if it breaks.
**Not doing:** Sweeping Edge or Lunge on axes (sword/spear mechanics), Mending on non-durable items or on modded
durables missing from `#enchantable/durability` (owner: no), milk/powder snow/fish buckets, trial spawners, iron-ingot
bucket repair. Vanilla tags stay untouched except the buckets joining `#enchantable/durability` (10gC, on purpose).
**Answered (owner, 2026-10-10):** spawner option on by default and the spawner keeps its mob; buckets reuse Infinity
and get durability (256 uses, the last use destroys it); only water and lava buckets take enchantments, never the
empty bucket; an Infinity lava bucket is never fuel; the table offers it on buckets; Sweeping Edge stays swords-only;
no Mending on untagged modded durables.
Durability enchantments on a bucket need Infinity on it (owner, 2026-10-10).

### Stage 10gA — Axes and crossbow → commit  ✅ 2026-10-12
- [x] 10gA.1 Tags `enchanting2:enchantable/{knockback,looting,fire_aspect,flame,infinity}` = the vanilla tag + new
      items (axes; crossbow; Infinity also gets the two buckets in 10gC, not here) and `bow_or_crossbow` for TrueShot.
- [x] 10gA.2 `MaxLevelHook`-style MODIFY step swaps `supported_items` and `primary_items` to those tags, by ID. A
      datapack that already replaced the definition keeps its own (log at INFO, don't fight it). TrueShot's JSON
      points at `bow_or_crossbow`.
      How (2026-10-12): MODIFY gets no registry lookup (item-api 11.6.0), and `BuiltInRegistries.ITEM`'s tag sets
      aren't bound yet at that point (tags are pending during the dynamic-registry load). Fabric fires MODIFY inside
      `RegistryDataLoader.loadElementFromResource(registry, decoder, RegistryOps ops, ...)`, so a HEAD/RETURN mixin
      there holds `ops.getter(Registries.ITEM)` in a ThreadLocal for the call, and the swap builds
      `getter.getOrThrow(TagKey)`, the same pending-tag lookup vanilla's JSON parse uses. "Datapack replaced it" =
      the current set isn't `HolderSet.Named` with the vanilla tag key (`enchantable/melee_weapon` for Knockback and
      Looting, `enchantable/fire_aspect` + primary `melee_weapon` for Fire Aspect, `enchantable/bow` for Flame and
      Infinity). No getter in the ThreadLocal (an injected entry) → leave it, WARN.
      Done: Fire Aspect's `primary_items` → `fire_aspect_primary` (melee weapons + axes); the others have none, so
      theirs follows `supported_items`. Mutation: unregistering the hook fails the anvil, table and crossbow tests.
- [x] 10gA.3 GameTests: Knockback/Looting/Fire Aspect apply to an iron axe at the anvil and are offered at the table;
      a Fire Aspect axe sets the target on fire. A Flame crossbow arrow ignites; an Infinity crossbow keeps its arrow
      and still uses a firework rocket; a TrueShot crossbow arrow has no gravity, all three with Multishot. Power and
      Punch still refuse a crossbow (the shared tag untouched). A shovel takes Reach, XP Boost and refuses
      Sharpness/Vampirism. Mending applies to every item in `#enchantable/durability`.
- [x] 10gA.4 docs/ENCHANTMENTS.md: drop the ‡ on these rows.

### Stage 10gB — Silk Touch spawners → commit  ✅ 2026-10-12
- [x] 10gB.1 `common.json5` `silk_touch.collect_spawners` (bool, default true), `SilkTouchSection` + config
      screen toggle in "Enchantment effects", following `SmeltingSection`.
- [x] 10gB.2 Drop: on a Silk Touch break of a `minecraft:spawner` with the option on, drop the spawner item carrying
      its **mob type only** (owner, 2026-10-12, decisions.md): a minimal `block_entity_data`
      (`SpawnData.entity.id`, so vanilla's tooltip and op placement work) + our unit stamp component
      `enchanting2:collected_spawner`. Added in `FortuneDrops`' `MODIFY_DROPS` listener before Fortune, on the
      spawner's own (empty) loot table, so vanilla's correct-tool and creative rules already apply. No XP:
      `SpawnerBlock.spawnAfterBreak` pops 15–43 XP directly, outside `block_experience` (javap 1.21.11), so Silk
      Touch's XP-zero effect never reaches it; a mixin turns its `dropExperience` off on the same condition.
      Off → vanilla (no drop, XP).
- [x] 10gB.3 Place: `BlockItem.updateCustomBlockEntityTag` (static) RETURN: refused by the op gate + stamped + the
      block entity is a spawner → `setEntityId(type)`, the spawn-egg path, only if the type has a spawn egg
      (`SpawnEggItem.byId`). Everything else in the data is ignored; ops keep vanilla's copy. Fortune hard-excluded
      like ancient debris (`FortuneRule`'s excluded flag covers both).
- [x] 10gB.4 GameTests: on → a silk-touched zombie spawner drops one stamped item and no XP orbs; a survival non-op
      places it and its spawn data is zombie; option off (`apply` with the flag given, never the live config) → no
      drop; no Silk → no drop + XP; a hand-made `block_entity_data` creeper spawner without the stamp places empty for
      a non-op, and a stamped wither one places empty too (the guard tests); Fortune V + `FORTUNE_MULTIPLIES_SILK`
      still gives exactly one. JUnit: `SilkTouchSection` read/write/default, lang keys.
      Done: mutation runs (no spawner in the Fortune exclusion; XP condition forced true; no spawn-egg check; no
      restore) each fail their test. Mock players are non-ops (the placement test asserts it).
- [x] 10gB.5 docs/ENCHANTMENTS.md + DESIGN §6: drop the ‡.

### Stage 10gC — Infinite buckets → commit  ✅ 2026-10-12
How (2026-10-12, javap 1.21.11): all three emptying paths end in `new ItemStack(Items.BUCKET)`: hand
(`BucketItem.getEmptySuccessItem`, static, returns the stack itself for creative), dispenser (`DispenseItemBehavior$4`
→ `consumeWithRemainder(source, stack, BUCKET)`), cauldron (`CauldronInteraction.emptyBucket` →
`ItemUtils.createFilledResult(held, player, BUCKET)`). Each gets the same answer from `bucket/InfiniteBuckets.afterUse`:
a copy of the bucket after `hurtAndBreak(1)` (Unbreaking applies; at 0 it's empty, with the break sound), so vanilla's
own consume-then-return-the-remainder logic hands it back. Nether evaporation goes through the same hand/dispenser path.
Owner (2026-10-12): the grindstone may extract Infinity and leave Mending on a plain bucket (visible choice, no rule).
- [x] 10gC.1 Tags: `enchanting2:enchantable/infinity` + `minecraft:water_bucket`, `minecraft:lava_bucket`;
      `minecraft:enchantable/durability` + both (our one vanilla tag addition, `replace: false`).
- [x] 10gC.2 Durability: `DefaultItemComponentEvents.MODIFY` gives both buckets `max_damage` 256 + `damage` 0.
      `EnchantRules.check`: on a water or lava bucket, anything but Infinity is `NOT_APPLICABLE` while `existing`
      lacks Infinity (so the table's pool and the anvil both refuse it). The anvil applies a book's Infinity first
      (`EnchantRules.APPLY_ORDER`), so Infinity + Mending in one book goes on in one go. An `enchantable` component
      only if the table GameTest shows the table needs one (our table never calls `isEnchantable`).
- [x] 10gC.3 Mixins: `BucketItemMixin` (hand, not for creative), `DispenseItemBehaviorBucketMixin` (`$4`, checked with
      javap), `CauldronInteractionMixin` (`emptyBucket`), `FuelValuesMixin` (`isFuel` false, `burnDuration` 0 for an
      Infinity bucket), `IngredientMixin` (`test` false for an Infinity bucket).
- [x] 10gC.4 GameTests: ten placements from one water and one lava bucket by a survival player (still one enchanted
      full bucket each, 10 damage); a dispenser and a cauldron keep it and damage it; at 1 durability left the next use
      destroys it; the table offers Infinity on a water bucket and nothing on an empty bucket; Unbreaking III skips uses
      over a seeded run; Mending repairs it from XP; Mending alone is refused on a plain bucket at the anvil (the guard
      test) and an Infinity + Mending book applies; a furnace won't take the lava one as fuel; an ingredient refuses it;
      a plain bucket still empties into an empty bucket and is never damaged; Nether evaporation leaves the bucket full
      and damages it.
- [x] 10gC.5 docs/ENCHANTMENTS.md + DESIGN §6: drop the ‡.
      Done: no `enchantable` component needed (the table offers Infinity without it). Mutation runs (no hand/cauldron
      remainder, no dispenser remainder, no fuel or ingredient guard, no bucket gate, ID-only apply order) each fail
      their tests (11 in all).

### Phase 10b — Custom effect types (three stages, one commit each)
**Goal:** the five E-route enchantments exist as `enchanting2:` data entries whose behaviour is driven by our own effect
components (so a datapack can put the component on another enchantment). They come from every source like 10a. Each has
a GameTest that fails when its Java or JSON breaks.
**Owner (2026-10-09, decisions.md "Phase 10b" + "round 2" + "dragon"):** Vampirism heals 4% × L of the health the
target actually lost, on direct melee hits only (not a thrown trident), every hit, no cooldown, against any victim.
TrueShot: no gravity for 100 ticks, then normal. Smelting on pickaxe/shovel/axe/hoe, **every** smelting recipe (logs →
charcoal), after Fortune, with recipe XP randomly rounded; Silk Touch wins by default. Beheading 2% × L, **player kills
only**, extra to vanilla's wither-skull roll, zombie / skeleton / wither skeleton / creeper / piglin, player heads behind
`beheading.player_heads` (off), **no ender dragon**. Vision for **players only**.
**Facts (1.21.11 `javap` + jar data, FAPI entity-events 5.0.5 source):**
- Registries: `BuiltInRegistries.ENCHANTMENT_EFFECT_COMPONENT_TYPE` (`DataComponentType`), `ENCHANTMENT_ENTITY_EFFECT_TYPE`
  (`MapCodec<? extends EnchantmentEntityEffect>`). `EnchantmentHelper.getHighestLevel(stack, type)` → `Pair<T, level>`,
  `has(stack, type)`, `runIterationOnEquipment` (respects the enchantment's slots).
- Fabric `AFTER_DAMAGE` is **not fired on a killing blow** (`if (!isDeadOrDying())`) and passes the post-armor amount, not
  health lost. So Vampirism wraps `LivingEntity.hurtServer` itself (MixinExtras `@WrapMethod`): health + absorption before
  and after, heal from the difference.
- `EnchantmentEffectComponents.PROJECTILE_SPAWNED` runs entity effects on a projectile a bow spawns (Flame's `ignite`).
  `Entity.setNoGravity` is synced entity data (`DATA_NO_GRAVITY`) and saved as `NoGravity`, so the client draws the
  straight path, and a chunk reload mid-flight would keep it floating forever unless our timer is persistent too.
- Recipes: `ServerLevel.recipeAccess().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(stack), level)`,
  `AbstractCookingRecipe.experience()`, `assemble(input, registries)`. `ExperienceOrb.award(level, Vec3, int)`.
- Heads: vanilla maps mob → head in data: `minecraft:charged_creeper/root` (alternatives by `this` entity type → piglin,
  creeper, skeleton, wither skeleton, zombie). Wither skeleton's own table rolls its skull (`killed_by_player`, 2.5%,
  +1%/Looting). `entities/player` exists (empty) and `dropAllDeathLoot` rolls it when `shouldDropLoot`. The dragon never
  rolls loot (DYING phase, then `remove`).
- Tags: `#minecraft:enchantable/mining` includes shears (owner said four tools, so our own tag), `enchantable/bow` = bow,
  `enchantable/head_armor` = `#minecraft:head_armor`.

**Effect components (ours, `effect/ModEffects`):**
| Component | Value | Read by |
|---|---|---|
| `enchanting2:lifesteal` | `LevelBasedValue` (fraction healed) | `LivingEntityMixin` wrap of `hurtServer` |
| `enchanting2:head_drop_chance` | `LevelBasedValue` (chance) | `effect/HeadDrops` on `MODIFY_DROPS` |
| `enchanting2:smelt_drops` | `Unit` | `effect/SmeltingDrops`, called from `FortuneDrops`' listener after Fortune |
| `enchanting2:night_vision` | `Unit` | `effect/NightVisionTicker` on `END_SERVER_TICK`, every 20 ticks |
| entity effect `enchanting2:suspend_gravity` `{ticks: LevelBasedValue}` | in vanilla `projectile_spawned` | `AbstractArrowMixin` tick countdown |

**Numbers:** (costs as 10a: 5-level min `5 + 8(L−1)`, max `min + 20`; 1-level min 15, max 65 like Silk Touch)
| ID | Max | Items (tag) | Slot | Effect | Weight |
|---|---|---|---|---|---|
| `vampirism` | 5 | `enchanting2:enchantable/melee` | mainhand | lifesteal `0.04 × L` | 2 |
| `beheading` | 5 | `enchanting2:enchantable/melee` | mainhand | head chance `0.02 × L` | 2 |
| `smelting` | 1 | `enchanting2:enchantable/smelting` (pickaxes, shovels, axes, hoes) | mainhand | `smelt_drops` | 2 |
| `true_shot_aura` | 1 | `#minecraft:enchantable/bow` | mainhand | `suspend_gravity` 100 ticks | 1 |
| `vision` | 1 | `#minecraft:enchantable/head_armor` | head | `night_vision` | 2 |

**Agent calls:**
- Vampirism "direct" = `source.getDirectEntity() == source.getEntity()`, attacker a living, alive entity; weapon =
  `source.getWeaponItem()`. Health lost counts absorption (`(health + absorption)` before − after, ≥ 0), so overkill
  never counts. Heal via `LivingEntity.heal`. Mobs holding a Vampirism weapon heal too (vanilla-like).
- Beheading uses the same "direct" rule (melee enchantment, so a thrown trident doesn't count, consistent with
  Vampirism). Player kill = the damage source's entity is a `Player`. Heads come from our own loot table
  `enchanting2:beheading/heads` (vanilla's mapping + `player` → `player_head` with `fill_player_head`), so datapacks can
  add mobs. One head at most: no roll if the drops already hold that item (the wither skull from vanilla's own roll), so
  the chance is `p_vanilla + (1 − p_vanilla) × p_ours`, which is close to additive.
- Smelting runs inside `FortuneDrops`' listener, after Fortune and outside its Silk-free re-roll. Run separately, it would
  smelt the re-roll and turn every log into "Silk changed the drop". Excluded inputs: item tag
  `enchanting2:smelting_excluded` (shipped empty). `smelting.silk_priority` = `silk_touch` (default: a Silk Touch tool
  never smelts) or `smelting` (the silk drop is smelted). XP: sum of `experience × count`, `StochasticRounding`, one
  `ExperienceOrb.award` at the block.
- TrueShot's countdown is a persistent entity attachment `enchanting2:gravity_ticks` (remaining ticks), so a reload
  mid-flight resumes it. At 0: `setNoGravity(false)` and remove the attachment. An arrow that is already `NoGravity` from
  elsewhere (a command) is left alone: we only set it when it wasn't set, and only clear what we set.
- Vision: every 20 ticks per player, iterate equipment (respects slots). Wearing it: if there is no Night Vision, or it's
  ours, or a non-infinite one with < 220 ticks left → add ours (300 ticks, ambient, no particles) when below 220.
  Ours = ambient + amplifier 0 + at most 300 ticks (potions are never ambient). Not wearing: remove only ours. A potion
  with 220+ ticks left is never touched.
- New config sections in `common.json5`: `beheading` {`player_heads`: false}, `smelting` {`silk_priority`: silk_touch}.
  Absent keys read as defaults; no file is rewritten (existing rule). Config screen entries: in 10bC.

**Not doing:** ender dragon heads; zombie variants (husk, drowned, zombie villager) beyond what the data table maps (a
datapack can add them); smelting for anything but block drops; Vampirism particles or sound; TrueShot on crossbows or
tridents; Vision for mobs; balance numbers beyond the starting values (10d).
**Blast radius:** a world opened with this build gains five more `enchanting2:` entries and five registered effect
types (registry-synced: clients need this build, as DESIGN §10 already requires). `common.json5` gains two sections
only through the GUI save (`saveWithBackup`). The `hurtServer` wrap runs on every damage event and must never change
the damage or the return value (a GameTest pins both). No world data except the arrow attachment, which only exists
on TrueShot arrows.
**Rollback:** `git revert` the stage's commit. Dev worlds (`run/`, gitignored) lose the enchantments on load with a
vanilla WARN; a TrueShot arrow in flight keeps `NoGravity` (vanilla saves it) until it despawns; Fabric WARNs about the
unknown attachment. Nothing outside the repo is touched.

### Stage 10bA — Effect registry, Vampirism, Beheading → commit  ✅ 2026-10-09
- [x] 10bA.1 `effect/ModEffects`: `lifesteal` and `head_drop_chance` components, registered first in
      `Enchanting2.onInitialize`. Changed: each later component (and `suspend_gravity`) registers in the stage that uses it.
- [x] 10bA.2 `config/BeheadingSection` (`player_heads`) **and** `config/SmeltingSection` (`silk_priority`, enum
      `effect/SmeltingSilkPriority`), both in `CommonConfig` now. Changed: `CommonConfig` has 18 constructor sites and the
      config screen rebuilds it, so both sections (and the screen's "Enchantment effects" category, 10bC.3) land
      together. JUnit: defaults, every value round-trips, bad values are issues, absent sections default; lang keys.
- [x] 10bA.3 `LivingEntityMixin` `@WrapMethod hurtServer` → `effect/Lifesteal.afterHurt`; return value untouched;
      exceptions logged inside `afterHurt`.
- [x] 10bA.4 `effect/HeadDrops` on `MODIFY_DROPS` (victim's own table only) + `loot_table/beheading/heads.json`. Errors
      leave vanilla's drops (ERROR log); a missing heads table WARNs.
- [x] 10bA.5 JSON `vampirism`, `beheading` + lang + `non_treasure` + `tooltip_order` + `ModEnchantments.KEYS`.
- [x] 10bA.6 `EffectEnchantmentsGameTest` (12): Vampirism V heals 0.2 × lost and the hit loses what a plain one does;
      2 HP target killed by 20 → heals 0.4; thrown trident → nothing; a missed hit (weaker one inside i-frames) returns
      false and heals nothing; melee items only. Beheading via `HeadDrops.apply` with a fixed roll: 0.019/0.021 at I and
      0.099/0.101 at V; each mob's own head, a cow none; no second wither skull; mob kill, thrown trident, no enchantment
      → nothing; `player_heads` off/on (head carries a profile); another table → nothing; a real kill at level 50 (chance
      1) drops a zombie head for a player and none for a mob. `NewEnchantmentsGameTest` pin extended. Mutation runs
      (two builds, 8 mutations: JSON numbers, `isDirect`, health-lost vs amount, Player-only killer, skull dedupe,
      player-heads gate, own-table guard): every one failed its test. Removing the own-table guard also recurses
      (the nested heads roll re-enters the handler), which the real-kill test catches.
      301 JUnit (+4), 199 GameTests (+12), 4 client GameTests.

### Stage 10bB — Smelting + TrueShot Aura → commit  ✅ 2026-10-09
- [x] 10bB.1 `config/SmeltingSection` (`silk_priority`) + JUnit. Done in 10bA.
- [x] 10bB.2 `effect/SmeltingDrops.apply(...)`, called inside `FortuneDrops`' listener after Fortune and inside its
      `!REROLLING` guard. One recipe lookup per drop; result × count split at max stack; XP = Σ experience × count,
      `StochasticRounding`, one `ExperienceOrb.award` at the block. Item tags `enchanting2:enchantable/smelting`
      (pickaxes, shovels, axes, hoes) and `enchanting2:smelting_excluded` (empty).
- [x] 10bB.3 `effect/SuspendGravity` (entity effect `enchanting2:suspend_gravity`, attachment `enchanting2:gravity_ticks`)
      + `AbstractArrowMixin` (server tick countdown). Vanilla runs `projectile_spawned` in
      `Projectile.applyOnProjectileSpawned(level, weapon)`, not in the arrow constructor; the tests call it.
- [x] 10bB.4 JSON `smelting` (`smelt_drops`), `true_shot_aura` (`projectile_spawned` → `suspend_gravity` 100) + lang +
      tags + keys. Both max 1, min cost 15, max 65.
- [x] 10bB.5 GameTests (+11): gold ore → gold ingot and exactly 1 XP each, iron ore × 40 → XP 18..38 (0.7 each, merged
      orbs counted through a GameTest accessor); log → charcoal, dirt unchanged, excluded raw copper (GameTest datapack)
      unchanged; Fortune V + Smelting on clay → bricks multiplied and XP > 40 (smelting first would pay ~24); Silk +
      Smelting iron ore → ore, no XP; Silk + Fortune + Smelting log still multiplies (the re-roll isn't smelted);
      `smelting` priority via `SmeltingDrops.apply`; placed log → one charcoal; supported items for both. TrueShot: bow
      arrow is `NoGravity` with 100 ticks, a plain one isn't; hanging arrow still floating at 90, gravity back with no
      attachment at 101; save/load keeps 40 ticks left; an already-`NoGravity` arrow gets no countdown. Mutation runs
      (three builds, 10 mutations: no XP orb, no silk check, no exclusion, endless countdown, ticks 120, Smelting before
      Fortune, non-persistent attachment, no NoGravity check, Smelting outside the re-roll guard): every one failed
      its test. 301 JUnit, 210 GameTests, 4 client GameTests (no client change).

### Stage 10bC — Vision + docs → commit  ✅ 2026-10-09
- [x] 10bC.1 `effect/NightVisionTicker` (`END_SERVER_TICK`, every 20 ticks, players only) + component
      `enchanting2:night_vision` + JSON `vision` (head slot, `#minecraft:enchantable/head_armor`) + lang + keys.
      Changed: no remove-before-replace for a running-out potion. A mutation run showed vanilla's
      `MobEffectInstance.update` already takes our ambient flag along with the longer duration, so that branch was dead.
- [x] 10bC.2 GameTests (+6): wearing → ambient, particle-free, 300 ticks; plain helmet → nothing; ours at 219 →
      refreshed, at 220 → untouched; a 3-minute and an infinite potion untouched on and off; a running-out potion
      becomes ours and goes with the helmet; a short potion without the helmet stays; the real server tick grants it
      within 60 ticks; helmets only. Mutation runs (refresh at 200, no ambient marker, no infinite check, ticker not
      registered, no removal on unequip): every one failed its test. 301 JUnit, 216 GameTests, 4 client GameTests.
- [x] 10bC.3 DESIGN §9 rows + Phase 10b note, §4 Silk/Smelting row. Config screen entries done in 10bA.
- [x] 10b done 2026-10-09. Owner: in the next `runClient` session, check the five names, a Vision helmet at night
      (no flicker, gone when taken off), a TrueShot arrow's flight, and the "Enchantment effects" config category.

### Phase 10a — Data-only enchantments (three stages, one commit each)
**Goal:** the eleven D-route enchantments exist as `enchanting2:` registry entries with effect components, show up in
every acquisition source, carry lang names, and each has a GameTest that fails when its JSON breaks.
**Owner (2026-10-09, Phase 10a start):** (1) **Sources: everywhere, like vanilla** (table, librarian books, random loot,
mob spawn gear, villager gear). (2) **Vision → 10b** (small Java). (3) **Health = data + relog fix.** (4) **Numbers below
as starting values**, retuned in 10d. (5) **XP Boost: random rounding mixin.**
**Facts (1.21.11 `javap` + jar data):**
- Every vanilla source tag (`in_enchanting_table`, `tradeable`, `on_random_loot`, `on_mob_spawn_equipment`,
  `on_traded_equipment`) includes `#minecraft:non_treasure`. Our `offered_at_table` includes `#in_enchanting_table`.
- `AbstractArrow` (and so `ThrownTrident`) calls `modifyDamage` and `doPostAttackEffectsWithItemSource` with the firing
  weapon, so `damage` and `post_attack` on a bow/crossbow/trident reach projectile hits. Fire Aspect's `is_direct`
  requirement is what keeps it melee-only, and ours have none.
- `processMobExperience` / `processBlockExperience` accumulate a `MutableFloat` and return `intValue()` (truncation).
- `LivingEntity.readAdditionalSaveData` calls `setHealth(saved)` after loading attributes. Enchantment attribute modifiers
  are transient and only return on the first tick's `detectEquipmentUpdates`, so health clamps to the base max.
- No damage-type tag holds only `fly_into_wall` (it's in `bypasses_armor` and `no_knockback`). Enchantment protection
  still applies to `bypasses_armor` (Feather Falling works on falls).
- Attribute IDs: `attack_speed`, `armor_toughness`, `max_health`, `movement_speed`, `block_interaction_range`,
  `entity_interaction_range`. Vanilla suffixes each enchantment attribute ID with the slot, so armor pieces stack.

**Numbers (per level L; weight / anvil_cost; costs: 5-level min `5 + 8(L−1)`, max `min + 20`; 3-level min
`10 + 10(L−1)`, max `min + 30`):**
| ID | Max | Items (tag) | Slot | Effect | Weight |
|---|---|---|---|---|---|
| `withering` | 5 | melee + ranged | mainhand | `post_attack` Wither I, `2 + (L−1)` s | 2 |
| `poisoning` | 5 | melee + ranged | mainhand | `post_attack` Poison I, `2 + (L−1)` s | 5 |
| `freezing` | 5 | melee + ranged | mainhand | `post_attack` Slowness I, `2 + (L−1)` s | 5 |
| `attack_speed` | 5 | melee | mainhand | `attack_speed` +10% × L (`add_multiplied_base`) | 2 |
| `critical_strike` | 5 | melee + ranged | mainhand | `damage` × 1.5, `random_chance` 5% × L | 2 |
| `reach` | 3 | mining + melee | mainhand | block and entity interaction range +0.5 × L | 1 |
| `xp_boost` | 5 | mining + melee + ranged | mainhand | `mob_experience` and `block_experience` × (1 + 0.05 L) | 5 |
| `toughness` | 5 | armor | armor | `armor_toughness` +0.5 × L per piece | 2 |
| `health` | 5 | armor | armor | `max_health` +1 × L per piece | 1 |
| `swiftness` | 5 | foot armor | feet | `movement_speed` +5% × L (`add_multiplied_base`) | 2 |
| `cushion` | 3 | chest armor + elytra | chest | `damage_protection` +4 × L vs `#enchanting2:cushioned` (= `fly_into_wall`) | 5 |
anvil_cost by weight as vanilla: 5 → 2, 2 → 4, 1 → 8. Item tags: `enchanting2:enchantable/melee` = `#minecraft:enchantable/weapon`
+ trident; `/ranged` = bow, crossbow, trident; `/melee_or_ranged`; `/tool_or_melee` = `#minecraft:enchantable/mining` +
melee; `/tool_or_weapon` = that + ranged; `/cushion` = `#minecraft:enchantable/chest_armor` + elytra.

**Agent calls (decisions.md "Phase 10a"):** no `exclusive_set` on any of them (§4). All eleven join
`#minecraft:non_treasure` and `#minecraft:tooltip_order`. `enchantment/ModEnchantments.KEYS` lists our IDs. The title-screen
config list = vanilla + ours, pinned to the registry by a GameTest. Pure math lives in MC-free classes with JUnit (the
random rounding).
**Not doing:** Vision (10b); curses (10e); per-enchantment `.desc` tooltip keys; crit particles or sound for Critical
Strike; healing mobs that spawn in Health armor up to their new max (vanilla behaviour for any `max_health` item);
config keys for any number (data, overridable by datapack); changing vanilla's truncation for anything but the two XP
methods.
**Blast radius:** a world opened with this build gains eleven `enchanting2:` registry entries. They start appearing on new
loot, mob gear, villager offers and table rows. Removing the mod later strips them from items (accepted, DESIGN §2a).
The XP mixin changes rounding for any mod's `mob_/block_experience` effects (vanilla ships none). The Health mixin only
restores a saved value, clamped to the live max. No config file changes, no world data written by us.
**Rollback:** `git revert` the stage's commit. Items in dev worlds (`run/`, gitignored) lose the new enchantments on
the next load with a vanilla WARN. Nothing outside the repo is touched.

### Stage 10aA — Shared data + weapon enchantments → commit  ✅ 2026-10-09
- [x] 10aA.1 Item tags (`enchanting2:enchantable/*`), damage-type tag `enchanting2:cushioned` (used in 10aC, ships here
      with the other tags), `#minecraft:non_treasure` / `#minecraft:tooltip_order` entries for the six weapon ones.
- [x] 10aA.2 JSON: `withering`, `poisoning`, `freezing`, `attack_speed`, `critical_strike`, `reach`. Lang names.
- [x] 10aA.3 `enchantment/ModEnchantments.KEYS` (+ `ids()`); `ConfigEditing.enchantmentNames` title screen = vanilla + ours.
      `ConfigScreenClientGameTest` title-screen assertion follows.
- [x] 10aA.4 GameTests (`NewEnchantmentsGameTest`): registry pin (every `enchanting2:` ID is in `ModEnchantments.KEYS`
      and vice versa, max levels as the table, every one in `non_treasure` and the table pool); supported items (a sword,
      bow and trident take Withering; a pickaxe doesn't; Reach on a pickaxe, not a bow). Effects: melee hit with level
      3 → victim has the effect for 80 ticks (each of the three); a bow's arrow hitting → effect applied; Attack Speed V
      → attack speed attribute = base × 1.5; Critical Strike: over N seeded `modifyDamage` calls at level 5, the ×1.5 rate is
      within bounds of 25% and every result is 1.0 or 1.5; Reach III → both ranges +1.5.
      Mutation: delete one JSON's effect → its test fails.
      Mutation run (five at once: `is_direct` on Poisoning, Attack Speed +0.2/level, Reach's entity range dropped,
      Withering base 3 s, crit 6%/level): exactly the five matching tests failed, each on its own assertion. Also
      `ModEnchantmentDataTest` (JUnit): JSON ↔ `ModEnchantments`, lang key per JSON, `non_treasure` + `tooltip_order`
      membership, no `exclusive_set`, own item tags exist. Attribute tests add `EnchantmentHelper.forEachModifier`
      directly: `runLocationChangedEffects` only adds attributes when an effect becomes active, and a mock player
      never ticks its equipment. 291 JUnit (+5), 179 GameTests (+8), 4 client GameTests.

### Stage 10aB — XP Boost + random rounding → commit  ✅ 2026-10-09
- [x] 10aB.1 `enchantment/StochasticRounding.round(float, float roll)` (MC-free): `floor(x) + (roll < frac ? 1 : 0)`,
      negatives and NaN/∞ handled (log + vanilla `intValue`). JUnit: integers unchanged, 5.75 rounds up iff roll < 0.75, mean
      over a fixed seed within bounds.
- [x] 10aB.2 `mixin/EnchantmentHelperMixin` (extend): `processMob/BlockExperience` return the rounded value, using
      the level's random.
- [x] 10aB.3 JSON `xp_boost` + tags + lang. GameTest: XP Boost V on 5 mob XP over N seeded calls averages 6.25 ± bound;
      no enchantment → exactly 5 every call (rounding is a no-op on whole numbers). Mutation: drop the mixin → average 6.
      Done: `round(float, DoubleSupplier)` draws a roll only for a fractional value, so vanilla's random sequence is
      untouched when no such enchantment applies; a non-finite value keeps vanilla's `intValue` with a WARN. Block XP
      tested too (2 → 2.5 average). Mutation (handler returns vanilla's `intValue`): exactly
      `xpBoostAddsFivePercentPerLevelOnAverage` failed, mean 6.0. 297 JUnit (+6), 181 GameTests (+2).

### Stage 10aC — Armor enchantments + Health relog fix → commit  ✅ 2026-10-09
- [x] 10aC.1 JSON `toughness`, `health`, `swiftness`, `cushion` + tags + lang.
- [x] 10aC.2 `mixin/LivingEntityMixin`: at `readAdditionalSaveData` TAIL keep the saved `Health` when it exceeds the
      clamped value; at `detectEquipmentUpdates` TAIL (first time only) `setHealth(min(saved, max))` and clear it.
      DEBUG log when it restores.
- [x] 10aC.3 GameTests: Toughness V on four pieces → +10 toughness; Health V on four → max 40; Swiftness V → speed ×1.25 of
      base; Cushion III → 12 protection vs `fly_into_wall`, 0 vs a fall. Relog: a zombie in Health V armor at 34/40, saved
      and loaded into a new zombie, has 34/40 after one tick (mutation: drop the mixin → 20). Health armor removed → health
      clamps to 20 (vanilla, pinned).
      Mutation run (restore disabled, Toughness +1.0/level, Cushion base 5, Swiftness +0.1/level): exactly the four
      matching tests failed (the relog one read 20.0). The `min(saved, max)` guard is redundant with `setHealth`'s own
      clamp, so it can't be mutation-run; `restoredHealthIsClampedToTheLiveMax` pins the behaviour. Done differently:
      `TableScreenClientGameTest.clickRow` scrolls the row into view first. It assumed Aqua Affinity and Efficiency
      start in view, which stopped being true once 11 `enchanting2:` rows sort ahead of `minecraft:` ones.
      297 JUnit, 187 GameTests (+6), 4 client GameTests.
- [x] 10a done 2026-10-09. Owner: in the next `runClient` session, check the new names, a Health V set (hearts row)
      and the table panel order (ours first, ID order; see state.md).

## Phase 11 — v1 wrap-up (private repo; nothing published)
- [ ] 11.1 README, changelog, config documentation
- [ ] 11.2 Version tag + build artifact (tag push is outward-facing, so the owner confirms)
- [ ] 11.3 Plan the 26.x port as a new roadmap entry. Public release is a later decision.

---

## Done

## Destructive-action doctrine

**Goal:** The template treats anything irreversible as a gated operation — the agent
names the blast radius, proves recoverability, and confirms before acting; and code the
agent *writes* ships with dry-run, confirmation, and reversibility built in.

**Approach:** Two enforcement layers. (1) Doctrine in `AGENTS.md` + a `guard-destructive`
skill, which works in every tool including Copilot. (2) Mechanical `permissions.deny` /
`permissions.ask` rules in `.claude/settings.json`, which actually block in Claude Code.
Prose alone loses under a truncated context; rules alone only cover one of the two tools.

**Not doing:** No `PreToolUse` hook script — a hook that silently no-ops is worse than no
hook (same reasoning as the `mem0` decision), and an LLM-prompt hook taxes every shell
call. Documented as an opt-in escalation instead. Not touching the tracked `.env`
(owner's standing decision), not adding `disableBypassPermissionsMode` (locks the owner
out of their own tool without asking).

- [x] 1. `.claude/skills/guard-destructive/SKILL.md` — classification, five gates, absolute
      stops, guards-in-code requirements; byte-identical copy to `.github/skills/`
- [x] 2. `.claude/settings.json` — deny (unrecoverable) + ask (destructive-shaped) rules
- [x] 3. `AGENTS.md` — non-negotiable bullet, `Destructive Actions` section, workflow step,
      skill table row, Definition of Done items
- [x] 4. `.github/copilot-instructions.md` — condensed version of the same rules
- [x] 5. `code-review` — destruction & data-safety checklist, unguarded destruction = CRITICAL
- [x] 6. `git-github` / `build-run` / `plan-work` / `test-iterate` — per-skill guards
- [x] 7. Blast radius + Rollback made mandatory in the `TODO.md` and `plan-work` templates
- [x] 8. Verify: `settings.json` parses, both skill trees byte-identical

**Rollback:** `git revert` the commit. Every change is additive documentation plus one new
JSON file — nothing existing is deleted, so revert is clean and total.

_Move completed tasks here, or delete them once they're in the git history._
 