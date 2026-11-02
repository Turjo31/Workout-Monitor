# Session handoff

CI preference update (2026-11-02): user no longer wants automatic CI. Removed push/pull_request triggers from `.github/workflows/android-ci.yml`; kept optional manual `workflow_dispatch`. Instrumented job remains disabled. This is a local uncommitted change on `refactor/exercise-monitoring`; remote behavior changes only after publication. No workflow was run, and no GitHub settings were changed. Local verification requirements remain applicable.

## Current work — 2026-11-01

Active branch: `refactor/exercise-monitoring`, created with user approval from clean local main `1718a44`. Prior squat implementation and workflow are already merged into this baseline; the old CI handoff below is historical. No commit/push/merge is authorized for this refactor.

The user approved the OCP integration plan and camera screen cleanup. Implemented: exact-name `ExerciseMonitorRegistry` with fresh analyzer factories; extendible result/feedback contracts and common presentation; generic ViewModel/feedback/UI/history routing. Only Bodyweight Squat is registered. Squat motion rules, camera files, controller, history schema/saver and Profile rendering are unchanged. Compact stats and one control row replace the larger measured controls; Start/Pause/Resume, Reset and camera switch use accessible icons. Next Set and Finish retain labels. The visible 2D-estimate paragraph is removed; limitations remain documented.

New extension guide: [docs/adding-exercise-analyzers.md](docs/adding-exercise-analyzers.md). Test-only alternate strategy checks factory isolation, custom messages/phases, stable message holds and generic partial history. Android worker test covers switching strategies and rejecting queued old frames.

Final verification after the power interruption: BUILD SUCCESSFUL in 2m37s, 6 tasks executed/75 up-to-date. All 44 JVM tests freshly executed, 0 failures/errors/skips. Lint freshly executed: 0 errors/32 warnings. Debug and Android-test APK assembly succeeded incrementally (up-to-date); Android tests were not executed. Reports verified at 07:58 local on 2026-11-01. `git diff --check` passed. Full command/inventory: [verification report](docs/squat-monitoring-verification.md).

User reports that squat counting worked on their own phone with the prior implementation. This is user-reported movement validation, not an exhaustive edge-case/accuracy evaluation or validation of this UI refactor. No phone deployment or local emulator is authorized. Current workflow's instrumented job is disabled; do not describe it as executed.

Next: review the completed uncommitted changes and, after deployment approval, check the compact UI on a phone (including large fonts), session actions and both cameras. Controls wrap when space is limited. Commit/push/merge are not authorized. No new critical defect was found in the scoped source review; Android worker execution and device layout remain unverified.

## Historical handoff — superseded branch/task status

Updated: 2026-10-28 (Asia/Dhaka). Active branch: `feat/squat-monitoring`; checkpoint `7acf936` (`feat: implement bodyweight squat monitoring`), based on main at `e891615`. The checkpoint already exists; do not reuse the earlier one-commit authorization.

## Current task and authorization

Current request: add GitHub Actions CI without committing/pushing/creating a PR or changing main/settings. Configure a disposable GitHub-hosted emulator for existing Android tests; do not install/configure/launch an emulator locally or interact with the Samsung. Application source, test logic, signing and Gradle dependencies must remain unchanged. Checkpoint C implementation and earlier local verification remain the baseline; physical-device deployment still requires approval.

Read the six context files in the order prescribed by AGENTS.md, then inspect Git and relevant source. The approved design's original minimal-history option was superseded by the user's final requirements.

## Implementation snapshot

- One existing Kotlin/Compose app with its warm glass design and Nunito fonts retained.
- MainActivity owns one `WorkoutSessionViewModel`; pure session/controller and analyzer code lives in `session/` and `analysis/`. Explicit Start, Pause/Resume, whole-session Reset, manual Next Set and idempotent Finish are implemented.
- CameraX shared viewport supports front/rear selection. Cropped/rotated RGBA input stays unmirrored; timestamped MediaPipe frames carry epochs and map through CameraX transforms to the existing overlay. Bounded workers, visible errors and tracking watchdogs are implemented.
- Exact Bodyweight Squat uses the five-phase state machine. Other exercises retain the labeled timer demo. No additional analyzer is implemented.
- History stores actual per-set counts and targets, deriving total reps/full sets/completion. The versioned saver reads legacy timestamp/name records; demo/legacy counts remain unknown. Activity/streak logic still uses timestamps.
- No durable database/storage was added. Saved-instance history is not permanent across arbitrary process termination, device restart or reinstall.

## Recent verified work

- Phases 1–3: audited main at e891615; seven original JVM tests passed; created and reviewed the six root documents and approved design. The user accepted B and C.
- Implementation: created feat/squat-monitoring with all seven untracked documents preserved; added analysis/session/history and camera/UI integration without dependency upgrades.
- Resumed verification on 2026-10-26: confirmed sandbox/USER debug certificate mismatch. A stable-source build, 34 JVM tests, lint (0 errors/34 warnings) and test APK assembly succeeded before review fixes. The prior internal lint crash did not recur.
- Review fixes: immediate watchdog/transform-loss epoch invalidation; resolved tracking errors can yield to actionable framing guidance; unavailable-lens retry rebinds; non-finite analyzer configuration is rejected. Added regression tests. Final outcomes are in TASKS.md.
- Checkpoint D final source review found no new critical defect requiring changes. No production/test code changed during preparation. Forty JVM tests were freshly executed again; incremental build/lint/test APK tasks succeeded. Added `docs/samsung-device-testing.md` with certificate gates, Studio deployment precautions, Logcat commands and 20 ordered tests. No phone commands or emulator actions were performed during preparation.
- CI preparation (2026-10-28): clean working tree at checkpoint `7acf936` before edits; no existing workflows. Added `.github/workflows/android-ci.yml` for feature pushes, PRs targeting main and manual dispatch. Checkout uses the event SHA (PR + main merge revision for PRs). Independent jobs run debug JVM/build/lint/test-APK tasks and the complete instrumented suite on hosted API 35 x86_64 with KVM/SwiftShader. Actual XML counts go to logs/step summaries; XML/HTML/lint/log artifacts upload even on failure. No tests are filtered or failures tolerated. CI has not executed.

## Relevant source paths

All Kotlin paths below are under `app/src/main/java/com/kuet/gymtest/`:

- Integration: `MeasuredWorkoutScreen.kt`, `WorkoutSessionScreen.kt`, `MainActivity.kt`, `CameraScreen.kt`, `session/*`.
- Pipeline: `CameraPreview.kt`, `CameraFrameConversion.kt`, `PoseCamera.kt`, `PoseDetector.kt`, `PoseOverlay.kt`, `analysis/*`.
- Catalog/history: `RecommendationEngine.kt`, `Exercise.kt`, `WorkoutRecord.kt`, `UiStateSavers.kt`.
- Preserve/reuse: `ui/components/CameraComponents.kt`, `WorkoutComponents.kt`, `GlassBackdrop.kt`, `ui/theme/*`.

Build versions: `app/build.gradle.kts`, `gradle/libs.versions.toml`, wrapper/daemon properties. Existing tests: `app/src/test` and `app/src/androidTest`. Historical frontend context: `docs/frontend-redesign.md` (some branch/status statements are historical).

## Risks, bugs and unverified behavior

- Physical front/rear overlay alignment, movement accuracy, camera throughput and device lifecycle behavior remain unverified. Conservative loss handling may miss real reps. Limited silhouette checks do not establish reliable orientation or person identity.
- Last resumed-session ADB check found no connected device; Checkpoint D preparation did not query or assume a current connection. The prior connected run failed before instrumentation with INSTALL_FAILED_UPDATE_INCOMPATIBLE. Sandbox and USER debug keys differ; installed certificate remains unknown until tomorrow's checks.
- The pinned test runner has optional uninstall cleanup even after installation failure. No emulator is allowed. A debug-only separate test ID is proposed if signatures differ or the existing installation must remain untouched; it is not implemented or approved yet. No install/uninstall/clear-data operation was run in the resumed or preparation sessions; the prior run's device state remains unknown.
- Previous lint failure was internal Kotlin FIR analysis: FirDeclaration was not found for KtFunctionLiteral. It did not reproduce with stable sources. Prior concurrent edits are a possible contributor, not a proven root cause. Keep sources unchanged during checks.

## Verification snapshot

Current CI-edit verification: Gradle task listing and a five-task dry run succeeded; YAML/trigger/checkout/artifact checks, eight shell syntax checks and fourteen synthetic report-summary scenarios passed. These are configuration checks, not app test execution. SDK package metadata confirms `platforms;android-37.0` / build-tools 36.0.0; daemon criteria require JDK 25. No hosted CI run or new app/JVM/instrumented execution occurred in this task.

Earlier Checkpoint D command succeeded in 24s: 81 tasks, 2 executed/79 up-to-date. Forty JVM tests freshly executed then (0 failures/errors/skips); APK/lint tasks up-to-date (0 errors/34 warnings). XML times: 2026-10-25T20:00:31Z. See TASKS.md and `docs/squat-monitoring-verification.md`. USER-key APK candidates remain ignored build artifacts; no movement calibration has occurred.

## Next actions

1. Review the uncommitted workflow/docs. A user-authorized commit/push is needed before this new workflow can run; do not perform it under the CI-edit request. Inspect the first hosted job logs/artifacts rather than assuming previous local test counts apply.
2. Hosted instrumentation is configured via `:app:connectedDebugAndroidTest`; graphics/worker timing reliability remains unverified until the first run. Do not filter/skip failing tests to turn CI green. Manual dispatch requires the workflow to be present on the default branch; feature push/PR triggers are the initial route without modifying main.
3. Physical Samsung camera/movement validation remains separate: follow `docs/samsung-device-testing.md` only after deployment approval. Installed-certificate comparison, both-lens alignment, lifecycle and real squat calibration remain pending. No local emulator or phone deployment is authorized by the hosted CI request.
