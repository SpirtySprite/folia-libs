# Changelog

All notable changes to folia-commons are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

## [Unreleased]

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
