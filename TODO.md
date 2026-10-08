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

## Phase 7 — Enchanting table (four stages, one commit each)
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

### Stage 7C — Panel model and scrolling → commit
- [ ] 7C.1 `table/TablePanelView` (pure): a row per offered enchantment (name, current → selected level, its line cost
      or the reason it can't be picked), surcharge / discount / total lines, footer (cost and lapis, too expensive with
      the max, insufficient levels or lapis, invalid pick named, pick something). JUnit for every status.
      `TableLangKeysTest` walks every key it can produce. Lang keys (en_us).
- [ ] 7C.2 `client/SidePanel`: vertical scrolling (wheel, clamped, kept across rebuilds while the item is unchanged)
      and per-row hit zones (`<` / `>` level arrows, the row body). Anvil and grindstone panels unchanged (a list that
      fits never scrolls). `screen/SidePanelLayout` takes the panel width (the table panel is wider for names + level
      + cost); JUnit for the new width.
- [ ] 7C.3 `client/TablePreviewState` (receiver; cleared on close, config INIT, disconnect), `client/TableScreenAccess`.
      Build green, commit.

### Stage 7D — Enchanting table screen → commit
- [ ] 7D.1 `mixin/client/EnchantmentScreenMixin`: `init` TAIL applies the layout to `leftPos`; `renderBg` shifted to
      `leftPos` (pose translation, as the grindstone); vanilla's three offer rows suppressed. In their 108×57 area: XP
      and lapis totals, the status line, and an Enchant button drawn with vanilla's own `enchantment_slot` sprites
      (normal / highlighted / disabled). Panel at TAIL. Row body click toggles a pick (default level = the highest
      selectable), arrows step the level, and each change sends `TableSelectPayload`. Enchant sends
      `TableEnchantPayload` with the shown numbers: the cost's levels and lapis, enabled for a priced status when the
      local player's levels and the lapis slot cover them. The panel's `INSUFFICIENT_LEVELS` can be stale, see 7B.
      Vanilla `mouseClicked` offer hit-test is skipped.
      `hasClickedOutside` is false over the panel.
- [ ] 7D.2 Client GameTest: singleplayer, real table with 15 shelves, diamond sword + lapis → rows; pick Sharpness V
      and Mending → totals shown; Enchant → sword has both, levels and lapis charged; plain book with two picks →
      enchanted book; scrolling on a book reaches the last row; tiny window → panel on screen; a panel click keeps a
      carried item. Screenshots.
- [ ] 7D.3 DESIGN §6 (table as built), state.md, decisions.md. Owner `runClient` list: panel look, a modded client on a
      vanilla server keeping the vanilla table. Mutation: the Enchant button sends nothing → client test fails. Build +
      `runClientGameTest` green, commit.

## Phase 8 — Conflict effect resolution + Fortune on all tools
- [ ] 8.1 Silk/Fortune behavior per Q5 (loot-table modification, not item hacks). Adds the
      `silk_fortune_mode` key and its screen toggle with the duplication warning (moved from Phase 2).
- [ ] 8.2 Damage-family stacking, Protection family (keep EPF cap), Mending + Infinity
- [ ] 8.3 Riptide/Loyalty/Channeling per Q11
- [ ] 8.4 `compat/PlacedBlockTracker`: MC-free core ported from
      `mcMMO-Singleplayer/.../util/PlacedBlockTracker.java` + `BlockRules` (mark/clear/move),
      unit-tested outside Minecraft
- [ ] 8.5 Per-chunk persistence via Fabric Data Attachment API; fail closed + WARN on read errors
- [ ] 8.6 Marking hooks: hand placement, piston move (three-pass), **falling block carries
      the mark**, lava/water generators and snow golems (only for blocks in a Fortune tag),
      break clears it
- [ ] 8.7 Fortune multiplies natural blocks in `fortune_multiplies/<tool>` tags; ancient
      debris hard-excluded; `FORTUNE_MULTIPLIES_SILK` uses the same tracker
- [ ] 8.8 GameTests: place → Fortune V mine → exactly 1 drop; the same via piston push and
      sand fall; natural → multiplied; unreadable chunk → no bonus
- [ ] 8.9 GameTests per rule (8.1–8.3)

## Phase 9 — Acquisition (loot, trades, table pools)
- [ ] 9.1 Per-enchantment table/loot/trade flags and weights
- [ ] 9.2 Treasure flag configurable; disabled enchantments disappear from every source
- [ ] 9.3 Tests: a disabled enchantment never appears from loot or trades

## Phase 10 — New enchantments (DESIGN §9), by route
- [ ] 10a Data-only (D): Withering, Poisoning, Freezing, Attack Speed, Critical Strike,
      Toughness, Health, Swiftness, Reach, XP Boost, Vision, Cushion. JSON + lang + tags +
      GameTest each.
- [ ] 10b Custom effect types (E): Vampirism, TrueShot Aura, Smelting, Beheading
- [ ] 10c Mixin/network (M): Quick Strike, Magnetic, Ender Link, Keep on Death, Double Jump,
      Flight, Area Mining, Replant, Tiller, Last Stand, Deflect, Shield Bash.
      Each gets its own written sub-plan here first, covering edge cases from §9 and dupe tests.
- [ ] 10e Curses (§5a): Fragility, Weight, Greed, Fumbling (D); Hunger (M). Curse tag +
      `discount_percent` data; table shows curses as optional picks with a negative cost line
- [ ] 10d Balance pass: every candidate moves from REVIEW to INCLUDE/DEFER/REMOVE

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
 