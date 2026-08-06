# Changelog

This file records version changes for BRNTalk.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project follows [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Nothing yet.

## [1.3.1] - 2026-08-06

### Fixed

- Fixed an issue where the HUD display limit could discard earlier messages before they began playing when a batch of consecutive messages arrived.
- Fixed the awaiting-response prompt appearing outside its message background when the speaker name was hidden for consecutive messages from the same speaker.

## [1.3.0] - 2026-08-01

### Added

- Added general client-state listeners and change events so the state layer no longer identifies or refreshes a specific screen directly.
- Added dedicated text formatting, timeline, preview, layout, chat rendering, scrolling, and texture-button components to reduce `TalkScreen` responsibility coupling.

### Changed

- Thread selection, stable ordering, and unread evaluation are now managed by `ClientTalkState` using `threadId`, with deterministic fallback after full synchronization.
- New messages no longer force the view to the bottom while reading history; the view still follows when already at the bottom and moves to the bottom when switching threads.
- Message rendering caches now remove stale entries and enforce a size limit to avoid retaining obsolete data after long sessions or full synchronization.
- Read requests are deduplicated per activity timestamp and client read state waits for server confirmation.

## [1.2.0] - 2026-07-15

### Added

- Validation reports now keep structured issue data, and logs include resource, script, message, and choice locations for author debugging.
- Added non-blocking validation warnings for blank message/choice text, non-resumable `wait` nodes, and messages unreachable from the start node.

### Changed

- After `/reload`, the in-game admin validation message stays as a short summary while detailed validation information is written to `latest.log`.
- `/reload` validation summaries and legacy data migration messages now use translation keys with built-in Simplified Chinese and English text.
- Runtime logs now include script, thread, and message locations when an `action` command fails, returns 0, or automatic progression reaches the safety limit.
- Documented the stable boundaries and event semantics for `BrntalkAPI` and `PlayerSeenMessageEvent`.

### External Tooling

- Added rule-level validation fixtures for every current blocking error and non-blocking warning.
- Added `tools/debug/run-validation-fixtures.ps1` to inject fixtures, run `reload`, and verify logs in batches through RCON.
- Updated `docs/EXTERNAL_VALIDATION_PLAN.md` to describe the current fixture, RCON smoke, and batch validation workflow.

### Fixed

- [1.20.1] Fixed a Forge dedicated-server crash caused by client packet handler registration loading client UI classes during common setup.

## [1.1.1] - 2026-07-11

### Changed

- `/brntalk` command feedback now uses translation keys, with built-in Simplified Chinese and English text selected by the player's client language.
- Message previews in the conversation list now truncate to the actual available width, using space more effectively and avoiding overflow across GUI sizes.
- Chat content now follows the bottom smoothly when typewriter text wraps onto new lines, reducing visible jumps as messages grow.

### Fixed

- Fixed long conversations being clipped at the bottom and becoming impossible to scroll further at certain content heights.
- Fixed render and timeline caches being reused across conversations with matching internal message IDs, which could cause one conversation to display another conversation's content.
- Fixed the chat viewport height and scroll range not updating when choice buttons appear after the typewriter animation finishes.

## [1.1.0] - 2026-07-08

### Changed

- Performed a larger multi-version refactor and organized the project around the 1.21.1 NeoForge and 1.20.1 Forge maintenance branches.
- Consolidated platform-specific logic into the Platform package so core runtime code depends less directly on loader APIs.
- Release artifact names now include the target Minecraft version, making same-version BRNTalk builds for different MC lines easier to distinguish.

### Fixed

- Completed the 1.20.1 Forge adaptation with additional lower-version fixes for Java 17 API differences, Forge networking/runtime differences, resource-pack metadata, legacy data migration prompts, and talk UI list/scrollbar behavior.

## [1.0.2] - 2026-06-10

### Added

- After `/reload`, BRNTalk now sends an in-game validation summary to online permission-level-2 players when dialogue scripts fail validation, with a pointer to `latest.log` for full details.
- Added the server config options `sendValidationReportInGame` and `validationReportMaxDetailLines` to control whether these in-game reports are sent and how many detail lines are included.

### Fixed

- Dialogue script loading now uses strict validation: broken links, duplicate IDs, empty choice nodes, and infinite TEXT auto-advance loops will block the script from loading.

## [1.0.1] - 2026-05-21

### Security

- Restricted the server-side `/brntalk` management command to permission level 2, usually OP-level access.
- Fixed a production risk where normal players could indirectly trigger dialogue script `action` commands through commands such as `/brntalk start`.

### Fixed

- Fixed `action` commands not running on the target message node after selecting a branch choice or resuming from a `wait` node.
