# Contributing

Thanks for helping out. This repository holds libraries for Paper and Folia plugins. The two rules that
matter most are: keep every public call safe from any thread, and keep the public API small and stable.

## Layout

| Directory | Library |
|---|---|
| `foliaboard/` | FoliaBoard: sidebars, nametags, tab lists, boss bars |
| `foliagui/` | FoliaGUI: inventory GUIs |
| `folianpc/` | FoliaNPC: packet-based NPCs |
| `folia-commons/` | Code shared by the libraries: scheduling, text, version detection |
| `integration/`, `integration-bot/` | Tests that run the libraries in real Paper and Folia servers (not published) |
| `benchmarks/` | JMH benchmarks (not published) |

Each directory is a Maven module with its own version, `README.md` and `CHANGELOG.md`. The parent
`pom.xml` holds the plugin versions, Java level, Paper API version and coverage check, so they are set
in one place.

## Building

Java 21 and any recent Maven:

```
mvn verify                    # everything
mvn verify -pl foliagui -am   # one module, plus what it depends on
```

`verify` compiles, runs the tests, builds the sources and javadoc jars, and checks that line coverage
has not dropped below the module's `coverage.minimum`. Raise that number in the module's `pom.xml`
when its tests improve; never lower it to make a build pass.

## Compatibility checks

FoliaBoard, FoliaNPC and folia-commons support several Paper versions. CI builds them against each, and
you can do the same locally:

```
mvn verify -Dpaper.version=1.20.6-R0.1-SNAPSHOT -pl '!foliagui'
```

This checks that the code compiles and unit-tests against that API. FoliaGUI is tested only against the Paper
version its MockBukkit dependency targets.

## Tests on real servers

Mocks cannot tell you that a class is missing on an older server or that a reflection lookup changed, so CI also
starts real servers. A test plugin that contains all four libraries (shaded and relocated, like a real plugin) runs
checks on Paper and Folia for 1.20.6, 1.21.4, 1.21.8 and 1.21.11. On 1.21.8 and 1.21.11 a headless client also
joins and the script checks the packets it receives: a sidebar, an NPC and an open menu.

```
mvn -Pintegration package -DskipTests -pl integration -am
integration/scripts/run-server-test.sh folia 1.21.4            # server checks only

# with the test client (pick the client release that matches the Minecraft version)
mvn -Pintegration package -DskipTests -pl integration-bot -Dmcprotocollib.version=1.21.11-1
BOT_JAR=integration-bot/target/folia-integration-bot.jar integration/scripts/run-server-test.sh paper 1.21.11
```

The script downloads the server from PaperMC into `~/.cache/folia-libs`, runs it in
`integration/target/server-<server>-<version>/` and keeps the log there. Needs Java 21, curl and python3.
A feature a library documents as unavailable on an old server (for example anvil input before 1.21) is listed
in `IntegrationPlugin` as a documented limit; anything else the library's own report calls a problem fails the run.
If you touch packet or reflection code, run it for the versions you changed.

## Code guidelines

- Anything under an `internal` package is not public API and may change freely. Do not reference it from
  documentation.
- New public types and methods need javadoc and a test.
- Do not block a region thread. Anything slow (HTTP, disk, database) runs asynchronously and hands its
  result back through the scheduler.
- Keep the style of the surrounding code. `.editorconfig` covers whitespace.

## Pull requests

- One logical change per pull request.
- Add a line to the module's `CHANGELOG.md` under "Unreleased" for anything a user would notice.
- Breaking changes to public API need a deprecation cycle first, unless the type was never released.

## Releasing (maintainers)

Each module is released on its own.

1. In the module's `CHANGELOG.md`, move the "Unreleased" notes under a new `## [x.y.z]` heading.
2. Set the module's version in its `pom.xml` to `x.y.z`.
3. Tag the commit `<module>-vx.y.z` (for example `foliagui-v1.1.0`) and push the tag. The release workflow
   checks that the tag matches the pom, builds the module and what it depends on, and publishes a GitHub
   release with the jars and the changelog notes.
