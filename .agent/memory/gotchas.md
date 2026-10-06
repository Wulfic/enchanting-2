# Gotchas

> **Append-only.** Traps, dead ends, and environment quirks that cost real time.
>
> The test for whether something belongs here: **would a competent developer hit this
> again in six months?** If yes, write it down. If it's obvious from reading the code,
> it does not belong here.
>
> Log a failed approach as carefully as a successful one. "We tried X, it doesn't work
> because Y" is what stops the next session from burning an hour on X.

## Entry template

```markdown
## YYYY-MM-DD — Short title
**Symptom:** what you actually saw (error text, wrong output, hang)
**Cause:** the real root cause
**Fix:** what worked
**Dead ends:** what looked right but wasn't, and why
**Affects:** src/path/to/file.ts
```

---

## 2026-08-12 — `.gitignore` does not cover `.env`
**Symptom:** `.env` is tracked in git despite a large "Secrets / config" section in
`.gitignore`.
**Cause:** That section only lists `credentials*`, `secrets*`, `*.pem`, `*.key`, `*.crt`.
There is no `.env` pattern, and a file already tracked stays tracked even after a matching
pattern is added.
**Fix:** Not applied — the repo owner opted to leave credential handling as-is for now.
Anything spawned from this template inherits both the tracked `.env` and the pattern gap.
**Dead ends:** Adding `.env` to `.gitignore` alone does nothing for an already-tracked
file; it needs `git rm --cached` as well.
**Affects:** `.gitignore`, `.env`, `.mcp.json`, `.claude/mcp.vscode-reference.json`

## 2026-08-12 — The two MCP config files disagreed on hosts (RESOLVED)
**Symptom:** `.mcp.json` pointed at `<lan-ip>`–`.126`;
`.claude/mcp.vscode-reference.json` pointed at `<lan-ip>:7801`–`7806`.
**Cause:** The stack migrated from a single docker-compose host (`.186`, one port per
service) to native OCI containers on Proxmox node `<lan-ip>` (CTIDs 120–126, **one
IP per server**). `.mcp.json` was updated; the reference file was not.
**Fix:** `.120`–`.126` is current. The reference file was realigned to `.120` and the
stale `.186:78xx` addresses removed.
**Dead ends:** We initially guessed the `.186` file might be the newer one because
`AGENTS.md` still described a docker-compose stack — the prose was simply stale too. The
`// description` comment in `.mcp.json` is the reliable source here, not the prose.
**Affects:** `.mcp.json`, `.claude/mcp.vscode-reference.json`

## 2026-08-12 — mcp-compressor 0.31.4 changed its CLI flags
**Symptom:** Server configs written with `--compression-level` and `--header` fail on
current mcp-compressor. Backends without an `Authorization` header fail differently —
mcp-compressor auto-attempts OAuth discovery, which these backends don't support.
**Cause:** 0.31.4 renamed `--compression-level` → `--compression`, `--header` → `-H`, and
now requires a `--` separator before the backend URL and its args.
**Fix:** Use `--compression`, `-H`, and `--`. For backends with no real auth, pass a
placeholder `-H "Authorization=Bearer unused"` to suppress OAuth discovery.
**Dead ends:** Omitting the Authorization header entirely looks correct for an unauthed
backend, but triggers the OAuth path and fails with an unrelated-looking error.
**Affects:** `.mcp.json`, `.claude/mcp.vscode-reference.json`

## 2026-08-12 — A newly created `.claude/settings.json` does not load in the session that created it
**Symptom:** Added `permissions.deny` / `permissions.ask` rules, then ran `dropdb --help`
(matches the `ask` rule `Bash(dropdb*)`). It executed straight through to
`bash: dropdb: command not found` — no prompt, no block.
**Cause:** The settings watcher only watches directories that already contained a settings
file when the session started. `.claude/settings.json` did not exist at session start, so
the new file stays invisible until the session restarts (or `/hooks` is opened, which
reloads config). Nothing wrong with the file — it parses and matches the published schema.
**Fix:** Restart the session, or open `/hooks` once. Verify afterward with a harmless
matching command such as `dropdb --help`.
**Dead ends:** Re-checking the JSON and the rule syntax — both were correct the whole time.
The failure looks exactly like a bad matcher pattern, which is the trap: **a permission
rule that silently doesn't load is indistinguishable from one that doesn't match.** Same
class of invisible-failure problem that got `mem0` dropped. Probe with a real command
rather than assuming the rules are live.
**Affects:** `.claude/settings.json`, `.claude/skills/guard-destructive/SKILL.md`

## 2026-10-06 — Fortune "not working" on most blocks is a vanilla anti-duplication design
**Symptom:** Fortune does nothing on logs, sand, dirt, etc., which looks like a bug to fix.
**Cause:** Multiplying a block that drops itself (or drops something that crafts back into
it) is an infinite loop: mine 1, get 3, place them, mine again. Vanilla caps melon at
9 slices and glowstone at 4 dust, exactly one block's worth, for this reason. Silk Touch +
Fortune on ores is the same loop.
**Fix:** Don't multiply self-drops. Add non-placeable byproduct drops instead (DESIGN §6,
Q13), and keep any loop-creating mode behind an off-by-default toggle with a warning.
**Dead ends:** A blocklist of storage blocks only. It misses plain self-dropping blocks
(logs, sand), which loop just as well.
**Affects:** DESIGN.md §4, §6; Phase 8

## 2026-10-06 — Correction to the Fortune entry above: the "Fix" line is superseded
**Symptom:** The entry above says "don't multiply self-drops, add byproduct drops instead".
**Cause:** That was written before Q13. The owner picked option B: multiply the block's own drop.
**Fix:** The loop is broken by the placed-block tracker (natural blocks only, fails closed), not
by avoiding self-drops. See decisions.md "Fortune multiplies natural blocks" and DESIGN §6.
The loop analysis above still holds and is the reason the tracker must cover pistons and
falling blocks.
**Dead ends:** n/a
**Affects:** DESIGN.md §6; Phase 8

## 2026-10-06 — Loom 1.18 for obfuscated MC (1.21.11) is the `fabric-loom-remap` plugin
**Symptom:** Older docs and templates use `id 'fabric-loom'` / `net.fabricmc.fabric-loom`.
**Cause:** Loom split the plugin once 26.x shipped unobfuscated. `net.fabricmc.fabric-loom` is the
no-remap variant for 26.x. Obfuscated versions (≤1.21.11) need `net.fabricmc.fabric-loom-remap`.
**Fix:** `id 'net.fabricmc.fabric-loom-remap' version '1.18.2'`, from FabricMC/fabric-example-mod
branch `1.21.11`. The 26.x port will have to switch the plugin ID back as well as bump versions.
**Dead ends:** Copying the plugin block from ../mcMMO-Singleplayer: it targets 26.3 and uses the
non-remap ID.
**Affects:** build.gradle

## 2026-10-06 — Removing an enchantment's registry entry strips it from items; the item survives
**Symptom:** Feared that uninstalling the mod would delete every item carrying an `enchanting2:` enchantment.
**Cause:** Measured on 1.21.11 (dev-server probe, TODO 1.10). With a datapack enchantment removed, the block entity
logs `WARN Serialization errors: Failed to get element probe:test_ench` and decodes **partially**. The item is kept,
the unknown entry is dropped, and the other enchantments survive. A book holding only that enchantment becomes an empty
enchanted book. Above-max vanilla levels (Protection 7) load fine. The drop is permanent on the next save.
**Fix:** none needed for safety. Document it: removal loses the new enchantments but keeps the items.
**Not yet verified:** player inventories (they need a real player, so use `runClient`). Same codec family, so expect
the same result, but it hasn't been measured.
**Affects:** DESIGN §2a, §9

## 2026-10-06 — 1.21.11 Mojang names: `ResourceLocation` → `Identifier`
**Symptom:** `ResourceKey#location()` → "cannot find symbol".
**Cause:** 1.21.11's official mappings renamed `ResourceLocation` to `Identifier`. Accessor: `ResourceKey#identifier()`.
**Fix:** check with `javap -cp .gradle/loom-cache/minecraftMaven/.../minecraft-common-*.jar <class>` instead of
guessing names from older versions' docs.
**Affects:** any code that handles IDs

## 2026-10-06 — `GameTestHelper#assertValueEqual(expected, actual, name)`
**Symptom:** A failing check read "Expected X to be 3: was 4" when the code expected 4.
**Cause:** The message treats the **first** argument as the expected value, the opposite of how it reads.
**Fix:** pass `(expected, actual, ...)`.
**Affects:** src/gametest/**

## 2026-10-06 — There is no `#minecraft:enchantable/sword` item tag
**Symptom:** The dev server refused to load the world ("Missing tag ... Failed to load datapacks").
**Cause:** The 1.21.11 tags are `enchantable/sharp_weapon`, `weapon`, `melee_weapon` and so on. A bad tag in
`supported_items` is a hard load failure, not a warning.
**Fix:** take tag names from the jar (`data/minecraft/tags/item/enchantable/`). Phase 10 enchantment JSON needs a
GameTest/dev-server boot for exactly this reason.
**Affects:** Phase 10 data enchantments

## 2026-10-06 — `EnchantmentEvents.MODIFY` runs before any `MinecraftServer` exists (unverified: `/reload`)
**Symptom:** (anticipated while planning Phase 2) a per-server config holder can't feed `MODIFY`.
**Cause:** in 1.21.x enchantments are a dynamic registry, loaded with the world's datapacks before the server object
is built (dedicated: in `Main`; integrated: in `WorldOpenFlows`). They're probably not reloaded by `/reload` either.
**Fix:** Phase 4 code reads the config file directly in `MODIFY`. TODO 4.0 probes `/reload` on the dev server before
DESIGN §2's "requires /reload or restart" is trusted.
**Affects:** Phase 4, DESIGN §2
