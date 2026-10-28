# Bodyweight Squat implementation and verification handoff

Date: 2026-10-26, Asia/Dhaka. Branch: `feat/squat-monitoring`, based on main at `e891615`. Checkpoint D authorizes one local commit, `feat: implement bodyweight squat monitoring`, containing this report. Resolve its hash from Git history/the final handoff. No push, merge, rebase, reset or clean is authorized or performed. Device deployment awaits explicit approval; no emulator may be installed, configured or launched.

## Checkpoint D: final review and preparation

Reviewed analyzer transitions/confirmation/event deduplication, pose validation/loss, session epochs and controls, history schema/saver/root insertion, front/rear camera selection, worker/native cleanup, crop/rotation/overlay mapping, runtime permission and Compose lifecycle paths against the tests. **No new critical defect requiring code changes was found.** No production or test source changed during this preparation. This is source review, not proof of device behavior: native callback/teardown timing, overlay alignment, permission recovery, throughput and actual movement calibration still need the Samsung checklist.

The complete pre-guide inventory was 35 expected Kotlin/Markdown files (10 modified production files, 9 new production files, 8 new test files, 8 documents). Added one Samsung testing guide, bringing the checkpoint to 36 files. Inspection and a focused secret-pattern scan found no private keys/tokens, unrelated file changes, temporary files or generated outputs among commit candidates. Public certificate fingerprints and local example paths in documentation are intentional. Ignored caches, IDE configuration, local.properties, APKs/signature files and older device-specific build artifacts stay out of Git; nothing was deleted.

Latest command, using one worker for the RAM constraint:

```powershell
.\gradlew.bat --offline :app:assembleDebug :app:testDebugUnitTest --rerun :app:lintDebug :app:assembleDebugAndroidTest --no-configuration-cache --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' --console=plain
```

BUILD SUCCESSFUL in 24s; 81 tasks, 2 executed/79 up-to-date. All **40 JVM tests executed again**, 0 failures/errors/skips; XML timestamps 2026-10-25T20:00:31Z (October 26 local). APK assemblies and lint analysis/report were **up-to-date**, not fresh full recompilations. Lint remains 0 errors/34 warnings. The earlier full stable-source run is documented below. All four local app/test APK signatures were reverified successfully; only the known JDK native-access warning appeared. No ADB phone command, phone install/uninstall/data clearing, connected test or emulator operation was performed during preparation.

The [Samsung device-testing guide](samsung-device-testing.md) contains USB debugging setup, serial/model checks, read-only extraction and certificate comparison, exact APK paths/file hashes, conditional Android Studio deployment, Logcat capture, and 20 ordered manual tests with expected outcomes/failure evidence. It proposes (but does not implement) `com.kuet.gymtest.squattest` for coexistence if signatures differ or the original installation must remain untouched. Matching certificates do not guarantee saved-instance-only history survives an update.

The final handoff reports the actual commit hash, branch and post-commit status. This report cannot contain its own commit hash. Authorization is consumed by that one commit; further commits/amends, deployment and connected tests require approval. **Stop after the handoff.**

## Implementation status

Exact **Bodyweight Squat** now uses a pure Kotlin `ExerciseAnalyzer`/`SquatAnalyzer`, a session controller and an Activity-owned ViewModel. The cycle is `NOT_READY → STANDING → DESCENDING → BOTTOM → ASCENDING → STANDING`, emitting one ordinal event only after continuous, confirmed motion. Same-side confidence/framing checks, aspect-correct geometry, timestamp EMA, hysteresis, minimum duration and observation ordering reject uncertain or incomplete attempts. Completed counts survive tracking loss; motion must reacquire standing. Configurable defaults require physical calibration.

Start is explicit. Pause cancels partial motion; Resume opens a fresh epoch. Confirmed Reset clears the entire session. Target counts freeze until manual Next Set. Finish saves actual partial/full results only with at least one valid rep and is idempotent. Running restored sessions become paused. The measured UI retains the app's glass cards, colors, typography and preview composition; other exercises retain an explicitly labeled timer demo.

Front/rear selection reuses CameraX and MediaPipe with a shared viewport, unmirrored upright model input and explicit crop/rotation/display mapping. Epochs reject callbacks from old bindings. Native initialization/inference/closure and geometry run on workers; inference and pending analysis are bounded. Errors, missing frames and model stalls are visible. Permission, Settings, background, rotation and lens transitions require safe recovery; counting does not automatically resume after a lifecycle interruption.

History records timestamp/name/session identity, measured/demo/legacy classification, actual per-set ledger and configured targets. Total reps, fully completed sets and early/full completion derive from the ledger. The versioned saver reads legacy timestamp/name entries without inventing counts. Profile activity and streak calculations remain unchanged. Android saved-instance history is **not durable permanent storage across arbitrary process termination, restart or reinstall**.

## Review fixes in the resumed session

- Watchdog/transform-loss now changes the analysis epoch under the commit lock immediately. Previously, queued cancellation could run after an in-flight result committed. RUNNING and accepted progress remain intact; standing must be reacquired.
- A resolved no-pose problem can yield to actionable framing feedback rather than leaving the old error displayed indefinitely.
- Retrying an unavailable requested lens forces a fresh binding even if the previous fallback left the requested-lens value unchanged. The actual fallback is displayed.
- Non-finite analyzer configuration is rejected.
- Added irregular-timing/long-hold, locked-side-loss, pause-at-bottom, watchdog and feedback regression tests. Android worker-race tests compile but remain unexecuted.

## Verification results

Earlier full stable-source verification command (before Checkpoint D preparation):

```powershell
.\gradlew.bat --offline :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest --no-configuration-cache '-Pkotlin.compiler.execution.strategy=in-process' --console=plain
```

BUILD SUCCESSFUL in 2m 21s: 81 actionable tasks, 20 executed and 61 up-to-date. Source files stayed unchanged during the run. Test XML timestamps are `2026-10-25T19:30:40Z` (October 26 local).

| Check | Result |
| --- | --- |
| Debug APK | Assembled successfully. |
| JVM tests | 40 executed; 0 failures, errors or skips. |
| Squat analyzer | 11 tests, including repeated cycles, partial/noisy motion, missing data, reset, multiple/irregular intervals, long holds and locked-side loss. |
| Session controller | 8 tests: explicit controls, manual sets, deduplication, actual partial/full Finish, restoration, camera/watchdog epochs and pause-at-bottom integration. |
| Geometry / clock / feedback | 3 tests each. |
| History / saver | 5 tests, including actual Compose saver round-trip, legacy compatibility, partial completion and duplicate Finish. |
| Original regression JVM tests | 7 retained and passing. |
| Android lint | 0 errors, 34 warnings. Dependency/catalog suggestions, unused resources/redundant manifest label and one `UseKtx` style suggestion in the measured screen. No checks suppressed. |
| Android test APK | Assembled, including existing frontend tests and new conversion/worker tests. |
| Instrumented execution | Blocked: no device/emulator currently connected. Prior Samsung SM-G781W/Android 13 attempt failed installation before tests. |
| Real squat movement / camera alignment | Not physically validated. No accuracy claim. |
| Git whitespace check | `git diff --check` passed; Git warns about normal LF-to-CRLF conversion. |

Reports: `app/build/reports/tests/testDebugUnitTest/index.html`, `app/build/test-results/testDebugUnitTest/*.xml`, `app/build/reports/lint-results-debug.html`. APKs are under `app/build/outputs/apk/`.

The previous internal lint failure was a Kotlin FIR exception while analyzing `WorkoutSessionViewModel.kt`: `FirDeclaration was not found ... KtFunctionLiteral`. It did not recur on either resumed stable-source run. Concurrent source edits during the interrupted run are a possible contributor, but the exact root cause is not proven. No dependency upgrade, lint baseline or suppression was used to obtain the pass.

Earlier intermediate compiler/lint violations were corrected. The prior offline connected run also lacked a cached UTP artifact. Its online retry reached the phone but failed with `INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package com.kuet.gymtest signatures do not match newer version`. The generated runner exit-code file was `0` despite that logged installation failure; it must not be interpreted as passing instrumented tests.

## Signing investigation and safe alternative

Read-only `:app:signingReport`, `apksigner verify --print-certs` and `keytool -list` establish:

| Source | Certificate SHA-256 |
| --- | --- |
| Sandbox Gradle debug key (`C:/Users/CodexSandboxOffline/.android/debug.keystore`) | `6116b8776be2003d36b2f3b561abaf5c5c5cd160902e75337c6c0e07dd9a000e` |
| USER local debug key (`C:/Users/USER/.android/debug.keystore`) | `495361e26d4065c93b4d8ca15c580e11a6fababb87104667f4f7f85e26232c73` |
| Existing phone installation | Unknown while disconnected; do not assume it matches either key. |

Separate copies of the final app and test APKs were signed using the existing USER debug key and verified with `apksigner`. They are ignored build artifacts, **not installed**:

- `app/build/outputs/apk/verification-user-key/app-debug.apk`
- `app/build/outputs/apk/verification-user-key/app-debug-androidTest.apk`

The standard Gradle APKs and production signing configuration are unchanged. These candidate copies solve only the local key-selection difference; compatibility with the installed phone package remains unverified. The signing utility emitted a JDK native-access warning but completed successfully.

For the existing phone: reconnect, inspect `adb shell pm path com.kuet.gymtest`, pull only the returned APK to an unused local verification path, and compare its public signing certificate with the candidate using `apksigner verify --print-certs`. Only a matching certificate supports a normal data-preserving update. A mismatch must remain a blocker; do not uninstall, clear data or use a force-reinstall workaround. The earlier test run's resulting phone/package state cannot be established while disconnected.

For automated execution: no emulator is allowed under Checkpoint D. Inspection of the pinned `android-test-engine:1.0.1` `AndroidTestRunner.run()` shows optional `uninstallApksAfterTests` cleanup on both success and failure paths. Therefore blindly repeating `connectedDebugAndroidTest` on the personal installation is unsafe even after correcting signing. No device installation/uninstallation/data clearing was performed in the resumed session or preparation. A future phone test run requires explicit approval and verified isolation/preservation behavior; do not apply the earlier emulator recommendation.

## File inventory

All Kotlin paths below start at `app/src/`.

New production files:

- `main/java/com/kuet/gymtest/CameraFrameConversion.kt`
- `main/java/com/kuet/gymtest/MeasuredWorkoutScreen.kt`
- `main/java/com/kuet/gymtest/analysis/PoseFrame.kt`
- `main/java/com/kuet/gymtest/analysis/PoseGeometry.kt`
- `main/java/com/kuet/gymtest/analysis/SquatAnalyzer.kt`
- `main/java/com/kuet/gymtest/analysis/FrameClock.kt`
- `main/java/com/kuet/gymtest/analysis/FeedbackPresenter.kt`
- `main/java/com/kuet/gymtest/session/WorkoutSessionController.kt`
- `main/java/com/kuet/gymtest/session/WorkoutSessionViewModel.kt`

Modified existing production files, in `main/java/com/kuet/gymtest/`: `CameraPreview.kt`, `CameraScreen.kt`, `MainActivity.kt`, `PoseCamera.kt`, `PoseDetector.kt`, `PoseOverlay.kt`, `ProfileScreen.kt`, `UiStateSavers.kt`, `WorkoutRecord.kt`, `WorkoutSessionScreen.kt`.

New tests:

- `test/java/com/kuet/gymtest/analysis/PoseGeometryTest.kt`
- `test/java/com/kuet/gymtest/analysis/SquatAnalyzerTest.kt`
- `test/java/com/kuet/gymtest/analysis/FrameClockTest.kt`
- `test/java/com/kuet/gymtest/analysis/FeedbackPresenterTest.kt`
- `test/java/com/kuet/gymtest/session/WorkoutSessionControllerTest.kt`
- `test/java/com/kuet/gymtest/WorkoutHistoryTest.kt`
- `androidTest/java/com/kuet/gymtest/CameraFrameConversionTest.kt`
- `androidTest/java/com/kuet/gymtest/WorkoutSessionViewModelTest.kt`

Documentation: the existing Phase 2/3 `AGENTS.md`, `CONTEXT.md`, `PROJECT.md`, `ARCHITECTURE.md`, `DECISIONS.md`, `TASKS.md`, and `docs/squat-monitoring-design.md` were preserved and updated. This verification report and `docs/samsung-device-testing.md` are also new since main. AGENTS gained source-stability/signing/device-data preservation rules and the Checkpoint D one-commit/no-emulator/approval gate. Build files, dependency versions, theme resources and unrelated screens were not changed.

## Remaining work before merging

Run the compiled instrumented suite on a safe target and complete these physical checks, recording device/lens, placement, lighting, expected/observed counts and missed/duplicate events:

- Front/rear cameras and both side-facing directions; full-body framing and overlay alignment.
- Full, partial, slow and fast squats; stillness; long bottom holds; multiple sets with manual transitions.
- Pause midway, move while paused, Resume, Reset, zero-rep Finish, partial/full Finish and repeat taps.
- Leave/re-enter the frame, poor lighting, occluded joints, another person entering, and camera switching midway.
- Rotation, background/foreground, permission denial and Settings return; no automatic counting resume.
- History restoration and profile activity/streak presentation after actual measured sessions.

Known limits: conservative one-frame tracking cancellation can miss legitimate reps; 2D target bend does not establish anatomical depth or medical safety; silhouette checks are limited; single-pose inference is not identity tracking; per-frame conversion/performance and native teardown need device observation. Saved-instance history can grow toward Android state-size limits and is not durable storage. No confirmed local test failure remains. These physical/instrumented acceptance items remain open. Only the one Checkpoint D commit is authorized; further commits/amends, push/merge and deployment require approval.
