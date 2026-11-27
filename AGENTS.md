# AGENTS.md — TerraSync Contributor & Agent Guide

## 1. Architecture Overview
- **Single-Activity + Extracted Controllers:** `MainActivity.kt` is the slim host (~2,380 lines) owning Activity lifecycle, view binding, inset/chrome theming, ActivityResultLaunchers, and top-level navigation between map chrome and full-screen overlays.
- **UI-Agnostic State (`MapViewModel.kt`):** Holds domain & interaction state (`FolderIndexSnapshot`, `visibleStatuses`, `hideSentPins`, selection/stack state, `GeoPoint` markers, ruler/lasso/navigation/search state) with **zero** Android `Context`, `View`, or MapLibre `Style` imports.
- **Unified Data Layer (`TerraSyncRepository.kt`):** Merges local SQLite WAL folders (`LocalFolderStore`) and Firestore cloud folders (`SyncRepo`) into immutable `FolderIndexSnapshot` instances, builds spatial stack buckets off the UI thread (`AppExecutors.uiQueryExecutor` when `>= 200` pins), and routes unified CRUD mutations (`savePinStatus`, `savePinsStatusBatch`, `savePinText`, `deletePinHistoryEntry`, `restorePinStatusSnapshot`, `resetPinStatus`, `deletePin`, `deletePins`, `addManualPin`, `renameLayer`, `deleteLocalLayer`).
- **Focused Feature Controllers (explicit constructor injection + `destroy()` cleanup):**
  - `MapLayerRenderer.kt`: MapLibre vector/satellite/contour styles, GeoJSON sources/layers, pulse animations, camera framing.
  - `GpsLocationController.kt`: GPS/Network provider selection (`ENV-01` aware), speed fallback estimation, location puck & follow state.
  - `NavigationController.kt`: OSRM/offline route preview, `Navigate from GPS` / `Route From`, maneuver banners, Android TTS announcements.
  - `MapToolsController.kt`: Lasso selection, Ruler distance tool, pin/coordinate tap hit-testing, reverse geocoding, deep links.
  - `PinCardSheetController.kt`: Bottom-sheet pin card, stacked-pin cycler (`Next ->`), quick actions, audit log rows, 5s status undo bar.
  - `PlaceAndPinDialogController.kt`: Tap-anywhere place cards, Field Issue / Done preset sheets, Edit Pin & Add Manual Pin dialogs.
  - `SearchController.kt`: Version-cached token/coordinate search index, RecyclerView `ListAdapter`, Nominatim lookup, nearest-pending jump.
  - `FolderActionsController.kt`: KML/KMZ/CSV export & streaming import, share codes, folder rename/delete, Combine Folders, Local -> Cloud upload.
  - `BookmarksPageController.kt`: Full-screen Bookmarks & Folders page, master visibility toggles, cross-folder search, progress bars.
  - `PinsListController.kt`: Per-folder & Starred pins list, background duplicate/stack clustering, Nearest/A-Z sort, bulk Done/Pending/Issue/Delete.
- **Legacy Extension UI Files (Keep As-Is):** `AboutAndLayersUi.kt`, `DailyReportUi.kt`, `EditHistoryUi.kt`, `FilterSheetUi.kt`, and `SettingsPagesUi.kt` remain `internal fun MainActivity.` extensions backed by `internal` delegates on `MainActivity`. **Do NOT create any new `internal fun MainActivity.xxx()` files** — extract real controller classes with explicit dependencies instead.

## 2. Storage & SQLite WAL Rules (`LocalFolderStore.kt`)
- **`minSdk = 24` Compatibility (SQLite 3.9):**
  - **NEVER** use `INSERT ... ON CONFLICT(...) DO UPDATE` (added in SQLite 3.24).
  - **NEVER** use `INSERT OR REPLACE INTO local_layers` because `foreign_keys = ON` and child tables (`local_pins`, `local_tracks`) declare `ON DELETE CASCADE` — replacing a parent row silently wipes prior pins/tracks on restart. Always use `UPDATE local_layers SET ... WHERE id = ?` first and only `INSERT INTO local_layers` when `executeUpdateDelete() == 0` (`upsertLayer`).
  - Always execute SQLite `PRAGMA` statements via `rawQuery(...).use { it.moveToFirst() }`, never `execSQL`.
- **Non-Blocking UI Writes:** Mutating methods update the volatile `cachedSnapshot` synchronously and serialize SQLite transactions onto single-thread FIFO `ioExecutor` with a single-flight legacy JSON migration (`local_folders_v1.json` -> `local_folders_v2.db`, preserving `.migrated.bak` after verified count match). Call `flushPendingWritesForTest(ctx)` in tests before asserting disk persistence.
- **Shared Edit-History Audit:** Always route edit-history deletion recalculations through `PinEditHistoryAudit.recomputeFromHistoryLines(...)`.

## 3. Android Auto & Background Concurrency
- `TerraSyncCarAppService.kt` (`TerraSyncCarFoldersScreen`) refreshes local storage on `onResume` (`invalidateCache`) and syncs mark-Done updates cleanly alongside `SyncRepo` and `LocalFolderStore`.
- Use `AppExecutors` (`uiQueryExecutor`, `batchIoExecutor`, `postMain`) rather than ad-hoc unbounded threads. Always guard async UI callbacks with lifecycle checks (`!isFinishing && !isDestroyed` / `isActivityAlive()`) and clean up listeners/animators/TTS in controller `destroy()`.

## 4. Build, CI & Testing
- **CI Workflow (`.github/workflows/build.yml`):**
  - Core stress & JVM tests (`-Xmx128m`): `./gradlew testDebugUnitTest --tests "*TerraSyncCoreStressTest"`
  - Robolectric SQLite WAL & offline routing tests (`-Xmx512m`): `./gradlew testDebugUnitTest --tests "*OfflineRoutingAndStoreRobolectricTest"`
  - Release APK build: `./gradlew assembleRelease`
  - Tag push (`v*`) publishes a GitHub Release automatically.
- **Where to Add Tests:** Add new unit tests inside `TerraSyncCoreStressTest.kt` (pure JVM) or `OfflineRoutingAndStoreRobolectricTest.kt` (Robolectric/SQLite/Context) so CI executes them automatically.
- **Versioning (`app/build.gradle.kts`):** `versionName` derives from exact git tag on `HEAD` or fallback `tagVer`; `versionCode` computes `major*1000000 + minor*10000 + patch*100 + 99`.

## 5. Theme, Localization & Hardware Safety Conventions
- Preserve Light/Dark parity via `DesignSystem.palette(isDark)` and RTL/BiDi safety (`DesignSystem.isolateBidi` / `joinBidi`, English/Kurdish/Arabic in `AppStrings.kt`).
- Keep `DevicePrefs` backward-compatible when upgrading in place.
- **v3.13.0 Conventions:** Night `actionDark` is emerald (`#238E5E`) with white `onActionDark`; the Undo banner uses `StatusUndoController.bannerFillColor(isDark)` (never `actionDark`). Map route layers sit above pin layers and below the selected pin/coord/ruler/user puck (`MapLayerRenderer.attachOverlayLayers`). Keyboard glide uses `UiAnimations.attachImePaddingGlide` and Predictive Back uses `UiAnimations.applyPredictiveBackProgress`; both respect `UiAnimations.isReducedMotion`. `TempCacheCleaner` purges `terrarium_dem`/`exports` contents but keeps the directories. Directional drawables (e.g. `ic_back`) must set `android:autoMirrored="true"`.
- Never alter screen resolution/density (`wm size/density`) or commit `.test_runs/` artifacts.
