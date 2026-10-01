# Changelog

All notable changes to folia-commons are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Fixed
- JitPack publication resolves shared module dependencies under the same release version.

## [1.1.0] - 2026-10-01

### Added
- Structured, immutable diagnostic entries with optional sections and explanations.
- Experimental location result calls, cancellable delayed entity/global/async work, and task groups.
- Experimental deterministic scheduling with virtual time, retirement, and shutdown simulation.
- Experimental monotonic deadlines for elapsed-time TTLs and cooldowns.
- Shared text parsing, legacy serializers, and escaping primitives, preserving library wrapper behavior.

### Fixed
- Built diagnostics remain unchanged when their builder is reused.
- Unknown and malformed server versions no longer enable features intended for newer servers.
- Accepted plugin-bound result calls fail on owner disable instead of remaining unresolved.
- Immediate entity work respects owner enablement; required scheduler inputs reject null consistently.

## [1.0.0] - 2026-10-01

### Added
- `Scheduler` and `TaskHandle`: one scheduling API for Folia and Paper, bound to a plugin, with a
  synchronous implementation for tests.
- `Scheduler#callForEntity`, `callGlobal` and `ensureForEntity`: futures for results from another thread, and
  "run now if this thread already owns the entity" (`SchedulingException` when nothing could be scheduled).
- `Scheduler#repeatForEntity`: a repeating entity task that receives a handle so it can cancel itself.
- `ServerVersion`: parses `1.x.y` and calendar-style versions and answers "is this at least 1.N.P".
- `Legacy`: legacy colour codes to MiniMessage.
- `Diagnostics`: a pasteable report of server facts and feature status.
- `LibraryVersion`: reads a library's own version from a build-filled resource, which survives shading.
- `FoliaEnvironment`: Folia detection.
