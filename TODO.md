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

Owner 2026-10-20, second round (decisions.md "Phase 13 owner rulings"):
- ~~Push `port/loaders` after every green stage; PR at 13F~~ **Superseded 2026-10-21:** `port/loaders` fast-forwarded
  into `main` and deleted; Phase 13 continues on `main`, no PR (owner: as few branches as possible; decisions.md
  "One branch (main)"). Pushed main a071f7f..467e0d1, then deleted (all already in main, checked with
  `merge-base --is-ancestor`; `ls-remote` shows only main): `port/loaders` (local cba2589, remote 9ed8c91) and
  `ci/client-gametests` (local a071f7f, remote 4e1659c). Undo: `git push origin cba2589:refs/heads/port/loaders`,
  `git push origin 4e1659c:refs/heads/ci/client-gametests`.
- **Every jar names its loader, Fabric too:** `enchanting2-1.2.0+26.3-fabric.jar`, `…+1.21.11-fabric.jar`,
  `…+26.3-neoforge.jar`, `…+26.3-forge.jar`. Breaks the 1.1.0 naming on purpose: `release-collect.sh`,
  `ReleaseDocsTest`, README/wiki tables and CI artifact paths change in 13F. Node names stay as they are.
- **Widen B1 now on NeoForge/Forge:** 13F adds runtime nodes for NeoForge 26.1.0 / 26.1.1 (beta builds) and Forge 62 /
  63 (26.1 / 26.1.1) on the 26.1.2 jars, as `matrix/` does. Green widens the claim; red leaves it at 26.1.2.

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

**Work on `main`** (until 2026-10-21 on `port/loaders`, then fast-forwarded into main). Each stage = one commit,
`verifyAll` green before it.

- [ ] 13A — **Spikes, throwaway, nothing merged.** These decide whether the rest holds.
  - [x] A1. Quilt: run the 1.21.11 and 26.x Fabric jars' server GameTests on Quilt Loader 0.31.0-beta.4 + Fabric API
        (Quilt installer CLI, server, scratchpad). Record pass/fail per band.
        **2026-10-20 result:** 26.1.2 / 26.2 / 26.3: **339/339 server GameTests pass** on Quilt with the unchanged
        1.1.0 Fabric jars (+ Fabric API, YACL, `fabric-gametest-api-v1`). 1.21.11: boots to "Done", config loads, no
        errors; GameTests can't run in *any* production 1.21.11 game. Fabric Loader 0.19.5 fails identically
        (`AnnotationFormatError` on `@GameTest.rotation()`'s intermediary enum default), so this is a harness limit,
        not a Quilt one. → Quilt is claimed for B1–B3. **Owner 2026-10-20: 1.21.11 stays Fabric-only** (no Quilt
        claim, no NeoForge/Forge); Quilt, NeoForge and Forge are for the 26.x bands. Harness notes in gotchas.md
        2026-10-20.
  - [x] A2. NeoForge 26.3: an empty node in this build (Stonecutter loader axis + ModDevGradle next to
        loom-back-compat, or Architectury Loom for both) that loads `enchanting2.mixins.json` with one MixinExtras
        `@WrapOperation`. Choose the build plugin, `/research-docs` first.
  - [x] A3. Forge 26.3: the same with ForgeGradle. Check that Forge 66 applies mixins **and** ships MixinExtras.
  - **Gate: passed 2026-10-20.** Scratch mini-build (Stonecutter 0.9.8, nodes `26.3` / `26.3-neoforge` /
        `26.3-forge` from one `src/`, `vers(name, mc).buildscript(...)`, `constants.match(loader, ...)`): Loom (via
        loom-back-compat), ModDevGradle 2.0.148 and ForgeGradle 7.0.40 build side by side, configuration cache on.
        `runGameTestServer` on both: one `@Inject` + one MixinExtras `@ModifyReturnValue` on `MinecraftServer` apply
        (`neoforge+SPIKE-MIXINEXTRAS`, MixinExtras 0.5.5; `forge+SPIKE-MIXINEXTRAS`, 0.5.4). Forge needs mixin
        `compatibilityLevel` ≤ `JAVA_21` (gotchas.md). decisions.md 2026-10-20 "build plugins".
- [x] 13B — **Done 2026-10-20.** `verifyAll` green on a quiet tree (435 JUnit + 339 GameTests on every node and on
      matrix 26.1 / 26.1.1); client GameTests green on 1.21.11, 26.1.2, 26.2, 26.3. `gametest-report.gradle`'s stale-
      report delete now fails closed (checked by hand: with the report held open, `runGameTest` refuses; build
      scripts have no test harness). **Platform seam, Fabric only, no behaviour change.** `platform/` interfaces + `platform/fabric`; the 28
      files call the seam. **Changed 2026-10-20:** server GameTests keep Fabric's `@GameTest` (Fabric's runner only
      finds its own annotation, so swapping it would mean re-implementing Fabric's test discovery). 13C/13D swap that
      one import for ours with a Stonecutter string replacement on their nodes, and their registrars scan it.
      Done so far: `Services` / `ClientServices` (loader picked by `//? if fabric`), `LoaderInfo`, `ServerEvents`,
      `ItemHooks`, `Attachments`/`Attachment`, `Networking`, `ClientNetworking`, `ClientHooks`; Fabric entry points
      `platform/fabric/Fabric{,Client}Entrypoint`; `PlatformImportsTest`. Proof: `verifyAll` and every
      node's `runClientGameTest` are as green as on `a071f7f`, with the same test counts (432 JUnit / 339 GameTests).
      Add a JUnit test that fails if any class outside `platform/<loader>/` imports `net.fabricmc` / `net.neoforged` /
      `net.minecraftforge`.
- [x] 13C — **NeoForge nodes** 26.1.2, 26.2, 26.3: `neoforge.mods.toml`, `platform/neoforge` (payload registration,
      data attachments with `copyOnDeath`, `ModifyDefaultComponentsEvent`, loot events, `PlayerEvent.Clone`,
      `RegisterGuiLayersEvent`, `ItemTooltipEvent`). The enchantment `MODIFY` replacement is a registry-load hook like
      10gA's `RegistryDataLoaderMixin`. Fix mixins that miss on NeoForge's patched targets (mixin `require` errors =
      the list), each with a GameTest already covering it. Proof: JUnit + 339 server GameTests on each node.
      **Plan (2026-10-20, checked against the NeoForge 26.3.0.95 sources jar, not docs):**
      - C1. Build: `settings.gradle` `vers('26.x-neoforge', '26.x').buildscript('build.neoforge.gradle')` for 26.1.2 /
        26.2 / 26.3 (start with 26.3 alone, widen once green); `stonecutter.gradle` plugins `moddev` 2.0.148 +
        `net.minecraftforge.gradle` 7.0.40 `apply false`; `build.neoforge.gradle` from the spike + the gametest source
        set as a second mod source set, `gameTestServer` run with vanilla `--report <build>/gametest-report.xml` (so
        `checkGameTestReport` is shared; `gradle/gametest-report.gradle` gets a non-Loom branch), `neo_version` per
        node. NeoForge-only resources (`META-INF/neoforge.mods.toml`, loot modifier JSON) under
        `src/main/resources-neoforge/`; `fabric.mod.json` excluded there, and the reverse in build.gradle.
        `ModMenuIntegration` and the YACL screen excluded from NeoForge compiles (Stonecutter `//? if fabric`).
        **C1a first:** move everything in build.gradle that isn't Loom (Stonecutter renames, band version, resource
        expansion, 26.3 loot upgrade, java/test config) into `gradle/common.gradle`, applied by both scripts. Proof of
        no change: the 1.21.11 and 26.3 Fabric jars' entry lists + CRC32s identical before and after.
        NeoForge mod IDs can't contain `-`, so on NeoForge the gametest source set joins mod `enchanting2` in dev runs
        (never in the jar). Client GameTest files + `SyncMarkerPayload` / `ClientWorlds` / `Delivery`: Fabric only.
        Server GameTests' other Fabric uses (`FabricLoader` ×3, `AttachmentTarget` ×2, `FakePlayer`,
        `PlayerBlockBreakEvents`, `EnvType`) go through the seam or a gametest-side helper.
      - C2. `platform/neoforge`: `NeoForgeEntrypoint` (`@Mod`, mod bus), `NeoForgeLoaderInfo` (`FMLPaths`, `ModList`),
        `NeoForgeServerEvents` (`ServerStartingEvent`, `ServerStoppedEvent`, `AddServerReloadListenersEvent` /
        `OnDatapackSyncEvent` for reload-done, `ServerTickEvent.Post`, `EntityJoinLevelEvent` server side,
        `PlayerEvent.Clone` with `isWasDeath`), `NeoForgeAttachments` (`AttachmentType.builder(() -> throw)` +
        `IAttachmentSerializer` writing the codec value as-is, `copyOnDeath`, `sync(targetOnly, codec)`; reads via
        `getExistingDataOrNull`), `NeoForgeNetworking` (`RegisterPayloadHandlersEvent` → `PayloadRegistrar`
        `playToClient` / `configurationToClient` / `playToServer`, `executesOn(HandlerThread.MAIN)` to keep the
        "server thread" contract, payloads marked **optional** so NeoForge doesn't kick a vanilla client with its own
        message before our `NetworkProtocol` check; `RegisterConfigurationTasksEvent` + an `ICustomConfigurationTask`
        for `onConfigure`, `hasChannel` for `sendableChannels`), `NeoForgeItemHooks` (`ModifyDefaultComponentsEvent`;
        `acceptsEnchantment` = `stack.supportsEnchantment`).
      - C3. Drops: one global loot modifier (`platform/neoforge/DropsLootModifier`, codec in
        `GLOBAL_LOOT_MODIFIER_SERIALIZERS`, JSON in `resources-neoforge/data/...`) calling the `onModifyDrops`
        listeners; the table `Holder` resolved from `getQueriedLootTableId()` through `reloadableRegistries()`.
      - C4. Enchantment load: NeoForge-only mixin (`mixin/neoforge/ResourceManagerRegistryLoadTaskMixin` + builder
        accessor), copied from Fabric item-api 14.1.0's own (`@WrapOperation` on `new PendingRegistration`), rebuilding
        only when the result differs (`Enchantment` is a record); lambda target picked by javap on NeoForge's jar.
        Listed in mixins.json under a loader condition, as band-A mixins are.
      - C5. Client: `NeoForgeClientEntrypoint`, `NeoForgeClientNetworking`, `NeoForgeClientHooks`
        (`ItemTooltipEvent`, `RegisterGuiLayersEvent.registerAbove(VanillaGuiLayers.HOTBAR)`). No config screen yet
        (13E).
      - C6. GameTests: our `platform/GameTest` annotation (same attributes as Fabric's that our tests use);
        Stonecutter string replacement swaps the import on non-Fabric nodes; `NeoForgeGameTests` reads the class list
        from the gametest `fabric.mod.json` entrypoints and registers each method on `RegisterGameTestsEvent`. Our own
        empty structure (Fabric's `fabric-gametest-api-v1:empty` doesn't exist there). `SaveFormatGameTest` /
        `PlacedBlocksGameTest` read the attachment NBT key from the seam, not Fabric's constant.
      - C7. Run the 339 on 26.3-neoforge; each failing mixin or test gets its own fix (budget 3 per failure). Then
        26.1.2 / 26.2.
      Blast radius: additive except `build.gradle` / `gametest-report.gradle` / mixins.json edits (git-restorable) and
      the gametest import swap (Fabric nodes unchanged, guarded by their 339 still running). Rollback: revert the 13C
      commit.
      **Progress 2026-10-20:** C1a committed (3ab2af2, Fabric jars identical entry for entry). **26.3-neoforge green:**
      JUnit 438 (5 Fabric-metadata tests skip, 2 NeoForge ones run), server GameTests 339/339 (338 ours +
      `minecraft:always_pass`, the same set as Fabric). Changed from the plan while doing it:
      - NeoForge resources live in `src/neoforge/resources` (mod toml, loot modifier JSON) and
        `src/neoforge/gametest/resources` (GameTest mod toml, `empty.nbt` from `scripts/make-empty-structure.py`), added
        as source dirs only on NeoForge nodes; nothing to exclude on Fabric.
      - The GameTest mod stays a separate mod there, `enchanting2_gametest` (no `-` in NeoForge IDs), with its own
        mixins; `NeoForgeGameTests` reads the `fabric-gametest` list from its fabric.mod.json.
      - Seam additions: `Registration` (NeoForge freezes built-in registries), `Attachment.set(null)` removes,
        `registerToServer(type, codec, handler)` and `onReceive(type, configuration, handler)` (typed, no casts).
      - Mixins that differ on NeoForge, behind `//? if neoforge`: `LivingEntityMixin` shield block (extra `int`),
        `LavaFluidMixin` (setBlock on 26.3), `SpawnerBlockMixin` (`getExpDrop`); NeoForge-only `mixin/neoforge/`
        (enchantment load + effects accessor). gotchas.md 2026-10-20 "NeoForge 26.3".
      - `ElytraGameTest` runs the tick's equipment check before its AI steps (NeoForge glides on an attribute).
      - CI's client jobs list Fabric nodes only. NeoForge jar name = Fabric's until 13F adds the loader to every name.
      Next: verifyAll + Fabric client GameTests → commit + push 26.3-neoforge. **Then 13F's jar naming moves up**
      (loader in every jar name, `ReleaseDocsTest` / README / wiki tables per loader): `ReleaseDocsTest` wants the
      install tables to list exactly the bands, and a 26.1.2-neoforge node's band (26.1.2) differs from Fabric's
      (26.1-26.1.2), so it can't pass under today's loader-less names. Then 26.1.2-neoforge, 26.2-neoforge.
      **Owner 2026-10-20 (decisions.md "third round"): fix the protocol-mismatch gap before the next node.** Plan,
      its own commit after the 26.3-neoforge one:
      - `network/HelloPayload` (`enchanting2:hello`, no `v<N>/`: its ID and wire format, one VarInt protocol, never
        change). NeoForge registers it `configurationBidirectional`, optional.
      - `NeoForgeNetworking.ConfigureTask`: client has `hello` → send `Hello(VERSION)`, finish only when the reply
        arrives; the reply handler runs the `onConfigure` listeners with the client's number. Client lacks it → run
        them at once (vanilla / no mod). Client handler replies with its own `VERSION`, unconditionally.
      - Seam: `ConfigurationConnection.clientProtocol()` (`OptionalInt`; Fabric empty, it reads channels).
        `NetworkProtocol.classify(channels, required, clientProtocol)`: a reported number decides MATCH / MISMATCH [N].
      - Tests: JUnit on the new `classify` overload (reported match, reported mismatch → message names N, no report →
        today's channel rules); NeoForge server GameTests unchanged (339). Owner `runClient` check: a protocol-5
        client against a protocol-6 server build gets the mismatch text (13E/C7 list).
      Not doing: hello on Fabric (Fabric already sees every channel); cross-loader joins.
      **Built 2026-10-20** as planned, with two refinements: the channels still decide MATCH (the reported number
      only fills in MISMATCH's protocol list, so a lying or odd hello can't let a client in), and the frozen-format
      test is a server GameTest (`ConfigWiringGameTest.helloWireFormatIsFrozen`: ID + exact bytes), because no JUnit
      test loads Minecraft classes. GameTests now 340 (339 ours + `always_pass`). **Not verified live:** no harness
      joins a NeoForge client in tests; the owner `runClient` check below covers it (a normal join must not hang in
      configuration: the server waits for the client's hello).
      **26.2-neoforge green 2026-10-20** (NeoForge 26.2.0.93): one fix, `DropsLootModifier` on 26.2's older
      `LootModifier` (condition array, required `conditions`), plus a bug that was on 26.3 too: the obsolete
      `global_loot_modifiers.json` list logged an ERROR at every start (removed; `NeoForgeLootModifiersTest`).
      `loot_modifiers` joined the 26.3 data upgrade. verifyAll: 445 JUnit, 340 GameTests on all six nodes; both
      NeoForge logs free of ERRORs but our config tests' deliberate ones. gotchas.md "NeoForge loot modifiers".
      **26.1.2-neoforge built 2026-10-20** (NeoForge 26.1.2.122): `:26.1.2-neoforge:build` green first try (340/340,
      log clean), README/wiki row split. verifyAll 2026-10-20: 445 JUnit + 340 GameTests on all 7 nodes. **13C done.**
      Owner check later (13E/C7): `runClient` on NeoForge (tooltips, HUD, table/anvil/grindstone panels, joining a
      vanilla server gets our message; singleplayer and a `runServer` join both reach the world, not stuck at
      "Joining world" — that is the hello round trip).
- [x] 13D — **Forge nodes** 26.1.2, 26.2, 26.3: `mods.toml`, `platform/forge`, Forge events. Same proof: JUnit +
      340 server GameTests on each node, log free of foreign ERRORs.
      **Plan (2026-10-20, read from the Forge 26.3-66.0.13 sources jar, not docs):** Forge 26.x = EventBus 7 (static
      `XEvent.BUS` per game event; mod-bus events via `XEvent.getBus(context.getModBusGroup())`), still capabilities,
      `ChannelBuilder` networking, `GatherLoginConfigurationTasksEvent`, `GatherComponentsEvent.Item`,
      `IGlobalLootModifier` with the **old** list file (`data/forge/loot_modifiers/global_loot_modifiers.json`, condition
      array like NeoForge 26.2), `ItemTooltipEvent`, `AddGuiOverlayLayersEvent`. What differs from the 13C design:
      - **Attachments are ours, not capabilities.** Forge 26.3 never saves chunk capabilities (`LevelChunk.writeCapsToNBT`
        has no caller in any patch, and `ChunkDataEvent.Load` is never posted), and `PlacedMarks` lives on chunks. So
        Forge-only mixins (`mixin/forge/`) hold a value map on `Entity` and `ChunkAccess`, saved under one key
        `enchanting2:attachments` (raw codec values, as Fabric), modelled on Fabric API's data-attachment mixins:
        entity save/load, `SerializableChunkData` copyOf/write/parse/read, ProtoChunk → LevelChunk hand-over.
        `copyOnDeath` / End return in `PlayerEvent.Clone`; `ownerSync` (Last Stand) by our own S2C payload, sent on
        set and on join/respawn/dimension change. decisions.md "13D attachments".
      - **GameTest instances have no runtime API** (Forge's `ForgeGameTestHooks.gatherTests` exists but nothing calls
        it; `test_instance` is a datapack registry and Forge has no `RegisterGameTestsEvent`). Test *functions* go in
        through `RegisterEvent` as on NeoForge; *instances* as an in-memory datapack from `AddPackFindersEvent` holding
        one `test_instance/<id>.json` per test, encoded from the annotation. Spike that first (D6a); fallback is a
        build-time generator writing the same JSON into the gametest resources. Discovery (class list, validation,
        IDs, environment check) moves out of `NeoForgeGameTests` into a loader-neutral `GameTestDiscovery`, shared.
      - D1. Build: `build.forge.gradle` (FG 7.0.40, from the spike + build.neoforge.gradle's source sets/excludes,
        `run_root`, port 25566, `clearGameTestRunDir`, `check` → `runGameTest`, `--report`); `stonecutter.gradle`
        adds the FG plugin `apply false` + Forge plugin repo; `settings.gradle` `vers('26.3-forge', '26.3')`; node
        `gradle.properties` (`forge_version`, `forge_dependency`, `minecraft_dependency`); `common.gradle` requires
        `forge_dependency` on Forge, expands `META-INF/mods.toml`; mixin `compatibilityLevel` `JAVA_21` on Forge
        (gotchas.md "Forge's Mixin 0.8.7"), `MixinConfigs` manifest + `--mixin.config` run args.
        Resources in `src/forge/resources` (`META-INF/mods.toml`, loot modifier list + `drops.json`) and
        `src/forge/gametest/resources` (GameTest mod toml, `empty.nbt`). Mod IDs: `enchanting2`, `enchanting2_gametest`.
      - D2. `platform/forge`: `ForgeEntrypoint` (`@Mod`, `FMLJavaModLoadingContext`), `ForgeBus` (mod BusGroup, set
        before init, as `NeoForgeBus`), `ForgeLoaderInfo`, `ForgeRegistration` (`RegisterEvent`, holder bound after),
        `ForgeServerEvents` (`ServerAboutToStartEvent`, `ServerStoppedEvent`, `OnDatapackSyncEvent`, `TickEvent`
        server post, `EntityJoinLevelEvent`, `PlayerEvent.Clone`), `ForgeItemHooks` (`GatherComponentsEvent.Item`;
        `acceptsEnchantment` by Forge's own rule), `ForgeAttachments` (above), `ForgeNetworking` (one optional
        `ChannelBuilder` payload channel per direction or per payload — whichever lets `hasChannel` work per type;
        configuration task from `GatherLoginConfigurationTasksEvent` at lowest priority; **no hello** (owner 2026-10-20:
        Forge keeps every client channel, as Fabric; decisions.md "13D owner rulings")).
      - D3. Drops: `platform/forge/DropsLootModifier` (codec in Forge's `GLOBAL_LOOT_MODIFIER_SERIALIZERS`).
      - D4. Enchantment load: Forge's `ResourceManagerRegistryLoadTask.load` lambda still does `new PendingRegistration`
        (with its conditions wrapper), so the NeoForge mixin should apply unchanged: list it for `loader != 'fabric'`
        and move it to a shared `mixin/forgelike/` package if it does (lambda name checked by javap on Forge's jar).
      - D5. Client: `ForgeClientEntrypoint`, `ForgeClientNetworking`, `ForgeClientHooks`. No config screen (13E).
      - D6. GameTests: D6a spike above; then `ForgeGameTests` (`@Mod enchanting2_gametest`), `TestPlatform` Forge
        branches (dist, attachments key, `FakePlayerFactory`, `BlockEvent.BreakEvent`).
      - D7. Run the 340 on 26.3-forge; each failing mixin or test its own fix (budget 3 per failure), behind
        `//? if forge` or the seam. Then 26.2-forge and 26.1.2-forge, README / wiki / CHANGELOG / release notes rows.
      **Not doing:** capabilities; a Forge config screen (13E); client GameTests on Forge; Forge 62/63 runtime nodes
      (13F); filing the chunk-capability issue upstream without the owner's go-ahead.
      **Blast radius:** additive (new nodes, `platform/forge`, `mixin/forge`, resources) except edits to
      `NeoForgeGameTests` (discovery moved out, NeoForge's 340 guard it), `mixins.json`, `common.gradle`,
      `stonecutter.gradle`, `settings.gradle` — all git-restorable. Rollback: revert the 13D commit(s); one per node.
      **Progress 2026-10-20:** shared groundwork committed on its own ("13D groundwork"): `Registration.register` returns
      `Supplier<Holder<T>>` (Forge's `RegistryObject` binds its holder only after `RegisterEvent`); the enchantment-load
      mixin + accessor moved `mixin/neoforge` → `mixin/forgelike`, its modifier list → `platform/forgelike/EnchantmentLoad`
      (listed for `loader != 'fabric'`; Fabric excludes both `forgelike` packages); `empty.nbt` → `src/forgelike/`;
      mixin `compatibilityLevel` from `mixin_java_level`; `gametest-report.gradle` Forge branch. Proof: verifyAll with
      `:26.3-forge:build` skipped — 340/340 GameTests on all 7 nodes + matrix 26.1 / 26.1.1, JUnit 444/445 where the one
      red is `ReleaseDocsTest` seeing the uncommitted `versions/26.3-forge/` (it lists nodes from `versions/`; the
      committed tree has no such node).
      Attachments as built: per attachment, a Guava weak-keys (identity) map holder → value, not a mixin field: typed
      without casts, thread-safe for off-thread chunk reads, and identity-keyed (`Entity.equals` compares IDs, which a
      singleplayer client and server share). Mixins only save/load/transfer.
      **26.3-forge green 2026-10-20** (Forge 26.3-66.0.13): 340/340 server GameTests, log free of ERRORs but our config
      tests' deliberate ones. D6a held: test instances come from an in-memory datapack (`ForgeGameTests`), discovery
      shared with NeoForge (`gametest/platform/GameTestDiscovery`). Changed from the plan while doing it (each a red
      GameTest or a failed launch first; gotchas.md "Forge 26.3 dev runs..."):
      - Seam: `ClientNetworking.onReceive` takes the codec (Forge pairs a payload's codec and receiving handler in one
        registration; Fabric/NeoForge ignore it); `ServerEvents.onFluidPlacesBlock` (Forge places generator output
        itself; never fired elsewhere); `CollectedSpawners.collects(boolean, int)`.
      - Forge-only: access transformer (`EnchantmentHelper.runIteration*`), `pack.mcmeta` per mod (node's
        `resource_pack_format` / `data_pack_format`), mixin `minVersion` 0.8, `DataComponentInitializersMixin` (default
        components; Forge's event doesn't reach ItemStacks), `//? if forge` in BlockMixin (7-arg dropResources),
        ServerPlayerGameModeMixin (block XP in DropRedirect), SpawnerBlockMixin (`getExpDrop`).
      - build.forge.gradle: one folder per mod in dev runs, GameTest run timeout 20 min.
      - JUnit: `ModIdentityTest` Forge metadata tests (mods.toml, entry points, pack.mcmeta), compatibility level capped
        at 21 on Forge, Forge loot modifiers keep `conditions`. README + wiki: a Forge column. release-collect skips
        Forge's `-slim.jar`.
      **Not verified live (owner checks, 13E/C7 list):** a Forge client — `runClient` tooltips, HUD (Last Stand's
      owner sync), table/anvil/grindstone panels, joining a vanilla server, singleplayer reaching the world. No harness
      joins a Forge client in tests.
      verifyAll 2026-10-20: 448 JUnit + 340 GameTests on all 8 nodes (+ matrix); client GameTests green on 1.21.11,
      26.1.2, 26.2, 26.3. Committed d9d1c1b.
      **26.2-forge green 2026-10-20** (Forge 26.2-65.1.5, packs 88 / 107): 340/340 first run; one change, the GameTest
      pack's `Pack.ResourcesSupplier` (`openPrimary` / `openFull` before 26.3). verifyAll: 448 JUnit + 340 GameTests on
      all 9 nodes. Committed 0abf6a6.
      **26.1.2-forge green 2026-10-20** (Forge 26.1.2-64.1.5, packs 84 / 101, range 26.1.2 alone as NeoForge's):
      340/340 first run, no source change. verifyAll: 448 JUnit + 340 GameTests on all 10 nodes. **13D done** but the
      owner's Forge `runClient` checks above. CI on d9d1c1b green (run 39534737507).
- [x] 13E — **Config screen for NeoForge + Forge** on vanilla widgets (`client/config/`, drawn from the existing
      `ConfigView` / drafts / `TabRows`; every option the YACL screen has, per DESIGN §1). Owner 2026-10-20: it
      mirrors the YACL screen (categories as tab rows, scrolling option list, per-option reset, Save / Cancel). Entry: NeoForge
      `IConfigScreenFactory`, Forge `ConfigScreenFactory`. JUnit on the model (an option missing from the screen fails),
      plus the owner's `runClient` check on both loaders.
      Owner 2026-10-21 (decisions.md "13E owner rulings"): **full mirror** of YACL's right pane (search, description,
      Undo / Cancel-Reset / Save-Done, "Save before exiting" on Esc); **Fabric falls back** to it when Mod Menu has no
      YACL; **one MC-free option model** for both screens. API check (javap, 4 versions): decisions.md "13E screen".
      - **E1 — model, YACL screen on it (no behaviour change).**
        - `src/main/.../config/form/`: `FormText` (key + args), `FormControl` (tick box, int field, int slider, cycle),
          `FormOption<T>` (key, description lines, control, default, get/set, editable; pending / changed / undo /
          reset / apply), `FormEntry` (label, option, group of one enchantment's options), `FormCategory`,
          `ConfigForm` (categories + save: writes only the files that changed, as today), `ConfigForms.build(Inputs)`
          (today's `ConfigScreen.build` body and its `Draft`; MC lookups come in as functions), `FormSearch`.
        - `client/ConfigScreen.java`: translates a `ConfigForm` to YACL; `build()` keeps its signature (the client
          GameTest reads it). `ConfigEditing.formInputs()` gathers the MC side.
        - JUnit `ConfigFormTest`: every `common.json5` leaf key has exactly one option, and changing that option
          changes exactly that key in the saved file; every enchantment × flag × number field (curse fields on curses
          only) maps onto `enchantments.json5`; display options onto `client.json5`; read-only views have no editable
          option and save nothing; save writes only changed files; undo / reset / apply. `FormSearchTest`.
          `ConfigLangKeysTest` walks the form (every option name + `.desc`, every cycle value's text).
        - Proof: verifyAll + `ConfigScreenClientGameTest` on 1.21.11 and 26.3 unchanged and green.
      - **E2 — our screen (`client/config/`), Fabric fallback.** `FormScreen` (tabs by `TabRows` via
        `FormTabButton`, `FormOptionList` left, pane right: search `EditBox`, wrapped description, three buttons),
        rows `OptionRow` (name, control, ↺ reset) / `GroupRow` (collapsible) / `LabelRow`; controls: tick box,
        `CycleButton` for enums, `EditBox` int field (out-of-range = red, not applied), slider. `ModMenuIntegration`:
        no YACL → `FormScreen`. Lang `enchanting2.config.gui.*`. Client GameTest `FormScreenClientGameTest` (1080p,
        scale 3): tabs in two rows, second-row click opens; toggle → pending, Undo restores; Esc with pending stays
        open; search "sharp" on Enchantments shows the Sharpness group; hosted save reaches the server, then restore.
        Screenshots for the owner. Proof: verifyAll + client GameTests on every Fabric node.
      - **E3 — NeoForge + Forge entries.** NeoForge `container.registerExtensionPoint(IConfigScreenFactory.class, …)`;
        Forge `ConfigScreenHandler.ConfigScreenFactory` (same record on Forge 62–66). README/wiki Requirements,
        CHANGELOG [Unreleased], `docs/CONFIG.md` if it names YACL. Proof: verifyAll; owner `runClient` on both.
      **E1 done 2026-10-21** (9ed8c91): verifyAll 457 JUnit + 340 GameTests ×10; YACL client GameTests 1.21.11 + 26.3
      unchanged. ConfigFormTest went red with one option removed.
      **E2 built 2026-10-21:** `client/config/` (FormScreen, FormOptionList, RowButton, FormTabButton, FormWidgets),
      `FormSearch` (+ JUnit), Mod Menu fallback, lang `enchanting2.config.gui.*`. `FormScreenClientGameTest` green on
      1.21.11 / 26.1.2 / 26.2 / 26.3 (red when Esc closed with a change pending). Screenshots matched YACL's except a
      search-result header drawn disabled: now a normal header, matches start open and can be closed.
      **E3 built 2026-10-21:** NeoForge `IConfigScreenFactory` in `NeoForgeClientEntrypoint(ModContainer)`; Forge
      `ConfigScreenFactory` in `ForgeClientEntrypoint.init(FMLJavaModLoadingContext)` (ForgeEntrypoint passes its
      context by reflection; `ModIdentityTest.forgeClientEntryPointTakesTheLoadingContext` guards the signature, red
      with the old `init()`). Docs: README, CHANGELOG, docs/CONFIG.md, DESIGN §1/§2, wiki FAQ/Home/Installation.
      **verifyAll red twice on E2/E3 (2026-10-21): not the code.** A different pair of GameTest JVMs died each run
      (26.1.2-neoforge + 26.2-forge, then 26.1.2-forge + matrix-26.1.1); the second run's log says "insufficient
      memory for the Java Runtime Environment" (G1 heap mmap) in both. Rerun alone, the first pair passed 340/340.
      `org.gradle.parallel` starts all 11 GameTest servers at once (commit limit 41 GB, 12 GB free at idle).
      Fix (Tier 1, build only): a root build service `gameTestServers` (`stonecutter.gradle`, `maxParallelUsages` =
      `gametest_max_parallel`, default 4) that every GameTest run task `usesService` (`gradle/gametest-report.gradle`).
      Proof: verifyAll green; the log shows no more than 4 GameTest servers overlapping.
      Blast radius: none (build scheduling only). Rollback: revert the two script edits.
      **Done 2026-10-21:** verifyAll green (463 JUnit + 340/340 GameTests on all 10 nodes and matrix 26.1 / 26.1.1;
      12 GameTest runs, at most 4 overlapping, no memory errors). `-Pgametest_max_parallel=0` refuses at configuration.
      **Dev-server smoke on NeoForge/Forge (rung 3, new template rule, 2026-10-21):** `NODE=26.3-forge|26.3-neoforge
      scripts/dev-server-smoke.sh` both exit 1, neither a mod fault: Forge stops at "You need to agree to the EULA"
      (only build.gradle had `acceptDevServerEula`), NeoForge reaches `Done (` but never reads `stop` (Loom forwards
      stdin to the game; MDG's `RunGameTask` and Forge's `JavaExec` don't). Fix: `acceptDevServerEula` moves to
      `gradle/common.gradle` for every loader (writes only when absent, as before); NeoForge/Forge `runServer` get
      `standardInput = System.in`. The server-generated `run/26.3-forge/server/eula.txt` (`eula=false`, disposable,
      git-ignored) is deleted once by hand so the task writes it. Proof: both smokes exit 0 with the init line.
      Blast radius: that one generated file. Rollback: revert the two script edits.
      **Done 2026-10-21:** `NODE=26.3-forge`, `26.3-neoforge`, `26.3` `scripts/dev-server-smoke.sh` all exit 0 ("OK":
      `Done (`, init line, no enchanting2 ERROR, clean stop); verifyAll green again on the final tree.
      **13E done 2026-10-21**: E2 26905e7, E3 894f632, build fixes e4838d4. Open: owner `runClient` checks on NeoForge
      and Forge (Owner checks below).
      **Not doing:** replacing YACL on Fabric when it's installed; YACL on NeoForge; client GameTests on
      NeoForge/Forge; any new option or config-format change; YACL's restart prompt (no option needs a restart).
      **Blast radius:** E1 rewrites how `ConfigScreen` builds YACL (edits; back from git at `ae17c11`); the save path
      (`ConfigEditing.save*` → `saveWithBackup`) is unchanged. The client GameTest writes the run dir's config and puts
      the old file back, as `ConfigScreenClientGameTest` already does. Nothing destructive. Rollback: revert the stage.
- [x] 13F-names **done 2026-10-20**: verifyAll green (443 JUnit, 340 GameTests everywhere); release-collect dry run
      on a scratch copy of the jars: 10 files / 5 bands, and it refuses a stale loader-less jar, a jar on another
      loader's node, and a name that disagrees with the shipped metadata; ModIdentityTest went red on both loaders
      with the loader removed from the version, green restored. Tables say "Fabric" for now: "/ Quilt" goes in with
      13F's Quilt runtime nodes (1.21.11 stays Fabric-only).
- 13F-names (moved up, 2026-10-20; decisions.md "13F naming"): **loader in the version and every jar name.**
      - `gradle/common.gradle`: `version = <mod_version>+<band>-<mod_loader>` (mod_loader read before the line).
      - `Bands` (test helper): `loader(node)` from the node name (`-neoforge` / `-forge` suffix, else `fabric`, the rule
        ci.yml already uses); `all()` → node → `<band>-<loader>`. `ModIdentityTest`: expected version gains the
        loader on both metadata tests, plus `Bands.loader(node)` = the build's `enchanting2.loader` (ties the rule to
        the build scripts).
      - `ReleaseDocsTest.installTablesListExactlyTheCurrentBands`: jar pattern `+<band>-<loader>.jar`; the set of
        `<band>-<loader>` in README and wiki/Installation.md = `Bands.all()` values.
      - README + wiki/Installation.md: the table with a column per loader (rows = Minecraft versions, "—" where none);
        Requirements rows per loader (NeoForge 26.x needs no Fabric API; config screen on NeoForge comes in 13E).
        On this branch the tables list what exists (NeoForge 26.3 only), widened as nodes land.
      - `release-collect.sh`: name → `<band>-<loader>`, check the shipped version from that loader's metadata
        (`fabric.mod.json` / `META-INF/neoforge.mods.toml`), table with a Loader column, notes' install text per
        loader. Dry-run it locally after `assemble` with a scratch `changes.md` (the script only writes
        release-notes.md / release-files.txt; both git-ignored? check first). `release.yml` comments.
      - CHANGELOG [Unreleased]: jar names now carry the loader.
      Proof: verifyAll green; release-collect dry run lists 5 jars with the right names; ModIdentityTest fails if
      the loader is dropped from the version (checked by reverting the common.gradle line once).
      Blast radius: names only, nothing released (13G). Rollback: revert the commit.
- [x] 13F — **Build, CI, release plumbing:** (done 2026-10-21; CI's first boot run checked after the push) `verifyAll` covers every node + Quilt nodes; `ci.yml` jobs per loader;
      jar names carry the loader (proposed `enchanting2-1.2.0+26.3-neoforge.jar`; Fabric names unchanged); `release-collect.sh`,
      `ModIdentityTest`, `ReleaseDocsTest`; README / wiki Installation tables per loader; CHANGELOG [Unreleased].
      **Plan (2026-10-21).** Fact that changed it (NeoForge 26.3.0.95 / Forge 26.3-66.0.13 sources): production servers
      never run GameTests (`GameTestHooks.isGametestEnabled()` and `ForgeGameTestHooks.isGametestServer()` both start
      with `!isProduction()`), and MDG/FG pin one loader version per node. **Owner 2026-10-21: NeoForge 26.1.0/26.1.1
      and Forge 62/63 claims are proven by a real-server boot check**, not GameTests (decisions.md "13F proof"). Quilt
      keeps full GameTests (13A1 ran them on a production Quilt server). The same harness is the template's release-gate
      rung 6 (built jar on a real server, every band × loader) that 13G needs.
      - F1. `scripts/server_boot_check.py`, from the owner's `the-continent/tools/server_smoke.py` (installs Fabric /
        Quilt / NeoForge / Forge servers from official metadata; Forge Maven needs a User-Agent). Args: `--loader`,
        `--minecraft`, `--loader-version`, `--mod JAR…`, `--work EMPTY_DIR` (refuses a non-empty one), `--commands FILE`
        (same directives as `dev-server-smoke.sh`: `@expect`, `@allow-error`, `#`), `--gametests JAR` (Fabric/Quilt on
        26.x only: adds `fabric-gametest-api-v1` from the fabric-api POM, `-Dfabric-api.gametest`, absolute report path,
        game dir ending `build/run/gameTest`; gotchas.md 13A1). `server-port=25567` written before the first start;
        refuses if 25567 is listening. Verdict from the log, never the exit code: `Done (`, init line, no
        `(/|\[)(ERROR|FATAL)\]` line outside `@allow-error`, every `@expect`, `stop` → exit 0. **Exit 0 proven · 1 mod
        bad · 2 nothing proven** (download / installer / port). `--self-test` feeds synthetic logs both ways, one with an
        ERROR line. Never kills a process (stop on stdin, timeout, then report exit 2 and the PID).
      - [x] F2. `scripts/checks/boot.txt`: the fingerprint (init line; the enchantment-load hook's default max-level lines;
        `reload` → "Reloaded … common.json5"). Checked against a revert: run once on a jar with the registry mixin left
        out (scratch build) and watch it go red.
        **Done 2026-10-21.** Rung 3: `NODE=26.3 scripts/dev-server-smoke.sh scripts/checks/boot.txt` → exit 0, matched
        "Applied enchantment Knockback V to Zombie's item", "6 is higher than the maximum level of 5 supported…",
        "Applied enchantment Vampirism I to Zombie's item", "Reloaded .\config\enchanting2\common.json5 (FILE, 0
        issue(s))". Mutant (cheaper than a scratch build, same effect on the hook's output): Knockback
        `max_level_override: 2` in the run dir's enchantments.json5 (backed up to build/, restored, `cmp` equal) →
        exit 1, both Knockback `@expect`s red ("5 is higher than the maximum level of 2"), Vampirism still green.
      - F3. `scripts/boot-matrix.txt` (loader, Minecraft, loader version, jar node; pins dated from official metadata)
        and `scripts/boot-all.sh` (sequential, one table, exit = worst). Rows: each shipped node on its own version and
        loader (Fabric ×4, NeoForge ×3, Forge ×3), Fabric on matrix 26.1 / 26.1.1, NeoForge 26.1.0 / 26.1.1 (betas) and
        Forge 62 / 63 on the 26.1.2 jars, Quilt (latest Loader) on 26.1.2 / 26.2 / 26.3 with `--gametests`.
      - F4. Claims: a green NeoForge/Forge 26.1.x row widens that jar's band (`common.gradle` reads the boot matrix's green
        rows next to `matrix/*`; `minecraft_dependency` must admit them, as `ModIdentityTest` already checks); red rows
        stay listed as unsupported in the plan, not in the tables. Quilt green → "Fabric / Quilt" for 26.x in README +
        wiki (1.21.11 stays Fabric-only). `ReleaseDocsTest` follows.
      - F5. `ci.yml`: `build` split per loader (verifyAll gets `-Ploaders=<list>`, refusing an empty or unknown list), a
        `boot` job running `boot-all.sh` on the built jars (uploads each `smoke.log`), client jobs stay Fabric.
      - F6. Docs: README / wiki / CHANGELOG [Unreleased], `state.md`, gotchas per loader surprise.
      **F1–F6 results (2026-10-21):**
      - F1 `scripts/server_boot_check.py`: `--self-test` 12/12 (synthetic logs both ways: ERROR, FATAL, allowed ERROR,
        no init, no Done, missing @expect, unclean stop; report pass/failure/empty/missing). Only `@sh sleep N` runs;
        any other `@sh` → exit 2. Installers get stdin DEVNULL; boot-all closes the check's stdin.
      - F2 needed waits after `forceload` / `summon` on a fresh real server (gotchas.md 2026-10-21).
      - F3 pins from official metadata 2026-10-21 (in the file's header). `BootMatrixTest` (6 tests: columns, node and
        loader per row, Fabric API = the node's pin, GameTests on 26.x Fabric/Quilt only, exactly one shipped row per
        node with its own pins, Fabric rows off the node's version need `matrix/<v>`, NeoForge/Forge ranges admit each
        row) → JUnit 463 → 469 per node. Mutation: Forge 26.3 pin 66.0.13 → 66.0.12 red at line 102, restored.
      - Rung 6, shipped (`bash scripts/boot-all.sh shipped`, run 20261021-143925): **10/10 PASS** (Fabric 1.21.11 /
        26.1.2 / 26.2 / 26.3, NeoForge 26.1.2.122 / 26.2.0.93 / 26.3.0.95, Forge 64.1.5 / 65.1.5 / 66.0.13), exit 0;
        each matched Knockback V, "6 is higher than the maximum level of 5", Vampirism I, "Reloaded … common.json5".
      - Full tier (`bash scripts/boot-all.sh full`, run 20261021-145233), exit 1: Fabric 26.1 / 26.1.1 PASS; **Forge
        62.0.9 (26.1) / 63.0.2 (26.1.1) PASS** on the widened `+26.1-26.1.2-forge` jar; **NeoForge 26.1.0.19-beta /
        26.1.1.15-beta FAIL** (NoSuchMethodError `LootModifier.codecStart`: P1 there, P2 with priority from 26.1.2;
        javap of both universal jars) → per the owner's rule (red leaves it at 26.1.2) the NeoForge rows are removed and
        its range reverted. Fix route if the owner wants 26.1/26.1.1 NeoForge later: `DropsLootModifier` implements
        `IGlobalLootModifier` directly via `LOOT_CONDITIONS_CODEC` (in both). **Quilt 0.31.0-beta.4 on 26.1.2 / 26.2 /
        26.3: boot PASS ×3, production server GameTests 340/340 ×3.** → README/wiki "Fabric / Quilt" (26.x), Forge
        26.1–26.3, NeoForge stays 26.1.2/26.2/26.3.
      - F4 mutation: NeoForge `minecraft_dependency` narrowed while rows claimed 26.1 → `ModIdentityTest
        neoforgeModsTomlNamesThisModAndItsBand` red, restored.
      - F5 `-Ploaders`: `bogus` and empty refused ("give a comma list"); `neoforge` dry run = the 3 NeoForge nodes'
        build + runGameTest, no matrix. ci.yml YAML parses (jobs build×3, boot×4, client-nodes, client, secrets).
        CI itself unproven until the push.
      - Final gate on the commit's tree: `scripts/verify-all.sh` exit 0 (469 JUnit + 340 GameTests on all 10 nodes,
        matrix 26.1 / 26.1.1 claimed 340/340, 5 link checks 0 problems); then `bash scripts/boot-all.sh shipped` on
        those jars (run 20261021-152208) **10/10 PASS**, exit 0. `save_log()` now also writes server.log on exit 2.
      **Not doing:** GameTests on production NeoForge/Forge; Quilt on 1.21.11; QSL; the client world check (rung 5,
      13G); widening without a green row.
      **Owner 2026-10-21 (third round, decisions.md "13F/13G owner rulings"):** CI `boot` runs the shipped rows (10)
      on every push; the full matrix (old loader versions, Quilt GameTests) on `workflow_dispatch` and before a tag.
      Quilt is expected to work (the owner's other 26.x mods run on it): a red Quilt row is a bug to fix, not a
      dropped claim. 13G: rung 5 on NeoForge/Forge is a scripted client boot (create + join a world, fingerprint).
      **Blast radius:** additive (scripts, a CI job, docs). Work dirs are new scratch dirs under `build/boot/` (refused
      unless empty). Nothing in the owner's saves or instances. Rollback: revert the commits.
- [ ] 13G — **Release 1.2.0.** Owner confirms first (outward-facing). Tag, ten jars, mod pages list the loaders.
      In the release commit: remove the "Until 1.2.0 is released" notes from README.md, wiki/Installation.md and
      wiki/Home.md (owner 2026-10-21: main is pushed, and with it the wiki, before 1.2.0 ships).

**Rollback:** `git revert` the stage's commit(s) on main (Phase 13 is per-stage commits, no squash). A bad release is fixed by a patch release, never by deleting tags.

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

- [ ] **13E config screen on NeoForge and Forge:** `./gradlew :26.3-neoforge:runClient` and `:26.3-forge:runClient`,
      Mods → Enchanting 2 → Config. It should match the YACL screen (Fabric `runClient`): tabs in two rows, each tab's
      options, a group opens, search "sharp", Esc with a change says "Save before exiting!", Save writes
      `config/enchanting2/common.json5` (+ `.bak`), Done goes back to the mod list. Also once on Fabric with YACL
      moved out of the dev run: Mod Menu's button opens our screen. Fabric's client GameTest already drives the screen
      itself (`FormScreenClientGameTest`, screenshots `form-screen-*`).
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
