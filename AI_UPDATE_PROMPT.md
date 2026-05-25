# 🤖 AI Agent Update Prompt

> **Copy the prompt below and give it to any AI agent (ChatGPT, Claude, Gemini, etc.) to update this fork to the latest CloudStream version.**

---

## How Updating Works

This repo is a **fork** of [recloudstream/cloudstream](https://github.com/recloudstream/cloudstream) with **2 custom features** added on top. When the original repo gets new updates, we need to:

1. **Sync** our fork with the upstream (original) repo
2. **Re-apply** our custom features if they got overwritten by merge conflicts
3. **Push** the updated code → GitHub Actions auto-builds a new APK release

### Will clicking "Update" in the app remove our features?

**NO.** The auto-updater has been redirected to check **our fork's releases** instead of the original CloudStream. Any update popup you see now comes from our fork, not the original. So updating will always give you a build with our custom features included.

---

## Copy This Prompt 👇

```
You are updating a custom fork of CloudStream (an Android streaming app) to the latest version from the original repo. The fork is at: https://github.com/AiCurv/cloudstream

## Context

This fork has 2 custom features added on top of the original CloudStream. Your job is to sync with the upstream repo and make sure the custom features still work.

## Custom Features (MUST be preserved)

### Feature 1: External Player Intent Filter (Stremio compatibility)

**What it does:** Makes CloudStream appear in Android's "Open with" menu when clicking video URLs from Stremio, browsers, etc.

**Files modified:**

1. **`app/src/main/AndroidManifest.xml`** — Added 3 `<intent-filter>` blocks to `DownloadedPlayerActivity` AFTER the existing `magnet` intent-filter:
   - `ACTION_VIEW` for `http/https` + `video/*` MIME type
   - `ACTION_VIEW` for `http/https` + `application/x-mpegURL` + `application/vnd.apple.mpegurl` MIME types
   - `ACTION_VIEW` for `http/https` + `audio/*` MIME type
   - All have `android:label="@string/play_with_app_name"` and both `DEFAULT` + `BROWSABLE` categories

2. **`app/src/main/java/com/lagradost/cloudstream3/ui/player/DownloadedPlayerActivity.kt`** — Two changes:
   - `handleIntent()`: Added routing for `http/https` URL schemes → `playLink()` instead of `playUri()`. The key lines are:
     ```kotlin
     item?.uri != null && item.uri.scheme in listOf("http", "https") ->
         playLink(this, item.uri.toString())
     // ... and ...
     data != null && data.scheme in listOf("http", "https") ->
         playLink(this, data.toString())
     ```
   - Back press: Changed from `finish()` to showing a confirmation dialog before exiting

3. **`app/src/main/res/values/strings.xml`** — Added strings:
   ```xml
   <string name="exit_player_confirm_title">Exit Player?</string>
   <string name="exit_player_confirm_message">Your buffered stream will be lost. Are you sure you want to exit?</string>
   ```

### Feature 2: Exit Confirmation Dialog

**What it does:** Shows a "Exit Player?" confirmation dialog when pressing back during playback, preventing accidental exits that lose buffered streams.

**Files modified:**

1. **`app/src/main/java/com/lagradost/cloudstream3/ui/player/FullScreenPlayer.kt`** — Added `showExitConfirmDialog()` method and `exitConfirmDialog` field. Changed the back press handler in `onResume()` from `activity?.popCurrentPage("FullScreenPlayer")` to `showExitConfirmDialog()`.

2. **`app/src/main/java/com/lagradost/cloudstream3/ui/player/DownloadedPlayerActivity.kt`** — Same confirmation dialog on back press (see Feature 1 above).

### Auto-Updater Redirect

**`app/src/main/java/com/lagradost/cloudstream3/utils/InAppUpdater.kt`** — Changed:
- `GITHUB_USER_NAME` from `"recloudstream"` to `"AiCurv"`
- `getPreReleaseUpdate()` rewritten to check our fork's releases instead of the original repo's `pre-release` tag

### GitHub Actions Workflow

**`.github/workflows/custom-build.yml`** — Custom build workflow that:
- Generates a signing keystore on-the-fly
- Builds both `prereleaseRelease` and `stableRelease` APKs
- Creates a GitHub Release with both APKs attached
- Triggered on push to `master` branch or manually via `workflow_dispatch`

## Update Steps

1. **Clone the fork** (if not already cloned):
   ```bash
   git clone https://github.com/AiCurv/cloudstream.git
   cd cloudstream
   ```

2. **Add upstream remote** (original repo):
   ```bash
   git remote add upstream https://github.com/recloudstream/cloudstream.git
   ```

3. **Fetch upstream changes**:
   ```bash
   git fetch upstream
   ```

4. **Merge upstream/master** into our master:
   ```bash
   git merge upstream/master
   ```

5. **Resolve merge conflicts** — If there are conflicts in our custom files, resolve them and RE-APPLY the custom features as described above. The most likely conflict files are:
   - `AndroidManifest.xml` — Make sure our 3 intent-filters are still present after the magnet intent-filter
   - `DownloadedPlayerActivity.kt` — Make sure http/https routing and exit dialog are intact
   - `FullScreenPlayer.kt` — Make sure `showExitConfirmDialog()` and the back press change are intact
   - `InAppUpdater.kt` — Make sure `GITHUB_USER_NAME = "AiCurv"` and the custom `getPreReleaseUpdate()` are intact
   - `strings.xml` — Make sure `exit_player_confirm_title` and `exit_player_confirm_message` strings exist

6. **Test build locally** (optional, if you have Android SDK):
   ```bash
   ./gradlew assemblePrereleaseRelease
   ```

7. **Commit and push**:
   ```bash
   git add -A
   git commit -m "chore: sync with upstream recloudstream/cloudstream"
   git push origin master
   ```

8. **Verify build** — The GitHub Actions workflow will automatically trigger and build a new APK release. Check:
   - https://github.com/AiCurv/cloudstream/actions
   - Once the build completes, a new release will appear at:
   - https://github.com/AiCurv/cloudstream/releases

9. **Disable original workflows** — If new workflow files were pulled from upstream that require secrets we don't have (GH_APP_ID, keystore, etc.), disable them:
   ```bash
   # List all workflows
   curl -s -H "Authorization: token <GITHUB_TOKEN>" \
     "https://api.github.com/repos/AiCurv/cloudstream/actions/workflows" | \
     python3 -c "import sys,json; [print(f\"{w['id']}: {w['name']} ({w['state']})\") for w in json.load(sys.stdin)['workflows']]"
   
   # Disable any workflow that isn't "Custom Build Release"
   curl -s -X PUT -H "Authorization: token <GITHUB_TOKEN>" \
     "https://api.github.com/repos/AiCurv/cloudstream/actions/workflows/<WORKFLOW_ID>/disable"
   ```

## Quick Verification Checklist

After merging, verify these specific lines exist in the codebase:

- [ ] `AndroidManifest.xml`: Contains 3 intent-filters with `android:scheme="http"` and `android:scheme="https"` on DownloadedPlayerActivity
- [ ] `DownloadedPlayerActivity.kt`: Contains `item.uri.scheme in listOf("http", "https")` in handleIntent()
- [ ] `DownloadedPlayerActivity.kt`: Contains `showExitConfirmDialog()` in back press callback
- [ ] `FullScreenPlayer.kt`: Contains `showExitConfirmDialog()` method and call in back press handler
- [ ] `InAppUpdater.kt`: `GITHUB_USER_NAME = "AiCurv"` (NOT "recloudstream")
- [ ] `strings.xml`: Contains `exit_player_confirm_title` and `exit_player_confirm_message`
- [ ] `.github/workflows/custom-build.yml` exists
```

---

## FAQ

**Q: How hard is it to update?**
A: Not hard at all! Most of the time it's just a git merge. The custom features touch very specific files that rarely change in the original repo, so merge conflicts are uncommon. An AI agent can handle the whole process in 5 minutes.

**Q: What if there are merge conflicts?**
A: The AI agent will resolve them by re-applying our custom features on top of the updated code. The verification checklist above ensures nothing is missed.

**Q: What about the original CloudStream's update popup?**
A: That popup is now gone! We redirected the auto-updater to check our fork's releases. Any update you see comes from our fork and includes our custom features.

**Q: Can I install this alongside the original CloudStream?**
A: Yes! The prerelease APK uses package name `com.lagradost.cloudstream3.prerelease` (different from original's `com.lagradost.cloudstream3`), so both can coexist.
