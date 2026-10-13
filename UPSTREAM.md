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

### 5.1 yt-dlp Guard & Auto-Update Containment (D16)
- **Issue:** Upstream's `YtdlpUpdater` (in `com.junkfood.seal.ui.page.YtdlpUpdater`) checked legacy V1 `Downloader.downloaderState` rather than active V2 tasks in `DownloaderV2`. If a background update triggers while tasks are in `Idle`, `FetchingInfo`, `ReadyWithInfo`, or `Running`, replacing Python packages or binaries crashes the active process.
- **Zero-UI-Modification Solution:** Modifying files in `com.junkfood.seal.ui.**` is strictly forbidden by the Whitelist. To neutralize upstream's un-guarded auto-update without touching `ui/**`, Jiyad sets `YT_DLP_AUTO_UPDATE = false` in MMKV preferences during initial configuration (`initPresetAndSyncMemory`). This disables upstream's un-guarded update trigger cleanly.
- **Guarded Update in Simple UI:** Updates in Jiyad's simplified UI (`simple/**`) are routed exclusively through `safeUpdateYtDlp(downloader: DownloaderV2)` which inspects `DownloaderV2.getTaskStateMap()` in real-time right before invoking `UpdateUtil.updateYtDlp()`. The guard postpones updating if ANY task is non-terminal (i.e. not `Completed`, `Canceled`, or `Error`), protecting tasks across all stages.
