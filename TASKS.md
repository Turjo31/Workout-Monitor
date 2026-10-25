# Development tracker

Updated: 2026-10-26 (Asia/Dhaka). Active branch: `feat/squat-monitoring`, based on `main` at `e891615`. Checkpoint D authorizes one local commit containing this handoff; identify it by subject `feat: implement bodyweight squat monitoring` and verify its hash in Git. Status vocabulary: completed, in review, pending, blocked/deferred. A completed implementation item must identify its verification limits.

## Current gate

Implementation and local verification are complete; instrumented execution and physical calibration remain pending. Checkpoint D permits one checkpoint commit and preparation only; deployment/connected tests await explicit approval. No emulator installation/configuration/launch. See the [verification report](docs/squat-monitoring-verification.md) and [Samsung test guide](docs/samsung-device-testing.md). No additional commit/amend, push, merge, rebase, reset or main edit is authorized.

## Prioritized work

| ID / priority | Work and status | Dependencies | Acceptance / verification |
| --- | --- | --- | --- |
| DOC-01 / P0 | Audit repository, Git, camera, UI and existing tests — completed. | None. | Read-only source audit presented at A; debug assembly and seven fresh JVM tests succeeded; device behavior unverified. |
| DOC-02 / P0 | Create six persistent context files — completed; B accepted. | Phase 2 approval received. | Documentation checks passed and the user accepted all six files; app code unchanged. |
| SAFE-01 / P0 | Preserve frontend and live overlay — ongoing constraint. | Every future change. | No unrelated design/navigation changes; review diff and relevant existing UI regressions. Physical camera overlay still needs validation. |
| DESIGN-01 / P1 | Analyzer, camera and session design — accepted at C. | B approved. | Actual measured history supersedes original minimal-history proposal. |
| ANALYSIS-01 / P1 | Pure pose/geometry/validation contract — completed. | C approved. | Geometry, confidence, timestamp and configuration tests pass. |
| SQUAT-01 / P1 | Bodyweight Squat analyzer — implemented; calibration pending. | ANALYSIS-01. | Complete/partial/noisy/lost-tracking trajectories tested at multiple and irregular frame intervals. |
| CAMERA-01 / P1 | Front/rear, transforms, errors and lifecycle — implemented; device checks pending. | C approved. | Compiles; Android crop/rotation/reflection tests assemble but have not executed. |
| SESSION-01 / P1 | Controller, ViewModel and measured UI — implemented. | SQUAT-01, CAMERA-01. | JVM controller tests pass; worker race tests compile; device/UI lifecycle behavior awaits execution. |
| HISTORY-01 / P1 | Actual results and legacy-compatible history — completed locally. | Session ledger. | Five saver/history tests pass; profile activity/streak code unchanged. |
| TEST-01 / P1 | Focused JVM tests — completed. | Implementation. | 40 total tests passed, 0 failures/errors/skips, including 7 original tests. |
| VERIFY-01 / P1 | Debug build, lint and UI-test APK — completed. | Implementation. | Final build successful; lint 0 errors/34 warnings. Instrumented execution blocked by absence of a safe connected target. |
| DEVICE-01 / P1 | Physical movement/camera checklist — pending. | Safe device and feature build. | Prior phone install failed on signing; no device connected on resume. Accuracy unverified. |
| DOC-03 / P1 | Implementation handoff — completed. | Verified changes. | Current context, architecture, decisions, signing evidence and limitations recorded. |
| PREP-01 / P1 | Final review and Samsung preparation — completed; one checkpoint commit authorized. | Checkpoint D. | No new critical code defect found; 40 fresh JVM tests pass; APK signatures checked. Guide has read-only certificate comparison, conditional deployment and 20 pending manual cases. No emulator or phone operation performed tonight. |
| PUSHUP-01 / P2 | Independent push-up analyzer — deferred. | Squat foundation, future authorization/design. | Reuse analyzer contract and camera; do not implement in initial squat task. |
| REGRESSION-01 / P2 | Broader integration/regression coverage — deferred. | Initial analyzer baseline. | Expand based on observed risks and failures; avoid a large speculative suite. |

## Required initial analyzer tests

Use synthetic timestamped landmarks where appropriate and local JVM tests for pure Kotlin. Instrumentation is reserved for Android behavior.

1. One complete valid squat counts exactly one repetition.
2. Incomplete descent does not count.
3. Repeated noisy threshold crossings do not duplicate counts.
4. Missing required landmarks do not create false repetitions.
5. Tracking loss invalidates unsafe partial movement and recovery cannot finish it falsely.
6. Reset clears analyzer session state.
7. Pause/resume prevents paused counting and unsafe continuation of partial motion.
8. Invalid geometry, including non-finite values and degenerate vectors, is handled safely.
9. A new session does not inherit old repetitions or partial-cycle state.

Executed JVM tests include 16/33/66 ms sampling and irregular intervals, long bottom holds, locked-side loss, interruption epochs, full/partial sessions, pause/resume, reset and legacy/partial history restoration. Android transform and actual worker-race tests are compiled but unexecuted; they are not covered by the JVM pass claim.

## Physical-device validation checklist: pending

Record device/OS, camera lens, orientation, lighting, approximate camera placement, configured thresholds, expected/observed reps and feedback. None of the following has been physically validated for this analyzer:

- Front and rear camera with full body visible sideways; verify overlay alignment and left/right handling.
- Full, partial, slow and fast repetitions; note missed/duplicate counts and depth classification.
- Pause midway, move while paused, resume, reset and start a fresh session.
- Leave/re-enter frame, poor lighting and landmark occlusion; verify readiness and partial-cycle cancellation.
- Switch cameras while standing and during partial movement; verify no stale overlay/count.
- Deny camera permission, retry and return from Settings where supported; verify actionable errors.
- Rotate the screen where supported and background/foreground the app; verify camera release/recovery and session policy.
- Finish early and at target sets/reps; confirm approved history behavior and existing navigation.

## Verification ledger

Checkpoint D preparation (2026-10-26 local; XML timestamps 2026-10-25T20:00:31Z):

```powershell
.\gradlew.bat --offline :app:assembleDebug :app:testDebugUnitTest --rerun :app:lintDebug :app:assembleDebugAndroidTest --no-configuration-cache --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' --console=plain
```

BUILD SUCCESSFUL in 24s, 81 actionable tasks (2 executed, 79 up-to-date). The JVM test task executed afresh: 40 tests, 0 failures/errors/skips. Debug/test APK assembly and lint analysis/report were up-to-date; existing lint result remains 0 errors/34 warnings. No source/test edits during preparation. All four standard/USER-key app/test APK signatures verified successfully (JDK native-access warning only). No connected tests, ADB phone commands or emulator operations performed. Inventory/secret-pattern review found only expected source/tests/docs; generated APKs, caches, IDE files and local.properties remain excluded from the checkpoint. Full staged inventory is reviewed before the single commit; hash/clean-tree verification is reported in the final handoff.

Earlier verification, retained for provenance:

Final resumed run (2026-10-26 local; XML timestamps 2026-10-25T19:30:40Z):

```powershell
.\gradlew.bat --offline :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest --no-configuration-cache '-Pkotlin.compiler.execution.strategy=in-process' --console=plain
```

BUILD SUCCESSFUL in 2m 21s, 81 actionable tasks (20 executed, 61 up-to-date). JVM test task executed: 40 tests, 0 failures/errors/skips. Lint: 0 errors, 34 warnings (dependency/version/catalog suggestions, existing manifest/resources, and one UseKtx style suggestion in the measured screen). Debug and test APKs assembled. No source edits occurred during this final run.

Resumed baseline also passed (34 tests before review fixes), including `:app:signingReport`. Prior failed attempts are retained as history: intermediate compiler issues and six lint violations were corrected; an internal Kotlin FIR lint crash did not recur on two stable-source runs. Its exact root cause is unproven. Prior offline connected testing lacked a cached UTP artifact; an online attempt reached the Samsung SM-G781W/Android 13 but failed installation with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` before executing tests. Its runner exit-code file alone is not a test-pass result. On resume, ADB lists no connected device; no installation, uninstallation or data clearing was performed.

Public signing evidence: sandbox APK SHA-256 `6116b8776be2003d36b2f3b561abaf5c5c5cd160902e75337c6c0e07dd9a000e`; USER debug key SHA-256 `495361e26d4065c93b4d8ca15c580e11a6fababb87104667f4f7f85e26232c73`. The installed phone certificate remains unknown. See the verification report for a safe candidate and device workflow.

Final `git diff --check` passed; Git reports expected LF-to-CRLF normalization warnings. All work remains on `feat/squat-monitoring` with no commit/push/merge. Historical audit results below are retained for provenance.

| Date / phase | Command or evidence | Actual result |
| --- | --- | --- |
| 2026-10-25 / Phase 1 | `git status --porcelain=v1`, branch/log inspection | Clean tree; `main`, `e891615`. No branch switch or commit. |
| 2026-10-25 / Phase 1 | `.\gradlew.bat --offline :app:assembleDebug :app:testDebugUnitTest '-Pkotlin.compiler.execution.strategy=in-process' --console=plain` | BUILD SUCCESSFUL; incremental assembly, test task initially UP-TO-DATE. Java emitted `AccessDeniedException` for SDK `platforms/android-37.0/core-for-system-modules.jar`; not diagnosed/resolved. |
| 2026-10-25 / Phase 1 | `.\gradlew.bat --offline :app:testDebugUnitTest --rerun '-Pkotlin.compiler.execution.strategy=in-process' --console=plain` | BUILD SUCCESSFUL; test task executed: six `ExerciseFiltersTest` tests plus one template `ExampleUnitTest`; 7 passed, 0 failures/errors/skips. |
| 2026-10-25 / Phase 1 | `git diff --check` | Passed. |
| 2026-10-25 / Phase 1 | SDK platform-tools `adb devices -l` | No connected devices/emulators. Instrumented/camera checks not executed; lint not executed. |
| 2026-10-23 / historical | `docs/frontend-redesign.md`, existing JVM XML and `app/build/reports/visual/warm-card-device-tests.txt` | Historical seven JVM and nine device-test successes recorded. Not current squat/camera validation; generated report files are ignored and may not exist on another checkout. |
| 2026-10-25 / Phase 2 | `git diff --exit-code`, `git diff --check`, file-status and documentation content checks | Passed: tracked files unchanged; only six expected new Markdown files; nonempty content, balanced fences, final newlines and no trailing whitespace. Scope/status consistency reviewed. No build/test rerun for documentation-only changes. |
| 2026-10-25 / Phase 3 | Source/context review, cached CameraX source and MediaPipe API inspection, official Android/MediaPipe references | Design evidence only. API signatures printed; `javap` also emitted cache-file `AccessDeniedException` diagnostics. No application code edits/build/test execution. Final documentation/scope check result is recorded in `CONTEXT.md`. |

Build artifacts/reports include `app/build/outputs/apk/debug/app-debug.apk`, `app/build/reports/tests/testDebugUnitTest/index.html` and `app/build/test-results/testDebugUnitTest/*.xml`. Reports describe their recorded run, not every subsequent source change.

## Blockers, risks and decisions still needed

- Samsung connection/installed certificate must be checked after approval. Emulators are prohibited. Follow the physical-device guide; instrumented execution requires separate approval and verified data isolation. Complete movement/camera calibration before asserting accuracy.
- Signing is environment-dependent. Compare installed and candidate certificates before updating an existing app. Do not uninstall/clear data to resolve mismatch; the pinned test runner can optionally uninstall during cleanup, including failure paths.
- Conservative tracking interruption can miss real reps. Camera performance, permission/lifecycle transitions and visual overlay alignment remain device-validation tasks.
- Preserve the authorized feature checkpoint. Only the one Checkpoint D commit is authorized; no additional branch/commit/amend, push, merge, reset or clean. Stop after handoff and await deployment approval.
