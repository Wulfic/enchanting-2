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

## Phase 4 — Enchantment rules engine
**Goal:** applicability is mandatory, conflict families are configurable, max levels are
overridden, and duplicate IDs can't occur.
- [ ] 4.0 Probe (dev server): does `/reload` re-run `EnchantmentEvents.MODIFY`? Enchantments are
      likely a world-load registry, so probably not, and max-level/conflict keys then need a restart.
      `MODIFY` runs before the `MinecraftServer` exists, so it reads the config file through
      `ConfigFiles.load`, not the server holder. Fix DESIGN §2 ("/reload or restart") and the screen hint to match.
- [ ] 4.1 `enchantment/ConflictFamilies` + `EnchantmentEvents.MODIFY` hook: relax
      `exclusive_set` per config, apply `max_level_override`
- [ ] 4.2 `enchantment/EnchantRules`: `canApply(item, ench, level) → Result(reason)`, the one
      place applicability/conflict/max checks live (used by anvil, table, books)
- [ ] 4.3 Unit/GameTests: Efficiency on helmet rejected; helmet enchant on pickaxe rejected;
      Silk+Fortune allowed only when the family is relaxed; unknown modded enchant preserved;
      duplicate entry normalized
- [ ] 4.4 Apply the §8 max-level table; GameTests for Lure 5 (fish still bite) and Riptide 5
      (server accepts the launch). Drop either to 4 if its test fails.

## Phase 5 — Cost model
**Goal:** pure cost calculator that reproduces the DESIGN §5 examples exactly.
- [ ] 5.1 `enchanting/CostModel` + `CostBreakdown` (per-line explanation for the UI)
- [ ] 5.2 Upgrade modes, Mending surcharge, rename, creative-free, `maximum_operation_cost`
- [ ] 5.3 Lapis calculation (table only, cap 64); the anvil path returns 0 lapis, with a test
- [ ] 5.4 Table-driven tests from every row of the DESIGN §5 examples table, plus a
      property test: any build order of the same final item → same total cost
- [ ] 5.5 Curse discount (§5a): per-curse percent, 45% cap, rounds up, never discounts
      rename/repair; Cleanse cost = max(min, repaid discount). Tests include "curse →
      enchant → cleanse is never cheaper than enchanting uncursed".

## Phase 6 — Anvil
- [ ] 6.1 Mixin `AnvilMenu#createResult` → `EnchantRules` + `CostModel`
- [ ] 6.2 Remove the 40-level cap and prior-work penalty (configurable)
- [ ] 6.3 Invalid-book modes + preview of applied and skipped enchantments
- [ ] 6.4 Reason messages (DESIGN §5) on the anvil screen
- [ ] 6.5 GameTests: multi-enchant book on a tool, repair, rename, **no enchantment ever
      silently lost**, insufficient levels blocks the take

## Phase 6b — Grindstone extraction (DESIGN §6)
- [ ] 6b.1 Grindstone mixin with three modes (Extract / Cleanse / Disenchant), priced by
      `CostModel`; vanilla Disenchant unchanged
- [ ] 6b.2 Action and enchantment picker on the grindstone screen + C2S choice payload
      (server re-validates)
- [ ] 6b.3 GameTests: enchantment moved, never copied or lost (including mid-operation
      desync); curse extraction refused; Cleanse charges the repay-the-discount cost;
      multi-enchantment book split

## Phase 7 — Enchanting table (new screen)
- [ ] 7.1 Server menu: list of applicable enchantments (Mending included, curses off),
      selection, validation, bookshelf tier gating per DESIGN §6 (vanilla shelf rules, 15 = full)
- [ ] 7.2 C2S selection payload, server re-validates everything (never trust the client cost)
- [ ] 7.3 Client screen: readable list, level pickers, live cost and lapis totals, reasons
- [ ] 7.4 Books: multi-enchant via the table
- [ ] 7.5 GameTests: forged C2S payloads (inapplicable enchant, over max, unaffordable) are rejected

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
 