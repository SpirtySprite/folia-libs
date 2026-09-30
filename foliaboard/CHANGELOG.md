# Changelog

All notable changes to FoliaBoard are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Changed
- Depends on folia-commons (`net.foliacommons:folia-commons`) for scheduling, server version detection and
  legacy colour conversion. Shade and relocate it together with this library.
- `net.foliaboard.internal.scheduler.Schedulers` is now a thin layer over the folia-commons scheduler.
  Timers started through it while the plugin is being disabled are no longer created.
- `FoliaBoard` is now a thin entry point. Features live in focused services reachable through
  `boards()`, `tabs()`, `bossBars()`, `nametags()` and `objectives()` (new public interfaces `Boards`, `Tabs`,
  `BossBars`, `Nametags`, `Objectives`). `createBoard`, `sidebar`, `createNametag`, `nametag`, `tab`,
  `bossBar`, `placeholders`, `stats`, `plugin` and `close` stay on `FoliaBoard`.
- The constructors of `BoardBuilder`, `TabBuilder`, `BossBarBuilder` and `NametagBuilder` now take the internal
  service and are marked `@ApiStatus.Internal`. Create builders through `FoliaBoard`, as documented.

### Deprecated
- `net.foliaboard.api.text.Legacy`. Use `net.foliacommons.text.Legacy`.
- Every other former `FoliaBoard` method (`setGlobalSidebar`, `registerLayout`, `applyLayout`, `belowName`,
  `tabName`, `tabHeaderFooter`, `setGlobalTab`, `hideBossBar`, and so on). They still work and forward to the
  services. They will be removed in 2.0.

### Removed
- Lifecycle plumbing that was public by accident: `FoliaBoard#handleJoin`, `handleQuit`, `handleWorldChange`,
  `markBuilderOwned`, `trackRefresh`, `recordRefresh`, `nametagInternal`, `trackTab`, `trackBossBar`,
  `forgetBossBar`. The library's own listener handles these; nothing in the public API needed them.

### Fixed
- Objective and team names now include a per-plugin namespace. Two plugins that each shade FoliaBoard
  no longer overwrite each other's sidebars, below-name/tab objectives or nametag teams.

### Added
- Continuous integration with a Paper API version matrix, CodeQL analysis and Dependabot.
- Release workflow: pushing a `vX.Y.Z` tag builds the project and publishes a GitHub release.
- `CONTRIBUTING.md`, `SECURITY.md`, issue and pull request templates, and `.editorconfig`.
- Sources and javadoc jars are built with every `mvn verify`.

## [1.0.0]

Initial tracked release. See the git history for earlier changes.
