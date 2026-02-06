# Changelog

All notable changes to BetterStructures-FAWE will be documented in this file.

## [2.1.2-FAWE.1]

Based on upstream BetterStructures 2.1.2. All changes below are relative to the original release.

### Fixed

- **PersistentDataContainer for chunk tracking**: Replaced `isNewChunk` flag with PersistentDataContainer to reliably track processed chunks, preventing duplicate structure placement on server restart.
- **Cross-chunk structure placement**: Fixed 5x5 transparent chunk holes by verifying chunk generation before world block access in `createPasteBlocks()` and `assignPedestalMaterial()`, ensuring all surrounding chunks are loaded before pasting.

### Changed

- **Async block pasting via FAWE**: Migrated block pasting from the WorkloadRunnable frame-splitting system to FAWE asynchronous EditSession, completely eliminating main thread blocking.
  - Hard dependency changed from WorldEdit to FastAsyncWorldEdit in `plugin.yml`.
  - FAWE is detected on startup; plugin disables itself if FAWE is not present.
  - Removed WorkloadRunnable system; all blocks (including NBT) are now pasted through a single FAWE async EditSession.
  - Bedrock checking uses `editSession.getBlock()` asynchronously.
  - Entity spawning and chest filling are deferred to the main thread after async paste completion.
  - Removed deprecated `createSingleBlockClipboard` method from WorldEditUtils.
- **Parallelized schematic loading**: Schematic and module file loading now uses `parallelStream()`, significantly reducing plugin startup time. Timing logs added for diagnostics.

### Added

- **Chinese localization**: Full Chinese language support throughout the codebase.
- **README**: Added project README.

### Notes

- The config option `percentageOfTickUsedForPasting` is now obsolete due to the fully async architecture.
