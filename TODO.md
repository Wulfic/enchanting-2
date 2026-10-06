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

# Enchantment 2 — v1 roadmap (Tier 2)

Spec: [docs/DESIGN.md](docs/DESIGN.md). Open questions Q1–Q11 live there. Each phase is
one or more reviewable commits, and the next phase doesn't start until the current one
builds green with tests. Before a phase starts, expand its steps into concrete file-level
steps here.

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
- [ ] 1.1 Verify the toolchain for 1.21.11 (Java, Loom, Loader, Fabric API, Mod Menu, YACL,
      Jankson versions) with `research-docs` → write the pinned versions to `gradle.properties`.
      Mod ID `enchantment2`, group `com.wulfnet.enchantment2`.
- [ ] 1.2 `settings.gradle`, `build.gradle` (Loom, Mojang mappings, `splitEnvironmentSourceSets`),
      Gradle wrapper
- [ ] 1.3 `src/main/resources/fabric.mod.json`, `enchantment2.mixins.json`,
      `enchantment2.client.mixins.json`, mod icon from `Logo/`
- [ ] 1.4 Entrypoints: `Enchantment2` (main), `Enchantment2Client` (client); `.gitignore` adds
      `.gradle/ build/ run/ out/ *.iml`
- [ ] 1.5 Test harness: JUnit 5 (`./gradlew test`) and a Fabric GameTest source set
      (`./gradlew runGametest`), with one trivial test each
- [ ] 1.6 Verify: `runClient` and `runServer` both start with no client-class-on-server crash
- [ ] 1.7 GitHub Actions: build + test on push (`.github/workflows/build.yml`)
- [ ] 1.8 Dump the 1.21.11 enchantment registry (IDs, max levels, exclusive sets) → confirm
      DESIGN §8 against reality (Lunge, effect components named in §9); confirm Fabric Data
      Attachment API persists chunk attachments on 1.21.11 (needed by 8.5)

## Phase 2 — Config system
**Goal:** validated JSON5 config, synced server→client, editable from Mod Menu.
- [ ] 2.1 `config/` model: `CommonConfig`, `ClientConfig`, `EnchantmentOverrides`, `schema_version`
- [ ] 2.2 Loader + validator on Jankson (bundled jar-in-jar, no GUI dependency on the server):
      invalid value → default + WARN log (key, bad value, default)
- [ ] 2.3 Unit tests: missing file, malformed file, out-of-range value, unknown enchantment ID,
      future schema_version
- [ ] 2.4 `network/ConfigSyncPayload` S2C on join; client keeps the server copy separately
- [ ] 2.5 `client/` Mod Menu + YACL screen (both optional; guarded by `isModLoaded`), read-only
      when connected to a remote server, "requires /reload" hint on registry-affecting keys.
      Includes the `silk_fortune_mode` toggle with its duplication warning.
- [ ] 2.6 GameTest/integration: a client-side edit cannot change server behavior

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
- [ ] 8.1 Silk/Fortune behavior per Q5 (loot-table modification, not item hacks)
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
 