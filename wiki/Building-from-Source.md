# Building from Source

Needs **JDK 21** and Git. The Gradle wrapper downloads everything else.

```sh
git clone https://github.com/Wulfic/enchanting-2.git
cd enchanting-2
./gradlew build                # jar in build/libs/; runs the JUnit suite and the server GameTests
./gradlew runClientGameTest    # client screen tests (opens a game window, about 2.5 minutes)
./gradlew runClient            # a development client with Mod Menu and YACL
./gradlew runServer            # a development server on port 25566
```

On Windows use `gradlew.bat` (or `./gradlew` from Git Bash).

## Layout

| Path | What's there |
|---|---|
| `src/main/` | the mod (both sides): rules, costs, menus, mixins, data |
| `src/client/` | client-only code: screens, tooltips, the config screen |
| `src/test/` | JUnit tests (no game needed) |
| `src/gametest/` | server and client GameTests, a separate test mod never shipped in the jar |
| `docs/` | [DESIGN.md](https://github.com/Wulfic/enchanting-2/blob/main/docs/DESIGN.md) (the full spec), CONFIG.md and ENCHANTMENTS.md (published here as [Configuration](Configuration) and [Enchantments](Enchantments)) |
| `wiki/` | this wiki's source |

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
