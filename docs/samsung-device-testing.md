# Samsung SM-G781W: physical-device test handoff

Prepared 2026-10-26 for the next user-approved testing session. **Preparation only: none of the phone commands below were run tonight.** Do not install, configure or launch an emulator. Do not deploy or run connected tests until the user explicitly approves them. Remain on `feat/squat-monitoring`.

## 1. Before connecting

Keep the existing installation and its data. Do not uninstall, clear storage, reset permissions through ADB, force a downgrade, or accept Android Studio's uninstall/reinstall workaround. The installed app's certificate and current package state are still unknown. The earlier installation failure does not prove what remains installed.

If existing history matters, record screenshots/notes in the existing app before any update. This project stores history in Android saved-instance state, not durable storage: even an ordinary matching-signature update can restart the process and lose transient/saved-task state. Copying an APK is **not** a backup of its data. Prefer the separate test ID proposed below if preserving the existing installation unchanged is essential.

On Samsung: Settings → About phone → Software information → tap Build number seven times, authenticate if requested, then Settings → Developer options → USB debugging. Connect a data-capable USB cable, unlock the phone and approve the computer's RSA prompt only if this is your trusted computer. Do not enable OEM unlocking or disable phone security protections. [Android developer-options instructions](https://developer.android.com/studio/debug/dev-options.html).

If ADB shows `unauthorized`, approve the prompt; if absent/offline, check cable/USB port and the normal Windows device driver setup. Do not install an emulator to troubleshoot USB. [Hardware-device setup](https://developer.android.com/studio/run/device).

## 2. Read-only device identification and certificate comparison

Run in PowerShell at `C:\Users\USER\AndroidStudioProjects\MediaPipeTest2`, tomorrow after approval to begin device checks. These checks read the phone and copy its APK to a new local folder; they do not install or launch anything.

```powershell
$adb = 'C:\Users\USER\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$apksigner = 'C:\Users\USER\AppData\Local\Android\Sdk\build-tools\36.0.0\apksigner.bat'
& $adb devices -l

# Replace with the exact serial shown as "device"; never target an unspecified device.
$deviceSerial = 'REPLACE_WITH_SAMSUNG_SERIAL'
& $adb -s $deviceSerial get-state
& $adb -s $deviceSerial shell getprop ro.product.model
& $adb -s $deviceSerial shell getprop ro.build.version.release
& $adb -s $deviceSerial shell getprop ro.build.version.sdk
& $adb -s $deviceSerial shell pm path com.kuet.gymtest
& $adb -s $deviceSerial shell dumpsys package com.kuet.gymtest |
    Select-String 'versionCode=|versionName=|firstInstallTime|lastUpdateTime'
```

Continue only when the selected device is authorized and the model is SM-G781W. If the package is missing, the output is ambiguous, or it is installed in another Android profile, stop and report that fact; do not assume permission for a fresh installation.

```powershell
$packagePaths = @(& $adb -s $deviceSerial shell pm path com.kuet.gymtest)
if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect installed package' }
$baseApks = @($packagePaths | Where-Object { $_.Trim() -match '^package:/.*/base\.apk$' })
if ($baseApks.Count -ne 1) { throw 'Expected one base APK; inspect package/profile before proceeding' }
$remoteBase = ($baseApks[0].Trim() -replace '^package:', '')
$captureDir = Join-Path (Get-Location) ('app\build\device-verification\' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $captureDir -ErrorAction Stop | Out-Null
$installedApk = Join-Path $captureDir 'installed-base.apk'
& $adb -s $deviceSerial pull $remoteBase $installedApk
if ($LASTEXITCODE -ne 0) { throw 'APK pull failed; do not infer its certificate' }

$candidateApk = (Resolve-Path 'app\build\outputs\apk\verification-user-key\app-debug.apk').Path
& $apksigner verify --print-certs $installedApk
if ($LASTEXITCODE -ne 0) { throw 'Installed APK certificate verification failed' }
& $apksigner verify --print-certs $candidateApk
if ($LASTEXITCODE -ne 0) { throw 'Candidate APK certificate verification failed' }
Get-FileHash -Algorithm SHA256 $candidateApk
```

Compare the **Signer #1 certificate SHA-256 digest**, not the APK file hash, SHA-1 or MD5. For these single-signer debug builds the complete SHA-256 values must match; multiple signers/key-rotation lineage require separate investigation. `apksigner` reports and verifies the public certificate. [Official apksigner reference](https://developer.android.com/tools/apksigner).

Locally verified public certificates:

| Artifact | Signer SHA-256 |
| --- | --- |
| Standard sandbox Gradle app/test APKs | `6116b8776be2003d36b2f3b561abaf5c5c5cd160902e75337c6c0e07dd9a000e` |
| Separate USER-key app/test APKs | `495361e26d4065c93b4d8ca15c580e11a6fababb87104667f4f7f85e26232c73` |
| Installed Samsung app | Unknown until the read-only check above. |

Matching application ID and signing certificate, plus a compatible non-downgrade version, are prerequisites for an ordinary update. Current local app is `com.kuet.gymtest`, versionCode `1`, versionName `1.0`, minSdk `24`. If the installed versionCode is higher, stop; do not add downgrade flags. A matching signer allows an update but is not a guarantee of saved-instance history retention. [Android signing/update rules](https://developer.android.com/studio/publish/app-signing).

## 3. If certificates differ: proposed coexistence, not implemented

Request approval for a debug-only `applicationIdSuffix = ".squattest"`, giving **`com.kuet.gymtest.squattest`**, with a distinguishable test launcher label. Keep the source namespace `com.kuet.gymtest` and release identity unchanged. Inspect the merged manifest and test target, and adjust any package-name assertions before rebuilding. This is a small proposed Gradle/debug-resource change, **not applied tonight**. Build variants support application-ID suffixes for separate installations. [Android build variants](https://developer.android.com/build/build-variants).

The test app would have its own sandbox/history/permissions and would not migrate the original app's data. Confirm the proposed ID is not already occupied before installing. Do not rename an APK or change only the Kotlin package: that does not safely establish a separate application identity. Approval for this future change and deployment is still required.

## 4. Safest Android Studio deployment, only after approval

1. Open this existing project as Windows USER. Confirm `feat/squat-monitoring` and the checkpoint commit. Do not select/create an emulator; use only SM-G781W in the device selector.
2. Select the **app / debug** configuration, not an instrumented-test configuration. First build without Run using the terminal command below (one worker limits concurrency). Recheck the resulting APK certificate: Studio/terminal signing can differ from the sandbox. Never assume the regenerated standard APK is signed with the USER key.
3. Compare that exact artifact with the installed certificate and installed version. If the match fails, stop and use the separately approved test-ID approach. If preserving transient history is essential, choose isolation even if the certificate matches.
4. After explicit deployment approval, use normal **Run app** only with the verified build/signing environment. Cancel any uninstall/reinstall prompt. Do not run `connectedDebugAndroidTest` against the valued installation: the pinned runner has optional uninstall cleanup, including failure paths.
5. Alternatively, install the already verified candidate by the explicit update command below, then open its launcher manually and use Studio's Logcat/Attach debugger. Normal Run rebuilds the standard APK; it does not automatically use the separate candidate copy.

```powershell
# Local build only; safe without a phone. Do not clean or upgrade dependencies.
.\gradlew.bat --offline :app:assembleDebug --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' --console=plain
& $apksigner verify --print-certs app\build\outputs\apk\debug\app-debug.apk
```

**Deployment command for a later explicitly approved, matching-certificate update only — do not run tonight:**

```powershell
& $adb -s $deviceSerial install -r $candidateApk
if ($LASTEXITCODE -ne 0) { throw 'Update failed: preserve error and stop; do not uninstall or clear data' }
```

`-r` requests replacement while keeping application data; it does not override signature compatibility. Do not use `-d`, forced uninstall, clear-data or grant-all-permissions workarounds. Manual permission testing needs the real permission UI. [ADB command reference](https://developer.android.com/tools/adb?hl=en).

Instrumented execution remains a separate approval gate, even if manual deployment succeeds. Before authorizing it on the Samsung, isolate the test app and inspect runner cleanup and test side effects; do not treat compilation of the test APK as an execution pass.

## 5. APKs and reproducibility

Paths relative to repository root (none are committed):

| Purpose | Exact path |
| --- | --- |
| Standard app | `app/build/outputs/apk/debug/app-debug.apk` |
| Standard instrumentation APK | `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` |
| USER-key app candidate | `app/build/outputs/apk/verification-user-key/app-debug.apk` |
| USER-key instrumentation candidate | `app/build/outputs/apk/verification-user-key/app-debug-androidTest.apk` |

All four signatures were verified during checkpoint preparation. Candidate app file SHA-256: `A819687B56E07141E97A7798AE1AD4B67B3A9F198E0A9993754764D798BA493D`. Candidate test file SHA-256: `58A0E94298910686360E850E3449E70CCA9CD143A258529BA68DD955F524F912`. These are **file hashes**, not signer fingerprints. Rebuilding/re-signing may change them: always recheck the actual selected APK. Older device-specific APK copies in build outputs are not the approved candidates; do not deploy them. Do not clean outputs before testing if you need these prepared copies.

The checkpoint is the commit containing this guide, with subject `feat: implement bodyweight squat monitoring`. Capture `git rev-parse HEAD` before testing and verify `git status --short`; the exact hash is reported in the Checkpoint D handoff rather than self-embedded in its own commit.

## 6. Evidence and Logcat

Record device model, Android version, commit hash, APK file hash, signer, selected lens, phone orientation, lighting, distance, left/right-facing side, target reps/sets, test ID, time and expected/observed counts. Keep logs/videos local under the unique `$captureDir`; redact device identifiers and unrelated personal data before sharing. Do not commit device captures or pulled APKs.

In Studio: View → Tool Windows → Logcat, choose the Samsung and filter `package:com.kuet.gymtest` (use the separate ID if approved). Add `level:ERROR` for failures, but also preserve surrounding unfiltered app messages and stack traces. [Logcat instructions and query syntax](https://developer.android.com/studio/debug/logcat).

Optional PowerShell capture, after the approved app is running:

```powershell
$testedPackage = 'com.kuet.gymtest' # change only if a separate test ID was approved
$appProcessId = ((& $adb -s $deviceSerial shell pidof -s $testedPackage) -join '').Trim()
if ($appProcessId -notmatch '^\d+$') { throw 'App is not running; record the launch failure first' }
$captureTag = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
# Streams until Ctrl+C. Restart this capture after a process restart (PID changes).
& $adb -s $deviceSerial logcat --pid=$appProcessId -v threadtime |
    Tee-Object -FilePath (Join-Path $captureDir "app-$captureTag.log")
```

After a crash, preserve the crash buffer without clearing it:

```powershell
$crashTag = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
& $adb -s $deviceSerial logcat -b crash -d -v threadtime |
    Out-File -Encoding utf8 (Join-Path $captureDir "crash-$crashTag.log")
```

Crash-buffer output may include other apps: inspect/redact it. Do not use `logcat -c`. Save installation/Studio Run error text as well. For missed/duplicate reps without a crash, record a short screen capture and, with consent, a side-view recording plus manual tally; raw per-frame diagnostic telemetry is not implemented, so Logcat alone cannot prove movement/angle correctness. Screenshots should include phase, feedback and count.

## 7. Ordered manual checklist (all pending)

Complete the deployment safety gate first. Use the exact **Bodyweight Squat** catalog entry and the displayed targets (currently 3 sets × 12 reps); do not change production thresholds tonight. Perform only comfortable movements; this is engineering validation, not medical or exercise-safety advice. Stop if uncomfortable. Repeat counting/alignment tests for front and rear lenses and both side-facing directions.

| ID / action | Expected behavior | Evidence if it fails |
| --- | --- | --- |
| T01 Permission: on first request deny; then use Allow/Settings to grant. If already granted, mark denial pending or change only this permission manually after approval. | Denial shows guidance without crash/count; granting restores preview. No data clearing. | Permission screen, buttons, OS permission state, Logcat. |
| T02 Front setup: select front lens; stand fully visible sideways; move without Start. | Front preview/skeleton appears; measured count stays zero before Start. | Lens label, screenshot/video, count before/after. |
| T03 Rear setup: switch to rear, then return to front. | Correct lens opens; no stale overlay; running session, if any, becomes paused and requires Resume. | Preview/lens label, transition time, error text. |
| T04 Overlay: move arms/legs near center and frame edges, then rotate the phone. | Skeleton stays on corresponding joints; front preview may mirror but analysis counts do not flip. No persistent crop offset/stretch. | Full-screen capture at each lens/orientation and camera placement. |
| T05 Start/readiness: Start while bent, then stand tall and still briefly. | No rep from starting bent or merely standing up. Valid standing acquires readiness (default ~300 ms of accepted observations). | Phase/feedback progression and initial tally. |
| T06 Full cycles: after readiness perform three comfortable full cycles, then remain still. | Each continuously tracked target-bend cycle counts once only on return to standing; stillness adds none. | Independent manual tally, screen/side-view clip, phase/feedback at missed or duplicate count. |
| T07 Shallow attempts: several obvious partial descents, return tall. | Attempts never reaching configured target bend do not count; no completed-rep event. | Side view, expected/observed counts, feedback. Physical depth is not an exact 2D-angle label. |
| T08 Timing/noise: slow cycle, comfortable faster cycle, brief bottom hold and small standing movements. | Full qualifying cycles count once; no extra counts during hold/stillness. Very fast/uncertain cycles may be conservatively rejected; record misses. | Timing, clips, count deltas and any unclear-pose messages. |
| T09 Tracking loss: after accepted reps begin a descent, leave/occlude frame, return bent then tall. | Existing reps retained; partial attempt cancelled; no count from re-entry/ascent. Fresh standing required before a new full cycle. | Counts before loss/after recovery, interruption length, recovery feedback. |
| T10 Pause/Resume: pause at bottom; move while paused; resume and stand tall. | No counts while paused or from pre-pause motion; existing ledger retained; new complete cycle required. | Counts and phases before/after, button/tap timing. |
| T11 Reset: with progress, open Reset then Cancel; reopen and confirm. | Exact confirmation "Reset this workout?"; Cancel preserves progress and stays paused if previously running. Confirm clears every set/rep, returns setup, saves no history. | Before/after ledger, dialog, History entry count. |
| T12 Manual sets: reach displayed target; perform extra motion without Next Set; then press Next Set. | Count freezes at target, no automatic advance/rest timer. Next Set starts at zero with fresh standing; prior sets retained. | Target/reps/sets before and after taps; extra-motion tally. |
| T13 Empty Finish: reset/start with zero accepted reps, Finish. | Dialog says no entry; returns without creating history. | Dialog plus History before/after. |
| T14 Partial Finish: complete e.g. 2 valid reps, Finish; repeat-tap confirmation. | Actual 2 reps/0 full sets saved once, marked finished early. Cancelled Finish stays paused until Resume. | Finish dialog, History count/details and tap sequence. |
| T15 Full Finish: complete targets across manual sets, Finish. | Exact measured per-set ledger, total and fully completed sets; targets completed, one record. | Manual tally for every set, dialog, History row. |
| T16 History/regression: open History/Profile, inspect known legacy/demo rows and activity/streak. | New measured counts match ledger; legacy/demo never invent measured counts; timestamps/activity remain meaningful. | Before/after rows, dates, streak and totals. Do not expect durable survival of force-stop/reinstall. |
| T17 Background: leave app during a partial squat, return. | Counts retained where state survives; paused, never auto-counting. Resume reacquires standing and cannot finish old motion. | Counts/status before/after and interruption timing/Logcat. |
| T18 Rotation/switching: rotate both directions or change lens during partial motion, repeat several times. | Progress retained where supported; pause/epoch reset; no old callbacks/counts, stretched overlay, frozen preview or crash. Explicit Resume needed. | Orientation/lens sequence, video and Logcat; note if OS rotation lock prevents test. |
| T19 Lighting/occlusion: lower lighting or briefly obscure a required joint. | Uncertain tracking stops acceptance; actionable feedback; accepted reps survive recovery. | Conditions, guidance, manual/app tally and recovery delay. |
| T20 Unrelated workflow: return Home/Plan/Library/Profile; open unsupported exercise briefly. | Existing styling/navigation remains; unsupported exercise clearly says timed demo, not measured squat. | Screenshots and reproduction steps for any regression. |

For every test record PASS / FAIL / BLOCKED / NOT RUN, not an assumed result. Do not mark the feature physically accurate based only on synthetic JVM tests or one successful cycle. Save evidence before changing anything. Any defect fix, alternate package configuration, additional commit or deployment requires the applicable new authorization.
