# Decisions

> **Append-only.** Newest entries at the bottom. Never edit or delete a past decision —
> if it gets reversed, append a new entry that says so and links back to it.
>
> Record a decision when a real alternative existed. "Used the standard library function"
> is not a decision. "Chose Postgres over SQLite because we need concurrent writers" is.
>
> Write the **reasoning**, not the diff. Git already stores what changed; it cannot store
> why, or what you rejected.

## Entry template

```markdown
## YYYY-MM-DD — Short title
**Context:** what forced the decision
**Choice:** what we did
**Why:** the actual reason
**Rejected:** what we didn't do, and why not
**Affects:** src/path/to/file.ts
```

---

## 2026-08-12 — Agent memory is repo-local files, not an MCP server
**Context:** Instructions mandated persisting decisions to the `mem0` MCP server, but
`mem0` was never present in the active `.mcp.json`, so nothing was actually being recorded.
**Choice:** Memory lives in `.agent/memory/` as committed Markdown: `decisions.md`,
`gotchas.md`, `state.md`.
**Why:** Works identically in Copilot and Claude Code with no network call, survives a
dead server, and shows up in code review where it can be corrected. A memory write that
silently no-ops is worse than none, because it feels like it worked.
**Rejected:** `mem0` MCP — cross-project recall wasn't worth an invisible failure mode on
the one guarantee that matters most. Also rejected keeping both as a mirror: two sources
of truth means neither is trusted.
**Affects:** `AGENTS.md`, `.agent/memory/`, `.github/skills/recall-session`, `.github/skills/save-memory`

## 2026-08-12 — Dropped the `think` MCP server
**Context:** Every skill routed reasoning through `mcp_wulfnet-think_think_invoke_tool`
before acting.
**Choice:** Removed the server and all call sites. Reasoning happens natively; the
`think-plan` skill was replaced by `plan-work`, which produces a written plan in `TODO.md`.
**Why:** Opus 5 reasons natively. The round-trip added latency and a failure mode without
improving output. A plan written to a file is also a checkable artifact — an MCP call is
not, which matters under Copilot where step-skipping can't be enforced.
**Rejected:** Keeping it for the `criticize` operation — self-review against an explicit
checklist in `code-review` covers the same ground without the dependency.
**Affects:** `AGENTS.md`, `.mcp.json`, all skills that previously called `think`

## 2026-08-12 — MCP surface cut to `github` + `context7` only
**Context:** Nine MCP servers were configured. `filesystem` and `git` had zero references
in any skill — dead config. The rest duplicated capability that is now native to both
Copilot and Claude Code.
**Choice:** Dropped `filesystem`, `git`, `gitnexus`, `playwright`, and `context-mode`.
Kept `github` (with a `gh` CLI fallback) and `context7`.
**Why:** The bar is now "does this do something native tools cannot?" Only two clear it.
`context7` serves live docs that training data can't; `github` returns structured JSON for
issues/PRs/CI. Everything else was a network round-trip and a failure mode in exchange for
what grep, the terminal, and native web fetch already do.
**Rejected:** Keeping `gitnexus` for cross-repo symbol lookup — real capability, but it
doesn't pay for itself on single-repo work, which is the norm here. Keeping `playwright`
for E2E — see the separate entry below; that turned out to be a category error on our part.
**Affects:** `.mcp.json`, `.claude/mcp.vscode-reference.json`, `AGENTS.md`,
`.github/copilot-instructions.md`, `code-explore`, `research-docs`

## 2026-08-12 — E2E runs through the project's own runner, not an MCP browser
**Context:** Dropping the `playwright` MCP server looked like it would remove E2E testing
capability, since `test-iterate` routed E2E through the old `web-task` skill.
**Choice:** E2E tests are committed spec files executed from the terminal
(`npx playwright test`, `npx cypress run`). The `web-task` skill was deleted.
**Why:** The playwright *MCP server* and Playwright *the test library* are different
things, and conflating them was the original mistake. Ad-hoc agent browser-driving isn't
a test — it can't run in CI and it isn't repeatable. Committed specs are strictly better,
and they need a terminal, not an MCP.
**Rejected:** Keeping the MCP for exploratory UI checks — real but rare, and native web
fetch plus the runner's own `--headed` mode and trace viewer cover the debugging case.
**Affects:** `test-iterate`, `AGENTS.md`, deleted `web-task`

## 2026-08-12 — Merged `git-ops` and `github-workflow` into `git-github`
**Context:** Two skills split one linear workflow: branch → commit → push → PR → CI.
**Choice:** Single `git-github` skill covering local git and GitHub remote operations.
**Why:** The split forced loading two skills for one continuous task and duplicated the
pre-commit checklist across both. The seam was in the docs, not in the work.
**Rejected:** Keeping them separate as "local vs remote concerns" — a clean distinction on
paper that nothing in the actual workflow respects.
**Affects:** `.claude/skills/git-github/`, `.github/skills/git-github/`, `AGENTS.md`,
`.github/copilot-instructions.md`

## 2026-08-12 — Destructive-action doctrine: prose first, permission rules as backup
**Context:** The template had no unified stance on irreversible operations — only scattered
rules in `git-github` (force-push, `.env`). Nothing covered filesystem deletion, database
drops, container/volume pruning, or the guards that code *we write* should carry. Needed a
gate that survives Copilot (no permission layer, no hooks) and a truncated context.
**Choice:** Two layers. (1) Doctrine — a `Destructive Actions` section in `AGENTS.md` as
rule zero, the `guard-destructive` skill holding the full procedure, and per-skill guards
in `code-review`, `git-github`, `build-run`, `plan-work`, `test-iterate`. (2) Mechanical —
`permissions.deny` (unrecoverable) and `permissions.ask` (destructive-shaped) in a new
`.claude/settings.json`. The doctrine is primary; the rules are a seatbelt.
**Why:** Permission rules match on **command prefix**, so `cd sub && rm -rf ../..` walks
straight past them, `PowerShell` coverage is unverified, and Copilot has no permission
layer at all. Anything that only exists as config would therefore cover roughly half the
actual usage. Conversely, prose alone loses to a truncated context. Each layer covers the
other's blind spot, and stating the rules' limits in writing is what stops them being
trusted past their range.
**Rejected:** A `PreToolUse` command-hook script — it would need to be cross-platform and
correct, and a hook that silently no-ops is worse than none (same reasoning that dropped
`mem0`). A `type: "prompt"` hook classifying every shell command semantically *would* catch
the compound-command cases, but costs a model call on every `ls`; documented in
`guard-destructive` as an opt-in escalation instead of a default. Also rejected
`disableBypassPermissionsMode` — locking the owner out of their own tool without asking.
**Affects:** `AGENTS.md`, `.github/copilot-instructions.md`, `.claude/settings.json`,
`.claude/skills/guard-destructive/`, `.github/skills/guard-destructive/`, `code-review`,
`git-github`, `build-run`, `plan-work`, `test-iterate`, `TODO.md`

## 2026-10-01 — No AI co-author attribution on commits or PRs
**Context:** Claude Code appends `Co-Authored-By: Claude …` to commits and a "Generated with
Claude Code" line to PR bodies by default. Owner wants commits signed by the git user only.
**Choice:** `.claude/settings.json` sets `attribution: { commit: "", pr: "" }` (current key)
plus `includeCoAuthoredBy: false` (deprecated key, kept for older Claude Code versions). The
`git-github` skill (both trees) states the rule in prose for Copilot and any agent that
ignores settings.json.
**Why:** The setting is what actually stops the harness injecting the trailer; prose alone
loses to the harness's own attribution instruction. Prose covers Copilot, which never reads
settings.json.
**Rejected:** A `commit-msg` git hook stripping the trailer — works for every tool, but it's
an extra installed artifact per clone and silently rewrites messages; revisit only if a
trailer still slips through.
**Affects:** `.claude/settings.json`, `.claude/skills/git-github/SKILL.md`,
`.github/skills/git-github/SKILL.md`

## 2026-10-06 — Enchantment 2: platform, mappings, config stack
**Context:** Starting a Fabric 1.21.11 mod (Enchantment 2). Needed mappings, a config format,
and a Mod Menu screen without making dedicated servers carry GUI libraries.
**Choice:** Mojang official mappings. Mod ID `enchantment2`, package
`com.wulfnet.enchantment2`, private repo, 1.21.11 only for v1. Config is JSON5 via Jankson
(bundled jar-in-jar); the screen is Mod Menu + YACL, both optional client dependencies.
**Why:** 1.21.11 is the last Yarn release and 26.x is unobfuscated Mojang names, so Mojang
mappings turn the planned 26.x port into a version bump instead of a rename. Keeping config
I/O off YACL means the server needs only Fabric API, and a missing GUI library degrades to
file-only config instead of a crash.
**Rejected:** Yarn (dead end after 1.21.11). YACL as the config serializer (would make it a
hard server dependency). Cloth Config (works with Mod Menu too; YACL is the more actively
maintained of the two on recent versions). Targeting 26.x now (owner: finish and test on
1.21.11 first).
**Affects:** `build.gradle`, `gradle.properties`, `src/main/java/.../config/`, `src/client/`

## 2026-10-06 — Enchantment 2: vanilla enchantments modified via EnchantmentEvents.MODIFY
**Context:** Need higher max levels and relaxed exclusive sets on vanilla enchantments.
In 1.21.x enchantments are data-driven registry entries.
**Choice:** Fabric API `EnchantmentEvents.MODIFY` edits the builder at registry load; we ship
no override JSON for vanilla IDs.
**Why:** Override JSONs replace the whole definition and silently clobber other mods' and
datapacks' changes. MODIFY layers on top. The registry syncs to clients, so the new limits
display correctly without a custom packet.
**Rejected:** Shipping replacement `data/minecraft/enchantment/*.json` (clobbers others).
Mixins into enchantment logic (data already covers it).
**Affects:** `enchantment/ConflictFamilies`, DESIGN.md §2, §4, §8

## 2026-10-06 — Enchantment 2: cost model and table design
**Context:** The rough plan's cost section contradicted itself (1 base level vs. 2 per level
vs. an Efficiency base of 5). The vanilla table is random and opaque.
**Choice:** Default 0 base + 1 XP level per enchantment level. Mending = base 2 + 2 × other
non-curse levels, defined as `2 × O_out` when newly added and `2 × (O_out − O_in)` when
already present, so cost is independent of build order. Lapis = min(enchant cost, 64), at
both table and anvil. Table is a deterministic picker listing every applicable enchantment,
Mending included. Vanilla bookshelf rules are kept: power tier = 1 + floor(shelves×4/15), and
15 shelves unlocks max levels. Silk Touch beats Fortune by default, with a config toggle.
**Why:** The owner wants Mending to be "properly expensive". Order independence stops players
gaming it by applying Mending first. Keeping vanilla shelf rules means existing builds work
unchanged. Fortune multiplying silk-touched ore is an infinite duplication loop, so it's
opt-in only.
**Rejected:** Mending as a FIXED 2 (from the draft; contradicts the owner's worked examples).
A surcharge only on levels added in the same operation (order-dependent and exploitable).
Keeping the random 3-offer table (owner wants readable, chosen enchantments).
**Affects:** `enchanting/CostModel`, enchanting table menu/screen, anvil mixin, DESIGN.md §5–6

## 2026-10-06 — Enchantment 2: max levels raised to 5 with exceptions
**Context:** Owner asked for "max 5 where applicable" and deferred edge cases to the agent.
**Choice:** All multi-level vanilla enchantments go to 5, except Depth Strider (stays 3)
and Quick Charge (4). Lure and Riptide go to 5 conditionally on a GameTest.
**Why:** Depth Strider's water-movement attribute is already at full effect at level 3, so
levels 4–5 would cost XP for nothing. Quick Charge 5 means zero charge time, which has
historically broken crossbows. Lure and Riptide are plausible but unverified on 1.21.11.
**Rejected:** A blanket 5 (ships no-op and broken levels).
**Affects:** DESIGN.md §8, Phase 4.4 tests

## 2026-10-06 — Enchantment 2: lapis only where vanilla uses it (amends the cost-model entry)
**Context:** The cost-model entry above said lapis is charged at both the table and the anvil.
The anvil has no lapis slot, so that needed a new UI or a hidden inventory pull.
**Choice:** Lapis is charged only at the enchanting table (1 per XP level of enchant cost,
capped at 64). The anvil never costs lapis.
**Why:** Owner: "only require lapis where vanilla required it." No new UI, and no surprise
inventory drain.
**Rejected:** Pulling lapis from the player's inventory at the anvil (invisible cost).
Adding a third anvil slot (larger UI change for no gameplay gain).
**Affects:** `enchanting/CostModel`, anvil mixin, DESIGN.md §5

## 2026-10-06 — Enchantment 2: Fortune multiplies natural blocks, gated by a placed-block tracker
**Context:** The owner wants Fortune to work on axes, shovels and hoes (option B: multiply the
block's own drop). On its own that's the infinite place-and-mine loop vanilla avoids (see
gotchas.md).
**Choice:** Fortune multiplies blocks in `fortune_multiplies/<tool>` tags, but only natural
ones. A placed-block tracker, ported from mcMMO-Singleplayer's `PlacedBlockTracker` +
`BlockRules`, marks hand-placed, piston-moved, falling-block-landed, generator-formed and
golem-formed blocks as ineligible. Stored per chunk via the Fabric Data Attachment API.
Fails closed. Ancient debris is hard-excluded.
**Why:** The owner's mcMMO port already proved the pattern, including the three-pass piston
move. Per-chunk storage scales to multiplayer servers. Failing closed is required because
the failure here is item duplication, not a free XP payout.
**Rejected:** Option A, byproduct-only bonus drops (owner preferred B). mcMMO's single
world-wide file (fine for singleplayer, but unbounded memory and rewrites on a busy server).
mcMMO's fail-open behavior (a duplication bug, not a harmless XP payout). Ignoring falling
blocks (mcMMO doesn't need it, but sand and gravel are in our shovel tag, so ignoring them
launders placed blocks).
**Affects:** `compat/PlacedBlockTracker`, Fortune loot hook, DESIGN.md §6, TODO Phase 8

## 2026-10-06 — Enchantment 2: curses are a discount, removable at a repay cost
**Context:** A deterministic table means nobody would choose a curse, so curses would only be
a loot punishment.
**Choice:** Each curse on the result item discounts `enchant_cost` by its `discount_percent`
(cap 45%, rounds up). Curses are offered at the table as optional picks with a negative cost
line. The grindstone Cleanse mode removes one for max(30, the discount it earned on the
item's current enchantments). New curses: Fragility, Weight, Greed, Fumbling, Hunger.
**Why:** It turns curses into a real trade-off that fits the transparent cost model. The
repay-the-discount removal cost stops "curse → enchant cheap → cleanse" being a free
discount on expensive Mending builds.
**Rejected:** A flat removal cost only (exploitable on large builds). Curses on loot in
exchange for over-cap levels (max levels are the design's hard ceiling).
**Affects:** `enchanting/CostModel`, grindstone mixin, table screen, DESIGN.md §5a, §6

## 2026-10-06 — Enchantment 2: commit straight to main, push, no CI for now
**Context:** Phase 1 setup. Needed a git flow and a call on GitHub Actions (TODO 1.7).
**Choice:** Commits go directly on `main` and get pushed to `origin` (Wulfic/enchanting-2).
No CI workflow yet. Author in `fabric.mod.json` is "Wulfic".
**Why:** Owner's call. It's a solo private repo, so a branch/PR review point isn't worth the
overhead yet.
**Rejected:** Branch per phase, branch + PR per phase, CI now. All are revisitable. With no CI,
"never commit red" rests entirely on running `./gradlew build test` locally before every commit.
**Affects:** TODO.md Phase 1.7, `src/main/resources/fabric.mod.json`

## 2026-10-06 — Renamed Enchantment 2 → Enchanting 2 (mod ID `enchanting2`)
**Context:** The owner's logo (`Logo/enchanting2.png`) and the repo name say "Enchanting 2". The spec,
mod ID and package said "Enchantment 2" / `enchantment2`.
**Choice:** Full rename before any registry IDs or config files exist: display name "Enchanting 2",
mod ID `enchanting2`, package `com.wulfnet.enchanting2`, config dir `config/enchanting2/`. The mod
icon comes from `Logo/enchanting2.png`. Earlier entries in this file keep the old name. This entry
supersedes them on naming only.
**Why:** The mod ID ends up in registry IDs (`enchanting2:vampirism`), config paths and world saves,
so renaming after the first release breaks existing worlds. Today it costs nothing.
**Rejected:** Changing the display name only (permanent mismatch between ID and name). Keeping the
old name (doesn't match the owner's branding).
**Affects:** all of src/, build.gradle, settings.gradle, gradle.properties, DESIGN.md, TODO.md

## 2026-10-06 — Phase 1.8: DESIGN §8 matches the 1.21.11 registry
**Context:** §8 was written from memory of vanilla. Phase 4's `EnchantmentEvents.MODIFY` keys depend on it.
**Choice:** Checked against `data/minecraft/enchantment/*.json` in the 1.21.11 jar. 43 enchantments. Every vanilla
max level in the §8 table is correct. `minecraft:lunge` exists (max 3, effect `post_piercing_attack`:
impulse 0.458/level, exhaustion 4/level). Exclusive sets are armor, boots, bow (Infinity+Mending), crossbow, damage
(includes Density and Breach), mining (Fortune+Silk), riptide (Loyalty+Channeling). Fortune has **no effect
components**: its bonus lives in loot tables (`apply_bonus`), which matters for Phase 8.
Pinned by `VanillaEnchantmentIdsGameTest`. Phase 4 flips its expected levels to the v1 column.
Fabric Data Attachment API v1.8.48 (in FAPI 0.141.6) persists chunk attachments (`SerializableChunkDataMixin`),
so Phase 8.5 can use it. Proto-chunks that are still generating aren't saved, which doesn't matter for placed blocks.
**Why:** a wrong key in MODIFY fails silently (the callback just never matches).
**Rejected:** a manual one-off registry dump. Nothing would notice drift on the 26.x port.
**Affects:** docs/DESIGN.md §8, src/gametest/.../VanillaEnchantmentIdsGameTest.java
**Balance flag for Phase 4:** Lunge V = 20 exhaustion per lunge (5 hunger points). Consider capping Lunge at 3 or 4.

## 2026-10-06 — Dev server on port 25566, separate run dirs
**Context:** Owner asked for a dev server to test the mod properly. They often run another mod's server on 25565.
**Choice:** `loom.runs.server` → `run/server` with `--port 25566`. Client → `run/client`. `acceptDevServerEula` writes
eula.txt only if it's absent. `scripts/dev-server-smoke.sh [commands-file]` boots the server, types console commands
through stdin, sends `stop`, and checks the log. It shuts down via `stop` on stdin and never kills a process.
**Why:** sharing a port or run dir with another instance is the failure that memory already warns about.
**Rejected:** RCON (more config for nothing, since stdin works). A shared `run/` (client saves and server world mix).
**Affects:** build.gradle, scripts/dev-server-smoke.sh

## 2026-10-06 — Add/remove mid-playthrough and SP/MP parity are requirements
**Context:** Owner: "it's important that this mod can be safely added or removed mid playthrough, and that it works
on multiplayer and singleplayer worlds."
**Choice:** DESIGN §2a. Adding keeps XP levels as stored. Blocks placed before install count as natural for Fortune
(owner chose "bonus, one-time exploit": each one can pay out once, then it's marked). No new blocks or items.
Per-server state only (no statics). Modded client on a vanilla server = vanilla. Vanilla client → clean kick.
**Why:** owner requirement.
**Rejected (Fortune on old chunks):** "no bonus in pre-install chunks". It permanently kills Fortune around spawn
and the base. A config toggle was more code for a case the owner doesn't care about.
**Affects:** docs/DESIGN.md §2a, all phases

## 2026-10-06 — Phase 2 config: who edits, when it reloads, how files are written
**Context:** Phase 2 planning. Needed to decide in-game editing rights, how a running server picks up edits, and how
code that writes config files avoids destroying an admin's hand edits.
**Choice:** (owner) Only the SP/LAN host edits in-game. On a dedicated server the screen is read-only for everyone,
ops included, and there is no C2S write packet. (owner) A running server re-reads the config on vanilla `/reload`
(`END_DATA_PACK_RELOAD`, success only). (owner) Losing `enchanting2:` enchantments on uninstall is accepted and
documented, with no restore mirror. (agent) The loader writes a file only when it is absent and never rewrites an
existing one, including invalid or future-schema files. The GUI save backs up to `.bak`, verifies the copy, then
writes temp + atomic move. An unparseable file on `/reload` keeps the previous config. Each phase adds its own keys.
Config lives on the `MinecraftServer` through a duck-interface mixin. Sync sends strict-JSON text.
**Why:** No remote write path means nothing to secure. `/reload` is the one command admins already know. A loader
that "fixes" files would erase comments and the admin's intent on every typo. Falling back to defaults mid-game on a
typo would silently reset a live server. Keys with no behavior behind them would ship dead toggles in the GUI.
**Rejected:** op editing via C2S (more surface and tests for a convenience). A custom `/enchanting2 reload` command
(a second command to learn). A file watcher. A restore mirror in `custom_data` (every enchant/extract path must
keep it in sync, which is a dupe risk). A static config holder (breaks integrated-server world switching, §2a).
**Affects:** src/main/java/com/wulfnet/enchanting2/config/, mixin/MinecraftServerMixin, network/ConfigSyncPayload,
src/client/.../client/, DESIGN §2 Config

## 2026-10-06 — Experience config: drop the two undefined keys
**Context:** Stage 2A was about to put DESIGN §3's `experience` block into the schema. `post_threshold_mode` had one
value ("FIXED"), and `preserve_vanilla_curve_below_threshold: false` had no defined meaning.
**Choice:** (owner) Drop both. The section is `enabled`, `flatten_after_level`, `fixed_points_per_level`, `maximum_level`.
**Why:** A one-value enum is a dead GUI toggle. The only sensible reading of the second key is `flatten_after_level: 0`.
A missing key reads as its default, so adding a mode key later doesn't break existing files.
**Rejected:** Keeping `post_threshold_mode` with FIXED only. Inventing a second curve mode now.
**Affects:** docs/DESIGN.md §3, src/main/java/com/wulfnet/enchanting2/config/ExperienceSection.java

## 2026-10-06 — Network protocol number in every payload ID; mismatch disconnects at join
**Context:** Stage 2B adds the first payload and the vanilla-client disconnect. We had to decide what happens when the
client's Enchanting 2 version differs from the server's.
**Choice:** (owner) A protocol check. (agent) `NetworkProtocol.VERSION` is part of every payload ID
(`enchanting2:v1/config_sync`). In the configuration phase the server checks `canSend` for its own config-sync type.
If that fails, the client's advertised channels decide the message: none in `enchanting2:` means a vanilla client,
`enchanting2:v<M>/…` means protocol M versus N. Bump `VERSION` only when a payload's wire format changes, not on every release.
**Why:** Versioned IDs mean mismatched packets can never be decoded against the wrong codec, so there's no garbage
decode or generic network-error kick. Detection needs no extra handshake packet.
**Rejected:** exact mod-version match (every patch forces everyone to update at once). No check (Phase 6b/7 C2S
packets would fail to decode with a generic message). A separate hello packet (one more payload for the same information).
**Affects:** src/main/java/com/wulfnet/enchanting2/network/

## 2026-10-06 — Config screen (2C): what it shows, who saves, how YACL is wired
**Context:** Stage 2C. Needed the screen's behavior for title screen / SP-LAN host / remote, and a dependency setup that
doesn't drag YACL's Kotlin and nested libraries into the build.
**Choice:** (owner) The title screen edits the local file. All SP worlds share one `common.json5`. (agent) A pure
`config/ConfigView.resolve` decides the mode: NONE → file, editable. HOSTING → the live server values, editable, and a
save writes the file and then runs `ServerConfigs.reload` on the server thread (the 2B path, so LAN guests re-sync).
REMOTE → the server copy read-only, or a "vanilla rules" note, and the local file is never read. A file that doesn't
parse or has a newer schema locks the screen. `ConfigEditing.save` re-resolves the view and refuses unless it is still
editable. Mod Menu + YACL are `modCompileOnly` + `modLocalRuntime` with `transitive = false`.
**Why:** The live values are what is in effect, so they are what the host should edit. Reusing `reload` means one apply
path, already GameTested. YACL nests its libraries in `META-INF/jars`, and only its `dsl` and `config.v3` packages
reference Kotlin (checked by grepping the class files), so the Java builder API needs nothing transitive.
**Rejected:** Per-world config (owner: v1 keeps one file). Showing the file's values while hosting (it would show
values that aren't in effect after a failed reload). Transitive YACL deps (they pull kotlin-stdlib and the quilt parsers
from repos we don't otherwise need).
**Trade-off accepted:** a host save replaces a hand edit that was never `/reload`ed. It survives in `.bak`, and DESIGN §2 says so.
YACL is environment `*`, so `modLocalRuntime` also loads it on the dev server and GameTest server. Both still pass.
**Affects:** src/main/java/com/wulfnet/enchanting2/config/ConfigView.java, src/client/.../client/{ConfigEditing,
ConfigScreen,ModMenuIntegration}.java, build.gradle, fabric.mod.json, docs/DESIGN.md §2

## 2026-10-06 — No XP level cap; drop `maximum_level`
**Context:** Phase 3 planning. 2A had shipped `maximum_level` (-1 = unlimited, ≥ 0 = admin cap). The cap raised two
questions with no good answer: what happens to XP gained at the cap, and whether lowering it takes levels away.
**Choice:** (owner) No admin cap. The only limit is `Integer.MAX_VALUE`. The key is removed from `ExperienceSection`, the
screen and the lang file. `schema_version` stays 1: nothing was released, so no migration.
**Why:** The owner doesn't want a cap. Vanilla `giveExperienceLevels` already uses `IntMath.saturatedAdd`, so levels stop at
MAX without wrapping. Only points-to-next needs a clamp (vanilla's formula overflows near L ≈ 238.6M).
**Rejected:** Keeping the key at -1 (dead config surface). Clamping players down when a cap is lowered (destroys levels).
**Trade-off accepted:** a dev-dir `common.json5` that still has `maximum_level` logs an unknown-key WARN. It is never
rewritten (2A guard); delete it and boot to regenerate.
**Affects:** src/main/java/com/wulfnet/enchanting2/config/ExperienceSection.java, src/client/.../client/ConfigScreen.java,
lang/en_us.json, docs/DESIGN.md §3

## 2026-10-06 — Exact XP points: TAIL snap on `giveExperiencePoints`, stored as the smallest float ≥ `p/n`
**Context:** 3B GameTests showed vanilla's float progress loses a point. 29 + 157 → 30 + 49, and some add/remove round
trips come back one short. DESIGN §3 makes "add-then-remove is lossless" an acceptance criterion.
**Choice:** (owner) TAIL snap. HEAD records the exact total. The vanilla body runs unchanged. TAIL resets level and
progress to `ExperienceCurve.fromTotal(before + amount)` and stores progress as the smallest float `f` with `f × n ≥ p`. That form floors to `p`
under vanilla's `floor(progress × n)` for any n ≤ 2^21, overshoots by ≤ 0.125 points, and is exactly 0 at 0 points. Our reader, `floor(progress × n + 0.25)`, accepts both it and
vanilla's `p/n`, so existing saves read correctly.
**Why:** Exact, and other mods' injects into `giveExperiencePoints` (XP multipliers, Mending redirects) still run.
**Rejected:** HEAD cancel replacing the body: simpler, but it skips other mods' later injects without any error.
Waiving the criterion: it leaves vanilla's ±1 drift and needs a DESIGN change.
Storing `(p + 0.5)/n`: also exact, but the bar draws half a point ahead, about 13 px of fill at level 0 with 0 XP.
**Trade-off accepted:** two injects instead of one. If the snap moves the level, the level-up sound plays from our
`giveExperienceLevels(Δ)` call. No snap when n > 2^21 (vanilla curve past ~233k only).
**Affects:** src/main/java/com/wulfnet/enchanting2/experience/ExperienceCurve.java, mixin/PlayerMixin.java,
mixin/ServerPlayerMixin.java

## 2026-10-06 — Score counters saturate instead of wrapping (Phase 3C)
**Context:** vanilla `totalExperience` resets to 0 at MAX + n (an `int` add before the clamp). The death-screen score
(`increaseScore`) is a separate plain `int` add, and it wraps negative.
**Choice:** owner chose a saturating add for both. `@ModifyArg` on the `Mth.clamp` input in `giveExperiencePoints`
(MixinExtras `@Local(argsOnly)` for `amount`), plus a cancellable HEAD on `increaseScore` that only acts when the sum leaves the `int` range.
**Why:** a counter that resets after billions of points is a bug, not vanilla flavor. Both are score-only; the curve is untouched.
**Rejected:** leaving vanilla (owner declined). A HEAD/TAIL record pair for the total, which a nested call would clear,
the same weakness the 3B.7 snap has. `@ModifyArg` needs no state.
**Affects:** src/main/java/com/wulfnet/enchanting2/mixin/PlayerMixin.java

## 2026-10-06 — A lowered max level never touches existing items (Phase 4)
**Context:** `max_level_override` can lower a max below levels already on items (e.g. Fortune V → max 3).
**Choice:** keep the item and its effect as-is (vanilla behavior). The max only limits new enchanting and combining in `EnchantRules`.
**Why:** nothing on an item is ever silently changed, and raising the max back needs no recovery.
**Rejected:** clamping the effect at use time (a mixin on every effect lookup); rewriting the stored level (destructive, irreversible).
**Affects:** Phase 4 `EnchantRules`, Phase 6 anvil, Phase 7 table

## 2026-10-06 — Conflict families live in `EnchantRules`, not the registry (Phase 4)
**Context:** DESIGN §2 said to relax `exclusive_set` through `EnchantmentEvents.MODIFY`. But `MODIFY` runs before tags are
bound, so it can only swap a whole tag-backed set (e.g. empty `#exclusive_set/mining`), never filter pairs. It would
need a restart, and it would also drop conflicts that other mods add to the same vanilla tags.
**Choice (owner):** registry exclusive sets stay vanilla. `EnchantRules` treats two enchantments as compatible when vanilla
says so **or** both are in the same enabled `ConflictFamily`. The toggles go in `common.json5` (`conflicts`), synced and
`/reload`-able.
**Why:** our anvil, table and books (Phases 6–7) all go through `EnchantRules`, so the registry edit buys nothing there.
It works per pair, other mods' conflicts survive, and removing the mod leaves nothing behind.
**Rejected:** registry `MODIFY` (tag-only granularity, needs a restart, collateral loss of other mods' conflicts); a mixin
on static `Enchantment.areCompatible` (global state with no server context).
**Cost:** vanilla `/enchant` and loot generation still apply vanilla conflicts. Accepted.
**Affects:** Phase 4 `ConflictFamily`/`EnchantRules`/`ConflictSection`, DESIGN §2 and §4

## 2026-10-06 — The damage family relaxes all six enchantments; Lunge max 5 (Phase 4)
**Context:** vanilla `#exclusive_set/damage` is Sharpness, Smite, Bane, Impaling, Density and Breach, but DESIGN §4 named
only three. Phase 1.8 flagged Lunge V's 20 exhaustion per lunge.
**Choice (owner):** one `damage` toggle relaxes all six (a mace can hold Smite + Bane + Density + Breach). Lunge stays at 5.
**Why:** one family per vanilla tag is simplest to explain. The hunger cost is the player's trade-off.
**Rejected:** relaxing only Sharpness/Smite/Bane; a separate mace toggle; capping Lunge at 3 or 4.
**Affects:** `ConflictFamily.DAMAGE`, `MaxLevels`, DESIGN §4 and §8

## 2026-10-07 — Lure and Riptide stay at vanilla 3 until Stage 4D
**Context:** §8 gives both "5*, drop to 4 if a GameTest fails", but that GameTest is in 4D, after the 4A table ships.
**Choice (owner):** `MaxLevels.TABLE` leaves them out (vanilla 3). 4D adds their rows only after the test passes.
**Why:** nothing unproven lands on main in between.
**Rejected:** setting 5 in 4A and letting 4D lower it.
**Affects:** `MaxLevels`, `VanillaEnchantmentIdsGameTest`, Stage 4D

## 2026-10-07 — Max-level table applies only over vanilla's loaded max
**Context:** `MODIFY` sees whatever a datapack or another mod already set.
**Choice:** a §8 row applies only when the loaded max equals vanilla's. Otherwise the loaded value is kept, with an INFO
pointing to `max_level_override`, which always wins.
**Why:** silently undoing a datapack author's deliberate change is worse than not raising it.
**Rejected:** always forcing the v1 value; skipping non-`VANILLA` `EnchantmentSource`s (a datapack can replace the
file with identical values, and we'd lose the table for no reason).
**Affects:** `MaxLevels.resolve`, `MaxLevelHook`

## 2026-10-07 — `MaxLevelHook` marks every enchantment modified, changed or not
**Context:** an entry Fabric sees as unmodified keeps its known-pack info, so a client may load it from its own resources
(and its own `MODIFY` + config) instead of receiving the server's. Example: the server overrides Fortune back to 3, the
server leaves the entry alone, and a modded client applies its table and shows 5.
**Choice:** call `exclusiveWith(sameSet)` for every entry the hook sees, so the server's registry is always the one synced.
**Why:** max level must be server-authoritative. The cost is a few KB of extra registry sync on join.
**Rejected:** marking only changed entries (the mismatch above); a custom sync packet (the registry already syncs).
**Unverified:** that a client re-runs `MODIFY` on known-pack entries at all. The fix holds either way; owner check 4A.7.
**Affects:** `MaxLevelHook`

## 2026-10-07 — Conflict toggles are `allow_together.<family>`, not `conflicts.<family>`
**Context:** 4B planned `conflicts.silk_touch_fortune: true` meaning "allow together". In a hand-edited file that reads
as "they conflict", the opposite.
**Choice (owner):** section `allow_together`, one bool per `ConflictFamily` (`key()` = lowercased enum name), true = may
share an item (default), false = vanilla exclusive.
**Why:** the key says what `true` does. A bool is a plain tick box on the screen.
**Rejected:** `conflicts` bools (inverted reading); `conflicts: "allow" | "exclusive"` strings (more typing, a cycling
button, and a typo'd string is just another issue to report).
**Affects:** `AllowTogetherSection`, `CommonConfig`, `ConfigScreen`, en_us.json, DESIGN §2/§4

## 2026-10-07 — Conflict families are fixed vanilla ID lists, not the live exclusive-set tags
**Context:** `relaxes` could read members from the vanilla `exclusive_set/*` tags at runtime instead.
**Choice:** `ConflictFamily` hardcodes the vanilla IDs (pure enum, unit-testable, families checked disjoint).
**Why:** a datapack or mod that adds its enchantment to `exclusive_set/damage` declared a conflict on purpose; our toggle
was written for the vanilla members and shouldn't silently lift theirs.
**Rejected:** tag-driven membership (needs a live registry, and changes meaning with every datapack).
**Affects:** `ConflictFamily`, Stage 4C `EnchantRules`

## 2026-10-07 — `EnchantRules`: books obey conflicts, existing entries are grandfathered, merge keeps the higher level
**Context:** Stage 4C had three behaviors DESIGN §4 didn't pin down.
**Choice (owner):** (1) a book is conflict-checked like an item; only applicability waits until the book is applied.
(2) Only a *newly added* enchantment is conflict-checked. Upgrading one already on the item passes even if the admin has
since made its family exclusive. (3) Merging two copies of an ID keeps the higher level; no vanilla "IV + IV = V".
Plus (agent): an `ALREADY_AT_LEVEL` verdict when the item already has the ID at ≥ the requested level, so callers can show
§5's "Already at maximum level" instead of charging for a no-op.
**Why:** (1) vanilla's anvil already refuses conflicting book merges, and a book no item can fully take is a trap.
(2) refusing the upgrade doesn't remove the pair, it only strands the item; same spirit as "a lowered max never touches
existing items". (3) levels are bought directly (§5), so +1 merging would be a cheaper route around the cost model.
**Rejected:** books as pure carriers; refusing any change to a grandfathered pair; vanilla +1 merging.
**Affects:** `EnchantRules`, Phase 5 cost model, Phase 6 anvil, Phase 7 table

## 2026-10-07 — Overrides past a known break level are clamped (Lure 5, Riptide 7, Quick Charge 4)
**Context:** `max_level_override` accepts 1..255, but Lure VI never bites, Quick Charge V has zero charge time, and a
fast enough Riptide launch trips the server's moved-too-quickly check. Arithmetic in TODO 4D.1.
**Choice (owner):** clamp the override to the last safe level and WARN, rather than load it as written. Riptide's cap is 7:
the highest level whose single launch passes `dist² ≤ 100` even if the server credits none of its own velocity and the
player is already at terminal fall speed (L7 98.4, L8 113.8).
**Scope (agent):** only `max_level_override` is clamped. The §8 table is already under every cap, and a datapack that
changes a max may also have changed the effect, so its value is respected (same rule as the 2026-10-07 datapack entry).
**Rejected:** WARN only; no check at all; caps derived from the live effect data (needs effect evaluation at `MODIFY`
time for three fixed vanilla IDs). Riptide 11/12 (the "push alone ≤ 10" estimate) ignores existing velocity.
**Affects:** `MaxLevels`, `MaxLevelHook`, DESIGN §8

## 2026-10-07 — Cost model gaps filled before Phase 5
**Context:** DESIGN §5/§5a left repair cost, `FIXED`, two multiplier fields and multi-curse Cleanse undefined, and the
§5 table only works if Mending's per-level cost is 0.
**Choice (owner):** (1) repair = 1 level per material item, 2 to combine two items (vanilla minus prior work), both in
`common.json5` `costs`. (2) drop `treasure_multiplier` and `curse_multiplier`. (3) `FIXED` upgrade = `cost_per_level`
once, however many levels. (4) Cleanse repays the removed curse's own `discount_percent`, min `removal_min_cost`.
**Choice (agent):** Mending is data — `base_cost 2`, `cost_per_level 0`, `other_levels_surcharge 2` — and the surcharge
is a generic per-enchantment field (default 0), so no Mending branch in code (§5 "never code branches"). A curse's own
line costs 0 and curses don't count in `O`. A curse with no entry discounts 10% (vanilla curses' value).
`maximum_operation_cost N` refuses totals above N; −1 = off; creative bypasses it (vanilla does). New package `cost/`.
**Why:** (1) familiar, no runaway penalty. (2) both duplicate what `base_cost`/`cost_per_level`/`discount_percent` already
express. (4) own-percent makes "curse → enchant → cleanse" never cheaper than enchanting clean: Σ own % ≥ the capped
discount. The marginal rule lets 4 curses at the cap be cleansed for the minimum twice, cheaper once cost > ~400.
Mending's per-level 0 is forced by "Mending alone = 2" and "Mending + Eff I = 5".
**Rejected:** flat-1 or durability-scaled repair; multipliers at 1.0 (knobs that do nothing); a separate `upgrade_cost`
field; marginal-drop Cleanse; 0% for unknown curses (a curse that buys nothing contradicts §5a).
**Affects:** `cost/*`, `CostsSection`, `CursesSection`, `EnchantmentOverrides`, DESIGN §5/§5a, Phases 6/6b/7

## 2026-10-07 — Correction: why Cleanse repays the curse's own percent
**Context:** the "Cost model gaps" entry above says the marginal-drop rule lets stacked curses be cleansed cheaply
enough to beat enchanting clean. That's wrong: summed over cleansing every curse, the marginal drops telescope to exactly
the capped discount received, so neither rule makes "curse → enchant → cleanse all" cheaper than enchanting clean.
**Choice:** unchanged (owner): the removed curse's own `discount_percent`, min `removal_min_cost`.
**Why (corrected):** simpler to show (one number per curse, independent of removal order), and conservative. The only
difference is that marginal lets a player strip curses that earned nothing past the cap for the minimum, while own-%
charges each curse its full percent. The owner was shown that trade-off accurately; only the recorded reason was off.
**Rejected:** marginal drop (order-dependent cost, cheap removal of curses past the cap).
**Affects:** `CostModel.cleanseCost`, TODO 5C.4 (mutation is now the flat-minimum cleanse, the 2026-10-06 rejected design)

## 2026-10-07 — Anvil: book modes, item + item, prior work, server-computed preview (Phase 6)
**Context:** DESIGN §6 named three invalid-book modes without defining them, said prior work is "ignored, or flat if
configured", and "never silently deletes" had no rule for item + item. Decompiling 1.21.11 showed the cost `DataSlot`
syncs as a short and `createResult` runs on both sides.
**Choice (owner):** `apply_valid` (default) leaves the skipped entries on the book in the right slot; `reject_all`
refuses unless every entry applies; `strip_invalid` consumes the book and destroys skipped entries after the preview
says so. Item + item refuses a loss except under `strip_invalid`; a dominated lower level is not a loss. Prior-work
`repair_cost` is never read or written, with no flat key. The preview is a side panel.
**Choice (agent):** the server computes the outcome and sends a preview payload. The client holds no pricing table and
defers `createResult` when the server is ours. `NetworkProtocol.VERSION` 1 → 2. Creative obeys `EnchantRules`.
Enchanting needs a single left item. Book entries are checked in ID order.
**Why:** a leftover book makes the default lossless without a confirmation step. Leaving `repair_cost` alone keeps
add/remove safe (§2a). A server-built preview can't disagree with what the server charges, and it carries totals past
32,767. A v1 client would compute vanilla results locally on a v2 server, so it must not join.
**Rejected:** consuming the book under `apply_valid` (loses entries by default); a flat prior-work key (it would have to
keep raising the component, so vanilla would charge more after removal); syncing `enchantments.json5` so the client
prices locally (two sources of truth); keeping vanilla's creative bypass (lets creative players build items that
survival rules call invalid).
**Affects:** `anvil/*`, `AnvilMenuMixin`, `AnvilScreenMixin`, `AnvilPreviewPayload`, `NetworkProtocol`, DESIGN §6

## 2026-10-07 — Anvil preview: wire format and panel placement (Stage 6C, 6D)
**Context:** 6C needed a wire shape for the preview; 6D's planned right-side panel would sit under recipe viewers
(EMI/REI/JEI use the space right of container screens).
**Choice (owner):** the panel goes **left** of the anvil window (right only if left doesn't fit), with a **toggle
button** on the window; its state is remembered in client config.
**Choice (agent):** the payload carries the domain records as they are (`AnvilOutcome.Status`, the whole
`CostBreakdown`, `AnvilOutcome.Entry`), not a parallel wire DTO. Enums travel as bounds-checked ordinals. Sizes are
checked in the record constructor. Dedup by `equals` per menu.
**Why:** one type, no mapping to drift; decoding the whole `CostBreakdown` re-runs its totals check, so a corrupt or
forged total fails to decode instead of being shown. Ordinals are compact, and the protocol version already guards
reordering. Constructor checks make an oversize preview fail on the server thread, where it's logged and replaced by a
status-only preview, instead of inside Netty where it would kick the player.
**Rejected:** right-side panel (recipe-viewer overlap); tooltip-only breakdown (owner: less discoverable); enum names as
strings (bigger, and the version bump covers renames anyway); a `long` total (`CostBreakdown.total` is already an
`int` saturated at `Integer.MAX_VALUE`; the bug was the short `DataSlot`, not the width).
**Affects:** `network/AnvilPreviewPayload`, `network/AnvilPreviewHolder`, `AnvilMenuMixin`, Stage 6D screen

## 2026-10-07 — Anvil screen: client.json5, tab, window shift, client GameTest (Stage 6D)
**Context:** 6D's toggle had to be "remembered in client config", but `client.json5` was deferred until a phase needed
a client key, and until now only the config screen's save wrote files. `AnvilScreen.subInit` places the rename box from
`(width - imageWidth) / 2`, not `leftPos`. Nothing automated proved a client mixin applies.
**Choice (owner):** (1) `client.json5` is created now; clicking the toggle saves it at once through the GUI save's
writer (`.bak` first), and the config screen gets a client category with the same key. (2) The toggle is a **tab off
the window's left edge**, near the top. (3) When neither side has room, **shift the anvil window** to make room (like
the recipe book). (4) Turn on Fabric **client GameTests**; one test drives the real screen.
**Choice (agent):** key `anvil.show_preview_panel` (default true) in `config/ClientConfig`, pure like `CommonConfig`.
A failed toggle save keeps the new state for the session, logs (ConfigFiles already does) and shows a toast. The panel
has a **fixed width** with wrapped text, so the layout depends on the screen size only and the window never moves as
items change. The shift moves `leftPos` and the rename box together. A click on the panel or tab is not "outside"
(vanilla would throw the carried item). The cost label shows the full message when it fits and a short one otherwise;
the panel always has the full one. The text decisions live in a pure `anvil/AnvilPreviewView` (lang keys + typed
args), JUnit-tested; the client only turns them into `Component`s. The client's `mayPickup` prediction uses the
preview's exact total. No panel and no tab on a vanilla server.
**Why:** a toggle that forgets is not "remembered"; the GUI writer already backs up and refuses unreadable or newer
files. A content-sized panel would make a shifted window jump on every item change. Without the outside-click guard,
clicking the panel while holding an item drops it in the world.
**Rejected:** per-session toggle and write-on-exit (owner); hide-with-tooltip and overlap when there's no room (owner);
JUnit-only for the screen (owner: a broken injection would only show in-game); a content-sized panel (jumps);
`rebuildWidgets()` to apply a toggle (re-runs `subInit`, which clears the name and sends two rename packets).
**Affects:** `config/ClientConfig`, `client/ClientSettings`, `client/AnvilPreviewState`, `mixin/client/AnvilScreenMixin`,
`anvil/AnvilPreviewView`, `anvil/AnvilPanelLayout`, `AnvilBridge`, `ConfigScreen`, build.gradle, DESIGN §2/§6

## 2026-10-07 — Grindstone: picker, explicit actions, server-held choice (Phase 6b)
**Context:** DESIGN §6 lists Extract / Cleanse / Disenchant but not how a player picks, what shows before a pick, or
whether the cost cap applies. Decompiling 1.21.11: the input slots (`GrindstoneMenu$2`/`$3`; `$1` is the
`SimpleContainer`) refuse plain books, the result slot (`$4`) awards XP orbs and empties both inputs on take, and
nothing at the grindstone costs levels.
**Choice (owner):** (1) the picker is a side panel placed like the anvil's, always shown (it's a control, no tab).
(2) The result slot stays empty until the player picks, vanilla Disenchant included. (3) `maximum_operation_cost` caps
Extract and Cleanse too.
**Choice (agent):** the choice lives on the server menu, set only by a validated C2S payload, and is cleared when the
source stack changes (not when only the book count changes). Either slot orientation works; a plain-book stack gives
one book per take. Extract/Cleanse replace vanilla's `onTake` (no orbs, `REPAIR_COST` untouched); Disenchant/Repair keep
vanilla's. A book holding nothing but the picked entry is refused (`NOTHING_TO_SPLIT`). An entry above the
current max level moves at its level (lowered maxima never touch existing items). Creative: cap bypass +
`creative_free`, as at the anvil. `NetworkProtocol.VERSION` 2 → 3. The anvil layout becomes a shared `SidePanelLayout`.
**Why:** empty-until-picked means a shift-click can't strip an item the player meant to extract from. A server-held
choice means a stale or forged click can only select something the current inputs allow. No orbs on Cleanse, or
cleansing a cursed item would refund XP for enchantments it keeps. A v2 client would run vanilla's result locally.
**Rejected:** in-window cycle buttons (owner: one choice visible at a time); auto-Disenchant when it's the only action,
and preselecting the first row (owner: accidental strips); an uncapped grindstone (owner: one rule everywhere); a choice
carried in the take click (vanilla's click packet has no room; a separate choice is validated once, then the take is a
plain slot click); keeping the choice across a source swap (a picked "Disenchant" would apply to the new item unseen).
**Affects:** `grindstone/*`, `GrindstoneMenuMixin`, grindstone slot mixins, `GrindstoneScreenMixin`, `network/Grindstone*`,
`NetworkProtocol`, `SidePanelLayout`, DESIGN §6

## 2026-10-07 — Grindstone: Disenchant and Repair call vanilla's `computeResult` (Stage 6bA)
**Context:** the 6bA plan copied vanilla's private `removeNonCursesFrom`/`mergeItems` into `GrindstoneCalculator`.
**Choice:** the calculator takes a `BinaryOperator<ItemStack> vanillaResult`; the server passes vanilla's
`computeResult` via `mixin/GrindstoneMenuInvoker`. Our mixins never touch `computeResult` (only `createResult` and the
slots), so it stays vanilla plus whatever other mods do to it.
**Why:** exact vanilla by construction, including `REPAIR_COST` and the book → plain book transmute; no copy to drift on
a port; another mod's grindstone tweaks keep working. `computeResult` reads only its arguments (decompiled 1.21.11).
**Rejected:** a copy (drifts silently on the 26.x port, and drops other mods' changes).
**Affects:** `grindstone/GrindstoneCalculator`, `mixin/GrindstoneMenuInvoker`

## 2026-10-07 — Side panels share a `screen` package (Stage 6bC)
**Context:** the grindstone's picker needs the anvil panel's layout, text types, text resolver and drawing. Those lived
in `anvil` and `client/AnvilPreview*`, so reusing them as-is meant grindstone code importing anvil classes.
**Choice:** move them, not copy them: `screen/SidePanelLayout` (tab width is a parameter: anvil 14, grindstone 0),
`screen/PanelText` (`Tone`, `Arg`, `Msg`, `Row`), `client/SidePanel` (adds a selected row, hover highlight and
`rowAt` for clicks), `client/PanelTextResolver`. Anvil-specific pieces (`AnvilPreviewView`, `AnvilPanelTab`, the label)
stay in `anvil`/`client`.
**Why:** one layout and one drawing path, so both panels look and behave the same and a fix lands in both. The
anvil's client GameTest passing unchanged is the check that the move didn't alter it.
**Rejected:** grindstone importing `anvil.*` types (wrong dependency direction, names that lie); a parallel copy for the
grindstone (two panels that drift apart).
**Affects:** `screen/*`, `client/SidePanel`, `client/PanelTextResolver`, `AnvilScreenMixin`, `GrindstoneScreenMixin`

## 2026-10-07 — Enchanting table: panel layout, enchanted items, offered pool, priced commit (Phase 7)
**Context:** DESIGN §6 said "our own screen" and "every enabled enchantment applicable, Mending included". It didn't say
how the list fits, whether enchanted items are accepted (vanilla's `slotsChanged` skips them), which treasure counts, or
when curses arrive (TODO 7.1 said "curses off", 10e "curses at the table"). Decompiled 1.21.11: vanilla charges only 1–3
levels (the shown cost is a requirement), and commits through `clickMenuButton(0..2)`.
**Choice (owner):** (1) vanilla window kept. The offer area holds the totals and an Enchant button, and the list is a
scrollable side panel placed like the anvil's. (2) Enchanted items and books accepted: add and upgrade. (3) Treasure at
the table: Mending and Frost Walker only. (4) Binding and Vanishing offered now, with the §5a negative line.
**Choice (agent):** the pool is the tag `enchanting2:offered_at_table` (`#in_enchanting_table` + those four).
`treasure_requires_full_power` gates `#treasure` minus `#curse`. Applicability is `supported_items` via `EnchantRules`,
not vanilla's `primary_items`. The selection is server-held and set by C2S. It's all or nothing: any invalid entry
means no enchant. The commit is our own payload carrying the selection and the levels and lapis shown, and the server
refuses on any mismatch. Vanilla `slotsChanged`/`clickMenuButton` are cancelled for menus we handle. The full cost is
charged via `giveExperienceLevels`. `NetworkProtocol.VERSION` 3 → 4.
**Why:** the panel reuses `SidePanel`/`SidePanelLayout` and matches the other two stations. The cost model is
order-independent, so building an item over several visits costs the same as one. Soul Speed, Swift Sneak and Wind
Burst are exploration rewards. Mending and Frost Walker are already sold by librarians. A tag keeps mods' table
enchantments in automatically and lets datapacks extend it. `supported_items` is DESIGN §4's applicability, and a
narrower table would only route players through books and the anvil. All or nothing means the server never charges for
a set the player didn't pick. A priced commit means a `/reload`, a broken shelf or a lost selection packet can never
charge a number the player wasn't shown.
**Rejected:** a new wide screen (new texture and small-window handling, for no gameplay gain); unenchanted items only
(vanilla rule; owner); all five treasure, or Mending only (owner); curses deferred to 10e (owner); a hardcoded ID list
(datapack-hostile, and Phase 9 would replace it anyway); every registry enchantment (would sell enchantments mods never
meant for a table); applying the valid subset of a selection (charges for something other than what was picked);
committing through vanilla's button packet after a separate selection packet (no room for the price, and it relies on
packet order).
**Affects:** `table/*`, `EnchantmentMenuMixin`, `EnchantmentScreenMixin`, `network/Table*`, `NetworkProtocol`,
`config/TableSection`, `SidePanel`, `data/enchanting2/tags/enchantment/offered_at_table.json`, DESIGN §6

## 2026-10-07 — Enchanting table screen: price in the offer rows, Enchant as the third (Stage 7D)
**Context:** the owner chose "vanilla window + side panel" and left the details of the offer area and the clicks open.
**Choice (agent):** offer rows 1 and 2 show the XP levels and lapis on vanilla's dark (disabled) offer sprite, green
when the player can pay and red when not. Without a price they show the panel's status line instead. Row 3 is Enchant,
drawn with vanilla's own offer sprites (normal / highlighted / disabled). A row body click picks at the highest level
the table sells, or unpicks, and `<` / `>` step the level. Every change sends the whole selection. The panel scrolls
back to the top when the item changes. The book opens when the panel has rows, as vanilla's opens when it has offers.
Enchant is enabled from the price plus the client's own levels and lapis (`TablePanelView.ready`), never the server's
status alone, which goes stale when the player gains levels.
**Why:** the window keeps vanilla's look and layout, and the price sits where vanilla showed its level cost. Picking at
the highest sellable level is what nearly every player wants, and the stepper fixes the rest. A whole-selection payload
can't get out of step with the server through a lost delta.
**Rejected:** a separate cost tooltip (hidden until hover); defaulting a pick to level I (one click per level for the
common case); a vanilla `Button` widget (looks unlike the table's rows).
**Affects:** `mixin/client/EnchantmentScreenMixin`, `table/TablePanelView`, `client/SidePanel`

## 2026-10-08 — Phase 8: conflict effects, trident rule, Fortune drops and the placed-block tracker
**Context:** DESIGN §4 and §6 left open which drops Fortune multiplies, which placements are marked, which Riptide
tridents throw on land, and what SILK_WINS does on blocks where Silk Touch doesn't change the drop. Facts: Fabric drops
an attachment that fails to decode (fail open), and vanilla removes a broken block before rolling its loot.
**Choice (owner):** every drop of a tagged natural block is multiplied (gravel → flint, clay → clay balls). Every
hand-placed block is marked, not only tagged ones. Any Riptide trident throws normally out of water and rain. SILK_WINS
still multiplies unless Silk changed the drop.
**Choice (agent):** 8.2 is GameTests only, because 1.21 sums per-enchantment effect components once `EnchantRules`
allows the pair. The trident rule is one `@WrapOperation` on `getTridentSpinAttackStrength` in `TridentItem`, keyed off
the item, not the toggle. Fortune runs in `LootTableEvents.MODIFY_DROPS`, only for the broken block's own table. "Silk
changed the drop" = a guarded re-roll with a Silk-free tool copy, compared by item type. Marks are per-chunk section
bitsets in a chunk attachment. The codec never fails: bad data becomes `UNREADABLE` (no bonus anywhere in the chunk,
raw data written back). A player break clears the mark after the loot (`PlayerBlockBreakEvents.AFTER`). Other removals
leave a stale mark, which fails closed. Endermen placements are marked. `FORTUNE_MULTIPLIES_SILK` covers the tool's
tag plus `enchanting2:fortune_multiplies_silk` (`#c:ores`).
**Why:** one drop rule is predictable, and the tracker already stops loops. Marking every placement survives tag edits.
A decode failure must not turn placed blocks natural, and Fabric's default would. Clearing on removal would erase the
mark before the loot reads it.
**Rejected:** only the block's own item multiplied (gravel at Fortune III+ never multiplies, and clay/snow/mushrooms fall
out of the tags); marking only tagged blocks (tag edits launder); Riptide throw only with Loyalty/Channeling; a static
"silk changes the drop" tag (hand-maintained, wrong for modded blocks); a mixin on `Block.getDrops` (misses tables
rolled elsewhere and other mods' loot hooks run in MODIFY_DROPS anyway); a set of packed longs (unbounded per chunk).
**Affects:** TODO Phase 8, `fortune/*`, `mixin/TridentItemMixin`, `config/FortuneSection`, DESIGN §4, §6

## 2026-10-09 — Phase 9: acquisition flags, weights, treasure, config screen
**Context:** DESIGN §5/§6/§7 list `enabled`, table/loot/trade flags + weights and a treasure flag per enchantment
without saying what "disabled" does to existing items, whether vanilla's uniform picks (librarian books,
`enchant_randomly`) become weighted, whether loot/trade levels are capped now that maxes are 5, or what `treasure`
changes. Facts: every weighted pick goes through `EnchantmentHelper.selectEnchantment`, which has no server; a registry
holder's `owner` is the server's own `MappedRegistry`.
**Choice (owner):** disabled = acquisition only (items keep it, it keeps working). Weighted by default (registry weight).
Loot and trade levels roll up to the new max, as vanilla does. `treasure` overrides only our table gate and the
librarian's doubled price. The config screen edits enable + the three flags in Phase 9.
**Choice (agent):** keys in `enchantments.json5`, all optional; flags only remove from a source (tags add). LOOT includes
mob spawn gear; TRADE includes villager-sold gear. The server stamps itself on its enchantment registry and the
`selectEnchantment` hook finds it through the holder's owner. A `@WrapMethod` TRADE scope on the three villager
`getOffer`s tells trade from loot. The screen lists §8's vanilla IDs + file IDs (title screen) or registry + file IDs
(host). Remote shows a "kept on the server" label, because `enchantments.json5` isn't synced.
**Why:** acquisition-only matches the "never touch existing items" rule for lowered maxes. One hook in
`selectEnchantment` covers every weighted caller, other mods' included; per-caller hooks would miss mods and need ~9
mixins. The owner stamp keeps config per server (DESIGN §2a: no static world state). Remove-only flags can't make a mod
sell something it never meant to.
**Rejected:** editing vanilla tags at load (the config reloads after tags on `/reload`, and `enchant_randomly` without
options ignores tags); writing weights into the registry at `MODIFY` (one weight can't be both loot and trade; weight 0
fails the client sync codec); a static "current server" (§2a); a thread-local set by every caller that has a level
(misses other mods' calls); stripping disabled enchantments from loot output (owner); a level cap for loot/trades (owner);
rewriting `#treasure` (owner); syncing `enchantments.json5` just for a read-only remote view.
**Affects:** TODO Phase 9, `config/EnchantmentOverrides`, `acquisition/*`, `table/TablePool`, `client/ConfigScreen`,
`config/ConfigView`

## 2026-10-09 — Stage 9C: one ConfigView for both files, file name as a text argument, VanillaEnchantments
**Context:** the Enchantments category needs the same modes as `common.json5` (title screen, host, locked), but the
mode texts named `common.json5`, a remote server never sends `enchantments.json5`, and the title screen has no registry
to list enchantments from.
**Choice (agent):** `ConfigView<T>` is generic. `resolveUnsynced` gives a remote session `KEPT_ON_SERVER` (or
`VANILLA_SERVER`) and no values. The mode texts take the file name as `%1$s`. The title screen lists
`enchantment/VanillaEnchantments.KEYS` (a GameTest pins it to the registry), the host lists its registry, and both add
IDs found in the file. The draft (`acquisition/EnchantmentFlagsDraft`) writes a key only when it differs from the
default or was already set.
**Why:** one resolver keeps "remote never touches the local file" proved once. A text argument avoids a second set of
six mode texts. A compile-time key list can't name a missing enchantment, and the GameTest catches one added by a
version bump.
**Rejected:** syncing `enchantments.json5` for a read-only remote view (a protocol change for display only);
`MaxLevels.TABLE` as the list (it only holds changed rows); `VanillaRegistries.createLookup()` at the title screen
(builds every worldgen registry to list 43 IDs); writing all four keys for every listed enchantment (a 43-entry file
from one click, and it would freeze today's defaults).
**Affects:** `config/ConfigView`, `client/ConfigEditing`, `client/ConfigScreen`, `acquisition/EnchantmentFlagsDraft`,
`enchantment/VanillaEnchantments`, `en_us.json`

## 2026-10-09 — Phase 10a: data enchantments, sources, Vision, Health relog, XP rounding
**Context:** DESIGN §9 lists 12 data-only (D) enchantments with open concerns: which sources they come from, Vision's
flicker, Health's clamping, and no numbers. Facts (1.21.11 `javap`): `LivingEntity.readAdditionalSaveData` calls
`setHealth(saved)` before any equipment attribute applies, so it clamps to the base max (MC-17876).
`processMob/BlockExperience` return `MutableFloat.intValue()` (truncation: 5 × 1.15 → 5). Arrows and thrown tridents
call `modifyDamage` and `doPostAttackEffectsWithItemSource` with the firing weapon, so on-hit effects reach projectiles.
**Choice (owner):** (1) **Sources: everywhere, like vanilla.** Every new non-curse enchantment joins
`#minecraft:non_treasure`, which feeds the table pool, librarian books, random loot, mob spawn gear and villager gear.
(2) **Vision moves to 10b** (small Java tick: refresh under 11 s, remove on unequip, never touch a longer potion).
(3) **Health is data plus a relog fix** (a mixin restores the saved health once equipment attributes apply).
(4) **Starting numbers as proposed** (TODO 10a), retuned in 10d. (5) **XP Boost random rounding:** a mixin rounds the
fraction of `processMob/BlockExperience` up with that probability, so the average is exact.
**Choice (agent):** no `exclusive_set` on any new enchantment (DESIGN §4 default = allow together). Own item tags under
`enchanting2:enchantable/` (melee = `#minecraft:enchantable/weapon` + trident; ranged = bow, crossbow, trident).
Reach = mining + melee (no bows). `enchantment/ModEnchantments.KEYS` joins `VanillaEnchantments.KEYS` on the title
screen, pinned to the registry's `enchanting2` namespace by a GameTest.
**Why:** `non_treasure` is the vanilla idiom, and every vanilla source tag includes it, so one tag entry reaches all
five sources and Phase 9's flags still remove per source. Truncation would make XP Boost I–III a no-op on most mobs.
Random rounding keeps it data-shaped (`mob_experience`/`block_experience`) and fixes Greed (10e) the same way.
**Rejected:** table/books/loot only (owner: everywhere); data-only Vision (lingers 11–15 s, a packet every tick);
accepting the relog clamp; bigger XP numbers (still truncated); `exclusive_set` on Withering/Poisoning/Freezing or
Swiftness vs Soul Speed (contradicts §4; 10d can add a family).
**Affects:** TODO Phase 10a, `data/enchanting2/enchantment/*`, `data/minecraft/tags/enchantment/non_treasure.json`,
`enchantment/ModEnchantments`, `client/ConfigEditing`, Health and XP mixins

## 2026-10-09 — Phase 10b owner answers (asked before planning)
**Context:** DESIGN §9 left the E-route enchantments' models open: Vampirism (threshold vs other), TrueShot Aura
(unlimited range), Smelting (items, XP, Fortune order), Beheading (chance, player heads).
**Choice (owner):** (1) **Vampirism = lifesteal percent:** heal 4% × level of the damage dealt (V = 20%), every hit, no
cooldown, players and bosses included. (2) **TrueShot Aura:** no gravity for 100 ticks (5 s), then normal gravity and
normal despawn. (3) **Smelting on all mining tools** (pickaxe, shovel, axe, hoe), Fortune multiplies first and then
each drop is smelted, and the recipe's XP is granted like a furnace (randomly rounded). Silk Touch still wins by default
(§4 `smelting_vs_silk_priority`). (4) **Beheading:** 2% × level (V = 10%), added to vanilla's own wither-skeleton
roll, mobs with a head item only (zombie, skeleton, wither skeleton, creeper, piglin, ender dragon); player heads
behind `beheading.player_heads`, default off.
**Why:** owner's calls. Lifesteal over the DESIGN threshold model: simpler and scales with the hit.
**Rejected:** threshold + cooldown, threshold PvE-only (Vampirism); gravity-free until hit (TrueShot); pickaxe-only and
no-XP Smelting; 5%/level and player heads on by default (Beheading).
**Affects:** TODO Phase 10b, DESIGN §9

## 2026-10-09 — Phase 10b owner answers, round 2 (asked before planning)
**Context:** Planning 10b raised four questions the first round didn't cover.
**Choice (owner):** (1) **Smelting smelts every recipe**: any drop with a smelting recipe is smelted, so logs become
charcoal on an axe, sand becomes glass and potatoes become baked potatoes. We ship an empty item tag
`enchanting2:smelting_excluded` for datapacks. (2) **Beheading needs a player kill**, like vanilla's skull rule, so a mob
holding a Beheading weapon never drops heads. (3) **Vampirism counts direct melee hits only** (not a thrown trident), and
the heal is based on the health the target actually lost, so overkill doesn't count. (4) **Vision is for players only.**
**Why:** owner's calls. Every recipe means no ore list and nothing to maintain (DESIGN §9 "no hard-coded ore list").
**Rejected:** an ore-only input tag; every recipe minus logs; any kill with the weapon (Beheading); full damage
including overkill; thrown tridents (Vampirism); Vision on all living entities.
**Affects:** TODO Phase 10b

## 2026-10-09 — Beheading leaves out the ender dragon (owner)
**Context:** `javap`: `EnderDragon.reallyHurt` sets health 1 and starts the DYING phase, and `tickDeath` removes it
200 ticks later. `dropAllDeathLoot` is never called, so no loot-table or `MODIFY_DROPS` hook fires for it.
**Choice (owner):** Beheading covers zombie, skeleton, wither skeleton, creeper and piglin, plus players behind
`beheading.player_heads`. No dragon.
**Why:** it would need its own mixin, and the dragon's killing blow is almost always an arrow or a bed, which a melee-only
enchantment never counts. Dragon heads still come from End ships.
**Rejected:** a mixin that remembers the Beheading hit that started the death animation.
**Affects:** TODO Phase 10b, DESIGN §9

## 2026-10-09 — Phase 10b: E-route enchantments on our own effect components
**Context:** DESIGN §9 route E: Vampirism, Beheading, Smelting, TrueShot Aura, Vision need small Java.
**Choice (agent):** each behaviour reads an effect component we register (`enchanting2:lifesteal`, `head_drop_chance`,
`smelt_drops`, `night_vision`; entity effect `suspend_gravity` inside vanilla's `projectile_spawned`), not an
enchantment ID, so datapacks can move or retune them. Vampirism wraps `LivingEntity.hurtServer` (Fabric's
`AFTER_DAMAGE` skips killing blows). Beheading and Smelting run in `MODIFY_DROPS` on the victim's or block's own table
only. Smelting is called inside `FortuneDrops`' listener after Fortune and inside its re-roll guard. Beheading's mob → head
mapping is our loot table `enchanting2:beheading/heads` (vanilla's charged-creeper mapping + `fill_player_head`).
TrueShot's countdown is a persistent entity attachment. Vision's marker is the ambient flag. Both config sections
landed in 10bA (one `CommonConfig` churn), with one "Enchantment effects" screen category instead of two one-option tabs.
**Why:** data-shaped like vanilla's own effects; every hook is a vanilla or Fabric seam already used in this repo.
A separate listener for Smelting would smelt the Silk-free re-roll (a mutation run proved the test catches it).
**Rejected:** ID-keyed checks (not datapack-movable); Fabric `AFTER_DAMAGE` (no killing blow, post-armor amount);
reusing `minecraft:charged_creeper/root` (a datapack retuning charged creepers would change Beheading);
a non-persistent TrueShot timer (vanilla saves `NoGravity`, so a reload would float the arrow forever).
**Affects:** src/main/java/com/wulfnet/enchanting2/effect/*, mixin/LivingEntityMixin, mixin/AbstractArrowMixin,
fortune/FortuneDrops, config/BeheadingSection, config/SmeltingSection

## 2026-10-10 — Single-level enchantments cost 10; Water Walking and Lava Walking added (owner)
**Context:** the default price is 1 XP level per enchantment level, so Silk Touch or Infinity cost 1, less than
Efficiency II.
**Choice (owner):** every single-level non-curse enchantment except Mending costs 10 XP levels: vanilla Aqua Affinity,
Silk Touch, Flame, Infinity, Channeling, Multishot; ours Smelting, TrueShot Aura, Vision; and the planned level-1 ones.
Two new boots enchantments, Water Walking and Lava Walking, max level 1, cost 10, route M (Phase 10c).
**Why:** a single level is the whole enchantment, so pricing it as one level undervalued it.
**How (agent):** `PricingTable.BUILT_IN` rows (`base_cost 10`, `cost_per_level 0`), like Mending's. Data, not a
"max level is 1" branch in `CostModel` (DESIGN §5: special cases are rows). The agent included the planned 10c level-1
enchantments (Magnetic, Ender Link, Keep on Death, Double Jump, Area Mining, Replant) because the owner said "all".
**Rejected:** a max-level-1 rule in code (would also price third-party enchantments and would change when an override
raises a max).
**Affects:** docs/DESIGN.md §5/§9, TODO Phase 10c/10f, src/main/java/com/wulfnet/enchanting2/cost/PricingTable.java (10f)

## 2026-10-10 — Fluid/snow walking boots: owner answers
**Context:** open questions on Water Walking and Lava Walking (DESIGN §9).
**Choice (owner):** sneak to sink on both; standing on lava still burns; mobs wearing the boots get the effect; every
single-level enchantment, built or not, costs 10 (confirms the agent's reading of "all"). New: Snow Walker (boots,
walk on powder snow instead of sinking). Agent assumptions, not yet confirmed: Snow Walker max level 1 (so priced 10);
"burns" = vanilla's lava contact (fire + lava damage), not just catching fire.
**Why:** owner's call.
**Rejected:** never sinking on lava (owner chose sneak to sink anyway).
**Affects:** docs/DESIGN.md §5/§9, TODO 10c/10f

## 2026-10-10 — Water Walking dropped; Frost Walker 2 per level; Snow Walker blocks freezing (owner)
**Context:** Water Walking overlapped Frost Walker (both cross water; Frost Walker would freeze the surface under a
Water Walker). Snow Walker's freezing behaviour was open.
**Choice (owner):** drop Water Walking. Frost Walker costs 2 XP levels per level (`PricingTable` row, `cost_per_level
2`). Snow Walker is exactly the leather-boots effect on any boots, freeze immunity included.
**Why:** Frost Walker already covers water; Frost Walker going to V made it cheap at 1 per level.
**Rejected:** keeping Water Walking exclusive with Frost Walker; Snow Walker without freeze immunity.
**Affects:** docs/DESIGN.md §5/§9, TODO 10c/10f

## 2026-10-10 — Snow Walker level 1, lava burn confirmed, new curses to level 5 (owner)
**Context:** two agent assumptions from the earlier entry, and the 10e curses' max levels.
**Choice (owner):** Snow Walker max level 1 (priced 10). Standing on lava with Lava Walking still burns (vanilla lava
contact). Fragility, Weight, Greed, Fumbling and Hunger go to max level 5; Binding and Vanishing stay at 1.
**Open:** per-level scaling of each new curse's effect and `discount_percent` (ask before 10e).
**Affects:** docs/DESIGN.md §5a/§9, docs/ENCHANTMENTS.md, TODO 10e

## 2026-10-10 — Elytra Speed, axe/crossbow widening, Silk Touch spawners, infinite buckets (owner)
**Context:** owner request: elytra Speed, more applicability for axes, shovels and the crossbow, Mending everywhere,
a spawner option for Silk Touch, infinite water and lava buckets, and shield ideas.
**Choice (owner):** Speed on elytra, +10% per level, 6 XP levels per level (10c). Axes take the melee weapon
enchantments and Reach/Magnetic/Ender Link; shovels the same minus weapons. Crossbow takes Infinity, Flame,
TrueShot Aura. Mending on every enchantable item. `silk_touch.collect_spawners` toggle. Infinite water and lava
buckets. All in Phase 10g except Speed.
**How (agent):** widen per enchantment with our own `enchanting2:enchantable/<id>` tags swapped in at MODIFY, never by
editing the shared vanilla tags (`#enchantable/bow` also drives Power/Punch; `#enchantable/melee_weapon` drives
Sharpness's primary items). Shovels and our melee enchantments on axes needed nothing: already true in data.
**Agent assumptions, not yet confirmed:** Speed max 5; Sweeping Edge stays off axes (`Player` sweeps only
`#minecraft:swords`); Mending already fits every vanilla durable item and is not put on non-durable ones (it would do
nothing); spawner option default off, spawner keeps its mob (vanilla copies spawner data only for ops,
`BlockEntityType.OP_ONLY_CUSTOM_DATA`, so placement needs a stamp-gated hook); infinite buckets = vanilla Infinity on
the two buckets, anvil + book only, an Infinity lava bucket is never fuel. Shield ideas are candidates only
(DESIGN §9).
**Rejected:** editing vanilla `#enchantable/*` tags (silently widens other enchantments); letting any
`block_entity_data` spawner place for non-ops (any player could make any spawner); Infinity lava bucket as fuel
(burns forever, or comes back a plain bucket).
**Affects:** docs/DESIGN.md §4/§5/§6/§9, docs/ENCHANTMENTS.md, TODO 10c/10g

## 2026-10-10 — Owner answers: Speed, shields, spawners, durable Infinity buckets
**Context:** open questions from the previous entry, plus shield candidates.
**Choice (owner):** Speed max 5 and it scales firework-rocket boosts as well as gliding. Shields: Steadfast,
Spiked, Bulwark, Riposte (+3% per level, not the proposed 10%), Steady Stance (10c). Quick Guard, Parry, Arrow
Catcher not picked. `silk_touch.collect_spawners` on by default. Buckets reuse Infinity and get durability so Mending,
Unbreaking etc. are useful. No Mending on modded durables missing from `#enchantable/durability`.
**How (agent):** the buckets get `max_damage` and join vanilla `#enchantable/durability` (the one deliberate vanilla
tag edit: every enchantment keyed on durability should apply). Agent proposals, not yet confirmed: 256 uses for both,
fixed (not config, since each side draws the bar from its own component); the last use leaves a plain empty bucket;
durability enchantments on a bucket need Infinity, because a plain bucket empties into a new item and would lose them;
a collected spawner keeps its mob.
**Rejected:** a config value for bucket uses (client/server mismatch shows a wrong bar); letting Mending onto a plain
bucket (lost on first use, wasted levels).
**Affects:** docs/DESIGN.md §4/§5/§6/§9, docs/ENCHANTMENTS.md, TODO 10c/10f/10g

## 2026-10-10 — Ctrl tooltip descriptions, helmet/leggings picks, bucket answers (owner)
**Context:** owner answers to the open questions, plus a new client feature.
**Choice (owner):** holding Ctrl over an item shows each enchantment's description (Phase 10h). Helmet: Calm Gaze,
Clarity, Phantom Ward, Sustenance (4% per level). Leggings: Step Assist, Anchored, Endurance (5% per level, sprint),
Sure Footing. Not picked: Hard Hat, Leaping, Shadow Step. Buckets: 256 uses; the last use destroys it like any tool;
only water and lava buckets take enchantments, never the empty bucket; never fuel; the table offers it. A collected
spawner keeps its mob. Sweeping Edge stays swords-only.
**How (agent):** descriptions are lang keys `enchantment.<ns>.<path>.desc` (the Enchantment Descriptions mod's
convention, so third-party ones that ship that key show too), guarded by a lang-completeness test so every later
enchantment stage must write one; off when that mod is loaded so lines don't double. D-route leggings (Step Assist,
Anchored, Sure Footing) get their own small Phase 10i instead of waiting for 10c.
**Still open:** Mending/Unbreaking on a water or lava bucket without Infinity. The owner's answer ("a plain bucket
can't be enchanted") reads as the empty bucket; the agent keeps the Infinity-first rule until confirmed. The "Hold
Ctrl" hint line.
**Affects:** docs/DESIGN.md §4/§5/§6/§9, docs/ENCHANTMENTS.md, TODO 10c/10f/10g/10h/10i

## 2026-10-10 — Highly configurable: max levels to 255 with ceilings, all in the config screen (owner)
**Context:** owner: users must be able to enable/disable enchantments and raise caps past the max where feasible,
with a note (Efficiency 100), all through the Mod Menu screen. Also: durability enchantments on a bucket need
Infinity; keep the "Hold Ctrl" hint.
**Choice (owner):** the above, as a DESIGN §1 principle and Phase 10j.
**How (agent):** three groups per enchantment: no ceiling, soft ceiling (accepted, the screen notes where more stops
helping), hard ceiling (clamped + WARN, the existing `SAFE_MAX` mechanism, extended only after a check). 255 is the
upper bound because `EnchantmentOverrides.MAX_MAX_LEVEL` already matches vanilla's stored-level limit. A JUnit
requires a ceiling row for every ID so a new enchantment can't skip it. Weights and `treasure` stay file-only.
**Rejected:** clamping soft ceilings (owner wants high levels allowed with a note); a GUI max level that applies
without a restart (MODIFY only runs at startup, gotchas.md 2026-10-07).
**Affects:** docs/DESIGN.md §1/§8/§10, docs/ENCHANTMENTS.md, TODO 10j

## 2026-10-12 — Curse levels, table row order, Fortune tag additions (owner)
**Context:** open owner items from state.md (10e curse scaling, panel order, Fortune default tags).
**Choice (owner):** the five new curses (10e) scale both effect and discount per level: discount 15% at I, +5% per
level (V = 35%), still capped by `curses.max_discount_percent`; the effect scales linearly with level (DESIGN §5a's
table is level I). The table panel sorts rows by translated name, so `enchanting2:` and `minecraft:` rows interleave.
Fortune default tags gain soil blocks (grass block, podzol, coarse dirt, rooted dirt, mycelium → shovels), bamboo
(`minecraft:bamboo`, the plant → axes) and `#minecraft:leaves` (→ hoes, vanilla's mineable tool for leaves).
**Why:** a +5%/level discount keeps one curse from hitting the 45% cap alone (15%×level would cap at III and make
IV–V pure downside); interleaving reads better than a namespace split.
**Rejected:** discount = 15% × level (caps at III); fixed discount (nobody would pick above I); keeping ID order.
**Affects:** docs/DESIGN.md §5a/§6/§7, TODO 10e/10k, `fortune_multiplies/*.json`, `TablePanelView`

## 2026-10-12 — Widening vanilla `supported_items` through a lent item lookup (Phase 10gA)
**Context:** 10gA points Knockback, Looting, Fire Aspect, Flame and Infinity at our item tags. `EnchantmentEvents.MODIFY`
(item-api 11.6.0) gets no registry lookup, and `BuiltInRegistries.ITEM`'s tag sets are pending (unbound) while dynamic
registries load.
**Choice:** `RegistryDataLoaderMixin` wraps `loadElementFromResource` (`@WrapMethod`, enchantment registry only) and
lends `ops.getter(Registries.ITEM)` to `ItemWideningHook` through a ThreadLocal, cleared in `finally`. The hook builds
the tag set from that getter, the same pending-tag lookup vanilla's own JSON parse uses. It swaps only when the loaded
items are still vanilla's tags (`ItemWidening.decide`), else INFO and keep theirs.
**Why:** keeps the 2026-10-06 "no override JSON" rule and never edits shared vanilla tags (`#enchantable/bow` also
drives Power and Punch).
**Rejected:** `BuiltInRegistries.ITEM.get(tag)` (unbound during load; stale set on a second world load); override
JSON (clobbers datapacks, decisions 2026-10-06); adding axes to vanilla `#enchantable/melee_weapon` (would also give
axes every other melee_weapon enchantment); Fabric `ALLOW_ENCHANTING` (doesn't change what the registry syncs, so the
table, loot and other mods would still see bow-only).
**Affects:** `enchantment/ItemWidening*.java`, `mixin/RegistryDataLoaderMixin.java`, `tags/item/enchantable/*`

## 2026-10-12 — A silk-touched spawner carries its mob type only (owner, Phase 10gB)
**Context:** 10gB said the spawner item keeps "its spawn data". Spawn data is arbitrary entity NBT (gear, names,
command-block minecarts), which is why vanilla's `OP_ONLY_CUSTOM_DATA` gate exists. An item component is forgeable by a
creative non-op (the client sends whole stacks in creative). `javap` (1.21.11): `SpawnEggItem.useOn` sets any spawner's
mob with `Spawner.setEntityId` for any player, no op check, only the `spawnerBlocksEnabled` gamerule.
**Choice (owner):** the item carries the mob type only, written as a minimal `block_entity_data`
(`SpawnData.entity.id`, so vanilla's tooltip and op placement still work) plus our unit stamp component. A non-op
placing a stamped spawner gets `setEntityId(type)`, the spawn-egg path, and only for a type that has a spawn egg
(primed TNT, minecarts, lightning don't; the creative-only Wither and Ender Dragon eggs do, so those pass, which a
spawn egg already allows). Everything else in the data is ignored.
**Why:** a forged item then grants exactly what a spawn egg already grants, so no secret or signing is needed.
**Rejected:** full NBT behind a per-world HMAC stamp (more code, items don't travel between worlds); full NBT behind a
plain stamp (a creative non-op could forge arbitrary entity NBT).
**Cost:** a map-maker's custom spawner (custom gear, timings, ranges) becomes a plain spawner of its mob.
**Affects:** TODO 10gB, docs/DESIGN.md §6 "Silk Touch and spawners"

## 2026-10-12 — Infinite buckets: one remainder hook, no `enchantable` component, grindstone allowed (Phase 10gC)
**Context:** DESIGN §6 named `BucketItem.getEmptySuccessItem`, a dispenser hook and a cauldron hook, and maybe an
`enchantable` component for the table.
**Choice:** `ItemUtilsMixin` swaps the remainder of `createFilledResult(held, player, remainder)` (3-arg) when the held
stack is an Infinity bucket, the remainder an empty bucket and the player not creative; both the hand
(`BucketItem.use`) and the cauldron (`CauldronInteraction.emptyBucket`) go through it. The dispenser's `$4`
gets the same answer through `@ModifyArg` on `consumeWithRemainder`. Every path calls `InfiniteBuckets.afterUse`
(a copy after `hurtAndBreak(1)`). No `enchantable` component (our table never calls `isEnchantable`; GameTest shows
Infinity offered). Owner: the grindstone may extract Infinity and leave the rest on a plain bucket.
**Why:** one hook on the shared call covers two paths and any mod that empties a bucket the same way; a copy is needed
because vanilla consumes the held stack before returning the remainder.
**Rejected:** a mixin on `CauldronInteraction.emptyBucket` (a static interface method); `getEmptySuccessItem` (creative
already returns the stack there, and the cauldron doesn't call it); a `@WrapOperation` on the dispenser call (its
receiver must be typed as the anonymous `$4`); a grindstone rule against extracting Infinity (owner: visible choice).
**Affects:** `bucket/InfiniteBuckets`, `mixin/{ItemUtils,BucketDispenseBehavior,FuelValues,Ingredient}Mixin`,
`EnchantRules` (bucket gate, `APPLY_ORDER`), `AnvilCalculator.inApplyOrder`

## 2026-10-12 — Sure Footing 1/3 per level on soul sand and honey, powder snow later; Anchored hits only (owner, 10i)
**Context:** 10i left Sure Footing's per-level amount and blocks to be measured. `javap` (1.21.11):
`movement_efficiency` is read only by `LivingEntity.getBlockSpeedFactor` (`lerp(efficiency, factor, 1)`), so it covers
soul sand and honey (factor 0.4) and never powder snow (`PowderSnowBlock` → `makeStuckInBlock`). Vanilla also has a
separate `explosion_knockback_resistance`.
**Choice (owner):** Sure Footing removes a third of the slowdown per level (I 60%, II 80%, III 100% speed on soul sand
and honey), as `fraction` level / 3 so III is exactly 1. Powder snow is added in 10c with a mixin. Anchored is
`knockback_resistance` only (hits), not explosions.
**Rejected:** 25% or 20% per level (agent proposals, owner picked the full removal at III); dropping powder snow;
explosion knockback resistance on Anchored (Blast Protection's job).
**Affects:** docs/DESIGN.md §8 ceilings / §9 rows, TODO 10i/10c, `sure_footing.json`, `anchored.json`

## 2026-10-12 — 10j ceilings: Piercing 127, Lunge and Wind Burst capped like Riptide, Multishot a note (owner)
**Context:** 10jA checks each ceiling candidate. Found (javap 1.21.11): `AbstractArrow` stores the pierce level as a
byte (`(byte) getPiercingCount`), so Piercing 128 wraps negative and stops piercing. Lunge (`apply_impulse` 0.458 ×
level) and Wind Burst (`explode` knockback 1.5 + 0.35/level) launch the player like Riptide. Multishot isn't really
single-level: +2 arrows per level (255 = 511 arrows a shot).
**Choice (owner):** Piercing hard 127; Lunge and Wind Burst hard caps from the Riptide no-velocity-credit bound
(TODO 4D.1), proven in `MechanicLimitsGameTest`; Multishot no ceiling with a note (arrows per level, lag), still
flat 10 at any level. Swiftness needs no cap (255 sprinting ≈ 4 b/t, far under the 10 b/t check).
**Rejected:** a soft note for the launches (a typo'd level would rubber-band players); a Multishot cap.
**Affects:** TODO 10jA, `LevelCeilings`, DESIGN §8

## 2026-10-12 — The config screen's "default" max comes from the shipped JSON, not the registry (Phase 10jB)
**Context:** each max-level field needs the value its reset goes back to. In a world the registry already holds the
override (MODIFY applied it at startup), and on the title screen there is no registry.
**Choice:** `DefaultMaxLevels` reads `/data/<ns>/enchantment/<path>.json` off the classpath (vanilla's from the game jar,
ours from the mod) and runs `MaxLevels.resolve` without an override. Another mod's enchantment falls back to the hosted
registry's max (which may include its override), else 1. `EnchantmentNumbersDraft` leaves untouched fields as the
file had them, clears an override set back to the default, and drops an entry left empty.
**Rejected:** the registry max (shows the override as the default, so "reset" would do nothing); a hard-coded vanilla
table (drifts on a version bump; the JSON can't).
**Not done:** a screenshot with a group expanded (YACL fixes `collapsed` at build); the client GameTest inspects the built
options (range, default, note) instead.
**Affects:** `enchantment/DefaultMaxLevels`, `config/EnchantmentNumbersDraft`, `client/ConfigScreen`

## 2026-10-12 — 10e curse scaling per curse, not "linear from level I" (owner)
**Context:** the 2026-10-12 entry said each new curse's effect scales linearly from DESIGN §5a's level-I value. Agent
check before 10e: linear breaks three of them (Greed −50%/level is −100% at II; Fumbling −30%/level can't mine at IV;
Weight −10%/level per piece is −200% with four pieces at V).
**Choice (owner):** Greed −10% XP per level (I 10% … V 50%). Fumbling −15% block break speed per level (I 15% … V 75%).
Weight −10% movement speed per level per armor piece, pieces add, the total capped at −50%. Fragility ×2 durability
loss at I, +0.5 per level (V ×4). Hunger +10% exhaustion per level (V +50%), armor only (not held items), pieces
add with no cap (four pieces at V = +200%). Discount unchanged (15% at I, +5%/level).
**Rejected:** Greed −50% at I then −10%/level, and Fumbling −30% then −10%/level (owner chose milder level I);
max level 1 for either; Weight multiplying per piece or adding uncapped; Fragility ×(1 + level); Hunger +50%/level,
on held items, or capped / highest-piece-only.
**Affects:** docs/DESIGN.md §5a, TODO 10e, the five curse JSONs

## 2026-10-12 — 10c order and eight design answers (owner)
**Context:** 10c holds 25 M-route enchantments; DESIGN §9 left their activation, cost and edge rules open.
**Choice (owner):** build easy first: (A) helmet/leggings hooks: Calm Gaze, Phantom Ward, Clarity, Sustenance,
Endurance, Sure Footing on powder snow; (B) boots: Lava Walking, Snow Walker; (C) the seven shields; (D) drops: Magnetic,
Ender Link, Keep on Death; (E) tools: Area Mining, Replant, Tiller; (F) Quick Strike, Last Stand; (G) Speed, Flight,
Double Jump (client input networking) last.
- Phantom Ward: insomnia spawning unchanged; phantoms ignore the wearer (still attack others); a phantom the wearer
  hurts targets them until vanilla drops the target.
- Endurance: sprint distance and sprint-jumps (0.2) both reduced; Sustenance and the Hunger curse multiply on top.
- Clarity: any wearer (players and mobs). I/II shorten Blindness, Darkness, Nausea at application (infinite stays
  infinite); III blocks all three, infinite included; each Warden Darkness pulse is shortened as it lands.
- Flight: while gliding, holding jump gives steady forward thrust growing per level (V ≈ one rocket); 1 elytra
  durability per second of thrust; Speed scales it; takeoff is vanilla.
- Keep on Death: free, forever: back in its slot on respawn, enchantment kept; which items take it stays configurable.
- Quick Strike: the second hit repeats the damage only, ignoring i-frames: no Fire Aspect/Knockback/Vampirism/
  on-hit effects/Critical Strike roll/durability, and it never chains.
- Last Stand: on in PvP by default (config toggle turns it off); a Totem of Undying fires first and doesn't use the
  cooldown.
**Rejected:** riskiest-first or shields-first order; Phantom Ward stopping insomnia spawns; Endurance on sprint
distance only; Clarity players-only or capping infinite effects at I/II; Flight as a cooldown rocket or creative
hover; Keep on Death costing durability or one use; Quick Strike at half damage or as a full second hit; Last Stand
off in PvP.
**Affects:** docs/DESIGN.md §9, TODO 10c

## 2026-10-12 — Lava Walking: fall damage on landing, normal speed on the surface (owner, 10cB)
**Context:** DESIGN §9 said falling onto lava "lands on the surface" without saying what the landing or walking costs.
**Choice (owner):** the surface is a floor: landing on it from a height takes normal fall damage (Feather Falling
applies), then the usual lava burn; walking on it is normal ground speed.
**Rejected:** lava cushioning a fall like water; a soul-sand-like slowdown on the surface.
**Affects:** docs/DESIGN.md §9, TODO 10cB

## 2026-10-12 — Shield Bash, Deflect, Bulwark, Magnetic/Ender Link scope (owner, for 10cC/10cD)
**Context:** DESIGN §9 left Shield Bash's stun and cooldown, Deflect's ownership, Bulwark's cooldown and what the
drop enchantments pull unspecified.
**Choice (owner):**
- Shield Bash: attacking with the shield raised knocks back like Knockback I per level and gives Slowness IV for 0.5 s
  per level; the shield then goes on a 3 s item cooldown.
- Deflect: the projectile flies back at its shooter at the same speed and damage, owned by the shield holder (kill
  credit, arrows not picked up); a projectile reflects at most once, so no ping-pong.
- Bulwark: absorption lasts 5 s; granted again only 10 s after the last grant.
- Magnetic / Ender Link: drops from blocks broken and mobs killed with the item; Magnetic also sends XP straight to the
  player (Mending applies); Ender Link moves items only.
**Rejected:** knockback-only or a full stun (Slowness + Weakness 255); the shooter keeping ownership; a 5 s or 30 s
Bulwark cooldown; blocks only; no XP.
**Affects:** docs/DESIGN.md §9, TODO 10cC, 10cD

## 2026-10-12 — Lava Walking stands on flowing lava too (agent, 10cB)
**Context:** vanilla's `LiquidBlock.getCollisionShape` gives the strider's stable shape only on a source (`LEVEL` 0).
Lava lakes are ringed by flows, so a source-only walker drops in at every edge.
**Choice:** for a lava walker, the `LEVEL` read in `getCollisionShape` returns 0; the rest of the check (above the shape,
not under more lava, `canStandOnFluid`) stays vanilla's. On a thin flow the feet can rest above the lava, so that spot
may not burn.
**Rejected:** source-only, as striders (feels broken at edges); a new collision shape following the flow's height (more
code for a rare case, and it would differ from the strider's half block).
**Affects:** mixin/LiquidBlockMixin, effect/Footing, BootsGameTest

## 2026-10-12 — Shield enchantments: book-only, Bulwark as a hidden mob effect, transient timers (agent, 10cCA)
**Context:** 10cC needed choices DESIGN §9 left open.
**Choice:**
- Shields stay out of the enchanting table: vanilla shields have no `enchantable` component, and adding one would
  change vanilla shields everywhere. Shield enchantments come on books + anvil, as Unbreaking/Mending already do.
  `enchanting2:enchantable/shield` = `minecraft:shield` + optional `#c:tools/shield`.
- Bulwark is a registered, hidden mob effect (`enchanting2:bulwark`): vanilla Absorption at 1 health per amplifier
  step, since vanilla's 4 per step can't give 1 heart per level. The effect system then handles expiry, milk, save and
  load, and dropping the hearts when it ends.
- Riposte's window and Bulwark's cooldown are `@Unique` fields on `LivingEntity`, not saved (2 s and 10 s).
- Durations live in the components (`block_absorption.duration/cooldown`, `riposte.window`), so a datapack can retune
  them without a config option.
- Spiked uses the thorns damage type and ignores thorns-typed hits, so two Spiked shields can't trade damage forever.
**Rejected:** an `enchantable` component on shields; vanilla Absorption (2 hearts per level); a transient
max-absorption modifier with our own tick-based expiry (re-implements the effect system); saved timers.
**Affects:** shield/*, effect/BlockAbsorption, effect/RiposteBonus, mixin/LivingEntityMixin, mixin/BlocksAttacksMixin

## 2026-10-12 — Deflect: vanilla's pre-hit deflection, a tag of projectiles, no tridents (agent, 10cCB)
**Context:** the owner set Deflect's ownership and once-per-projectile rule; the hook, which projectiles, and what a
deflection costs were open.
**Choice:**
- Hook `Entity.deflection(projectile)`, which `Projectile.hitTargetOrDeflectSelf` asks before any hit (the breeze
  path), so the projectile never hits, is never discarded, and every projectile class works the same. Server only (the
  roll is random); the client gets the new velocity from the server.
- Which projectiles: entity type tag `enchanting2:deflectable` = arrow, spectral arrow, small fireball, fireball,
  wither skull, llama spit. Not tridents (the blocker would own a thrower's Loyalty trident, and it would return to
  them), not shulker bullets (they home back to their target), nothing harmless (pearls would teleport the blocker,
  potions, eggs, snowballs, fishing hooks, wind charges). Datapacks can add.
- One roll per projectile, made only when the shield would block it (in front, not piercing, not bypassing).
- A deflection counts as a block for the shield (its sound, 1 durability on a player's shield) but is not a blocked
  hit, so Bulwark, Riposte and Spiked don't fire on it.
- Aim: at the shooter's middle with the arrow's drop over that distance added; no shooter → straight back.
**Rejected:** hooking each projectile class's hit (`AbstractArrow.onHitEntity` reverses at 20% speed; fireballs are
discarded on hit); tridents in the default tag; a free deflection (no durability).
**Affects:** shield/ShieldDeflect, mixin/EntityMixin, mixin/ProjectileMixin, data/enchanting2/tags/entity_type/deflectable.json

## 2026-10-12 — Steady Stance keeps the sprint rule; Shield Bash is the normal attack plus the bash (agent, 10cCC)
**Context:** "no movement slowdown while blocking" and "attacking while blocking knocks back and stuns" left two
readings each.
**Choice:**
- Steady Stance only lifts the use slowdown (`use_effects.speed_multiplier` → 1 for that shield); blocking still stops
  sprinting, so it's walking at full speed with the shield up, not sprint-blocking.
- Shield Bash lets the attack click through while the shield is up (crosshair on an entity only, so blocking never
  mines) and the hit is the ordinary attack with whatever is in the main hand; the bash adds Knockback I per level,
  Slowness IV 0.5 s per level and the 3 s shield cooldown once the hit landed. The cooldown lowers the shield the way
  an axe's disable does (cooldown + `stopUsingItem`).
**Rejected:** sprinting while blocking; a bash that replaces the attack (no weapon damage); bashing on a miss.
**Affects:** shield/ShieldBash, mixin/PlayerMixin, mixin/client/LocalPlayerMixin, mixin/client/MinecraftMixin

## 2026-10-12 — Magnetic and Ender Link share a tool; one redirect for both (agent, 10cDA)
**Context:** DESIGN §9 marked Ender Link "conflicts with Magnetic", but DESIGN §4 (owner) says conflicts default to
allow together and none of ours declares an `exclusive_set`; `ModEnchantmentDataTest` guards that and failed a first
try at an exclusive set.
**Choice:** no exclusive set; with both on one tool the ender chest fills first, then the inventory, then the ground.
Both run through `drops/DropRedirect`, a server-thread context set around `Block.dropResources` (the tool's block
drops, after Fortune and Smelting) and `LivingEntity.dropAllDeathLoot` (loot, gear, XP of a mob the player's weapon
killed; a bow counts for its arrow), read where items (`Block.popResource`, `Entity.spawnAtLocation`) and orbs
(`ExperienceOrb.award`) spawn.
**Rejected:** an exclusive set (against §4 and its test); `LootTableEvents.MODIFY_DROPS` (misses a mob's held and
worn gear and XP); giving XP points directly (Mending only works through orbs).
**Affects:** drops/DropRedirect, mixin/BlockMixin, mixin/EntityMixin, mixin/ExperienceOrbMixin, mixin/LivingEntityMixin

## 2026-10-12 — Keep on Death keeps items in the dead player's inventory; items by tag (agent, 10cDB)
**Context:** owner: free, forever, back in its slot, "which items take it stays configurable".
**Choice:** `Inventory.dropAll` leaves kept stacks in their slots, so they're saved with a player who logs out on the
death screen; Fabric `COPY_FROM` moves them to the same slots on respawn, skipping stacks keepInventory or the end
portal already copied, and sending one whose slot something else took anywhere else (then the ground), never leaving
it on the discarded old player. Items: `supported_items` = tag `enchanting2:enchantable/keep_on_death`, default
`#minecraft:enchantable/durability`, so a datapack configures it (owner asked whether a screen list is wanted).
**Rejected:** a side stash on the old player (lost if the player logs out while dead); a config-screen item list
without the owner asking (big UI for a rare setting); dropping a kept item when its slot is taken.
**Affects:** drops/KeepOnDeath, mixin/InventoryMixin

## 2026-10-13 — Last Stand: Absorption I for 5 s, a 2-minute floor, any wearer (agent, 10cFB)
**Context:** the owner set the cooldowns (10/8/6 min), the totem order, the HUD and the PvP toggle; "brief
absorption" and raised max levels were open.
**Choice:** Absorption I (2 hearts) for 5 s, as data in the component; the cooldown formula (12000 − 2400/level) never
goes below level V's 2400 ticks, with a soft ceiling at 5, because VI reaches 0 and the wearer could never die; void and
/kill (`#bypasses_invulnerability`) go through it, like a totem; any living entity can wear it, like a totem; the ready
tick is a persistent attachment copied through death (dying another way doesn't reset it) and synced to its own player
for the HUD; an anvil-land "clang" plays on a save.
**Rejected:** Absorption II like the totem (owner said brief); letting the cooldown reach 0; a players-only rule; a
transient cooldown (a relog would reset it).
**Affects:** combat/LastStand, combat/LastStandClock, effect/LastStandEffect, config/LastStandSection, client/LastStandHud

## 2026-10-13 — Flight is a share of a rocket's step per tick, not a constant force (agent, 10cGB; owner confirmed 2026-10-13)
**Context:** owner: "steady forward thrust growing per level, V ≈ one rocket". A scratch simulation of vanilla's glide
(`updateFallFlyingMovement`) and rocket step (pull toward 1.5 × look by half the gap, + 0.1 × look) settled numbers:
level flight: glide alone 1.52, rocket 1.67 blocks/tick; climbing at 20°: glide alone 0.38, rocket 1.66.
**Choice:** each tick with jump held, Flight applies 0.2 × level of the rocket's own step (V = exactly a rocket; Speed
scales the target like a rocket's; capped at X, where share 2 reaches the target in one tick). Results: V 1.67 level,
1.66 climbing; I reaches nearly the same cruise (1.62 level, 1.50 climbing) but more slowly.
**Rejected:** a constant push along the look (V: 3.19 in level flight, only 1.04 climbing at 10°: neither is a rocket);
a level-scaled target with the full pull (brakes a coasting glider: I made you slower than not thrusting); an
add-only push along the look with a level-scaled target (good progression, but V climbs 2.5× harder than a rocket).
If the owner wants levels to differ more in cruise, that last one plus a perpendicular damping is the next candidate.
**Affects:** elytra/Flight, mixin/LivingEntityMixin

## 2026-10-13 — Double Jump has no packet (agent, 10cGC)
**Context:** DESIGN §9 listed "client input packet; server validation" as Double Jump's concerns, and the first build
sent a `DoubleJumpPayload` whose server handler re-checked the rules and reset the fall distance.
**Choice:** client-only: the client's fresh jump press in the air (where vanilla tries to start a glide) jumps, once
per air time; no packet. A mutation that dropped the server reset failed nothing: vanilla's
`Entity.checkFallDistanceAccumulation` caps the fall distance at 1 every tick the player isn't falling faster than 0.5
blocks a tick, on both sides, so any upward jump already breaks the fall. Movement is the client's, so the server could
never refuse the jump itself, only log it.
**Rejected:** the payload (dead weight: a channel, a handler and a protocol question for no behavior).
**Affects:** movement/DoubleJump, mixin/client/LocalPlayerMixin, mixin/PlayerMixin

## 2026-10-13 — Three open 10c questions closed (owner)
**Context:** state.md carried owner questions left open by 10cDA, 10cDB and 10cGB.
**Choice (owner):** (1) Magnetic and Ender Link stay allowed together on one tool (ender chest first, then the
inventory); no exclusive set. (2) Flight keeps the share-of-a-rocket model (every level cruises near a rocket's speed;
levels differ in how fast they get there). (3) Keep on Death's items stay an item tag only
(`enchanting2:enchantable/keep_on_death`); no config-screen list.
**Why:** (1) DESIGN §4: our enchantments declare no exclusive sets, and the combination is useful. (2) The alternatives
in the Flight entry above either brake a coasting glider or make V climb 2.5x harder than a rocket. (3) A datapack tag
is already configurable; a list would add a config surface for a rare edit.
**Rejected:** an exclusive Magnetic/Ender Link set (DESIGN §9's original line); a level-scaled Flight target; an item
list in common.json5 and the config screen.
**Affects:** docs/DESIGN.md §9 (Ender Link row), elytra/Flight, drops/KeepOnDeath

## 2026-10-13 — 10d rulings and the table screen rework (owner)
**Context:** the 10d sheet (TODO "10d") proposed five retunes; state.md also asked about the table panel's scroll.
**Choice (owner):** R1 Attack Speed +5%/level (V = +25%). R2 Vampirism 2%/level (V = 10%), reversing the owner's own
2026-10-09 4%. R3 Reach entity range +0.25/level (III = 3.75), block range +0.5/level kept. R4 treasure (Mending's
treatment) for Health and Last Stand only; Keep on Death stays non-treasure. R5 Flight 6/level like Speed. Every other
§9 enchantment INCLUDE at its shipped numbers; DEFER list unchanged; REMOVE none. Table screen: the scrolling list
moves out of the side panel into the window's own offer area, with a scroll bar and hover descriptions; the Enchant
button moves out of the list's box into the rest of the window; the picked row stays in view. Owner reviews it in a
running client before it's final.
**Why:** owner: the side panel "feels janky". Retunes: Attack Speed V outdid Sharpness V, Vampirism V out-healed
Regeneration II six times over, Reach III gave 4.5-block melee, Health and Last Stand rolled onto mob spawn gear,
Flight V cost 5 while Speed V cost 30.
**Rejected:** treasure for Keep on Death (owner); keeping the side panel.
**Affects:** data/enchanting2/enchantment/{attack_speed,vampirism,reach}.json, enchantment tags, cost/PricingTable,
client table screen (EnchantmentScreenMixin, TablePanelView)

## 2026-10-13 — Table wider with one-line rows; mob health multiplier; cost units (owner)
**Context:** owner, after the first in-window table (10l): "make the enchant screen wider", arrows beside the name
instead of under it; a config mob health multiplier; the anvil's bare "40000" read as confusing (that screenshot is the
40,000-level rename stress test, but every panel amount is a bare number).
**Choice (owner):** (1) Window 72 px wider (176 → 248), list 180 wide, one 12 px line per entry: name, `< V >`, cost;
inventory grid centred. (2) `mobs.health_multiplier` 1–100 (whole x), default 1, slider in the config screen; every
non-player living entity, bosses included; applied to mobs already in the world too (on load and on config reload),
current health scaled with it. (3) Units on every cost number in the anvil, grindstone and table panels and rows
("5 levels"); vanilla's anvil label "Enchantment Cost: 5" stays as is.
**Why:** owner preference after seeing it; one-line rows make the stepper read as part of the row.
**Rejected:** +40 px, a resolution-dependent width; hostile-only or boss-excluding scope; new-spawns-only.
**Affects:** table/TableWindow, client/TableList, EnchantmentScreenMixin, config (common.json5 + ConfigScreen),
mob health hook, PanelText amounts in SidePanel/TableList

## 2026-10-13 — Mob health multiplier: permanent modifier, ceiling raised (agent + owner)
**Context:** TODO 10m planned a transient `max_health` modifier. Vanilla's `readAdditionalSaveData` loads attributes,
then sets the saved health clamped to the live max; a transient modifier isn't there yet, so a 3x zombie at 30/60
loads at 20/20, and keeping its share on re-apply would heal it to 60/60 every chunk load. Vanilla also caps
`max_health` at 1024, so 100x stopped at 1024.
**Choice:** a permanent (saved) modifier `enchanting2:health_multiplier`, re-checked on `ENTITY_LOAD` (changed only if
its amount differs) and on every config reload; health keeps its share on a change. Owner: raise the ceiling to
1,000,000 through `AttributesMixin` (`max_health` only, matched by description key since other attributes share 1024).
Applies to `Mob`, not every `LivingEntity` (armor stands have no use for it).
**Why:** the save/load GameTest fails with a transient modifier (mutation run: 60/60 instead of 30/60).
**Rejected:** transient + the 10a saved-health restore (that restore only raises health, so a share-scaled heal on
load still wins); keeping vanilla's cap (owner).
**Affects:** mobs/MobHealth, mixin/AttributesMixin, config/MobsSection, ServerConfigs.reload, DESIGN §2a + §6

## 2026-10-13 — Anvil and grindstone in the window: full-height right column (owner)
**Context:** the owner accepted the 10l v2 table window ("looks good, finalize") and had left the anvil and
grindstone side panels to be decided after it (they "feel janky", decisions.md "10d rulings").
**Choice (owner):** both move into the window. Mocked first as a top-half box with the inventory centred; that leaves
~50 px for the anvil's "Enchantment Cost: 9" (~95 px) beside the moved "Inventory" label, so the owner picked the
variant: vanilla's window untouched on the left, the window 120 px wider (296 x 166), and one box running the full
height of the new right column (the villager trade list's shape, mirrored). Anvil: read-only breakdown, 10 px wrapped
lines, scroll bar. Grindstone: table-style picker rows (beige / pink picked / dark disabled), 12 px, scroll bar, hover
tooltips with descriptions. The anvil's show/hide tab, `client.json5` `anvil.show_preview_panel` and its config-screen
toggle are removed; an old file's key is reported "no longer used" (not "typo?") and otherwise ignored.
Agent detail: the grindstone's price statuses (cost, free, insufficient, too expensive) draw where the anvil's cost
label is, in vanilla's green/red; its long instructions ("Pick an action above.") stay as the list's last line, since
they can be ~400 px wide and the label spot has ~110.
**Why:** one look across all three screens; nothing drawn outside the window, so recipe viewers (which read the
window's bounds) lay out around it — the reason the 6D panel avoided the right side no longer applies.
**Rejected:** top-half box with centred inventory (label squeeze); taller windows (240 px minimum GUI height leaves
~60 px); keeping the tab as a collapse toggle (a window that changes size on click).
**Affects:** AnvilScreenMixin, GrindstoneScreenMixin, new ItemCombinerScreenMixin, screen/ColumnWindow, ScrollModel,
ClientConfig, ConfigScreen; deletes SidePanel, SidePanelLayout, AnvilPanelTab

## 2026-10-13 — Config docs quote the default files verbatim, under a test
**Context:** 11.1 needed config documentation for players and modpack makers; the keys already carry comments in the
files the mod writes.
**Choice:** `docs/CONFIG.md` has prose (where files live, who edits, `/reload`, `.bak`, restart for max levels), a
short section table, and each file's defaults pasted verbatim. JUnit `ConfigDocsTest` renders `codec.defaults()` with
`ConfigFiles.render` and compares it with the block after `<!-- defaults: <file> -->`.
**Why:** one source of truth (the codecs' comments), and the doc can't go stale silently: the failure message is
the text to paste.
**Rejected:** a hand-written key table for every key (drifts with each new option); generating the doc in Gradle
(another build step for three blocks).
**Affects:** docs/CONFIG.md, src/test/java/com/wulfnet/enchanting2/config/ConfigDocsTest.java

## 2026-10-13 — 26.x: every version, 1.21.11 kept in step (Phase 12 plan)
**Context:** 11.3 asked for a 26.x port plan. Stable lines today: 26.1.2, 26.2, 26.3 (26.4 in snapshots).
**Choice (owner):** support all 26.x versions; keep 1.21.11 in step (every feature on every version); start after the
owner's runClient checks and the v1.0.0 tag push. Agent's plan: one tree with Stonecutter + loom-back-compat,
proven first by a 1.21.11-only spike (12A).
**Why:** in-step support across four or more versions makes per-version branches a cherry-pick tax on every change.
**Rejected:** branch per version (fallback only if 12A fails, with its cost shown to the owner); 26.x only; one
jump straight to 26.3 (owner chose all versions, so each 26.x line is its own node anyway).
**Affects:** build.gradle, settings.gradle, gradle.properties, every version-sensitive mixin


## 2026-10-13 — Table window: armor + offhand slots, picks start at I, Ctrl for row descriptions (owner, Phase 11b)
**Context:** owner: no armor slots in the table window; every pick started at the max level; descriptions showed by
default and made long tooltips unreadable.
**Choice (owner):** armor and offhand slots in the window; a row click picks level I (one above the item's own level
for an upgrade), reversing the agent's 2026-10-07 "highest sellable level"; the row tooltip's description needs Ctrl,
like item tooltips, and shows the "hold Ctrl" hint otherwise. **(agent):** real menu slots, indices 38–42 after the
inventory, added on the server always and on the client only when `TableBridge.clientServerIsOurs()`; a
`TableEquipmentSlot` mirrors package-private `ArmorSlot` instead of an access widener (the project has none, and
one class is less than a new build-wide mechanism). `NetworkProtocol.VERSION` 4 → 5 although no payload changed: the
menu's slot count is a client/server contract, and a 1.0.0 client's menu would be five slots short. Shift-click keeps
vanilla's rule (anything not on the table goes onto it), so worn armor shift-clicks straight onto the table.
**Why:** the owner wants to enchant worn gear without leaving the table; level I matches how players step up.
**Rejected:** slots drawn client-side only (can't be clicked); an access widener for `ArmorSlot`; shift-click from the
table back into the armor slot (vanilla's index ranges stay as they are).
**Affects:** table/TableEquipmentSlot, TableWindow, mixin/EnchantmentMenuMixin, EnchantmentScreenMixin,
TablePanelView.click, client/TableList, network/NetworkProtocol

## 2026-10-13 — Tall tooltips: compact Ctrl layout, then scrolling (owner, Phase 11b D–E)
**Context:** owner: with Ctrl, a tool with many enchantments got a tooltip taller than the screen. Vanilla's
`DefaultTooltipPositioner` moves a tall tooltip up until its bottom fits (`y = screenH - h - 3`, no floor), so the
item's name and first enchantments are what get cut.
**Choice (owner: "compact + scroll"):** with Ctrl, if descriptions under their names won't fit, every description goes
on its name's line ("Name: description", lang key `enchanting2.tooltip.inline_description`), wrapped at half the
screen less 24 (200–320 px). If the tooltip still doesn't fit (with or without Ctrl, any item), it starts at y 4 and
the wheel scrolls it 30 px a notch while it's shown; our hook adds "Scroll to see the rest" under the item's name for
enchanted items. **(agent):** pure `tooltip/TooltipFit` + `TooltipScroll`; `GuiGraphicsMixin` wraps only the
positioner's result (a tooltip that fits is placed exactly as vanilla places it); `MouseHandlerMixin` wraps
`Screen.mouseScrolled` inside `onScroll`, so it is ahead of every screen (creative tab grid included) and uses
vanilla's sensitivity. A new tooltip (different width/height) or one not drawn for 200 ms starts at the top again.
**Why:** compact keeps every description readable at once where it can; scrolling covers what no layout can fit.
**Rejected:** scroll only (owner); compact only (a 90-enchantment item still can't fit); multi-column tooltips (needs a
custom tooltip component and a mixin into tooltip building, for little gain over scrolling); a config toggle (it only
changes tooltips that were broken).
**Affects:** tooltip/TooltipFit, tooltip/TooltipScroll, client/TallTooltips, client/EnchantmentDescriptions,
mixin/client/GuiGraphicsMixin, mixin/client/MouseHandlerMixin, lang en_us

## 2026-10-13 — Ctrl descriptions are always "Name: description" (owner, Phase 11c A)
**Context:** owner: inventory Ctrl tooltips "still too large". 11b only went compact when the under-the-name layout
overflowed the screen, so a normal item still got two or three lines per enchantment.
**Choice (owner, asked: "always condensed"):** Ctrl always puts each description on its enchantment's line, wrapped at
`TooltipFit.compactWidth` (200–320 px). `TooltipFit.Layout`/`choose` removed. Scrolling and the scroll line unchanged.
**Rejected (offered to the owner):** clipping each description to one line with "…" (loses text); Ctrl = full,
Ctrl+Shift = condensed (a second modifier for the layout nobody wanted).
**Affects:** client/EnchantmentDescriptions, tooltip/TooltipFit, TooltipClientGameTest

## 2026-10-13 — A picked row's price includes its own surcharge (owner, Phase 11c B)
**Context:** owner: Mending's table price should show its true cost with the other enchantments. The row showed only
its ENCHANT line (2); the `other_levels_surcharge` was a separate footer line.
**Choice (agent):** a picked row's amount = its ENCHANT + its SURCHARGE line, and its tooltip says "Includes N for the
M other enchantment levels". The footer keeps a surcharge line only when the enchantment has no ENCHANT line, i.e. it
is already on the item (Mending on the item, Efficiency added), so every charged level is still shown somewhere.
Unpicked rows still show no price (pricing every unpicked row would need the server to price N what-ifs).
**Rejected:** dropping footer surcharge lines entirely (hides levels in the already-on-the-item case); changing the
anvil's breakdown (owner asked about the enchant screen).
**Affects:** table/TablePanelView, client/TableList, lang `enchanting2.table.tooltip.surcharge`

## 2026-10-13 — Anvils unbreakable by default (owner, Phase 11c C)
**Context:** owner: anvils unbreakable by default, an option for vanilla durability, a multiplier to make them last.
**Choice:** `anvil.vanilla_durability` (false) and `anvil.durability_multiplier` (int 1–100, like
`mobs.health_multiplier`). (agent) Both vanilla damage sites (use: `AnvilMenu` `onTake` lambda, 12%; landing:
`FallingBlockEntity.causeFallDamage`, 0.05 + 0.05 × distance) are wrapped at their `AnvilBlock.damage` call: off =
keep the state; on = a second roll keeps 1/multiplier of vanilla's chips (no extra roll at 1, so vanilla is exact).
"Unbreakable" covers falls too: a dropped anvil that chipped would contradict the setting's name.
**Rejected:** an `@Inject` into static `AnvilBlock.damage` (no level, so no server config, and it would change every
other mod's call); a double multiplier (no double reader; whole multiples match mobs); multiplier below 1 (owner asked
only to increase durability, and the second-roll scheme can't raise the chance).
**Affects:** config/AnvilSection, anvil/AnvilWear, mixin/AnvilMenuMixin, mixin/FallingBlockEntityMixin, ConfigScreen

## 2026-10-13 — Config screen tabs in rows: subclass YACLScreen, not mixins into YACL (owner, Phase 11d B)
**Context:** owner: the config screen's tab row scrolls behind an arrow; wants two rows of tabs. YACL 3.8.1's
`ScrollableNavigationBar` lays every category in one 24-tall row, and `YACLScreen.init` hard-codes the tab area at
y 23 and hands it to each `CategoryTab`'s constructor (its right pane and description box keep it), so the area
must be right before the tabs are built.
**Choice (agent):** `client/TabbedConfigScreen extends YACLScreen`, whose `init` is YACL's own with the tab area
below the rows, and `RowsBar extends ScrollableNavigationBar` (it must be one: YACL's code reads that field type).
`RowsBar.arrangeElements` never calls YACL's, so YACL's scroll range stays 0 (no arrows, `ensureVisible` is a no-op).
Rows come from MC-free `screen/TabRows`: fewest rows that fit, evened out by width, each stretched; a narrow window
gets a third row rather than scrolling. `ConfigScreen.create` builds it directly instead of `generateScreen`.
**Rejected:** mixins into YACL's `YACLScreen`/`ScrollableNavigationBar` (private internals, refmap-dependent, more
to redo for the Phase 12 ports); exactly two rows always (a narrow window would overflow again); shorter category
names (owner asked for rows).
**Port note:** `init` copies YACL 3.8.1's; when YACL changes `YACLScreen.init`, compare and copy again.
**Affects:** client/TabbedConfigScreen, screen/TabRows, client/ConfigScreen

## 2026-10-14 — Going public: new repo with scrubbed history, MIT, tag-driven releases (owner, Phase 11e)
**Context:** owner wants the repo public with CI. Audit found `.env` (real account passwords) and both MCP configs
(GitHub PAT, Context7 key) tracked since the initial commit — the earlier "tracked secrets are the owner's call"
posture (gotchas.md, 2026-08) ends here.
**Choice (owner):** scrub history with `git filter-repo` in a scratch clone, rename the old GitHub repo to
`enchanting-2-archive` (private) and push the scrubbed history to a new `enchanting-2`. MIT license. A release is
cut by pushing tag `v<mod_version>`; every push/PR only builds and tests. First public version is 1.0.0 from HEAD
(the old local `v1.0.0` was never pushed). The MCP config becomes untracked `.mcp.json` + tracked `.mcp.example.json`.
**Why:** force-pushing over the same repo leaves the old commits reachable by SHA on a public repo until GitHub
Support purges them (short-SHA brute force is a known attack); a new repo never had them. Tag-driven over
mcMMO-SP's version-bump-on-push model: one branch and one Minecraft line here, and the owner decides when to ship.
**Rejected:** force-push to the same repo (above); squash to one commit (loses 105 commits of reasoning trail);
mcMMO-SP's release-on-push (built for its many-band branches); GPL/LGPL/ARR (owner chose MIT).
**Still required of the owner:** rotate the GitHub PAT, the Context7 key and every password in the old `.env` —
they were on GitHub, and the archive repo still holds them.
**Affects:** .gitignore, .mcp.example.json, LICENSE, .github/workflows/*, wiki/, README.md, CHANGELOG.md

## 2026-10-14 — Speed and Flight read Elytra Slot's elytra by reflection (agent, Phase 11f)
**Context:** owner: a fully enchanted elytra never thrusts on jump. Their Prism instance runs Elytra Slot
(`elytraslot-fabric-1.21.11-1.0.0.jar`, warwa, All Rights Reserved, not on Maven): the elytra lives in a synced
Fabric attachment, so `runIterationOnEquipment` never sees it, and the mod cancels vanilla's `updateFallFlying`.
**Choice:** `compat/ElytraSlot` resolves the mod's public static `ElytraSlotUtil.getAccessoryElytra(Player)` /
`setAccessoryElytra(Player, ItemStack)` once with `MethodHandles.publicLookup()`; `elytra/WornElytra` adds that
stack's chest-slot enchantments to Speed's and Flight's sums, and Flight's wear charges it and writes it back (sync).
Flight's durability hook moved from `updateFallFlying` TAIL to a `@WrapOperation` on its call in `aiStep`.
Fails closed: class absent → empty slot; wrong shape or a throw → one warning, empty slot.
**Why:** its helper class is the mod's own cross-platform API and survives an attachment rename; reflection avoids a
compile dependency on a jar we can't fetch from Maven or redistribute. Wrapping the call site survives any mod that
cancels the method body.
**Rejected:** reading the attachment `elytraslot:elytra` directly (Fabric has no public lookup by ID); a compile
dependency (no Maven, ARR); applying the slot to every worn effect (Keep on Death, curses — elytraslot does its own
drops and Mending; widen only when asked); telling the owner to wear the elytra in the chest slot.
**Test seam:** the gametest source set has a stub `com.warwa.elytraslot.ElytraSlotUtil` plus `ElytraSlotStubMixin`
(canGlide + cancelled `updateFallFlying`, copied from the mod's bytecode), so the real reflective path runs.
**Affects:** compat/ElytraSlot, elytra/WornElytra, elytra/Flight, elytra/GlideSpeed, mixin/LivingEntityMixin

## 2026-10-14 — Version bands, mcMMO-style: one jar per band, runtime adapters inside a band (agent, Phase 12 re-plan)
**Context:** owner: "look at how mcMMO builds its bands for different Minecraft versions, we are going to use a similar
thing ... keeping as many versions in one jar as feasible". The 2026-10-13 plan had one jar per 26.x line.
**Choice:** mcMMO (2.3.003) compiles one jar against a band's floor (Spigot 1.20.5) that runs on 1.20.5 → 26.x,
handles differences at runtime (`MinecraftGameVersion`, `ReflectionUtils`, `adapter/*`), and keeps older bands as
frozen releases. Ours: band A = 1.21.11, band B = all 26.x in one jar built on 26.1.2, proven per runtime by a
GameTest matrix; adapters + `IMixinConfigPlugin`-gated mixins for differences inside B; Stonecutter only for the
A/B split. Split rule fixed in advance (TODO.md Phase 12): >~6 adapters/gated mixins, reflection on a per-tick path,
or a subclassed class that differs → that line starts a new band.
**Why:** fewer jars and fewer dependency sets for lines that mostly share code; the matrix makes "supported" a test
result instead of a guess. Unlike Bukkit we sit on vanilla internals (55 mixin classes), so the gate (12C) comes
before any adapter is written.
**Rejected:** one jar for 1.21.11 + 26.x (intermediary vs Mojang names in the bytecode, Java 21 vs 25; jar-in-jar of
both relies on unverified loader behaviour); one jar per 26.x line (kept as the fallback if 12C says so); an
open-ended `minecraft` range (a mixin mod crashes at startup on an untested version).
**Open (owner):** mcMMO freezes old bands. Does 1.21.11 keep getting every feature (2026-10-13 ruling, kept for now),
or freeze at 1.0.x once band B ships (no Stonecutter needed then)?
**Affects:** build.gradle, settings.gradle, gradle.properties, release.yml, fabric.mod.json, future compat/McVersion

## 2026-10-14 — 12A build layout: Stonecutter 0.9.8 + loom-back-compat 0.4.3, Groovy, per-node properties (agent)
**Context:** Phase 12A spike. Owner 2026-10-14: "1.21.11 is its own band, and 26.x versions will have however few
bands as possible" (confirms the band layout; 1.21.11 stays in step, the 2026-10-13 ruling).
**Choice:** Stonecutter 0.9.8 with one node, `versions/1.21.11/`; `vcsVersion` 1.21.11. `build.gradle` stays the
single central script, applied to each node; `stonecutter.gradle` is the Groovy controller. loom-back-compat picks
`fabric-loom-remap` / `fabric-loom` and aliases `mod*` configurations; `loomx.applyMojangMappings()` replaces the
mappings line; Loom's version is `loomx.loom_version` in the root gradle.properties (kept at 1.18.2 for the spike).
Per-node keys (minecraft_version, fabric_api_version, java_version, modmenu_version, yacl_version, run_root) live in
`versions/<node>/gradle.properties`; shared ones stay in the root file. Paths that must not move with the node go
through `rootProject`: run dirs (`run_root`; 1.21.11 keeps `run/client`, `run/server`), the dev-server eula, and the
`test` task's `workingDir` and doc inputs. CI installs every node's JDK; release.yml releases exactly one node and
refuses more until 12F. Run tasks are always node-qualified (`:1.21.11:runServer`; the smoke script takes `NODE=`).
**Why:** CullLeaves (TeamMidnightDust) ships 1.21.11 + 26.1–26.3 from the same two plugins, so the wiring is proven
elsewhere. Groovy keeps the diff to a layout change; `dev.kikugie.stonecutter.hard_mode=true` silences Stonecutter's
"prefer Kotlin" banner. Result: the node's jar equals the v1.0.1 release jar entry for entry (only four
`Stonecutter-*` manifest attributes added), Jankson still nested, 423 JUnit + 336 server + client GameTests green.
**Rejected:** converting the build to Kotlin DSL (Stonecutter's recommendation; a rewrite of a working 160-line script
during a layout spike); the stonecutter.properties YAML/TOML (Kotlin-only accessors in the examples, plain
gradle.properties per node works in Groovy); bumping Loom to 1.18.4 in 12A (two variables at once); per-node run dirs
for 1.21.11 too (would orphan the owner's existing `run/client` saves).
**Affects:** settings.gradle, stonecutter.gradle, build.gradle, gradle.properties, versions/1.21.11/gradle.properties,
.github/workflows/ci.yml, .github/workflows/release.yml, scripts/dev-server-smoke.sh, README.md,
wiki/Building-from-Source.md
