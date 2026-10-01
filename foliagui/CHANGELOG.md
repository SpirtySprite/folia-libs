# Changelog

All notable changes to FoliaGUI are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- Experimental operation outcomes, per-player menu factories and observable asynchronous content loading.
- Managed text input with explicit outcomes, native fallback, validators and typed multi-step forms.
- Player theme resolution and configurable built-in prompt and control messages.
- Bounded supplier caching, targeted invalidation, remote pages and page-position policies.
- Search aliases, entry lifecycle operations, debounced asynchronous search and safe collection snapshots.
- Managed item editing and isolated stack snapshots.

### Fixed
- Count pages using effective content capacity and reject stale loading or search results.
- Enforce one viewer per GUI instance and preserve storage deposits across title changes and redraws.
- Remove prompt sessions conditionally and bind native callbacks to their expected window identity.
- Separate merchant result clicks from accepted purchases while preserving the deprecated click callback.
- Validate serialized inventory bounds and malformed item data before allocation.
- Schedule owner-thread window operations and location-thread sign sampling; terminate refused scheduling.
- Resolve inventory view access across the Bukkit class-to-interface change on 1.20.6 and newer servers.

### Changed
- Text parsing and serialization use shared folia-commons primitives, preserving null handling and item italic defaults.

## [1.2.0] - 2026-10-01

### Fixed
- No menu reacted to clicks on Paper versions that lack `UncheckedSignChangeEvent` (for example 1.21.4): Bukkit
  refused to register the whole listener. The sign handler now lives in its own `SignChangeListener`, which is only
  registered when the event exists.

### Changed
- Depends on folia-commons (`net.foliacommons:folia-commons`) for scheduling, server version detection and
  legacy colour conversion. Shade and relocate it together with this library.
- `PaperFoliaScheduler` is now backed by the folia-commons scheduler, so a plugin being disabled in the middle
  of a scheduling call no longer throws.
- Runtime state is no longer process-wide. A `FoliaGUIService` (create one with `FoliaGUI.create(plugin)`)
  owns the open-GUI registry, navigation history, anvil/sign/merchant/chat sessions, theme and listener.
  `FoliaGUI.init` still creates a default service and all existing static helpers keep working on it.
- `BaseGui#service()`, `BaseGui#service(FoliaGUIService)` and `.service(...)` on the GUI builders bind a GUI
  to an explicit service. `ChatPrompt.ask(service, player, ...)` and `hasSession(service, player)` were added.
- `FoliaGUI.init` from a second plugin while a default service exists now logs a warning instead of
  silently doing nothing.
- The item identity tag uses the fixed key `foliagui:item` instead of a key namespaced by the owning plugin.
- Internal `handleClick`/`handleClose`/`handleDrag`/`handleSignChange`/`handleQuit` methods on `AnvilGui`,
  `MerchantGui` and `SignGui` now take the service as their first argument. They are `@ApiStatus.Internal`.

### Deprecated
- `com.foliagui.util.Legacy`. Use `net.foliacommons.text.Legacy`.

### Removed
- `GuiManager.register` and `GuiManager.unregister` (internal, now on `GuiRegistry`).

### Fixed
- A `GuiClickEvent` listener could un-cancel a click on a protected slot. Protected slots now stay
  locked regardless of listeners.
- Asking a new `ChatPrompt` for a player who already had one pending now completes the old prompt's
  callback with `null` instead of leaving it waiting forever.
- `BaseGui.open` no longer reads the inventory's viewers from the caller's thread.
- Made two async-content tests deterministic (they failed intermittently).

### Added
- `FoliaGUIService#stats()` (`FoliaGUIStats`) and `FoliaGUIService#diagnose()`: what a service is doing now, and
  a report of which features work on this server.
- Continuous integration with a Paper API version matrix, CodeQL analysis and Dependabot.
- Release workflow: pushing a `vX.Y.Z` tag builds the project and publishes a GitHub release.
- `CONTRIBUTING.md`, `SECURITY.md`, issue and pull request templates, and `.editorconfig`.
- Sources and javadoc jars are built with every `mvn verify`.

## [1.0.0]

Initial tracked release. See the git history for earlier changes.
