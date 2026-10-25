# Project requirements

Last updated: 2026-10-26. Checkpoint C is approved; implementation is on `feat/squat-monitoring`, based on `main` at `e891615`. The current feature requirements/status below supersede the historical audit sections retained afterward.

## Current feature: implemented and locally verified; device calibration pending

- Exact **Bodyweight Squat** selection uses measured pose analysis; other exercises keep a clearly labeled timer demo and never acquire fabricated measured counts.
- Explicit Start, immediate Pause, fresh-standing Resume, confirmed whole-workout Reset, frozen target counts and manual Next Set. No automatic rest timer. Interrupted movement is never restored.
- Finish records an actual per-set ledger only when at least one valid repetition exists. Empty/reset/discarded sessions create no record. Finish and record insertion are idempotent within restored app state.
- `WorkoutRecord` now stores session identity, exercise name, timestamp, classification, per-set actual counts and configured targets. Totals, full sets and completed/early status derive from that ledger. Legacy timestamp/name records remain readable with unknown counts; demo entries are explicitly classified.
- Front/rear camera selection, visible failures/Retry, unmirrored aspect-correct analysis and CameraX display mapping are implemented. Setup requires side view; limited silhouette checks are not reliable automatic orientation detection.
- Session ViewModel retains accepted progress across recreation; saved progress restores running sessions paused. Saved-instance-state history/progress is **not durable permanent storage across arbitrary process termination, device restart or reinstall**.
- Existing palette, typography, glass cards, navigation and unrelated screens remain. The measured screen reuses the camera/session composition and design components; no dependency upgrades or new modules/storage systems were introduced.

Acceptance: deterministic geometry/analyzer/session/history tests plus build/lint/device regression checks; actual movement and both-lens overlay accuracy require physical calibration. See `TASKS.md` for actual executed results. Numerical defaults are engineering starting points, not clinical or injury-prevention guarantees.

Unverified limits: occlusion may cancel legitimate reps; single-pose tracking cannot establish person identity; 2D bend is not anatomical squat-depth measurement; camera throughput and overlays need multiple-device evaluation. Push-up and other squat variants remain deferred.

## Historical audit and pre-C questions (superseded where noted above)

## Purpose and source of truth

Workout-Monitor is an existing Kotlin/Jetpack Compose Android application for selecting exercises, maintaining a session-level workout plan/history and displaying live on-device MediaPipe pose tracking. The next approved feature is initial side-view **Bodyweight Squat** monitoring, with a reusable foundation for later exercise analyzers.

Implementation statements below come from repository inspection. Requirements marked planned are user-approved goals, not current capabilities. The user's approximate completion estimate is not a verified engineering metric.

## Implemented behavior

| Workflow | Actual behavior and evidence |
| --- | --- |
| Onboarding | Five profile questions, including a snapping age picker; `OnboardingScreen.kt`, `UserProfile.kt`. |
| Home | Search, selected-plan exercises, recommendation fallback, weekly activity and streak summaries; `HomeScreen.kt`. |
| Plan | Browse/search/filter eligible exercises, add/remove selections and start sessions; `RecommendationScreen.kt`, `RecommendationEngine.kt`. |
| Exercise library | Search and filter the bundled catalog; view details/instructions and start a session; `ExercisePoolScreen.kt`, `ExerciseFilters.kt`, `ui/components/ExerciseDetails.kt`. |
| Profile | Edit preferences, view history/activity/milestones and open standalone pose preview; `ProfileScreen.kt`. |
| Workout session | Front-camera preview, live skeleton, coarse tracking indicator, timer-driven rep/set progress, pause/resume and Finish confirmation; `WorkoutSessionScreen.kt`. |
| Session history | Finish records timestamp and exercise name, including an early Finish; `MainActivity.kt`, `WorkoutRecord.kt`. |
| State restoration | Compose saved-instance restoration for profile, plan, history and selected UI/session state; `UiStateSavers.kt`. This is not durable account/storage functionality. |

Session entry starts the timer demo automatically. The current counter advances every 1.5 seconds independently of detected movement or camera availability. Current controls do not include explicit Start or Reset buttons. Pose detection is real; repetition analysis is not implemented.

## Approved planned functionality

- Preserve the current frontend design, navigation, preview, overlay and working interactions.
- Introduce a reusable `ExerciseAnalyzer` contract with independent exercise implementations; implement `SquatAnalyzer` first. A future `PushUpAnalyzer` must reuse the pose pipeline, but push-up analysis is deferred.
- Limit the initial analyzer to the catalog exercise named **Bodyweight Squat**. Do not automatically apply it to Goblet Squat, Split Squat, Pistol Squat, Squat Jumps or combination movements.
- Support front and rear cameras for sideways body positioning, with correct preview/overlay alignment and safe switching.
- Validate required landmarks and supported confidence information; reject invalid geometry and unreliable measurements, and handle missing observations.
- Use an explicit movement state machine, configurable thresholds, hysteresis, noise filtering and timestamp-based confirmation. Count only completed valid cycles, with no duplicate counts.
- Provide conservative depth status and actionable feedback using supported measurements. Prioritize camera/tracking problems, body positioning, measurement uncertainty, movement guidance, repetition completion, then encouragement. Avoid conflicting or flickering messages.
- Integrate counting with existing pause/resume/finish and sets. Paused sessions must not count; tracking loss and transitions must not produce false counts. Provide analyzer reset and fresh-session isolation.
- Display measured repetitions, movement phase, feedback, tracking status and depth using existing UI components and an appropriate state owner.
- Add a small meaningful JVM test baseline and a physical-device validation checklist. Document calibration needs and actual observed errors.

## Acceptance criteria for the initial feature

1. A valid Bodyweight Squat cycle counts exactly once; partial motion, noisy crossings and missing/invalid observations cannot create a repetition.
2. Counting uses observation time rather than rendering frame rate, with documented configurable thresholds and coordinate conventions.
3. Pause/resume, reset, tracking recovery, camera transitions and new sessions cannot complete an old partial cycle accidentally.
4. Both camera directions can be selected and used with a side view. Mirroring, rotation and overlay alignment receive physical-device checks; any untested cases remain explicitly unverified.
5. Guidance is based on observable landmarks and reported uncertainty; there are no claims of medical safety, precise spinal alignment or validated biomechanical accuracy.
6. Existing frontend workflows and styling remain intact, and relevant existing regression checks are retained.
7. Required analyzer tests execute successfully where the environment supports them; blocked tests and device validation limits are reported honestly.

Concrete API signatures, algorithm constants, session policies and the verification matrix require Checkpoint C approval before implementation.

## Known limitations

- There is no exercise analyzer, joint-angle processing, squat depth detection or automatic orientation detector.
- Camera selection is front-only. Rotation, mirroring and cross-device overlay alignment are not physically verified in the current audit.
- Tracking status only checks whether any eight landmarks have visibility above `0.5`; this is insufficient for squat readiness.
- Pose results omit timestamps and camera/session identifiers. Errors largely go to logs.
- Profile/plan/history have no permanent storage. History has no measured rep/set count, duration, calories or accuracy score.
- Existing tests cover filters and frontend behavior, not camera inference or movement accuracy. Seven JVM tests passed during Phase 1; details are in `TASKS.md`.
- No device/emulator was connected during the Phase 1 audit. Real movement accuracy has not been established.

## Unresolved product behavior

| Question | Status |
| --- | --- |
| Should Reset clear the current set or the whole session, and should it be a visible control? | Pending Checkpoint C review. Analyzer reset itself is required. |
| Should a completed set advance automatically, and is a rest interval needed? | Current demo auto-advances after one second; measured-session policy is unresolved. |
| What should other catalog exercises show after Bodyweight Squat becomes measured? | Preserve honest demo labeling pending an approved policy; do not implement additional analyzers implicitly. |
| Should measured reps/sets be added to `WorkoutRecord` and its saver/history UI? | Unconfirmed; current two-field model remains unchanged. |
| How should configuration/process restoration preserve counts while discarding unsafe partial motion? | Pending architecture review. |
| Should readiness start counting automatically or require explicit Start? | Current session starts automatically; any change needs an explicit design. |

## Scope boundaries

The six context files are accepted at Checkpoint B. Current authorization covers Phase 3 technical design and a stop at Checkpoint C. The linked design recommends defaults for the unresolved questions above; those recommendations are not accepted requirements until the user approves them. Application implementation requires C approval. No new app, redesign, broad refactor, push-up implementation, other squat variants, backend/account system, durable storage migration or dependency upgrade is included. Cross-tool adapter files may be proposed if needed; none are currently required or created.
