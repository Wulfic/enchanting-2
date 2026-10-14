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

## Phase 12 — Version bands: one jar per band, as many Minecraft versions per jar as hold up (Tier 2)

**Goal:** every release ships one jar per *band*. Band A = 1.21.11 (obfuscated, intermediary names, Java 21). Band B
= every stable 26.x line (26.1.2, 26.2, 26.3 today) in **one** jar (unobfuscated, Java 25). Every feature lands in
both bands in the same commit. The B jar is proven on each 26.x runtime by the full GameTest suite.

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

- [ ] 12A **Spike, band A only:** Stonecutter + loom-back-compat with the one 1.21.11 node. Exit: jar contents
      identical to v1.0.1 (class list + `fabric.mod.json` diff empty but the version), JUnit + server GameTests +
      client GameTests green, dev server still on 25566. If the tooling can't run GameTests per node or breaks
      Jankson's jar-in-jar: stop, bring the owner the branch-per-band fallback and its cost.
- [ ] 12B **Band B floor, 26.1.2 node:** Java 25, `fabric-loom` (no remap), `implementation` instead of `mod*`,
      `jar` instead of `remapJar`, access widener header `official`, Fabric API 26.1 renames (its porting page), vanilla
      changes (NeoForge 26.1 primer). 1.21.11 ↔ 26.1 differences behind Stonecutter conditions; anything used in more
      than ~3 places gets a small adapter class per band instead. Check each mixin against 26.1.2's source, not just
      "it compiles". Add a GameTest that loads a 1.21.11-written chunk/player (attachment codecs read the same).
      Exit: everything green on 26.1.2.
- [ ] 12C **Runtime matrix (the feasibility gate):** nodes `26.2` and `26.3` run the 26.1.2 jar's server GameTests
      with that line's Minecraft + Fabric API + YACL + Mod Menu. Write every failure into this file: mixin apply
      errors, `NoSuchMethodError`/`NoSuchFieldError`, YACL API breaks, behaviour diffs. Then apply the split rule
      above and record the band layout in decisions.md **before** writing any adapter.
- [ ] 12D **Adapters inside band B:** `compat/McVersion` (parse + compare, JUnit like mcMMO's
      `MinecraftGameVersionTest`), one adapter per failure from 12C, gated mixins via `VersionedMixins`, and every
      lookup logs and degrades (feature off, not crash) if it can't resolve. `fabric.mod.json` `minecraft` range = the
      versions the matrix proved (e.g. `>=26.1.2 <26.4-`); never open-ended, because a mixin mod on an untested
      version crashes at startup instead of saying "unsupported". Exit: one B jar, server GameTests green on
      26.1.2, 26.2, 26.3; client GameTests run locally on each.
- [ ] 12E **Scripts and process:** `scripts/` task that builds both bands and runs the whole matrix with a per-node
      report (~5–6 min per node with client GameTests). CI runs the matrix (server GameTests) on every push. AGENTS.md
      and state.md: "done" = green on every node of every band.
- [ ] 12F **Release:** `release.yml` builds both bands and attaches `enchanting2-<mod>+1.21.11.jar` and
      `enchanting2-<mod>+26.1.2-26.3.jar` (name from the proven range); `ReleaseDocsTest`, CHANGELOG and README list the
      supported versions per jar; wiki Installation page gets the version table. First band release = 1.1.0.
- [ ] 12G **New Minecraft versions from here on (process, not code):** add the new line to the matrix first. Green →
      widen band B's range, no new jar. Red → an adapter if it stays under the split rule, otherwise a new band. Dropping
      a band (mcMMO "raising the floor") is the owner's call; it becomes a frozen release, like mcMMO's 2.2.049.

**Risks:** rendering and `GuiGraphics` changes in the three window screens (26.2 was "primarily rendering"); YACL's
API across its per-version builds; enchantment component or registry changes in `MODIFY` hooks and `LevelCeilings`;
GameTest API changes in the gametest source set (it must also compile against the floor); 26.4 when it goes stable.

## Owner checks (open)

In-game checks only the owner can do, collected from Phases 1–11f. Each one was GameTested where a test could reach it;
these cover look, feel and real clients. Superseded checks (the old side panels, `anvil.show_preview_panel`) are gone.

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
- [ ] `docs/CURSEFORGE.md` is deleted in the working tree but not in git. Keep it or commit the deletion.

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
