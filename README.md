# TerraSync

[![Latest Release](https://img.shields.io/github/v/release/Thamirakkurd/TerraSync?include_prereleases&label=Release&color=2563EB)](https://github.com/Thamirakkurd/TerraSync/releases)
[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](https://github.com/Thamirakkurd/TerraSync/releases)
[![Language](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)

**TerraSync** is a collaborative Android field-mapping, bookmarking, and turn-by-turn navigation app built in Kotlin with MapLibre GL and Firebase Firestore. Designed for field survey and utility teams, it combines live shared pin folders, offline maps and routing, full KML/KMZ status round-tripping, and end-of-day team reporting.

---

## Key Features

### Live Team Sync & Folder Management
* **Real-Time Shared Folders:** Share any folder with a 6-character code so field teams stay synced on pin updates in real time (with local Firestore caching for offline resilience).
* **Multi-Status Pin Workflow:** Track pins as **Pending**, **Done**, or **Issue** with quick-select notes and full edit-history audit logs.
* **Owner Moderation Controls:** Folder creators can delete individual pin edits (automatically recalculating status from remaining history) without deleting the pin itself, protect pinned folders, and manage cloud deletion.
* **Cross-Folder Search & Filtering:** Search across all joined folders, filter by pin status, and cycle cleanly through stacked pins at identical coordinates.

### Maps, Navigation & Offline Support
* **Vector & Satellite Maps:** Switch between crisp vector street maps and satellite hybrid imagery with optional 3D buildings, terrain hillshading, and Day/Night/Auto UI themes.
* **Valhalla + Local Offline Routing:** Turn-by-turn routing for **Car**, **Bicycle**, and **Walk** modes powered by Valhalla (with OSRM fallback) and a built-in **on-device A\* offline routing engine** using downloadable regional road graphs.
* **Offline Map Packs:** Download regional packs (Kurdistan & Southern Iraq) with bundled offline road routing for full offline field use.
* **Field Utilities:** Live GPS speed indicator, multi-point distance ruler, local lasso selection for tracking sent pins, customizable map buttons, and Android Auto support.

### KML/KMZ Round-Trip & Reporting
* **Status-Preserving KML Export & Import:** Exports and re-imports `Done`, `Issue`, and `Pending` statuses, notes, editor stamps, and edit logs cleanly across devices.
* **Auto-Numbered Imports:** Automatically appends `(2)`, `(3)`, etc. when importing a KML/KMZ with a duplicate folder name.
* **End-of-Day WhatsApp Reports:** Generates a ready-to-share team summary with per-team submission breakdowns and **deduplicated unique Done counts**.

---

## Installation

1. Open the **[Releases](https://github.com/Thamirakkurd/TerraSync/releases)** page on your Android device.
2. Download the latest signed `.apk` under **Assets**.
3. Install the APK (enable *Install unknown apps* if prompted).
4. **Releases & Updates:** Check **About → Releases & Updates** anytime to download new stable or pre-release builds from GitHub Releases.

---

## Quick Start

1. **Set Your Team / Device Name:** Open **Settings** and set a custom name so your pin edits and end-of-day reports clearly identify your team.
2. **Import or Join a Folder:** Tap **Import KML/KMZ** (or enter a 6-character share code) to load work points.
3. **Prepare for Offline Areas:** Open **Settings → Offline Maps** to download both map tiles and **Offline Routing Road Graphs** for your work area before heading out.
4. **End-of-Day Export:** Open a folder's export menu to share a **WhatsApp Team Report** (deduplicated counts) or export filtered KML/CSV files.

---

## Building from Source

### Prerequisites
* Android SDK (API 34+) and JDK 17
* A Firebase project with Cloud Firestore enabled and your own `app/google-services.json`

### Local Build
```bash
git clone https://github.com/Thamirakkurd/TerraSync.git
cd TerraSync
# Place your Firebase config at app/google-services.json
./gradlew assembleDebug
```

### Automated Signed Releases (GitHub Actions)
Push a version tag to trigger an automated signed APK build and GitHub Release:
```bash
# Stable release (delivered to all users)
git tag v2.7.0 && git push origin v2.7.0

# Beta pre-release (delivered only to users with Beta Updates enabled)
git tag v2.7.0-beta.1 && git push origin v2.7.0-beta.1
```

---

## Tech Stack
* **Language:** Kotlin (Android)
* **Mapping:** MapLibre GL Native + OpenFreeMap vector tiles
* **Routing:** Valhalla API, OSRM fallback, and custom pure-Kotlin offline A* road-graph engine
* **Backend Sync:** Firebase Cloud Firestore (with persistent offline cache)
* **CI/CD:** GitHub Actions

---

## License
Distributed under the MIT License. See `LICENSE` for details.
