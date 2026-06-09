# Changelog

This file records version changes for BRNTalk.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project follows [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Nothing yet.

### Fixed

- Nothing yet.

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
