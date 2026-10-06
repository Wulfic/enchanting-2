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
