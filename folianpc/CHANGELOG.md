# Changelog

All notable changes to FoliaNPC are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- Experimental autonomous packet-NPC controllers with bounded minimum-cost goal planning, managed inventories, dynamic world perception and custom actions.
- Region-owned mining, pickup, block placement, explicit crafting and station-bound smelting, with authorization and stale-goal commit gates.
- Opt-in collision-checked gap jumps, terrain penalties, occupied-block approach radii and cooperative route cancellation.
- Coherent position snapshots and conditional movement starts for lifecycle-safe action composition.

### Fixed
- Search edges now reject obstructed movement arcs before selecting a route, allowing reachable detours.
- Navigation uses a heuristic consistent with its horizontal and vertical edge costs.

## [1.4.1] - 2026-10-01

### Fixed
- JitPack publication resolves shared module dependencies under the same release version.

## [1.4.0] - 2026-10-01

### Added
- Managed patrol and follow behavior with waits, distance limits and lifecycle completion.
- Coherent appearance batches, per-viewer appearance and equipment overrides, and configurable entity-relative nametags.
- Bounded skin caches and network concurrency, typed fetch outcomes and latest-request-only skin application.
- High-level persistence loading with migration reports for dropped fields, schema assumptions and substituted values.
- Snapshot-based navigation with bounded search options, clearance dimensions, ground following and observable movement outcomes.
- Visibility hysteresis for existing viewers.

### Fixed
- Arrival callbacks retain committed viewer movement; queued updates reconcile against the last sent position.
- Existing nametag displays reposition after layout, scale, pose and baby changes, including large offsets.
- Appearance recipes and text-only nametag edits use partial updates; removing equipment sends explicit empty slots.
- Pending navigation completes on cancellation, replacement, removal, shutdown and search failure.
- Visibility predicates and viewer updates run on player ownership threads; hidden and stale clicks are rejected.
- Duplicate UUIDs and creation after shutdown are rejected; equipment and position snapshots are independent and coherent.
- Nonfinite coordinates, speed, scale, view distance and proximity inputs are rejected.

### Deprecated
- The boolean navigation overload remains supported; the movement task overload also reports arrival and cancellation.

### Changed
- Depends on folia-commons 1.1.0 for the shared scheduling, lifecycle and text primitives.
- Text parsing and serialization use shared folia-commons primitives, preserving empty input and escaping behavior.

## [1.3.0] - 2026-10-01

### Fixed
- Villager data (type, profession, level) failed to bind on servers older than 1.21.5, where the constructor takes
  plain registry values instead of holders. Both forms are handled.
- Registry lookups (mob variants, villager data) are now resolved on 1.20.6, where they were silently disabled.

### Performance
- The visibility pass only examines players within an NPC's view distance (or proximity radius). Players are
  sorted into 32 block squares once per pass. 10,000 NPCs with 500 players spread over a large world take
  about 3 ms per pass instead of about 48 ms. Worlds with 16 players or fewer are scanned in full as before.

### Changed
- Depends on folia-commons (`net.foliacommons:folia-commons`) for scheduling, server version detection and
  legacy colour conversion. Shade and relocate it together with this library.
- Scheduling goes through the folia-commons scheduler. Exceptions other than "plugin disabled" thrown while
  scheduling are no longer silently swallowed.

### Deprecated
- `net.folianpc.api.Legacy`. Use `net.foliacommons.text.Legacy`.

### Added
- `FoliaNpc.VERSION` and `FoliaNpc#diagnose()`: a report built from `capabilities()` and `stats()` to paste
  into bug reports.
- `NpcData.builder()` and `NpcData#toBuilder()`: build and copy snapshots without a 21-argument constructor.
- `NpcData#serialize()`, `NpcData.deserialize(map)` and `NpcDataCodec`: a versioned, forgiving map format
  for storing NPC snapshots in YAML, JSON or a database.

### Deprecated
- The `NpcData` constructors. Use `NpcData.builder()`. They still work and will not be removed before 2.0.

### Fixed
- Players riding a vehicle now keep their tracked position up to date (via `VehicleMoveEvent`), so NPCs
  spawn and despawn for them while they ride. Movement of a rider is not reported as player movement.
- Skin lookups that fail (Mojang outage or rate limit) are remembered for 5 seconds instead of being
  retried on every request. Configure with `FoliaNpc#skinFailureCooldown`; `Duration.ZERO` disables it.
- `fetchSkinFromUrl` results are cached per URL and concurrent requests for one URL share a single call.

### Added
- Continuous integration with a Paper API version matrix, CodeQL analysis and Dependabot.
- Release workflow: pushing a `vX.Y.Z` tag builds the project and publishes a GitHub release.
- `CONTRIBUTING.md`, `SECURITY.md`, issue and pull request templates, and `.editorconfig`.
- Sources and javadoc jars are built with every `mvn verify`.

## [1.1.1]

Initial tracked release. See the git history for earlier changes.
