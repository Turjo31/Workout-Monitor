# Technical architecture

Updated on 2026-10-26 for `feat/squat-monitoring`, based on `e891615`. The implemented feature architecture below supersedes the pre-feature audit retained later in this file.

## Implemented squat architecture

```text
CameraPreview: shared viewport Preview + RGBA ImageAnalysis, selected lens
  -> CameraFrameConversion: padded RGBA crop + upright rotation (no mirror)
  -> PoseDetector: one LIVE_STREAM inference slot, source-anchored timestamp + frame key
  -> PoseCamera: current-epoch result -> raw-to-preview CameraX mapping -> PoseOverlay
  -> WorkoutSessionViewModel: bounded pose worker -> SquatAnalyzer
  -> short locked epoch check + WorkoutSessionController ledger commit
  -> StateFlow -> MeasuredWorkoutScreen (lifecycle-aware collection)
  -> explicit Finish -> WorkoutRecord -> versioned HistoryStateSaver -> Profile history
```

### Pure Kotlin analysis (`analysis/`)

- `PoseFrame.kt`: canonical upright/unmirrored normalized landmarks, optional visibility/presence, dimensions, capture time and session/camera/analysis/sequence identity; reusable `ExerciseAnalyzer` and result contract.
- `PoseGeometry.kt`: aspect-correct pixels, finite/degenerate checks, `atan2(abs(cross), dot)` interior angles.
- `SquatAnalyzer.kt`: same-side selection/locking, head/foot framing, confidence and discontinuity validation; five phases, time-based EMA, hysteresis and sustained confirmation. Invalid tracking immediately cancels the current attempt, preserving accepted count. Ordinal events, not cumulative-count differences, enter the session ledger.
- Defaults: standing 165/155 degrees, hip minimum 150, descent 150, bottom 100/112, EMA 80 ms, readiness 300 ms, phase confirmation 80 ms, final standing 200 ms, minimum cycle 600 ms, maximum valid gap 300 ms. Each confirmation needs multiple supporting observations. Values live in validated `SquatConfig`; real-device calibration remains pending.
- `FrameClock.kt` anchors camera nanosecond intervals to first elapsed-time receipt; duplicates are dropped and clock rollback/large drift causes a visible restart requirement. No receipt-time motion fallback.
- `FeedbackPresenter.kt` stabilizes text without delaying counting; problems preempt, recovery reacquires standing, confirmed phase guidance preempts ordinary text holds.

### Session ownership (`session/`)

`WorkoutSessionController` owns SETUP/RUNNING/PAUSED/BETWEEN_SETS/COMPLETE/FINISHED, immutable per-set ledger, epochs, frame/event high-water marks and idempotent Finish. Full Reset creates a new session ID. Set targets freeze counting and require Next Set. History summaries include partial sets, including a started zero-rep final set, without inventing completed sets.

`WorkoutSessionViewModel` is created once by MainActivity's existing Activity ViewModel facilities. It retains no camera/view/context. A single worker owns analyzer/filter mutation; one pending pose is allowed and overload invalidates continuity. Lightweight controller commands and commits share a short lock: Pause/Reset/etc close the epoch gate immediately, geometry runs outside that lock, and stale results cannot commit. Control-triggered analyzer resets are ordered on the worker. This realizes serialized state access without moving native work or expensive math onto the UI thread.

`SavedStateHandle` stores only versioned progress (`WorkoutProgressCodec`), never partial motion, landmarks or repetition events. Running restoration is PAUSED. Camera binding/background/rotation/errors pause active work; ordinary tracking loss requires standing reacquisition but not explicit Resume. Navigation clears the one active owner on exit. Unsupported exercises never instantiate their own analyzer.

Watchdog/transform-loss commands immediately advance the analysis epoch while retaining RUNNING and the accepted ledger. This prevents already queued/in-flight observations from committing after loss was signaled; the worker resets motion/ordinal state before processing the fresh epoch. Invalid landmark frames invalidate the analyzer directly on its serialized worker. Recovery feedback retains uncertainty during standing acquisition but can switch to a current, actionable framing problem.

### Camera, transforms and lifecycle

`CameraPreview` waits for layout, uses `PreviewView.COMPATIBLE`/FILL_CENTER and a shared `UseCaseGroup` viewport. It selects front/rear, reports actual fallback lens, and unbinds only owned use cases. `PoseCamera` remounts on lens/retry/layout/display-rotation/lifecycle epochs, invalidates old callbacks and clears stale overlays. Permission handling and Settings recovery live in the measured screen.

`PoseDetector` initializes/detects/closes on its camera worker. A single pending slot retains input image/bitmap, frame key, source transform and inverse crop/rotation matrix until completion or graph closure. Every proxy closes on success, drop or exception. Model errors, delayed inference and stalls surface to UI. A 500 ms watchdog clears stale tracking; a stuck inference requires Retry, which creates a fresh detector/epoch. Resource release follows native close before input reuse.

Analysis never mirrors coordinates. Overlay mapping is model pixels -> inverse crop/rotation -> raw analysis pixels -> `CoordinateTransform` from default `ImageProxyTransformFactory` to current `PreviewView.outputTransform`. Preview's target transform supplies front-camera mirroring. Invalid/unavailable transforms suppress readiness instead of using an approximate fill mapping. CameraX experimental opt-in is local.

### UI and history

`WorkoutSessionScreen` dispatches only exact Bodyweight Squat to `MeasuredWorkoutScreen`; its existing timer path remains explicitly demo-labeled. The measured screen reuses glass stats/cards, scrim, typography and controls, adding Start, Next Set, Reset, lens/Retry and single-priority feedback. Dialogs pause counting. Root navigation appends a measured summary once, or a classified demo entry; empty measured Finish saves nothing.

`WorkoutRecord` retains timestamp/name constructor compatibility. `WorkoutHistoryCodec` writes `workout-history-v2` length-delimited primitive rows and reads legacy alternating timestamp/name lists. Per-set counts/targets determine actual totals/full sets/completion status. Unknown/malformed entries fail safely without invented measurements. `HistoryStateSaver` delegates to this codec. Profile's history cards show measured details or honest legacy/demo labels; timestamp-based activity/streak calculations are unchanged.

Saved-instance-state history is not durable storage across arbitrary termination, restart or reinstall. No Room/DataStore/backend/dependency/module change was introduced. Full verification and physical-device limitations are recorded in `TASKS.md`.

## Historical pre-feature architecture (audit of main at e891615)

## Stack and build configuration

| Item | Repository configuration |
| --- | --- |
| Project/modules | `GymTest`; single Android application module `:app` in `settings.gradle.kts`. |
| Identity | Namespace/application ID `com.kuet.gymtest`; manifest launcher `.MainActivity`; display name Workout-Monitor. |
| Android | min SDK 24; compile/target SDK 37; version code 1, name 1.0. |
| Build | Gradle wrapper 9.6.0; Android Gradle Plugin 9.4.1; configuration cache enabled; Foojay resolver convention 1.0.0. |
| JVM/Kotlin | Daemon JVM criteria 25; Java source/target 11; Compose compiler plugin 2.2.10; no separate Kotlin Android plugin declared. Built-in Kotlin compiler version was not independently inspected. |
| Compose | BOM 2026.02.01; UI/graphics/tooling, Material 3 and extended Material icons; Activity Compose 1.8.0. |
| Lifecycle/core | Lifecycle runtime KTX 2.6.1, runtime Compose 2.11.0; core KTX 1.19.1; Activity KTX 1.8.0. |
| Camera/vision | CameraX core/camera2/lifecycle/view 1.6.2; MediaPipe Tasks Vision 1.1.0. |
| Design | Haze 1.6.10; four bundled Nunito font weights; shared theme tokens. |
| Other dependencies | AppCompat 1.8.0; Material Components 1.10.0; ConstraintLayout 2.1.4; Preference KTX 1.2.0. |
| Tests | JUnit 4.13.2; AndroidX test JUnit 1.3.0; Espresso 3.7.0; Compose UI tests from BOM; AndroidJUnitRunner. |

Dependency declarations live in `app/build.gradle.kts` and `gradle/libs.versions.toml`. Repositories are Google/Maven Central, with the plugin portal for plugins. `local.properties` points to the local SDK and is ignored. Release optimization is explicitly disabled in the current build script.

## Source map

Paths below are relative to `app/src/main/java/com/kuet/gymtest/`.

| Area | Files and responsibility |
| --- | --- |
| Entry/navigation | `MainActivity.kt`: edge-to-edge setup, theme, `AppNavigation`, tabs, profile/plan/history and session routing. |
| Screens | `OnboardingScreen.kt`, `HomeScreen.kt`, `RecommendationScreen.kt`, `ExercisePoolScreen.kt`, `ProfileScreen.kt`, `WorkoutSessionScreen.kt`, `CameraScreen.kt`. |
| Domain/catalog | `Exercise.kt`, `UserProfile.kt`, `RecommendationEngine.kt`, `ExerciseFilters.kt`. Exercise names currently act as selection identifiers. |
| History/date | `WorkoutRecord.kt`: timestamp/exercise name plus calendar-based date/streak/greeting helpers. |
| Saved state | `UiStateSavers.kt`: profile, history and plan-name savers; filters also define their own saver. |
| Vision/camera | `CameraPreview.kt`, `PoseDetector.kt`, `PoseCamera.kt`, `PoseOverlay.kt`. |
| UI reuse | `ui/components/WorkoutComponents.kt`, `GlassBackdrop.kt`, `CameraComponents.kt`, `AgeWheelPicker.kt`, `ExerciseFilterSheet.kt`, `ExerciseDetails.kt`. |
| Tokens | `ui/theme/Color.kt`, `Type.kt`, `Theme.kt`, `Shape.kt`. |

Other roots: `app/src/main/assets/pose_landmarker_lite.task`, `app/src/main/res`, JVM tests in `app/src/test`, instrumented tests in `app/src/androidTest`, and `docs/frontend-redesign.md`. Legacy preference/layout resources remain; no settings activity is registered. Generated `build`, `.gradle` and `.kotlin` trees are not application architecture.

## Compose navigation and state

`MainActivity` renders `GymTestTheme { AppBackground { AppNavigation() } }`. Navigation uses conditional composition and `BackHandler`, with Home, Plan, Exercises and Profile tabs. Onboarding appears when the saved profile is absent. The active exercise name routes to a workout session; Profile can open standalone pose preview.

`AppNavigation` owns profile, selected plan names, history, current tab and active route using `rememberSaveable`. A saveable state holder preserves tab state. Screens use local Compose state and callbacks. There is no existing ViewModel, repository/storage layer, Room/DataStore integration or Navigation Compose graph.

Finishing invokes a callback that appends `WorkoutRecord(System.currentTimeMillis(), exercise.name)`. Its saver serializes alternating timestamp/name values. Adding fields would require reviewing this schema and consumers; no such change is approved yet.

The warm light theme, bundled typography, spacing/shapes and glass components are established frontend infrastructure. Camera routes set `LocalGlassBackdrop` to null and use dark translucent controls rather than Haze sampling of the preview. See `docs/frontend-redesign.md` for historical styling/verification details; its branch/status narrative is historical.

## Camera and MediaPipe pipeline: implemented

```text
WorkoutSessionScreen or CameraScreen checks/requests CAMERA permission
  -> PoseCamera composes CameraPreview and PoseOverlay
  -> CameraPreview binds front-camera Preview + ImageAnalysis to lifecycle
  -> RGBA_8888 frames on one executor; KEEP_ONLY_LATEST backpressure
  -> PoseDetector copies pixels to bitmap, rotates and mirrors front input
  -> PoseLandmarker.detectAsync(image, SystemClock.uptimeMillis())
  -> LIVE_STREAM result listener maps first pose to PoseResult
  -> PoseCamera posts result to main executor
  -> overlay state and screen tracking callback update
```

`CameraPreview` uses `PreviewView.ScaleType.FILL_CENTER`. Both preview binding and `PoseCamera`'s detector call hardcode the front camera. It has no switching API or rear-camera control.

`PoseDetector` loads the bundled lite model, requests one pose and sets minimum detection/presence/tracking confidences to `0.5`. An absent pose becomes an empty landmark list. `PoseLandmark` stores `x`, `y`, `z`, `visibility`; missing visibility becomes zero. `PoseResult` stores landmarks and transformed input dimensions. Result time, landmark presence and world coordinates are not propagated.

The detector reads `ImageProxy.imageInfo.rotationDegrees` after the proxy's `use` block closes it. RGBA copying assumes the plane can be copied directly to a bitmap; explicit row-stride handling is absent. Front-camera inference input is mirrored with a matrix after rotation. These details need review and real-device validation before extending transforms.

## Coordinates and overlay: implemented versus pending

The adapter copies MediaPipe normalized coordinates directly. The current overlay maps `x` and `y` through input pixel dimensions, a maximum fill scale and centered crop offsets. Visibility above `0.5` gates lines/dots. Connections include shoulders, arms, torso, hips, knees and ankles; no exercise measurements use these coordinates yet.

The future analysis convention must be specified at Checkpoint C: image orientation, mirroring, timestamps, side identity, aspect-ratio correction for 2D geometry and any normalization. Normalized x/y components must not be assumed to have equal physical scale. Current `z` is not a validated metric 3D representation. Overlay alignment across differing preview/analysis aspect ratios and device rotations remains unverified; there is no explicit shared viewport mapping in source.

## Threading, lifecycle and errors: implemented

- Camera analysis runs on a single executor. MediaPipe uses asynchronous live-stream results. Compose state/callback delivery is posted to the main executor.
- Detector construction is initiated by `remember` during composition; closing occurs on disposal. These operations are not explicitly moved to a worker.
- `detect`/`close` synchronize native lifetime access; a volatile closed flag and `PoseCamera` active guard limit callbacks after disposal. `rememberUpdatedState` keeps the callback current.
- CameraX binds to lifecycle. Disposal clears analysis, unbinds only owned use cases and shuts down the executor; late provider callbacks are ignored.
- Camera binding and MediaPipe error listeners print exceptions. There is no structured camera/inference error state, initialization recovery UI or stale-pose timeout.
- Permission screens can request/retry access. Permanent-denial/settings flow and revalidation after settings changes are not explicit.
- Lifecycle binding does not define session pause/recovery. Camera transitions, app backgrounding and configuration restoration need an explicit analyzer reset policy.

## Workout behavior: implemented

`WorkoutSessionScreen` owns set, reps, paused/completed flags with Compose saved state. A coroutine increments reps every 1.5 seconds, advances sets after a one-second delay and completes at catalog targets. Pause cancels the timer effect; Resume restarts it. Finish is confirmed in a dialog and can save an incomplete session. There is no visible Reset or explicit Start control.

Pose tracking is independent of the timer: any eight landmarks with visibility above `0.5` indicate tracking. It does not validate required squat landmarks. There is no geometry module, exercise-analysis interface or movement state machine in current source.

## Approved direction: not implemented

The user approved a reusable `ExerciseAnalyzer` architecture with independent `SquatAnalyzer` and later `PushUpAnalyzer` implementations sharing one camera/pose pipeline. Initial analysis is restricted to **Bodyweight Squat**. See `ADR-001` and `ADR-002` in `DECISIONS.md`.

```text
Existing camera capture -> MediaPipe -> landmark validation
  -> normalized pose representation -> selected ExerciseAnalyzer
  -> exercise state/feedback -> session state owner -> existing Compose UI
```

The conceptual contract is `processFrame(pose): analysisResult` plus `reset()`. Exact types, package paths, execution ownership, timestamp and camera-generation handling, session ViewModel choice, reset/pause policy and algorithm thresholds are pending Checkpoint C. No class names in this proposed flow imply files already exist. There is no implemented reusable analysis interface to document yet.

Geometry should be pure Kotlin; rendering should consume state. Avoid duplicating capture/inference or adding a new module/dependency without demonstrated need. A future push-up analyzer must not require rewriting the camera pipeline.

The [detailed Checkpoint C proposal](docs/squat-monitoring-design.md) now specifies candidate data types, serialized ownership, camera transforms/timestamps, validation, thresholds, interruption rules, controls/history and tests. Its implementation choices are **proposed, not approved or implemented**. B is approved; C review is pending. Read-only inspection of cached CameraX 1.6.2 source and MediaPipe 1.1.0 signatures confirmed the transform and confidence/timestamp APIs used in the proposal. Some `javap` invocations also emitted cache-file access exceptions; no new application build/test was performed.

## Verification and technical debt

Phase 1 debug assembly succeeded and seven JVM tests executed successfully on 2026-10-25. A Java SDK-file access exception appeared during the successful assembly. Camera/device checks and current lint were not run. Existing historical UI tests do not establish movement accuracy. See `TASKS.md` for commands, results, risks and planned validation.
