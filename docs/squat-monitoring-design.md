# Bodyweight Squat monitoring — Checkpoint C proposal

Date: 2026-10-25. Status: **Checkpoint C approved and implemented; local verification passed, physical calibration pending**. The final authorization explicitly replaces section 11's original minimal-history recommendation: actual measured per-set counts, targets, completion status and measured/demo/legacy classification are required and implemented with versioned legacy-compatible saved state. Creation of the one local `feat/squat-monitoring` branch was explicitly authorized and performed. Checkpoint D on 2026-10-26 authorizes one local checkpoint commit and Samsung test preparation only; push/merge and device deployment remain unauthorized, and emulator use is prohibited. This document preserves the original review proposal below as design rationale. Current implementation and verification, including deviations, are in [ARCHITECTURE.md](../ARCHITECTURE.md) and [TASKS.md](../TASKS.md).

## 1. Recommendation and boundaries

Verification follow-up (2026-10-26): local build, 40 JVM tests, lint and test APK assembly passed. Instrumented execution and physical calibration remain pending. See [implementation verification](squat-monitoring-verification.md). Watchdog/transform-loss now invalidates the analysis epoch immediately before queued work can commit, preserving RUNNING and accepted progress. Current measured-history behavior follows the final authorization, not the superseded minimal-history recommendation retained below.

Extend the existing camera, detector, overlay and workout screen. Add a pure Kotlin analyzer and a small session state owner. Initial measured analysis is exclusively for **Bodyweight Squat**, viewed from either side with the front or rear camera. Push-up analysis and other squat variants remain deferred.

Preserve the existing theme, typography, glass cards, navigation and preview composition. New controls use existing button/card styles. No new app/module, navigation migration, dependency upgrade, durable storage layer or wholesale camera replacement is proposed.

Prefer missing an uncertain repetition to completing one from observations separated by tracking loss. The initial algorithm estimates side-view knee bend from 2D landmarks; it does not establish anatomical squat depth, medical safety or precise biomechanics. Every numerical constant below is a configurable engineering starting point for device calibration, not a universal exercise prescription.

## 2. Repository/API evidence

The current app uses CameraX 1.6.2 and MediaPipe Tasks Vision 1.1.0. `CameraPreview.kt` hardcodes the front lens; `PoseDetector.kt` mirrors input; `PoseResult` omits time; `WorkoutSessionScreen.kt` counts using a timer. `WorkoutRecord` has timestamp/name only. These remain unchanged during this design phase.

Read-only inspection of cached pinned artifacts confirmed these APIs:

- `PoseLandmarkerResult.timestampMs()`, `landmarks()`, `worldLandmarks()` and `NormalizedLandmark.presence()`/`visibility()`.
- `ImageProxyTransformFactory`, `CoordinateTransform`, and `PreviewView` output transforms. The CameraX source marks transform APIs experimental and recommends `COMPATIBLE` mode for accurate output transforms.
- `ImageInfo.getTimestamp()` documentation delegates clock details to the image source. The cached CameraX `AndroidImageProxy` source forwards the Android image timestamp; camera capture metadata also forwards its source timestamp. Do not assume these share the app's elapsed-time origin.

`javap` printed the MediaPipe signatures but also emitted cache-file `AccessDeniedException` diagnostics on exit. This is evidence of available signatures, not a successful compilation/validation of the proposed implementation.

Official documentation confirms per-frame live-stream timestamps and normalized x/y coordinates, while CameraX documents shared viewport mapping. These facts inform the proposal; the algorithm and policies below are project-specific recommendations. [MediaPipe Android guide](https://developers.google.com/edge/mediapipe/solutions/vision/pose_landmarker/android), [CameraX configuration](https://developer.android.com/media/camera/camerax/configuration), [CoordinateTransform](https://developer.android.com/reference/androidx/camera/view/transform/CoordinateTransform).

## 3. Component and ownership design

```text
Existing CameraPreview + PoseDetector
  -> timestamped PoseFrame + Android-only overlay transform metadata
       |-> PoseOverlay on the main thread
       `-> serialized session processor
             -> frame/landmark validation -> geometry -> SquatAnalyzer
             -> WorkoutSessionController -> immutable WorkoutUiState
             -> WorkoutSessionViewModel -> existing Compose components
```

| Component | Responsibility |
| --- | --- |
| Existing camera composables | Permission/lens selection, lifecycle binding, preview, disposal and display transforms. No squat rules. |
| `PoseDetector` | Model lifetime, safe RGBA conversion, input orientation, bounded asynchronous inference and result provenance. No repetitions or session policy. |
| Pure `PoseFrame`/landmark models | Canonical coordinates, confidence, dimensions, observation time and frame identity. No Android/Compose/MediaPipe classes. |
| Geometry/validation functions | Finite values, segment lengths, angle math, required body parts and usable side selection. No UI or wall clock. |
| `SquatAnalyzer` | Motion state, continuous confirmation, filtering, depth estimate, cycle completion and continuity invalidation. No sets or persistence. |
| Pure `WorkoutSessionController` | Start/pause/reset/set rules, cumulative counts, event deduplication and finish summary. Testable without Android. |
| `WorkoutSessionViewModel` | Lifecycle-retained session owner, serialized background processing, observable state and saved completed progress. No Activity/PreviewView references. |
| Existing workout screen | Collect lifecycle-aware state and dispatch actions; render cards, overlay, labels and controls. No geometry or counting coroutines. |

Use one simple exact-name analyzer factory for the current catalog: `Bodyweight Squat -> SquatAnalyzer`, otherwise no measured analyzer. Do not use substring matching or create a plugin/DI registry. Keep existing package layout; add small `analysis` and `session` packages inside `:app`.

Proposed new source files: `analysis/PoseFrame.kt`, `ExerciseAnalyzer.kt`, `PoseGeometry.kt`, `SquatAnalyzer.kt` (including config and validation helpers where cohesive), `session/WorkoutSessionController.kt`, and `WorkoutSessionViewModel.kt`. Split further only if implementation size requires it. The current pose DTO definitions can move out of `PoseDetector.kt` into the pure model file and be adapted to the new frame type at their small number of call sites. Do not maintain two independent landmark representations/pipelines.

ViewModel runtime/saved-state and coroutines artifacts are already present in the local build cache/transitive stack, but the exact resolved classpath must be checked before implementation. Prefer existing `ViewModelProvider`/saved-state APIs and avoid adding a Compose ViewModel helper dependency just for construction. Any newly referenced artifact should be declared directly at the already-resolved version if required; no version upgrade is approved. One Activity-retained session owner may hold the currently active session; it must explicitly clear that session on route exit and reset when its saved session identifier changes. Do not retain an accumulating ViewModel per workout in the Activity store.

## 4. Pure contract and data model

Proposed public shape (a design sketch; no `.kt` file is created now):

```kotlin
interface ExerciseAnalyzer {
    fun processFrame(pose: PoseFrame): ExerciseAnalysisResult
    fun invalidateTracking(reason: DiscontinuityReason)
    fun reset()
}

data class FrameKey(
    val sessionId: String,
    val cameraEpoch: Long,
    val analysisEpoch: Long,
    val sequence: Long
)

data class PoseFrame(
    val key: FrameKey,
    val timestampMs: Long,
    val imageWidth: Int,
    val imageHeight: Int,
    val landmarks: List<PoseLandmark>
)
```

`PoseLandmark` keeps x/y/z and visibility, adding nullable presence/confidence availability rather than inventing a score for missing data. Dimensions describe the actual cropped, upright model input. Landmarks remain anatomically indexed. A no-person result is an explicit empty landmark list with its original frame key/time; do not discard it before analysis.

`ExerciseAnalysisResult` is a small sealed interface with frame identity/time, tracking status, cumulative completed repetitions since full reset, an optional `RepetitionCompleted(ordinal, timestampMs)` and a structured feedback code. `SquatAnalysisResult` adds `SquatPhase`, `DepthStatus`, selected side and nullable `SquatMetrics`. A later push-up result can implement the shared interface without exposing squat fields as a universal contract. Avoid string phases, untyped metric maps and a speculative hierarchy of generic engines.

`SquatMetrics` contains knee interior angle, optional hip angle and optional torso inclination for diagnostics. `DepthStatus` is `UNKNOWN`, `ABOVE_TARGET`, or `TARGET_REACHED`; it must become unknown when measurement is invalid. UI strings describe estimates.

All analyzer methods are called on one serialized worker. `reset()` clears counts, ordinals, phase, filter history, timers, selected side and partial motion. `invalidateTracking()` clears motion/filter/timer/side/timestamp continuity, returning to `NOT_READY`, while preserving completed count and ordinal. It is required because camera loss can occur with no frame callback. Neither method reads system time. Pause itself is session policy, not a hidden analyzer clock.

## 5. Time, identity and backpressure

Keep three distinct concepts:

| Value | Use |
| --- | --- |
| Camera source timestamp in ns | Capture ordering and intervals within the same binding; never directly subtract from an app clock or compare across lenses. |
| MediaPipe input/result timestamp in ms | Motion timing; use the exact supplied time returned by `result.timestampMs()`, not callback arrival time. |
| App `elapsedRealtime` receipt/submission time | Pipeline watchdogs, inference latency and UI feedback timing. Wall-clock milliseconds are only for history. |

For the CameraX camera-backed stream, convert source intervals to milliseconds with a per-camera anchor: `taskMs = firstReceiptElapsedMs + (sourceNs - firstSourceNs) / 1_000_000`. This preserves source intervals without assuming a shared origin. The pinned camera adapter source was inspected and Android documents image timestamps in nanoseconds with source-dependent origins. Device timing still needs validation. [Android Image timestamp](https://developer.android.com/reference/android/media/Image#getTimestamp()).

Drop duplicate input times instead of manufacturing elapsed movement. A source clock rollback or a forward jump inconsistent with elapsed receipt intervals by more than a configurable 1000 ms invalidates the binding; restart with a new camera epoch and detector. A normal observation gap above 300 ms only invalidates motion continuity. Never switch timing conventions mid-cycle. A source that cannot provide usable monotonic time produces measurement uncertainty/Retry; receipt-time counting is not an implicit fallback in the initial implementation.

Store frame metadata in the pending inference slot keyed by the task timestamp; recover it using the result timestamp. Never attach the latest lens, analysis epoch or transform to a callback from an earlier submission.

- `sessionId` changes for a new workout and full Reset.
- `cameraEpoch` changes on bind/rebind, lens change, orientation/viewport change or detector restart.
- `analysisEpoch` changes whenever count acceptance changes: Start, Pause, Resume, Next set, Reset, Finish, dialog interruption and lifecycle interruption.
- `sequence` and accepted observation times must increase. Reject duplicates, out-of-order times and old epochs before analysis and again before committing UI/count state.

Retain `KEEP_ONLY_LATEST`. Allow one MediaPipe inference in flight; close skipped CameraX frames immediately. This bounds frame metadata/image retention and avoids relying on callbacks for every submitted camera frame. On completion/error, release owned input resources on the camera worker; do not recycle an image still used natively. A timed-out task is invalidated and the detector is closed/recreated before its resources are reused. An asynchronous native close must not run concurrently with detect.

Native initialization, conversion, submission and close run on the camera worker. Result callbacks copy/forward immutable values and never block waiting for the worker that closes the graph. All analyzer/session commands run on a serial background session processor. Permit at most one outstanding pose-processing task; if that slot is full, mark continuity invalid before processing the next observation rather than accumulating old frames. Control commands are ordered and never dropped. Only publication of the latest overlay/UI snapshot may be conflated; counting never depends on Compose collection speed. Invalid/missing observations must reach continuity handling before any display conflation.

Start/Pause/etc. close the acceptance gate immediately at the command entry point, then enqueue the serialized command. Every pose carries the epoch captured at submission. A result queued before Pause cannot count after Resume. Protect the short epoch-check/count-commit operation and command gate update with the same lock; do not hold it during geometry or native calls. Work committed before the command boundary remains valid; an uncommitted result from the previous epoch is rejected. A control epoch change resets the analyzer/ordinal namespace on the serialized worker while retaining the controller's accepted set ledger, removing any uncommitted analyzer mutation. Observation-only tracking loss uses `invalidateTracking()` instead. The controller consumes committed repetition events only; it never reconstructs repetitions from a difference in analyzer cumulative totals. Inject the worker and monotonic clock in tests to control these races.

## 6. Coordinates, rotation and front/rear display

Canonical analysis input is **cropped, upright, unmirrored image space**: x increases right, y increases down. Use anatomical landmark indices; never swap left/right because the preview is mirrored. Keep z as supplied for diagnostics; the initial counter uses aspect-correct 2D coordinates, not z or world landmarks.

For a model-input landmark `(x, y)` and dimensions `(W, H)`, use `p = (x * W, y * H)` for geometry, or divide both components by the same scale such as `min(W, H)`. Multiplying x and y by different physical scales changes angles; computing angles directly in the normalized unit square is incorrect for non-square images.

Camera/overlay sequence:

1. Wait for PreviewView layout; bind existing Preview and ImageAnalysis in one `UseCaseGroup` using `previewView.viewPort` and the selected front/back selector. Keep `FILL_CENTER`. Query lens availability; preserve front as default when available and fall back visibly to rear if necessary.
2. While the proxy is open, snapshot source size, crop rect, rotation, time and source output transform. Copy RGBA respecting buffer position, row stride and pixel stride. Close the proxy exactly once in `finally`, including error/drop paths.
3. Crop to CameraX's viewport rect, then rotate into upright image space, adjusting translation/dimensions. Remove the existing inference-input horizontal mirror. Record the invertible matrix `M_raw_to_model` used by these exact operations.
4. Feed that model image to MediaPipe without a second rotation option. Convert landmarks to model-input pixels and invert `M_raw_to_model` to recover raw analysis-buffer coordinates.
5. Configure `ImageProxyTransformFactory` with both crop/rotation options **false**, because the points at this stage are raw full-buffer coordinates. Combine its snapshot with the matching PreviewView output transform using `CoordinateTransform`.
6. Map the raw points to PreviewView pixels. This target transform includes the preview's front-camera presentation. Do not apply another `1 - x`, Canvas reflection or view scale. Add only a real measured offset if the Canvas and PreviewView origins differ.

Use `PreviewView.ImplementationMode.COMPATIBLE` for the transform path, as the pinned source recommends. This is a targeted implementation change requiring device/performance verification; the visual layout remains the same. Keep the experimental transform opt-in local. [PreviewView reference](https://developer.android.com/reference/androidx/camera/view/PreviewView), [transform output guide](https://developer.android.com/media/camera/camerax/transform-output).

Snapshot matrices for each frame; do not mutate a shared Matrix after submission. When transforms are not ready or are from a different layout/binding epoch, clear/suppress the overlay and count readiness rather than use the old centered-scale approximation. Viewport, display rotation and PreviewView layout changes invalidate partial motion and old overlay metadata. Preview target rotation and ImageAnalysis target rotation must be coordinated; no orientation lock is introduced.

Transform tests cover non-square images, nonzero crop origins, padded rows, rotations 0/90/180/270, front/rear presentation and viewport resizing. Geometry is independent of display mirroring. Physical-device landmarks must visibly match the joints on both lenses; mathematical tests alone do not validate CameraX presentation.

## 7. Landmark validation and side-view setup

Use shoulders 11/12, hips 23/24, knees 25/26 and ankles 27/28 for same-side chains. Head/nose 0 and same-side heel 29/30 or toe 31/32 support framing. Arms are not required. Validate raw values before filtering; never forward-fill or interpolate missing joints into a count.

Proposed validation order:

1. Active provenance/time, positive dimensions, complete indexed input for required joints and finite coordinate/confidence values.
2. At least one shoulder-hip-knee-ankle chain with visibility at least `0.65`; enforce presence at least `0.5` when available. Missing required visibility means uncertainty, not confidence 1. Missing optional presence is recorded without fabricating a value.
3. The chosen chain, head proxy and at least one same-side foot marker lie inside the visible model crop with a `0.02` image-edge margin; framing proxies use visibility at least `0.5`. A hidden head/foot yields uncertain full-body visibility rather than an invented off-screen position.
4. Nondegenerate segments: at least `0.025 * min(W,H)` for required angle vectors. Shoulder must be above hip and hip above ankle for standing readiness; measurements must be consistent with an upright side-view setup.
5. Reject abrupt measurement discontinuities: proposed raw knee-angle speed over `600 degrees/second`, or a selected torso/leg segment changing by more than 35% between accepted observations no more than 300 ms apart. These are configurable uncertainty checks, not form judgments; invalidate continuity rather than clamp a bad value into a valid squat.

Choose the valid side with the highest minimum chain confidence; use a deterministic tie-breaker. Lock it during readiness and the complete attempt. Do not pick a different leg on every frame. If that chain becomes unusable, cancel the partial attempt; the next readiness sequence may choose the other side. Preserve already completed repetitions.

Side-view is a setup requirement: show "Stand sideways with your head and feet visible" before Start. Do not require the occluded far-side leg to be confidently visible. When both shoulder/hip pairs are reliable, a sustained broad front-view silhouette can trigger "Turn sideways": proposed shoulder separation / mean torso length > 0.75 **and** hip separation / mean torso length > 0.55 for 300 ms. While this candidate positioning problem is present, suspend readiness/count confirmation immediately; the timer only delays the text classification. If bilateral information is absent or ambiguous, do not claim orientation is detected: allow only the selected valid chain under the documented side-view assumption and retain the setup guidance.

This heuristic is deliberately limited, requires calibration and cannot guarantee side-view recognition. A second person taking a similar position also cannot be reliably identified with the current single-pose model. Missing pose, obvious jumps or changed side cancel continuity; do not describe that as identity tracking.

## 8. Geometry, filtering and depth

For joint points A-B-C, use vectors `u=A-B`, `v=C-B` and `atan2(abs(cross(u,v)), dot(u,v))` in degrees with Double intermediates. Reject non-finite inputs and near-zero vector norms before evaluation. This handles 0/180-degree cases without unclamped acos inputs.

- Knee interior angle: hip-knee-ankle; approximately 180 degrees when straight. If displaying flexion, name it explicitly as `180 - interiorAngle`.
- Hip interior angle: shoulder-hip-knee; use as a standing-readiness check, not an initial depth/form score.
- Torso inclination: shoulder-hip vector relative to image vertical; diagnostic only. Camera tilt and perspective prevent a reliable spinal/form judgment, so initial corrective torso feedback is disabled.

Filter valid knee measurements with a timestamp-based EMA: `alpha = 1 - exp(-dt / 80ms)`. Seed from the first valid measurement; clear the filter on every continuity break. Do not smooth NaN or missing data. Do not use a fixed number of rendering frames. Raw and filtered angles must both satisfy the target-depth and final-standing confirmation predicates, so a single spike followed by filter lag cannot manufacture a dwell.

Depth uses the configured knee-bend target only. `ABOVE_TARGET` means the target knee bend has not yet been confirmed in this attempt; `TARGET_REACHED` is latched for the valid attempt once bottom confirmation succeeds. On ascent, guide return to standing. A shallow attempt returning to standing emits "Try lowering farther on your next repetition" without a count. Do not claim hips reached anatomical parallel from this proxy.

## 9. Squat state machine and initial configuration

All constants belong in `SquatConfig`, checked for valid ordering/ranges. Units are explicit. Candidate confirmations accumulate only across ordered valid observations satisfying the condition, with at least two supporting observations. A contradictory/invalid sample clears the candidate timer. Gaps above the maximum observation interval break continuity; elapsed time alone never substitutes for observations.

| Parameter | Proposed default | Purpose |
| --- | --- | --- |
| Standing entry / exit | 165 / 155 degrees knee interior | Separate acquisition from leaving a standing pose. |
| Standing hip minimum | 150 degrees | Require an extended torso/leg start, not a bent seated position. |
| Readiness confirmation | 300 ms | Acquire standing after Start/recovery. |
| Descent confirmation | Knee <= 150 degrees for 80 ms | Commit a new descent after leaving standing. |
| Bottom entry / exit | <= 100 / >= 112 degrees | Depth threshold with hysteresis. |
| Bottom and ascent confirmation | 80 ms each | Reject isolated bottom or ascent samples. |
| Final standing confirmation | 200 ms | Complete the cycle only after returning fully. |
| Minimum descent-to-return duration | 600 ms | Reject an implausibly short full cycle. No maximum rep duration is imposed initially. |
| Maximum valid observation gap | 300 ms | Prevent counting across missing portions of movement. |
| No-result UI watchdog | 500 ms | Clear stale overlay/status and signal interruption, including no-callback stalls. |
| Maximum submission-to-result age | 500 ms | Reject delayed results rather than counting old movement. |
| EMA time constant | 80 ms | Reduce angle noise using elapsed time. |

```text
NOT_READY --confirmed standing--> STANDING
STANDING --confirmed descent--> DESCENDING
DESCENDING --confirmed target bend--> BOTTOM
BOTTOM --confirmed extension above exit threshold--> ASCENDING
ASCENDING --confirmed standing + valid full cycle--> STANDING [one rep]
```

| State | Exact behavior |
| --- | --- |
| `NOT_READY` | No counting. Choose/lock a usable side, seed filters and confirm standing. Starting at the bottom cannot arm a repetition. |
| `STANDING` | Armed. Leaving the 155-degree standing band clears top confirmation; a descent <=150 for 80 ms creates an attempt, with its start timestamp at the first supporting descent sample. Hovering in the hysteresis band cannot count. |
| `DESCENDING` | Record attempt start/minimum angle; bottom not yet earned. Returning to knee >=165 and hip >=150 for 200 ms without confirmed bottom cancels as partial, with zero reps, and rearms STANDING. |
| `BOTTOM` | Latch target reached. Repeated crossings near 100 cannot emit a rep. Confirm angle >=112 and at least 12 degrees above the attempt minimum before entering ascent. |
| `ASCENDING` | Confirm final knee >=165 and hip >=150 for 200 ms, continuous valid tracking and at least 600 ms since descent began. Then increment once, emit a new ordinal and clear the attempt/depth latch. Knee <=100 for 80 ms re-enters BOTTOM within the same attempt, retaining its start/depth latch and creating no new count. |
| Any active state | Missing/invalid required data, provenance change, side change or excessive gap immediately invalidates the attempt and returns to NOT_READY, preserving completed reps. |

Process at most one phase transition per observation; never infer unobserved descent/bottom/ascent states from a jump. A cycle that returns too quickly is canceled without a count and must reacquire standing. After a count, only a new confirmed descent can arm the next rep. Standing jitter, bottom bouncing, repeated callbacks and UI recomposition cannot increment anything.

A one-frame missing landmark cancels an in-progress attempt immediately. This conservative first policy will miss some real repetitions during occlusion; it prevents finishing a movement whose critical portion was unobserved. A future short-gap recovery policy requires additional evidence/tests and approval.

If no callbacks arrive, a foreground watchdog enqueues a discontinuity independently of inference. Even before the UI watchdog fires, the next observation with a gap >300 ms cancels the old attempt. On recovery, require a new 300 ms standing observation sequence. Never bridge a gap using the last angle. Watchdogs cannot increment reps.

## 10. Session actions and UI behavior

Session state is independent of movement phase: `SETUP`, `RUNNING`, `PAUSED`, `BETWEEN_SETS`, `COMPLETE`, `FINISHED`; unsupported exercises retain a separate explicit demo mode. Catalog defaults remain the source of target sets/reps.

| Action / event | Recommended behavior |
| --- | --- |
| Enter Bodyweight Squat | Preview and setup instructions appear; zero measured reps. Start is enabled when permission, camera/model and display transform are available. Tracking readiness is assessed after Start, without counting setup movement. |
| Start | Begin a fresh measured session/analysis epoch, RUNNING with NOT_READY; count only after standing confirmation. No countdown timer advances reps. |
| Pause | Immediately close count acceptance, preserve completed reps/sets and clear the partial attempt/filter. Keep preview/overlay live while the screen is foreground. |
| Resume | New analysis epoch; preserve ledger, reacquire standing. Returning from a bottom position cannot complete a pre-pause repetition. |
| Reset | Visible secondary text action using existing styling. Confirm "Reset this workout?"; clear the entire session, including completed sets/current reps, without writing history. Create a fresh session ID and return to SETUP. Camera selection may remain. No separate reset-current-set control initially. |
| Set target reached | Freeze the count at target and enter BETWEEN_SETS. Show set completion, keep overlay, and replace the primary action with "Next set". No one-second auto-advance or prescribed rest timer. |
| Next set | Archive prior completed set count, increment set, reset analyzer for the new set/ordinal namespace, and require standing again. Idle/rest movement cannot count. |
| Final set reached | Enter COMPLETE. Disable further counting; Finish remains explicit. Completion itself does not write history. |
| Tracking loss | Stay RUNNING but NOT_READY; preserve accepted totals, cancel partial motion and require standing again. Resume is not required for a transient tracking interruption. |
| Lens change / detector error / background | Pause a running measured session; cancel partial motion and clear stale overlay. Resume explicitly after recovery. Other non-running states retain their status. |
| Rotation/recreation | Preserve completed ledger and selected exercise/session identity; restore running work as PAUSED. Rebind with new camera/analysis epochs and fresh transforms. Never restore a partial phase or EMA. |
| Process restoration | Restore only small saved progress if Android supplies saved state; enter PAUSED (or SETUP if unstarted). Never claim durable recovery or persist landmarks/filter/matrices. |
| Finish dialog | Opening it suspends counting and cancels partial motion. Canceling the dialog leaves a formerly running session PAUSED; require Resume, so dialog-time motion cannot count. BETWEEN_SETS/COMPLETE remain in those states. |
| Back / exit | For a started measured session, confirm discard versus Finish; unstarted setup can exit directly. Exit clears the active session and camera resources. A later visit gets a new ID. |

The pure controller owns set ledgers and consumes each completion once, keyed by `(sessionId, analyzerGeneration, ordinal)`. Analyzer generation changes after full analyzer reset/new set. The UI never applies `reps++` on recomposition or collector restart. Restore ledger counts, not pending rep events. Commands/double taps and Finish callbacks are idempotent within the restored app-session state.

Keep existing stat cards for current-set reps and set number. Add compact phase/depth/tracking text within the existing feedback panel. Replace demo copy only for Bodyweight Squat. Use the existing main control position for Start/Pause/Resume/Next set, retain Finish and add small Reset/lens-switch controls. A pose-tracking indicator is separate from valid-measurement readiness; do not show a green count-ready state merely because a skeleton is drawn.

## 11. Unsupported exercises and history

**Recommended minimum-scope policy:** other catalog exercises retain the existing timer demo, explicitly labeled "Timed demo — movement is not counted". Their frontend and Finish behavior remain as currently implemented. They do not receive `SquatAnalyzer`, exercise feedback or a fabricated accuracy score. Standalone pose preview remains overlay-only and can reuse the camera fixes.

Keep the existing `WorkoutRecord(timestamp, exerciseName)` schema and saver in the initial implementation. Show measured totals in the squat Finish dialog/live session, but do not pretend those totals are stored. No calories, duration or form score is proposed.

For measured Bodyweight Squat, recommend:

- Finish with at least one valid repetition: append one record, whether target sets were fully or partially completed; phrase it as a recorded session. Only COMPLETE means all configured targets were reached.
- Finish with zero valid reps: end without a history record; confirmation explains this before exit. Reset/discard never records a session.
- Double Finish/replayed completion cannot append twice. Track the completed session identifier in root saved session state and append/clear the active route together on the main thread. This is app-session idempotence, not a database-backed exactly-once guarantee across arbitrary crashes.
- Existing history entries, activity/streak calculations and demo history retain their meaning. The two-field history cannot distinguish partial/measured/demo sessions or reconstruct counts; record this limitation clearly in project docs.

Alternative for later approval: add an optional versioned measured summary and session ID, migrate `HistoryStateSaver`, update history rendering and add round-trip/legacy saver tests. This is more scope and is not recommended for the first analyzer unless the user needs persistent rep/set details now. Permanent storage remains a separate task in either case.

## 12. Feedback selection and stability

Generate stable structured codes with optional values; map them to existing text styles at the UI boundary. Show one actionable pose instruction at a time. Session status such as Paused/Complete remains authoritative and must not imply counting is active.

| Priority | Example and condition |
| --- | --- |
| 1. Camera/tracking problem | Permission denied, camera unavailable, model failed, no pose, inference timeout. Stop count acceptance as needed. |
| 2. Positioning | Required joints outside visible crop -> "Move farther back"; broad-view candidate -> "Turn sideways"; head/foot framing missing -> "Keep your head and feet visible". |
| 3. Measurement uncertainty | Low visibility/presence, collapsed vectors, abrupt jumps, slow/inconsistent observations -> "Pose unclear — adjust the camera or lighting". Do not diagnose poor form. |
| 4. Movement guidance | Recovery -> "Stand tall to begin"; descent -> "Lower into the squat"; bottom/ascent -> "Return to standing"; shallow return -> "Try lowering farther on the next rep". |
| 5. Repetition completion | Brief "Repetition counted" only when no more urgent action/problem exists. |
| 6. General/setup guidance | Side-view/full-body reminder or ready-to-begin message. |

Invalidate measurement immediately; message smoothing never keeps counting alive. Hard errors preempt instantly. For brief tracking fluctuations, keep the uncertainty message until valid standing/recovery is confirmed. Ordinary movement text changes have a proposed 500 ms minimum display time, but higher-priority problems and confirmed phase changes requiring a new action preempt it. Rep completion can show for up to 700 ms at the top; the generic next-rep prompt is suppressed during that interval, while actual movement guidance preempts it. Do not delay phase/count state for text presentation.

Torso/spinal alignment warnings, knee-over-toe rules, symmetry scores and injury-prevention claims are excluded from initial feedback. The geometry may expose diagnostic values without presenting them as validated coaching.

## 13. Camera failure and lifecycle sequence

On lens switch: close count acceptance, pause if running, increment epochs, clear overlay/readiness, clear old analyzer, unbind only owned Preview/Analysis use cases, close the old detector on its worker, query/bind the chosen camera with a fresh detector/metadata slot, await current preview transform and pose, then offer Resume. Disable repeated switch taps while binding; failed binding shows Retry or the available lens. Never silently label one lens while processing another.

Maintain explicit camera states: PermissionRequired, Initializing, Ready, Switching, Unavailable, Error. If permission is denied, show the existing explanation/retry; distinguish permanent denial after an actual request and offer app Settings. Recheck permission/camera state on foreground return. Model/permission errors must reach UI; printed traces alone are insufficient. No retry loop should reinitialize the graph every frame.

Lifecycle ON_STOP/background closes the analysis gate before further frames can commit, pauses measured work and clears stale overlay. CameraX still manages use-case lifecycle; native tasks must stop accepting work and release/recreate on the worker as required. On foreground, reconcile permission, lens, model and layout before accepting a fresh epoch; do not resume the exercise automatically. On disposal/route exit, invalidate callbacks first, unbind scoped use cases on main, then drain/close native resources and shut down owned workers. Preserve the existing late-callback and scoped-unbind guards. The ViewModel must never retain a destroyed view/lifecycle owner.

## 14. Unit and device verification strategy

Tests use timestamped synthetic landmark trajectories, not just direct calls to force angle states. Construct hip-knee-ankle geometry with known angles, complete head/shoulder/foot framing, valid confidence and both anatomical sides. Generate a continuous trajectory sampled at 15/30/60 Hz and irregular intervals; use virtual times, no sleeps. Assert count/phase/event traces, not every intermediate EMA value. Frame-rate equivalence is required only when observations actually support all phases and confirmations; sparse streams must fail closed.

| Test group | Required scenarios |
| --- | --- |
| Squat cycles | Exactly one full cycle; two full cycles; partial descent; start already at bottom; bottom bounce; noisy standing/depth crossings; too-fast cycle; slow movement/long bottom hold; standing still never counts. |
| Tracking | Missing/low-confidence head/foot/chain; brief invalid sample at every phase; empty result; long gap and no-callback watchdog; return at bottom versus standing; side confidence swaps and locked-side loss. |
| Geometry | Known 0/90/180 angles; zero-length vectors; NaN/infinity; bad dimensions; portrait/landscape aspect scaling; left/right reflection invariance; implausible discontinuities. |
| Time/provenance | Duplicate/reversed timestamps; delayed callback; old camera/analysis/session epochs; clock reset; bounded-slot overflow; pending result across Pause/Resume/Next set/Reset/Finish. |
| Session controller | Pause prevents counts; Resume reacquires standing; Reset clears entire ledger; new session has no stale state; target freezes at N; Next set does not count rest movement; final completion; idempotent rep/Finish; zero/partial/full history policy. |
| Feedback | Priority preemption, no simultaneous contradictory guidance, recovery after loss, no stale completion text after Reset and deterministic virtual-clock holds. |
| Saved state | Completed progress round-trip; running restored paused; no partial state/event replay. Test the existing history saver only if it changes. |

Use a few focused JUnit classes (`PoseGeometryTest`, `SquatAnalyzerTest`, `WorkoutSessionControllerTest`, plus small time/feedback tests where needed). Parameterize loops over sampling/lens-equivalent transforms rather than creating a large framework. Keep existing seven JVM tests.

Android-specific instrumentation covers actual Matrix/Bitmap stride/crop operations, PreviewView mapping and permission/lifecycle UI where useful. Existing nine frontend device tests remain regression coverage; add a small fake-pose integration seam through the existing callback/state boundary for Start/Pause/Reset/set UI, avoiding tests that require real human motion. No production fake-repetition generator is introduced.

After implementation run debug assembly, fresh JVM tests, lint and UI-test APK assembly using the commands in `AGENTS.md`. Run instrumented tests when a device is available. Record actual results/warnings/blocked checks; the previous audit's seven successes do not validate this design.

Physical checks follow `TASKS.md`: both lenses and side directions; full/partial/slow/fast repetitions; no motion; pause at every phase; leaving frame; lighting/occlusion; lens switching; rotation; Settings; foreground/background; early/final Finish. Compare expected and observed reps and document misses/duplicates and device conditions. No numerical accuracy target is proposed without a calibration dataset.

## 15. Planned implementation surface and approval choices

Expected targeted existing edits after C approval: `CameraPreview.kt`, `PoseDetector.kt`, `PoseCamera.kt`, `PoseOverlay.kt`, `WorkoutSessionScreen.kt`, `CameraScreen.kt`, `MainActivity.kt`, and saved session wiring in `UiStateSavers.kt` if needed. `CameraComponents.kt` may gain a small lens/status control. Existing typography/colors/shapes and unrelated catalog/screens remain unchanged. `WorkoutRecord`/history saver changes are excluded by the recommended minimal history option. New analyzer/session/JVM tests and documentation updates complete the work.

Review these proposed decisions together:

| Choice | Recommended default | What remains uncertain |
| --- | --- | --- |
| Initial algorithm | Conservative 2D side-view state machine and config above; cancel any invalid partial cycle. | Threshold/filter/gap calibration and device performance. |
| Controls | Explicit Start; Resume reacquires standing; confirmed Reset clears whole session. | User preference on reset-current-set instead. |
| Sets | Manual Next set; no automatic rest timer. | Whether the user wants timed rest/auto-advance later. |
| Other exercises | Preserve explicitly labeled timer demo. | Whether demos should instead become overlay-only. |
| History | Existing schema; measured sessions save only when >=1 valid rep, allow partial sessions; no stored rep details. | Whether measured totals must be retained now, requiring the alternative schema/saver work. |
| Interruptions | Auto-pause on switch/background/rotation/dialog; explicit Resume. Transient tracking loss auto-reacquires standing. | Device lifecycle and transform behavior still need execution. |

Approval of C would authorize implementation of the selected policies and an initial configurable algorithm, not a claim of calibrated accuracy. Branch creation, commits and pushes remain separately unauthorized. Until approval, application code remains unchanged.
