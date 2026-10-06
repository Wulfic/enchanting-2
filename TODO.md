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

### Stage 2A — Core (pure Java, no MC classes) → commit
- [ ] 2A.1 `build.gradle`: `include implementation "blue.endless:jankson:${jankson_version}"`
      (jar-in-jar). First confirm the Maven coordinates with research-docs.
- [ ] 2A.2 `config/ConfigIssue` (key, bad value, default, reason) and `config/ConfigReader`
      (typed reads on a Jankson `JsonObject`: `int(key, def, min, max)`, `bool`, `enumValue`). Collects
      issues and flags unknown keys (typo detection).
- [ ] 2A.3 `config/ExperienceSection` record (the §3 keys, defaults and ranges) and `config/CommonConfig`
      record (`schema_version`, `experience`). `CommonConfig.defaults()`.
- [ ] 2A.4 `config/EnchantmentOverrides`: map keyed by enchantment ID from `enchantments.json5`. Rejects
      malformed IDs. Unknown IDs are checked later through an injected `Predicate<String> isKnown`
      (the registry exists only at runtime). Entries have no fields yet; Phase 4/5/9 add them.
- [ ] 2A.5 `config/ConfigFiles`: `load(path)` → `LoadResult(config, issues, source = FILE|DEFAULTS_WRITTEN|
      DEFAULTS_UNPARSEABLE, futureSchema)`, `writeDefaultsIfAbsent`, `saveWithBackup`. JSON5 with comments.
- [ ] 2A.6 JUnit `ConfigFilesTest` (temp dir): missing → defaults written; malformed → defaults and the
      file is **byte-identical** afterwards; one out-of-range key → only that key defaults; wrong type;
      unknown key; future `schema_version` → best-effort load + save refused, file untouched; defaults
      round-trip with zero issues; `saveWithBackup` → `.bak` equals the old bytes; malformed and
      unknown enchantment IDs.

### Stage 2B — Server wiring → commit
- [ ] 2B.1 `mixin/MinecraftServerMixin` + `config/ServerConfigHolder` duck interface: config lives on the
      server instance (DESIGN §2a, no statics). Loaded at `SERVER_STARTING`, every issue logged as WARN.
- [ ] 2B.2 `/reload`: `END_DATA_PACK_RELOAD` (success only) re-reads the files. Unparseable → keep the
      previous config + WARN. Then re-sync to every player.
- [ ] 2B.3 `network/ConfigSyncPayload` S2C (strict-JSON text of `CommonConfig`, length-capped on decode),
      sent on join and after `/reload`.
- [ ] 2B.4 Vanilla client → disconnect at join with a message naming the mod (`canSend` false; DESIGN §2a).
      First check with research-docs whether the configuration phase is the right hook.
- [ ] 2B.5 GameTests: the holder is non-null and matches the file. The payload codec round-trips.
      `/reload` with a bad value → that key defaults and nothing else changes.
- [ ] 2B.6 Dev server (`scripts/dev-server-smoke.sh`): first boot writes defaults. A bad value + `/reload`
      → WARN names the key. Unparseable + `/reload` → previous config kept.

### Stage 2C — Client → commit
- [ ] 2C.1 `build.gradle`: Mod Menu + YACL as `modCompileOnly` + `modLocalRuntime` (mcMMO-Singleplayer
      pattern). `fabric.mod.json` `suggests`, `modmenu` entrypoint. Confirm the repo URLs with research-docs.
- [ ] 2C.2 `client/ClientConfigState`: the server copy (cleared on disconnect), kept apart from the local
      file. Pure resolver `effective(connection, local, serverCopy)`: remote → server copy (or vanilla if the
      server never sent one), SP/LAN host → own file.
- [ ] 2C.3 `client/ModMenuIntegration` → `client/ConfigScreen` (YACL). Only class-loaded when
      `isModLoaded("yet_another_config_lib_v3")`. Without YACL: Mod Menu shows no screen, no crash.
      Remote → every option read-only, with a "server-controlled" banner.
      SP/LAN host → `saveWithBackup`, then apply on the server thread (`server.execute`), then re-sync.
- [ ] 2C.4 JUnit: the resolver never returns the local file on a remote connection. Covers the old 2.6
      ("a client-side edit cannot change server behavior") together with the absence of any C2S config path.
- [ ] 2C.5 Owner check in `runClient` (folded into the 1.6 check): screen opens, SP edit persists and makes
      a `.bak`, LAN guest sees read-only.

## Phase 3 — Experience curve
**Goal:** DESIGN §3 acceptance list passes.
- [ ] 3.1 `experience/ExperienceCurve` (pure, `long` math) + exhaustive unit tests (boundaries
      0/15/16/30/31, 10k, overflow clamp, round-trip lossless)
- [ ] 3.2 Mixin `Player#getXpNeededForNextLevel` → service
- [ ] 3.3 Audit every other level/points call site (commands, orbs, death, `totalExperience`)
      and route through the service; list findings in this file
- [ ] 3.4 `maximum_level` cap
- [ ] 3.5 GameTests: `/xp` above 30, spend across the 30 boundary, death ± keepInventory,
      save/reload at a high level

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
 