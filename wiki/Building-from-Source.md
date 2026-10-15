# Building from Source

Needs **JDK 25** (Gradle runs on it; Fabric Loom requires it) and Git. The mod itself is compiled for each version's Java
(21 for 1.21.11, 25 for 26.x): Gradle uses an installed JDK or downloads one. The Gradle wrapper downloads everything else. With an older `JAVA_HOME`
the build stops at once and says which JDK to point it at.

```sh
git clone https://github.com/Wulfic/enchanting-2.git
cd enchanting-2
./gradlew build                          # every version: jars in versions/<version>/build/libs/, JUnit + server GameTests
./gradlew :1.21.11:runClientGameTest     # client screen tests for one version (opens a game window, ~3 minutes)
./gradlew :1.21.11:runClient             # a development client with Mod Menu and YACL
./gradlew :1.21.11:runServer             # a development server on port 25566
```

On Windows use `gradlew.bat` (or `./gradlew` from Git Bash).

The build uses [Stonecutter](https://stonecutter.kikugie.dev/): one source tree, one Gradle subproject per Minecraft
version under `versions/<version>/` (that version's `gradle.properties` and build output): `1.21.11`, `26.1.2` and
`26.2`. A bare task name like `build` runs in every version; `:1.21.11:`, `:26.1.2:` or `:26.2:` picks one. Run, launch and client
GameTest tasks always name the version, or every version's game would start at once.

Some Minecraft versions run a jar built for another one: the 26.1.2 jar also covers 26.1 and 26.1.1. Those are proven
by the runtime matrix under `matrix/<version>/`, which compiles nothing and runs a built jar plus its GameTests on that
version. `./gradlew :matrix-26.1:linkCheck` checks in seconds that every Minecraft, Fabric and mixin reference
resolves there; `:matrix-26.1:runGameTest` and `:matrix-26.1:runClientGameTest` run the tests.

## Layout

| Path | What's there |
|---|---|
| `src/main/` | the mod (both sides): rules, costs, menus, mixins, data |
| `src/client/` | client-only code: screens, tooltips, the config screen |
| `src/test/` | JUnit tests (no game needed) |
| `src/gametest/` | server and client GameTests, a separate test mod never shipped in the jar |
| `docs/` | [DESIGN.md](https://github.com/Wulfic/enchanting-2/blob/main/docs/DESIGN.md) (the full spec), CONFIG.md and ENCHANTMENTS.md (published here as [Configuration](Configuration) and [Enchantments](Enchantments)) |
| `wiki/` | this wiki's source |
| `versions/<version>/` | per-Minecraft-version settings (`gradle.properties`: Minecraft, Fabric API, Java) and that version's `build/` |
| `stonecutter.gradle` | which version `src/` is currently written for (`stonecutter.active`) |

## Tests

- **JUnit** covers the pure logic: the XP curve, the cost model, config parsing, and checks that the docs match the
  code (`ConfigDocsTest` fails when a config default changes and `docs/CONFIG.md` doesn't; `ReleaseDocsTest` checks
  the changelog, the license and every wiki link).
- **Server GameTests** run real game mechanics on a test server: enchanting, anvils, grindstones, Fortune, every
  enchantment's effect.
- **Client GameTests** drive the real client: screens, tooltips, Double Jump.

A new GameTest class must be listed in `src/gametest/resources/fabric.mod.json`, or it silently never runs.

## Continuous integration

Every push to `main` and every pull request runs `./gradlew build` and a [gitleaks](https://github.com/gitleaks/gitleaks)
secret scan over the whole history. The built jar is attached to each run for 14 days. Client GameTests need a display
and aren't run in CI.

## Making a release

1. Set `mod_version` in `gradle.properties` and add a `## [<version>] - YYYY-MM-DD` section to `CHANGELOG.md`.
   `./gradlew build` fails until both agree.
2. Commit, push to `main`, and wait for CI to go green.
3. Tag and push the tag:
   ```sh
   git tag v1.2.3
   git push origin v1.2.3
   ```

The **Release** workflow checks the tag is exactly `v` + `mod_version`, is on `main`, and has a CHANGELOG section;
builds and tests; and publishes a GitHub Release with the jar, using that CHANGELOG section as the notes. It never
replaces an existing release. The **Wiki** workflow publishes `wiki/` on every push to `main` that changes it.

## Contributing

Issues and pull requests are welcome. Keep a change small, add a test that fails without it, and run `./gradlew build`
before opening the PR.
