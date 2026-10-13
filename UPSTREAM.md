# Upstream Tracking & Integration Document (UPSTREAM.md)

## 1. Upstream Repository
- **Remote:** `upstream = https://github.com/JunkFood02/Seal`
- **Origin:** `origin = https://github.com/SMSMy/jiyad`
- **Application ID:** `com.jiyad.downloader` (independent identity)
- **Namespace:** `com.junkfood.seal`

## 2. Reconvergence & History Points
- **Divergence Base Commit (`$base`):** `d00bfaa9860dc38cb937d45f14d9e12b1a0a993e` (2026-01-25)
- **Current Upstream Main:** `7677f61a20fda4210e84215fef9e9b51d25daa47` (2026-09-25)
- **Reconvergence Branch:** `jiyad/reconverge`
- **Merge Strategy:** Git merge from `upstream/main` with Jiyad's `README.md` and branding preserved.

## 3. Restored Upstream Files
All 176 files previously deleted from upstream have been restored to prevent drift:
- 91 fastlane metadata files
- 59 localized strings (`res/values-*`)
- 15 translation READMEs (`translations/`)
- 11 core repository files (`.github/**`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `ic_stat_seal.xml`)

## 4. Whitelist Policy (v1.3.1)
Modifications to upstream code are strictly quarantined:
- Zero modifications inside `com.junkfood.seal.ui.**`.
- Simple interface quarantined in `com.junkfood.seal.simple.**`.
- `MainActivity.kt`: Simple/Advanced switcher and return button (≤ 18 lines).
- `QuickDownloadActivity.kt`: Share flow branch (≤ 12 lines).
- `UpdateUtil.kt`: Repository owner/repo pointing to `SMSMy/jiyad` (≤ 4 lines).
- `app/build.gradle.kts`: Automated `versionCode = currentVersionCode` driven by `Version.kt`.
- `strings.xml`: Exclusive `jiyad_*` keys with single named exception `app_name`.

## 5. Architectural Decisions & Deviations

### 5.1 yt-dlp Guard & Auto-Update Protection (D16)
- **Issue:** Upstream's `YtdlpUpdater` (in `com.junkfood.seal.ui.page.YtdlpUpdater`) checked legacy V1 `Downloader.downloaderState` rather than active V2 tasks in `DownloaderV2`. If a background update triggers while tasks are in `Idle`, `FetchingInfo`, `ReadyWithInfo`, or `Running`, replacing Python packages or binaries crashes the active process.
- **Documented UI Exception (Single OVERRIDE):** By owner architectural decision, automatic engine update remains enabled across both UI modes (`YT_DLP_AUTO_UPDATE` remains default `true` in upstream and `SimpleConfig.AUTO_UPDATE_YTDLP = true` in Simple UI). To protect the advanced flow while keeping zero modification elsewhere, a single documented exception (OVERRIDE ≤ 6 lines) is granted exclusively to `app/src/main/java/com/junkfood/seal/ui/page/YtdlpUpdater.kt`. It invokes the shared `JiyadPreset.isAnyTaskActive(downloader)` guard before triggering engine updates, and logs the postponement reason identically to the Simple UI. All other files in `ui/**` strictly remain untouched and forbidden. (When `tools/check-divergence` is built in Phase 6, it will explicitly whitelist only this file).
- **Shared Guard Logic:** `JiyadPreset.isBlockingDownloadState` serves as the single source of truth across both paths, postponing engine updates whenever any task is non-terminal (`Idle`, `FetchingInfo`, `ReadyWithInfo`, `Running`).

### 5.2 Automated Divergence & Architecture Checker (`tools/check-divergence`) (Phase 6)
- Script: `tools/check-divergence` (Python 3).
- Integrated into `.github/workflows/android_ci.yml` and `android.yml`.
- Enforces 8 automated gates:
  1. No deleted upstream files (preserves all 176 files).
  2. `AndroidManifest.xml` exact parity with `upstream/main` (0 diff lines).
  3. UI zero-modification: fails on any diff in `ui/**` except the approved `YtdlpUpdater.kt` (≤ 6 lines).
  4. Whitelist line ceilings: `MainActivity.kt` (≤ 18 lines, actual: 9), `QuickDownloadActivity.kt` (≤ 12 lines, actual: 7), `UpdateUtil.kt` (≤ 4 lines, actual: 4), `build.gradle.kts` (≤ 20 lines), `Version.kt` (≤ 3 lines).
  5. Forbidden legacy artifacts absent: verifies no `SimpleDownloadService`, `SimpleQuickDownloadActivity`, `JiyadDownloader`, or `YoutubeDL.getInstance`.
  6. Strings gate: verifies only `jiyad_*` and `app_name` exist in strings diffs.
  7. Engine guard gate: verifies `isAnyTaskActive` precedes all engine updates.
  8. Version code check: verifies `versionCode` meets minimum requirements.
