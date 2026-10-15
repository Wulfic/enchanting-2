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

## 2026-10-06 — `EnchantmentEvents.MODIFY` runs before any `MinecraftServer` exists (`/reload`: confirmed not re-run, 2026-10-07)
**Symptom:** (anticipated while planning Phase 2) a per-server config holder can't feed `MODIFY`.
**Cause:** in 1.21.x enchantments are a dynamic registry, loaded with the world's datapacks before the server object
is built (dedicated: in `Main`; integrated: in `WorldOpenFlows`). They're probably not reloaded by `/reload` either.
**Fix:** Phase 4 code reads the config file directly in `MODIFY`. TODO 4.0 probes `/reload` on the dev server before
DESIGN §2's "requires /reload or restart" is trusted.
**Affects:** Phase 4, DESIGN §2

## 2026-10-06 — Jankson 1.2.3 is more lenient than JSON5, and has quirks
**Symptom:** Probing `Jankson.load` before writing the config core.
- Duplicate keys: the last one wins silently. Nothing can detect it after parsing.
- `{"a":1} trailing` parses, and the trailing text is ignored. `{a: 1 b: 2}` (no comma) parses.
- `1e3` is a SyntaxError for the whole file. `1.5e3` is fine. Whole numbers come back as `Long`, decimals as `Double`.
  `null` is a separate `JsonNull`, not a `JsonPrimitive`.
- `JsonObject.keySet()` does **not** keep file order, even though `toJson` prints in insertion order.
- After one SyntaxError (the `1e3` case), the same `Jankson` instance failed on valid text. Couldn't reproduce with
  a plain unclosed brace, so the trigger is unclear.
**Fix:** `ConfigFiles.parse` builds a fresh `Jankson` per call. `ConfigReader.keys()` sorts. Validation rejects
non-`Long` numbers. Duplicate keys are a known blind spot, documented here rather than worked around.
**Affects:** src/main/java/com/wulfnet/enchanting2/config/ConfigFiles.java, ConfigReader.java

## 2026-10-06 — Jankson `JsonGrammar.STRICT` is pretty-printed
**Symptom:** The "no newlines" assertion on the network JSON failed. STRICT means quoted keys and no comments, not compact.
**Fix:** `ConfigFiles.WIRE` is built with `JsonGrammar.builder()` and every flag spelled out. `COMPACT` exists, but its
quoting and comma defaults aren't documented, so it isn't used.
**Affects:** src/main/java/com/wulfnet/enchanting2/config/ConfigFiles.java

## 2026-10-06 — Client: clear per-server state on configuration `INIT`, not configuration `DISCONNECT`
**Symptom:** (found while designing Stage 2B, from Fabric networking 5.1.6 sources) the docs don't say whether
`ClientConfigurationConnectionEvents.DISCONNECT` fires when configuration hands off to play. If it does, clearing there
would drop the server's config copy right after it arrives.
**Fix:** `ClientConfigState` clears on configuration `INIT` (each new connection) and on play `DISCONNECT`.
**Affects:** src/client/java/com/wulfnet/enchanting2/client/ClientConfigState.java

## 2026-10-06 — A loader must not log what its caller does with a failure
**Symptom:** On `/reload` with an unparseable file, the log said "Using defaults", and the next line said "Kept the previous config".
**Cause:** `ConfigFiles.load` described startup policy, but reload has a different policy.
**Fix:** `load` only says the file wasn't changed. `ServerConfigs` logs the outcome: defaults at startup, previous config on reload.
**Affects:** src/main/java/com/wulfnet/enchanting2/config/ConfigFiles.java, ServerConfigs.java

## 2026-10-06 — Vanilla XP progress is a float; `floor(progress × needed)` drifts by 1 point
**Context:** 3B GameTests on the real `Player` methods.
**Trap:** 29 + 157 points lands on 30 + 49, not 50. +/-10000 at level 20 + 10 comes back as 20 + 9. This is plain
vanilla (same curve at those levels): `p/n` stored as a float, multiplied back, reads `p − ε` and floors down.
**Fix:** 3B.7 TAIL snap stores the smallest float ≥ `p/n` and reads with a +0.25 offset (decisions.md 2026-10-06).
**Affects:** src/gametest/.../ExperienceGameTest.java

## 2026-10-06 — GameTests share one live server; batches are per test environment
**Context:** `ConfigWiringGameTest`'s reload test rewrites the config file and `/reload`s mid-run.
**Trap:** tests in the same batch run together, so any test that reads `ServerConfigs.get(server)` can see its temporary
values (`flatten_after_level: 41`). `GameTestBatchFactory` groups by environment and runs batches one at a time.
**Fix:** a test that mutates shared server state gets its own `environment = "enchanting2:<name>"`, with a
`data/enchanting2/test_environment/<name>.json` (`minecraft:all_of`, no definitions) in `src/gametest/resources`.
Readers of the live config assert it's the default first, so a leak fails loudly instead of silently.
Also: `GameTestHelper#makeMockServerPlayerInLevel()` is `@Deprecated(forRemoval)` in 1.21.11 with no replacement; it
still works. Expect javac removal warnings.
**Affects:** src/gametest/

## 2026-10-06 — Vanilla `totalExperience` wraps to 0 at MAX instead of clamping
**Context:** a 3B.7 GameTest set `totalExperience = MAX` and gave 5 points, expecting it to stay MAX.
**Trap:** `giveExperiencePoints` does `Mth.clamp(totalExperience + amount, 0, MAX)` with an `int` add. MAX + 5 overflows
negative first, so the clamp gives 0. The Phase 3 audit said "vanilla clamps it at MAX", and that was wrong.
Separately, the server sends the XP packet only when `totalExperience != lastSentExp`. A change that leaves the total
alone (removing points while it's 0, which `/xp set levels` leaves it at) never reaches the client.
**Fix:** the snap sets `lastSentExp = -1` whenever it changes progress. The wrap is fixed in 3C (owner chose saturating
add). **Naming trap:** `totalExperience` is *not* the death-screen score. That's `getScore()`/`increaseScore`, a
separate plain `int` add with no clamp, so it wraps negative. 3C saturates both.
**Affects:** src/main/java/com/wulfnet/enchanting2/mixin/PlayerMixin.java, ServerPlayerAccessor.java

## 2026-10-06 — `runGameTest` JVM dies with "paging file is too small" (DOS error 1455)
**Context:** a full `./gradlew build` while the owner's other workspace had several JVMs up. Windows commit headroom
was about 2.6 GB, even with 10 GB of physical RAM free.
**Trap:** the GameTest server fails to reserve its heap in about 20 s. The build stops before `:test`, so
`build/test-results` still shows the previous run's counts.
**Fix:** don't kill java (the other instance's tests). Run `./gradlew test`, then `./gradlew runGameTest`, then
`build`. Always read the test counts from a run whose task actually executed.
**Affects:** build environment only

## 2026-10-07 — `max_level_override` needs a restart; `/reload` doesn't re-run `MODIFY`
**Symptom:** 4A.6 probe (`scripts/checks/max-level-reload.txt`): Fortune override 2 + `reload`. The reload ran
(common.json5 re-read), but no `minecraft:fortune max level 3 -> 2` line. A plain restart printed it.
**Cause:** enchantments are a dynamic registry, loaded once with the world's datapacks. `/reload` only reloads
reloadable resources (tags, loot, recipes, functions).
**Fix:** documented in DESIGN §2, the `enchantments.json5` header comment, and `MaxLevelHook`'s Javadoc.
**Affects:** `MaxLevelHook`, any future registry-load edit (Phase 9/10)

## 2026-10-07 — Fabric drops a `MODIFY` edit made only through a mixin accessor
**Symptom:** none yet; checked by mutation. With only `@Mutable @Accessor` setting `definition`, the v1 GameTest fails
(the registry keeps vanilla's max).
**Cause:** `EnchantmentBuilderMixin.didModify` is set by an `@Inject(method = "*", RETURN)` that fires only when a builder
method returns the builder. Accessor methods added by our mixin don't trip it, and an unmodified builder is discarded.
**Fix:** after the setter, `builder.exclusiveWith(sameSet)`. It's a no-op that marks the builder as modified.
**Affects:** `MaxLevelHook`

## 2026-10-07 — The gradle cache holds several fabric-item-api versions; read the one on the classpath
**Symptom:** planned `MODIFY_WITH_LOOKUP` from the newest `-sources.jar` in `~/.gradle/caches` (14.5.0); compile failed.
**Cause:** the owner's other mods put newer FAPI modules in the shared cache. FAPI 0.141.6 uses item-api **11.6.0**,
which has only `MODIFY`, and it isn't deprecated there.
**Fix:** `./gradlew dependencies --configuration compileClasspath | grep <module>` first, then read that version's source.
**Affects:** any research against Fabric API sources

## 2026-10-07 — A GameTest mock player is never ticked
**Symptom:** a mock `ServerPlayer` standing in water reports `isInWaterOrRain() == false`, so `TridentItem#releaseUsing`
won't Riptide.
**Cause:** `makeMockServerPlayerInLevel()` players aren't ticked (their connection isn't), and `wasTouchingWater` is only
refreshed in `baseTick`.
**Fix:** a GameTest-only mixin (`src/gametest/.../mixin/EntityInvoker`, config `enchanting2-gametest.mixins.json`
registered in the GameTest mod's `fabric.mod.json`) invokes `updateInWaterStateAndDoFluidPushing()` once. A fishing hook,
by contrast, *is* ticked by the level, so a real hook in a glass-walled pool with `@GameTest(skyAccess = true)` gets bites.

## 2026-10-07 — A failed edit script doesn't stop a `git commit` on the next line
**Symptom:** a Python heredoc that updates TODO.md/state.md hit an `assert`, but the commit ran anyway, so 5A's code
landed without its TODO/state checkpoint (fixed in a follow-up commit, 4250d59).
**Cause:** in one Bash call, commands on separate lines run regardless of the previous exit code; only `&&` chains.
Separately, a heredoc containing Python `'''` strings made the Bash tool report "unexpected EOF".
**Fix:** put edit scripts in the scratchpad and run `python script.py || exit 1` before `git add`/`git commit`.
**Affects:** agent workflow only

## 2026-10-07 — `git checkout -- <file>` doesn't undo a mutation on a new, untracked file
**Symptom:** mutation-testing `anvil/TransferRules.java` (new in 6A, not yet committed), `git checkout --` printed nothing
and left the mutated code in place. Only the follow-up `git status` showed `??`.
**Cause:** checkout restores from the index; an untracked file has no index entry, so there is nothing to restore.
**Fix:** before mutating, copy the file to the scratchpad. Restore from that copy, then diff/grep to prove it's restored.
Also: `$TMP` in Git Bash is the Windows temp dir, not the session scratchpad.
**Affects:** agent workflow only (mutation checks)

## 2026-10-07 — The GameTest mock player is always creative
**Symptom:** every anvil GameTest that clicked the result saw cost 0; tests calling `AnvilCalculator` directly passed.
**Cause:** `GameTestHelper.makeMockServerPlayerInLevel()` returns an anonymous `ServerPlayer` whose `gameMode()` is
hard-wired to CREATIVE, so `abilities.instabuild` is true and `creative_free` made everything free. `setGameMode` can't
change it (the override ignores it).
**Fix:** `player.getAbilities().instabuild = false`. `hasInfiniteMaterials()` reads only `instabuild` (javap, 1.21.11).
`isCreative()` still says true, so code that checks `isCreative()` can't be tested this way.
**Affects:** src/gametest/java/com/wulfnet/enchanting2/gametest/AnvilGameTest.java, any GameTest that charges a player

## 2026-10-07 — The dev client crashed at startup: YACL's nested jars aren't loaded in the dev runtime
**Symptom:** the first client launch ever (Stage 6D's client GameTest) died in YACL's `client` entrypoint:
`NoClassDefFoundError: org/quiltmc/parsers/json/gson/GsonWriter`. `runClient` had the same crash since 2C; nobody had
launched the client yet (1.6 and 2C.7 are owner checks still open).
**Cause:** YACL ships the quilt parsers in `META-INF/jars`, and 2C wired it `modLocalRuntime { transitive = false }`
because the Java builder API needs nothing transitive. True at compile time, but YACL's own client init serializes its
config through them, and the dev runtime doesn't load a remapped mod's nested jars. 2C's "dev server and GameTests still
pass" never ran YACL's client entrypoint.
**Fix:** `localRuntime` `org.quiltmc.parsers:json` + `gson` 0.2.1 (the version YACL nests), not transitive, from Quilt's
Maven behind `exclusiveContent`. Dev runtime only; players get them inside YACL's jar. If YACL is bumped, read its
`META-INF/jars` and match `quilt_parsers_version`.
**Lesson:** a client-only dependency is only verified by launching a client; the client GameTest now does that every run.
**Affects:** build.gradle, gradle.properties

## 2026-10-07 — Grindstone slots are `GrindstoneMenu$2`/`$3`/`$4`, not `$1`/`$2`/`$3`
**Symptom (caught before it shipped):** the 6b plan targeted `GrindstoneMenu$1`/`$2` for the input slots and `$3` for
the result, reading the decompiled source top to bottom.
**Cause:** the field initializer `repairSlots = new SimpleContainer(2) { ... }` is also an anonymous class, and it's
numbered first. `javap` on the jar showed `$1 extends SimpleContainer`, `$2`/`$3` the input slots, `$4` the result.
A mixin on the wrong class would apply cleanly, since every one of them is a valid target, and do nothing useful.
**Fix:** check anonymous targets with `javap -p 'GrindstoneMenu$N.class'` before writing `@Mixin(targets = ...)`. The
menu also hands itself to its slots through a duck interface, and its constructor casts every slot to it, so a
mistargeted slot mixin fails loudly (`ClassCastException`) in any test that builds the menu.
**Affects:** `mixin/GrindstoneInputSlotMixin`, `mixin/GrindstoneResultSlotMixin`

## 2026-10-07 — The enchanting table's book doesn't follow a pose translation
**Symptom (caught before it shipped):** the 7D plan shifted `EnchantmentScreen.renderBg` with a pose translation, as the
grindstone does. That would move the background and slots but leave the 3D book at the centred position.
**Cause:** `renderBook` calls `GuiGraphics.submitBookModelRenderState(..., x0, y0, x1, y1)`. It's a picture-in-picture
element drawn later at those absolute screen coordinates, and the pose stack doesn't apply to it.
**Fix:** change `renderBg`'s own X instead: `@ModifyVariable(method = "renderBg", at = @At("STORE"), index = 5)` returns
`leftPos` (local 5 is `k = (width - imageWidth) / 2`, per `javap -c -l`). Everything vanilla draws from `k`, the book
included, then follows the window. The 1.21.11 jar has a LocalVariableTable, so `@ModifyVariable(ordinal = 0)` on
`tickBook`'s only boolean works too.
**Dead ends:** pose translation (fine for the grindstone, which has no picture-in-picture element).
**Affects:** `mixin/client/EnchantmentScreenMixin`

## 2026-10-08 — Fortune V averages 22/7 ≈ 3.14×, not "about 2.9×"
**Symptom:** DESIGN §6 said Fortune V's ore multiplier averages about 2.9×. The plan, the config tooltip and the
`FortuneRule` javadoc copied it.
**Cause:** never computed. `ApplyBonusCount.OreDrops` (`javap`, 1.21.11) is `count × (max(0, nextInt(level + 2) − 1) + 1)`.
At level 5 that's 1, 1, 2, 3, 4, 5, 6 with equal odds, so the mean is 22/7. Fortune III is 2.2 (matches the wiki).
**Fix:** corrected everywhere before Stage 8A was committed. Derive a formula's numbers from the bytecode, not memory.
**Affects:** DESIGN §6, `FortuneRule`, `FortuneSection`, en_us.json

## 2026-10-08 — `PlayerBlockBreakEvents.AFTER` fires before the broken block's loot is rolled
**Symptom (caught in planning, before code):** the Phase 8 plan cleared a placed-block mark in `AFTER`. The Fortune
drop hook reads the mark while the loot is rolled, so it would always have seen "natural".
**Cause:** Fabric 0.141.6 (`fabric-events-interaction-v0` 4.1.1) fires `AFTER` from `ServerPlayerGameModeMixin` at the
`Block.destroy` call in `destroyBlock`. Vanilla's order is `playerWillDestroy` → `removeBlock` → `destroy` (AFTER) →
`playerDestroy` (loot). "After" means after removal, not after drops.
**Fix:** our own `ServerPlayerGameModeMixin` clears at `destroyBlock` RETURN when it returns true. Mutation-tested:
without it, "after the player broke it" stays PLACED. Confirmed in Stage 8D: with the clear moved into `AFTER`, 10
place-and-mine loops with a Fortune V axe dropped 30 logs instead of 10. Only the end-to-end loop test caught it; the
mark-level tests all passed.
**Lesson:** read where a Fabric event's mixin injects before relying on its name for ordering.
**Affects:** `mixin/ServerPlayerGameModeMixin`, `fortune/PlacedBlocks`, Stage 8D's loot hook

## 2026-10-09 — Client GameTests: `server.execute` work waits for a server tick
**Symptom:** `ConfigScreenClientGameTest` saved `enchantments.json5` through `ConfigEditing`, then
`world.getServer().computeOnServer(...)` still saw the old rules. The log showed the "Reloaded" line only at shutdown.
**Cause:** `ConfigEditing.save*` queues the re-read with `server.execute`, and that queue only drains when the
integrated server ticks. The client GameTest framework runs `computeOnServer` without ticking, so it runs first.
**Fix:** `context.waitTicks(2)` after anything that queues server work, before `computeOnServer` checks its effect.
**Dead ends:** none. The failing assertion message plus the log timestamps were enough.
**Affects:** `src/gametest/.../ConfigScreenClientGameTest.java`, any client test after a `ConfigEditing` save

## 2026-10-09 — `runLocationChangedEffects` doesn't add enchantment attributes in a GameTest
**Symptom:** Attack Speed / Reach tests read the bare base value after
`EnchantmentHelper.runLocationChangedEffects(level, stack, player, slot)`.
**Cause:** `EnchantmentAttributeEffect.onChangedBlock` only adds modifiers when its "became active" flag is set. Real
equipping adds them in `LivingEntity.collectEquipmentChanges` through `ItemStack#forEachModifier` (which includes the
enchantment's), and a mock `ServerPlayer` never ticks, so that path never runs.
**Fix:** in tests, add `EnchantmentHelper.forEachModifier(stack, slot, ...)` to the attribute instances yourself. It
leaves out the item's own modifiers, so the value is the enchantment's alone.
**Affects:** src/gametest/**/NewEnchantmentsGameTest.java, any attribute enchantment test (10aC armor)

## 2026-10-09 — Fabric `AFTER_DAMAGE` skips killing blows and passes the post-armor amount
**Context:** Vampirism needs the health a hit actually took, killing blows included.
**Trap:** FAPI entity-events 5.0.5 fires `ServerLivingEntityEvents.AFTER_DAMAGE` at `hurtServer` TAIL only
`if (!isDeadOrDying())`, and its `damageTaken` is the post-armor amount, overkill included.
**Fix:** `LivingEntityMixin` wraps `hurtServer` (`@WrapMethod`) and diffs health + absorption itself.
**Affects:** src/main/java/com/wulfnet/enchanting2/mixin/LivingEntityMixin.java

## 2026-10-09 — A GameTest mock player's hit ignores `setInvulnerable(true)`
**Trap:** `target.setInvulnerable(true)` didn't stop `damageSources().playerAttack(mockPlayer)`: the hit landed. The
mock player's attack bypasses `Invulnerable` (it behaves like a creative player's).
**Fix:** to test a hit that doesn't land, hit once, then hit weaker inside the invulnerability frames
(`hurtServer` returns false).
**Affects:** src/gametest/java/com/wulfnet/enchanting2/gametest/EffectEnchantmentsGameTest.java

## 2026-10-09 — A missing loot table is `LootTable.EMPTY`, silently
**Trap:** `ReloadableServerRegistries.Holder.getLootTable(key)` returns `LootTable.EMPTY` for an unknown key and logs
nothing, so a datapack removing our table makes the feature silently do nothing.
**Fix:** compare against `LootTable.EMPTY` and WARN (`HeadDrops`). A `MODIFY_DROPS` handler that rolls another table
must keep its own-table guard, or the nested roll re-enters it (mutation run showed infinite recursion).
**Affects:** src/main/java/com/wulfnet/enchanting2/effect/HeadDrops.java

## 2026-10-09 — `projectile_spawned` runs in `Projectile.applyOnProjectileSpawned`, not the arrow constructor
**Trap:** `new Arrow(level, x, y, z, arrowStack, bow)` runs no `projectile_spawned` effects (`javap`: only
`Projectile.applyOnProjectileSpawned(level, weapon)` and `spawnProjectile` call `EnchantmentHelper.onProjectileSpawned`).
A test that builds an arrow with the bow sees no Flame and no TrueShot.
**Fix:** call `arrow.applyOnProjectileSpawned(level, bow)` after constructing it, as the shoot path does.
**Affects:** src/gametest/java/com/wulfnet/enchanting2/gametest/EffectEnchantmentsGameTest.java

## 2026-10-12 — 1.21.11 crossbows load in `onUseTick`, not `releaseUsing`
**Symptom:** a GameTest calling `CrossbowItem.releaseUsing(stack, level, player, 0)` never charged the crossbow.
**Cause:** loading (`tryLoadProjectiles`) moved to `onUseTick`: it loads once `(useDuration - remaining) /
chargeDuration >= 1`. `releaseUsing` only reports whether it's charged.
**Fix:** `onUseTick(level, player, crossbow, 0)` (0 ticks left = fully drawn), then `use(...)` to fire.
**Affects:** `ItemWideningGameTest`, any crossbow test

## 2026-10-12 — A GameTest mock player starts at world (0, 0, 0), not in the test structure
**Symptom:** a crossbow fired by `makeMockServerPlayerInLevel()` spawned no arrow near the player's block position
inside the test; diagnostics showed the player at `(0.0, 0.0, 0.0)`.
**Cause:** the mock is added to the level without being placed in the structure.
**Fix:** `player.setPos(helper.absoluteVec(...))` before anything that spawns entities from the player's position.
**Affects:** GameTests that spawn projectiles or drops from a mock player

## 2026-10-12 — Spawner XP bypasses Silk Touch's `block_experience`
**Trap:** Silk Touch's JSON sets `block_experience` to 0, so "silk breaks give no XP" looks free. It isn't for spawners:
`SpawnerBlock.spawnAfterBreak` (javap, 1.21.11) calls `popExperience(level, pos, 15 + nextInt(15) + nextInt(15))`
directly, never through `EnchantmentHelper.processBlockExperience`.
**Fix:** `SpawnerBlockMixin` `@WrapWithCondition` on that `popExperience` call, same condition as the drop. A mutation
run (condition forced true) gave 36 XP and failed the silk-break test.
**Affects:** src/main/java/com/wulfnet/enchanting2/mixin/SpawnerBlockMixin.java

## 2026-10-12 — Rolling a loot table in a test also runs our live `MODIFY_DROPS` listener
**Symptom:** a GameTest that rolled the spawner table, then called `CollectedSpawners.addDrop(..., false)` for the
"option off" case, still got one spawner; with `true` it got two.
**Cause:** Fabric fires `MODIFY_DROPS` inside `LootTable.getRandomItems`, so the live listener (server config, option
on) had already added a drop before the test's own call.
**Fix:** for a table vanilla leaves empty, start the test from an empty list. For one with real drops, only test a
mode the live config doesn't already apply (the Fortune tests roll under SILK_WINS, then apply FORTUNE_MULTIPLIES_SILK).
**Affects:** src/gametest/java/com/wulfnet/enchanting2/gametest/SpawnerGameTest.java

## 2026-10-12 — `@WrapOperation` can't target a call inside an anonymous class
**Symptom:** Mixin apply failed for `DispenseItemBehavior$4`: "invalid signature. Found unexpected argument type
DefaultDispenseItemBehavior at index 0, expected DispenseItemBehavior$4."
**Cause:** a `@WrapOperation` handler's first parameter is the call's receiver, typed exactly as the owner class, and
Java code can't name an anonymous class.
**Fix:** `@ModifyArg` on the argument you want to change (with `@Local(argsOnly = true)` for the rest), which has no
receiver parameter. Same applies to `@Redirect`.
**Affects:** src/main/java/com/wulfnet/enchanting2/mixin/BucketDispenseBehaviorMixin.java

## 2026-10-12 — Two "obvious" ceiling rows were wrong: Aqua Affinity scales, Efficiency is capped
**Symptom:** DESIGN §8's draft table had Aqua Affinity as "single-level, extra levels do nothing" and Efficiency as
"no ceiling, Efficiency 100 mines instantly". `LevelCeilingsGameTest` failed both.
**Cause:** Aqua Affinity is `submerged_mining_speed` × (1 + 4 × level) (vanilla JSON, linear), capped at 20, so it keeps
adding to XXV. Efficiency adds level² + 1 to `mining_efficiency`, capped at 1024, so it stops at XXXII, and obsidian
(hardness 50) needs 1500 destroy speed: never instant. A mock player also starts in the air, which divides mining
speed by 5 (`setOnGround(true)` before `getDestroyProgress`).
**Fix:** soft 25 and soft 32 rows. The GameTest now scans every soft-1 row's encoded effects for a level-based value
(with Multishot, Thorns and Aqua Affinity as positive controls), and reads attribute maxima from `RangedAttribute`.
**Lesson:** a ceiling from memory is a guess; read the enchantment JSON and the attribute's max first.
**Affects:** `enchantment/LevelCeilings`, `LevelCeilingsGameTest`

## 2026-10-12 — `I18n.get` formats, so a lang string with a bare "%" comes back as "Format error"
**Trap:** the client GameTest matched YACL option names with `I18n.get(key)`. `I18n.get` runs `String.format` on the
string, so a name like "Discount at level I (%)" (no args) returns "Format error: ..." and never matches, while the
screen itself (a `Component.translatable`) shows it fine.
**Fix:** compare against `Component.translatable(key).getString()`. In en_us.json a name with no args uses a single
"%" like "Maximum curse discount (%)"; "%%" is only for strings that take arguments.
**Affects:** src/gametest/.../ConfigScreenClientGameTest.java

## 2026-10-12 — A GameTest sequence reports its last failing step, not its first
**Trap:** with Weight's hook disabled, `weightSlows...` reported "Weight III + III, capped ... on tick 3" although the
earlier "Weight I" step (tick 2) also saw no modifier. A failed `thenExecute` (`executeWithoutFail` →
`GameTestInfo.fail`) doesn't stop the later steps, and a later failure's message replaces the first one.
**Fix:** none needed: the test still fails. When a mutation run reports a later step than expected, instrument the
earlier ones (a temporary `System.out` shows as `[STDOUT]` in the log) before concluding the earlier assert passed.
**Affects:** any `helper.startSequence()` GameTest

## 2026-10-12 — A new GameTest class runs only once it's listed in the GameTest `fabric.mod.json`
**Symptom:** `./gradlew build` said "All 267 required tests passed" after adding `WornEnchantmentsGameTest` (six tests):
the same count as before, all green.
**Cause:** Fabric finds GameTest classes through the `fabric-gametest` entrypoint list in
`src/gametest/resources/fabric.mod.json`, not by scanning. An unlisted class compiles and is silently skipped.
**Fix:** add the class to that list (client ones to `fabric-client-gametest`), and check that the "N required tests"
count went up by the number of tests added.
**Affects:** src/gametest/resources/fabric.mod.json

## 2026-10-12 — The mock GameTest player is creative, so mob targeting never picks it
**Trap:** `makeMockServerPlayerInLevel()` overrides `gameMode()`; `TargetingConditions.DEFAULT` refuses creative
players, so `Phantom.canAttack(level, mockPlayer, DEFAULT)` is false bare-headed and a "ward refuses" test would pass
vacuously. It's also invulnerable (no exhaustion): set `getAbilities().invulnerable = false` for hunger tests.
**Fix:** test targeting on a zombie when the hook reads only the target's equipment, and assert the bare case first.
**Affects:** src/gametest/.../WornEnchantmentsGameTest.java

## 2026-10-12 — Lava Walking: the strider's surface puts your feet in the lava
**Trap:** DESIGN §9 assumed a lava walker's box sits above the lava, so the burn would need applying by hand.
**Fact (javap 1.21.11):** `LiquidBlock.SHAPE_STABLE` is half a block, under a source's 8/9 surface, so a standing walker
is `isInLava()` and `LavaFluid.entityInside` burns it unaided. The same `canStandOnFluid` answer also decides
`shouldTravelInFluid`, so it must turn false once the walker has sunk (lava height > 0.5), or a sunk walker walks the
lava floor at ground speed. `EntityCollisionContext.isAbove` doesn't read sneaking; check `isDescending()` yourself.
**Affects:** effect/Footing

## 2026-10-12 — GameTest: the empty test structure is 8 high with a ceiling
**Symptom:** a husk spawned at relative y 9 "fell through" the lava; it was standing at y 10, on top of the structure.
**Fix:** keep spawns and drops inside y 0..7 (a 5.5-block drop for the fall-damage test).
**Affects:** src/gametest/

## 2026-10-12 — Seeded sample tests break when an enchantment joins a pool
**Symptom:** `AcquisitionGameTest.treasureOverrideDoublesOrHalvesTheLibrarianPrice` (100 seeded librarian rolls) saw no
cheap treasure book once Lava Walking and Snow Walker became tradeable: every seed now picks a different book.
**Fix:** roll until each case is seen a few times, with a cap, instead of a fixed count. Any later "N seeded rolls must
include X" test needs the same shape, since every 10c stage adds tradeable enchantments.
**Affects:** src/gametest/java/com/wulfnet/enchanting2/gametest/AcquisitionGameTest.java

## 2026-10-12 — GameTest: zombies burn in the test level's daylight
**Symptom:** shield GameTests saw a zombie lose 1 health from "nothing" (a plain shield "hurt" the attacker; a 10 hit
took 11).
**Fix:** use husks (no sunburn) for any test that reads health or fire. `BootsGameTest` and `ShieldsGameTest` do.
**Affects:** src/gametest/

## 2026-10-12 — `BlocksAttacks.disableBlockingForTicks` rounds
**Fact (javap 1.21.11):** `Math.round(seconds × disable_cooldown_scale × 20)`, and nothing at all when the scaled
seconds are ≤ 0. Steadfast I on a 5 s axe is 67 ticks, II 33, III none.
**Affects:** ShieldsGameTest

## 2026-10-12 — A mock player's held weapon never adds attack damage
**Symptom:** a GameTest kill with `player.attack(husk)` left a 0.5-health husk alive while holding a diamond sword.
**Cause:** weapon attribute modifiers apply in `detectEquipmentUpdates` on tick, and a mock player is never ticked; its
attack strength is also at 0, so a hit does about 0.2.
**Fix:** give the target almost no health (0.01), or tick-independent damage. Affects: DropsGameTest

## 2026-10-13 — A mock player can't be hurt until its client "loads"
**Symptom:** a lethal `hurtServer` on a survival mock player left it at 20 health.
**Cause (javap 1.21.11):** `ServerPlayer.isInvulnerableTo` is true while `connection.hasClientLoaded()` is false, and a
mock player's connection never loads. (`die()` directly still works, which is how Keep on Death's test kills.)
**Fix:** `((PacketListenerInvoker) player.connection).e2test$markClientLoaded()` (gametest invoker).
**Affects:** LastStandGameTest

## 2026-10-13 — Float error made stochastic XP rounding drop a point
**Symptom:** `CursesGameTest`'s Greed V check (4 block XP → exactly 2) failed once: it got 1.
**Cause:** Greed V's factor is 0.9f + 4 × −0.1f = 0.49999997, so 4 × factor = 1.9999999, and `StochasticRounding`
rounded that down one time in ten million. A latent 10eB bug, caught by a long run of builds.
**Fix:** values within 1e-5 (relative) of a whole number count as whole and draw no roll. JUnit
`floatErrorNextToAWholeValueIsNotAFraction`.
**Affects:** enchantment/StochasticRounding

## 2026-10-13 — Client GameTest: `pressKey` is a click, not a hold
**Trap:** `TestInput.pressKey` presses and releases before the tick runs, so anything read through `KeyMapping.isDown()`
(jump, sneak, sprint) never sees it; click-counted keys (attack, use via `consumeClick`) do.
**Fix:** `holdKeyFor(options -> options.keyJump, 2)`. Affects: DoubleJumpClientGameTest

## 2026-10-13 — Client GameTest flake: "Network synchronizer in invalid state"
**Symptom:** `runClientGameTest` fails once with `AssertionError: Network synchronizer in invalid state` and
"Detected interfacing with packets at a lower level" from fabric-client-gametest-api, during a later test world
(after the tooltip test, before the Last Stand HUD shot); every table/anvil/grindstone test had already passed.
**Cause:** not found; no code in that area changed, and the next run was green. Fabric's synchronizer tracks packets
between the client and integrated server per tick.
**Fix:** rerun once. If it repeats, look at which test touches packets outside the API (the shield/Double Jump client
tests drive real input) before suspecting the change under test.
**Seen again** 2026-10-13 (10oC), same place (the world after the config-screen test); the rerun was green. Twice
in one day now: if it shows a third time, chase it instead of rerunning.
**Third time** 2026-10-14 (12B, 1.21.11 node): this time while the *grindstone* test's world loaded, right after
the anvil test and before any test body ran ("Detected interfacing with packets at a lower level" 10 s after spawn
prep). Three different spots, so not one test's input; looks like a race in the synchronizer during world load. The
rerun was green and the commit went ahead on that; chasing it is its own TODO item ("Client GameTest flake").

## 2026-10-13 — A full-height box must clear vanilla's part at full height, not just the top band (10oC)
**Symptom:** the grindstone's picker box covered the inventory's 9th column, and the client GameTest's "a click in
the list keeps the carried item" failed: the diamonds went missing. Our `mouseClicked` ate the click, but vanilla's
`mouseReleased` still saw the hotbar slot under the mouse.
**Cause:** the box's left edge (x 150) came from texture columns that are plain grey in rows 4–80 only. The box runs
rows 11–155, through the inventory grid (to column 168). Over rows 3–162 only columns 169–171 are plain, in both
textures.
**Fix:** one `ColumnWindow.BOX` at x 173 for both screens; `ColumnWindowTest` now asserts the frame clears the
inventory grid. Measure a texture over the rows the overlay actually covers. And never let an in-window box overlap
a slot: vanilla's release handling acts on the slot under the mouse whatever the click handler did.

## 2026-10-13 — The GameTest run's enchantment registry has test-only entries with no description
**Symptom:** a client GameTest that put "the first 16 enchantments by ID" on a pickaxe expected each to get a Ctrl
description and failed on "High Level (GameTest) I" and "Third Party (GameTest)".
**Cause:** the gametest source set registers its own enchantments (data under src/gametest/resources), which have no
`.desc` lang line; `EnchantmentDescriptionLangTest` only covers real ones.
**Fix:** filter by `I18n.exists(DescriptionPlan.key(id))` when a test needs described enchantments.
**Affects:** src/gametest/java/com/wulfnet/enchanting2/gametest/TooltipClientGameTest.java

## 2026-10-13 — The anvil-wear hook targets a lambda by its intermediary name
**Fact (javap 1.21.11):** the chip roll in `AnvilMenu.onTake` lives in the `access.execute` lambda, named
`method_24922(Player, Level, BlockPos)` in both the named dev jar and production (Mojang mappings don't name lambdas,
so intermediary's name stays). `AnvilMenuMixin.enchanting2$wearOnUse` targets it by that name.
**Trap for Phase 12:** 26.x is unobfuscated, so the lambda will be `lambda$onTake$N`. Find N with javap per node and
put the name behind a Stonecutter condition. A wrong name doesn't fail quietly: `defaultRequire: 1` makes the game
refuse to start, and `AnvilWearGameTest` covers the behaviour both ways.
**Affects:** src/main/java/com/wulfnet/enchanting2/mixin/AnvilMenuMixin.java

## 2026-10-13 — Client GameTests only render on a tick: time-based client state can go stale between frames
**Symptom:** `TooltipClientGameTest` "and back up: 0 from 0": after 100 wheel notches the tall tooltip's offset was 0.
**Cause:** `TooltipScroll` resets a tooltip not drawn for 200 ms. The client GameTest draws only on `waitTick`, so 100
`scroll` calls with no tick between them took over 200 ms on a loaded machine (another instance was running tests)
and the tooltip started over. Real play draws every frame, so the product was fine.
**Fix:** one notch per `waitTick` until the offset stops moving. Any client GameTest step that relies on "drawn
recently" must tick between actions.
**Affects:** src/gametest/java/com/wulfnet/enchanting2/gametest/TooltipClientGameTest.java

## 2026-10-13 — "Descriptions always show" was Enchantment Descriptions, not ours
**Symptom:** owner's screenshot: every enchantment's description under its name with Ctrl up, tooltip off the
bottom of the screen.
**Cause:** the owner's Prism instance (`%APPDATA%/PrismLauncher/instances/1.21.11`) had `enchdesc` installed. Its
default `require_keybind: false` shows descriptions always, on their own lines; ours step aside when it's loaded
(DESIGN §6). Tell-tale: the description sat under the name, but ours has been "Name: description" since 11c; the
instance log says "Enchantment Descriptions (enchdesc) is loaded". Our scroll line also lived in the skipped listener.
**Fix:** owner removed enchdesc (moved to `minecraft/mods-disabled/`); the scroll line now registers always (11d A).
Check the owner's instance logs and mod list before debugging a screenshot: it runs ~200 other mods (Iceberg
regathers tooltip lines, ImmersiveUI moves screens).
**Affects:** src/client/java/com/wulfnet/enchanting2/client/EnchantmentDescriptions.java

## 2026-10-14 — A docs-only edit left `test` UP-TO-DATE, so a broken doc passed locally
**Symptom:** a deliberately broken wiki link, then `./gradlew test` → `:test UP-TO-DATE`, BUILD SUCCESSFUL.
**Cause:** `ConfigDocsTest` and `ReleaseDocsTest` read `docs/`, `wiki/`, `CHANGELOG.md`, `LICENSE` and `wiki.yml`
from disk; none were task inputs, so Gradle saw nothing changed. CI was never affected (fresh checkout).
**Fix:** `build.gradle` declares them as `test` inputs (`releaseDocs`). Any new test that reads a repo file must add
that file there.
**Affects:** build.gradle, src/test/.../ReleaseDocsTest.java, config/ConfigDocsTest.java

## 2026-10-14 — GitHub runners dropped Node 20 on 2026-09-16; `@v4` actions are dead
**Symptom (found while writing CI):** gitleaks-action's v3 notes: Node 20 removed from GitHub-hosted runners
2026-09-16, so Node 20 actions stop working regardless of opt-outs.
**Fix:** majors checked with `gh api repos/<action>/releases/latest` on 2026-10-14: checkout v7, setup-java v6,
gradle/actions v6, upload-artifact v7. mcMMO-Singleplayer's `release.yml` still pins checkout/setup-java/setup-gradle
`@v4` — it likely fails now (told the owner).
**Affects:** .github/workflows/*.yml

## 2026-10-14 — gitleaks runs as the pinned CLI, not gitleaks-action
**Why:** the action scans only the pushed commit range on `push`; the guarantee needed after the history scrub is
"no secret in ANY commit". `ci.yml` downloads gitleaks 8.30.1, checks its sha256, runs `gitleaks git --log-opts=--all`.
Bumping the version means changing GITLEAKS_VERSION and GITLEAKS_SHA256 together.
**Affects:** .github/workflows/ci.yml

## 2026-10-14 — `Files.notExists` under a regular file: true on Windows, false on Linux (first CI run)
**Symptom:** `ConfigFilesTest.createFailureIsReportedNotThrown` green on Windows, red on GitHub's Linux runner:
expected `CREATE_FAILED`, got `UNREADABLE`.
**Cause:** the test puts a file where `config/enchanting2/` should be. Windows answers "no such file" for the path
under it; Linux answers ENOTDIR, which `Files.notExists` treats as status unknown (false), so `load` reads instead of
creating.
**Fix:** the test, not the code. `UNREADABLE` is the fail-closed answer (a `/reload` keeps the live config;
`CREATE_FAILED` would apply defaults), so switching `load` to `!Files.exists` would make a permission glitch reset a
live server. Create failure is now tested through `writeDefaultsIfAbsent` (same on both OSes) and `load` accepts
either source. Anything path-shaped in tests should be suspected of being Windows-only until CI has run it.
**Affects:** src/test/.../config/ConfigFilesTest.java

## 2026-10-14 — Elytra Slot hides the elytra from equipment and cancels `updateFallFlying`
**Symptom:** owner: Flight elytra glides but never thrusts; Speed silently did nothing too. Our GameTests were green
(mock player, elytra in the chest slot).
**Cause:** the owner's instance runs Elytra Slot: the elytra is in a Fabric attachment, not `EquipmentSlot.CHEST`,
and the mod's `ElytraSlotMixin` cancels `LivingEntity.updateFallFlying` at HEAD whenever its slot holds a glider, so
any TAIL inject there never runs. Fixed in Phase 11f (decisions.md).
**Lesson:** an owner report that contradicts green tests → read the instance's `mods/` first (same as enchdesc in 11d).
Anything reading "the worn elytra" must go through `elytra/WornElytra`, and hooks around vanilla's elytra tick go at
its call in `aiStep`, not inside it.
**Also:** driving a mock `ServerPlayer` through `aiStep()` really moves it (travel runs): a coasting glide from 5
blocks up lands on tick 38. Start such tests high.

## 2026-10-14 — A Windows-built jar and the CI release jar differ in text resources only (CRLF)
**Symptom:** comparing a local `enchanting2-1.0.1.jar` with the GitHub release jar: same 414 entries, 6 "changed"
(`en_us.json`, some tags, one enchantment JSON). Byte compare of the whole jar always differs (timestamps).
**Cause:** the Windows checkout has CRLF in some resources, the Linux CI build LF. Classes are identical.
**Fix:** compare jars by entry name + content with text entries normalised CRLF → LF (12A's `jardiff.py` in the
session scratchpad: binary suffixes `.class .png .jar .ogg .nbt` compared raw). Stonecutter also adds four
`Stonecutter-*` lines to `META-INF/MANIFEST.MF`; expect that one entry to differ from a pre-Phase-12 jar.
**Affects:** any "same jar as before" check (Phase 12A, 12F)

## 2026-10-14 — With Stonecutter, a bare task name runs in every version node
**Symptom:** none yet (one node). `./gradlew build` runs `:1.21.11:build`; with two nodes it builds both, which is
what CI wants. But `./gradlew runServer` / `runClient` would start every node's game, all servers on port 25566.
**Fix:** always qualify run tasks (`./gradlew :1.21.11:runServer`); `scripts/dev-server-smoke.sh` takes `NODE=`.
Build output moved to `versions/<node>/build/` (libs, test results, `run/gameTest`, `run/clientGameTest/screenshots`).
**Affects:** scripts/, README.md, wiki/Building-from-Source.md, ci.yml, release.yml

## 2026-10-14 — An edited `filesMatching` pattern list left `processResources` UP-TO-DATE (12B2)
**Symptom:** mutation check for `ModIdentityTest.minecraftRangeAndJavaLevelAreExpandedForThisNode`: dropping
`'*.mixins.json'` from `filesMatching([...])` still passed. The old, expanded mixin configs were reused.
**Cause:** Gradle fingerprints declared inputs and the copied files, not the `filesMatching` closure or its patterns.
`--rerun` on `processResources` showed the test does catch it.
**Fix:** the pattern list is a named value and an input (`inputs.property 'expandedResources', expandedResources`);
editing it now re-runs the task. Any future processResources closure that reads values must declare them the same way.
**Affects:** build.gradle (processResources), any mutation check that edits build logic

## 2026-10-14 — 26.1: a stale anonymous-class number applies without an error (12B)
**Symptom:** on 26.1.2 only `InfiniteBucketGameTest`'s dispenser case failed (an empty bucket came back). No mixin
error: `DispenseItemBehavior$4` exists there and its `execute` also calls `consumeWithRemainder`, so the
`@ModifyArg` applied to the wrong dispenser behaviour.
**Cause:** javac numbers anonymous classes by source order; 26.1 removed one before the bucket behaviour, so it is `$3`
(javap: the class calling `DispensibleContainerItem.emptyContents`). Same risk for lambda names (`lambda$onTake$0`
replaced intermediary `method_24922` in `AnvilMenuMixin`).
**Fix:** `//?` conditions per band, chosen by javap on the target jar, never by "it applied". `GrindstoneMenu$4` was
checked and is the same class on both. 12C must re-check every numbered target (`$3`, `$4`, `lambda$...$0`) per line.
**Affects:** mixin/BucketDispenseBehaviorMixin.java, mixin/AnvilMenuMixin.java, mixin/GrindstoneMenu*Mixin

## 2026-10-14 — 26.1: an API that now returns Optional compiles against an old `== null` guard (12B)
**Symptom:** `SpawnerGameTest.aStampedSpawnerOfAnEgglessTypePlacesEmpty` failed on 26.1.2: a forged spawner item set
primed TNT as the mob. Compiled without a warning.
**Cause:** `SpawnEggItem.byId` went from a nullable `SpawnEggItem` to `Optional<Holder<Item>>` (any item whose default
`ENTITY_DATA` names the type). `Optional == null` is legal Java and always false, so the forged-item guard passed
everything.
**Fix:** 26.1 branch filters for a real `SpawnEggItem` and tests `isEmpty()`. B5 sweeps every `== null` / `!= null`
on a Minecraft call's result against 26.1's return types.
**Affects:** spawner/CollectedSpawners.java; any null check on a vanilla return value

## 2026-10-14 — 26.1 moved lava's stable shape into LivingEntity.getLiquidCollisionShape (12B)
**Symptom:** Lava Walking: three `BootsGameTest` cases sank on 26.1.2 although `canStandOnFluid` still said yes.
**Cause:** `LiquidBlock.getCollisionShape` now maps the entity to `getLiquidCollisionShape()`, empty for everything but
striders, then filters with `canStandOnFluid`. A yes there has no shape to keep.
**Fix:** 26.1 only: `LivingEntityMixin` returns the strider's column (`Block.column(16, 0, 8)`) for a lava walker;
the existing filter and the flowing-lava hook (`LiquidBlockMixin`) are unchanged.
**Affects:** mixin/LivingEntityMixin.java, effect/Footing.java

## 2026-10-14 — 26.1's RegistryDataLoader has no per-element method; MODIFY_WITH_LOOKUP replaces the lend (12B)
**Symptom:** `RegistryDataLoaderMixin` failed to apply on 26.1.2 (`loadElementFromResource` gone; the loader is an
async pipeline now).
**Fix:** on 26.1, `ItemWideningHook` takes items from Fabric's `MODIFY_WITH_LOOKUP` lookup; the mixin is listed only
for band A through a Groovy template block in `enchanting2.mixins.json` (`<% if (band == 'A') { %>`), expanded by
processResources. `ModIdentityTest.everyListedMixinIsAClass` guards the template.
**Affects:** enchantment/ItemWideningHook.java, mixin/RegistryDataLoaderMixin.java, enchanting2.mixins.json, build.gradle

## 2026-10-15 — A GameTest that fails in its own setup hides every mod break behind it (12C)
On 26.2, 80 of 83 failures were the GameTest jar's own `NoSuchFieldError: EntityType.ZOMBIE` while spawning its mob.
The mod code those tests exist to check never ran, so the run said nothing about it. The static link check
(`./gradlew :matrix-<version>:linkCheck`) resolves every reference in both jars without running anything and found the
masked ones (Shield Bash's `knockback`, `Options.hideGui`, YACL's `arrangeElements`, the toast manager). On a new
Minecraft version: link check first, GameTests after it's clean.

## 2026-10-15 — loom-back-compat outside Stonecutter needs `loomx.unobfuscated=true` (12C)
`LoomCompatProjectExtension.isUnobfuscated` reads the property `loomx.unobfuscated` / `loomx.obfuscated`, else asks
Stonecutter's `sc.current` (compare with "26"). A plain subproject (the matrix nodes) has no Stonecutter, so its
gradle.properties says `loomx.unobfuscated=true` or it gets fabric-loom-remap and tries to remap a 26.x game.

## 2026-10-15 — A plain mod jar on the dev classpath does load its nested jars (12C)
The matrix puts the shipped `enchanting2-*.jar` (not remapped, 26.x) on `localRuntime`. Fabric Loader lists
`enchanting2 \-- blue_endless_jankson 1.2.3`: nested jars from `META-INF/jars` load, unlike a Loom-remapped dependency's
(YACL's parsers, gotchas 2026-10-07). So the matrix proves the jar players get, Jankson included.

## 2026-10-15 — In a Gradle init script, `name` inside `tasks.register { }` is the task's name (12C)
`new File(dir, "cp-${name}.txt")` inside the register closure resolved to the task name, so three projects' dumps
overwrote one file. Use `project.name` (captured outside any execution-time closure for the configuration cache).

## 2026-10-15 — 12A's ci.yml never parsed: a scripted edit turned `printf '%s\n'` into a real line break
`port/26x` was first pushed after 12C, and CI failed in 0 s ("workflow file issue"): the 12A rewrite of the JDK step
wrote `\n` as a newline inside a `run: |` block, which ends the YAML scalar. Nothing local runs workflows, so it sat
unseen since 12A. After editing a workflow: `python -c "import yaml; yaml.safe_load(open(f))"` on it, and run its
shell steps locally with `GITHUB_OUTPUT` pointed at a temp file.

## 2026-10-15 — 26.2 moved screens, toasts and the hidden-HUD flag onto `Minecraft.gui` (12D)
`minecraft.screen` / `setScreen` → `minecraft.gui.screen()` / `gui.setScreen`; `getToastManager()` →
`gui.toastManager()`; `options.hideGui` is gone, F1 state is `minecraft.gui.hud.isHidden()`; the spectator GUI is
`gui.hud.getSpectatorGui()`. `AbstractWidget.extractRenderState` became final: override `extractWidgetRenderState`.
Entity predicates in data are keyed by sub-predicate ID (`"minecraft:entity_type"`, not `"type"`); a stale file fails
to parse at load with "Unknown registry key ... entity_sub_predicate_type: minecraft:type" and its loot table is gone.

## 2026-10-15 — The mixin body diff mixes anonymous classes; decide `$N` targets by javap (12D)
The scratch `mixindiff.py` collects every same-named method in the outer source file, so
`DispenseItemBehavior$3.execute` diffed against ~20 other `execute`s (63 lines, all noise). The question for a `$N`
target is only "which `$N` calls the method we wrap": `javap -c -p 'DispenseItemBehavior$N'` per version and grep for
it (26.2: still `$3`; `AnvilMenu.lambda$onTake$0` still calls `AnvilBlock.damage`).

## 2026-10-15 — An XP orb can stand for several: sum `value × count`, never `getValue()` alone (12D)
`SpawnerGameTest.withoutSilkTouch...GivesXp` failed once on 26.1.2 (8 XP, expected 15–43) and passed on every other
run. `ExperienceOrb.awardWithDirection` → `tryMergeToExisting` folds an equal-value orb into one nearby at random
(`id == random(40)`) by `count++`, and `count` is private. Count XP with `ExperienceOrbAccessor.e2test$getCount()`, as
`EffectEnchantmentsGameTest` already did. A check of "no orbs" / "some orbs" (GrindstoneGameTest) is merge-safe.
