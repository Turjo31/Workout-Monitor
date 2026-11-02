# Adding a repetition-based exercise analyzer

The app uses Strategy (`ExerciseAnalyzer`) and a registry-backed Simple Factory (`ExerciseMonitorRegistry`). Only Bodyweight Squat is currently registered. Push-up and Jumping Jack are future implementations, not existing capabilities.

## Implementation steps

1. Create an independent pure Kotlin analyzer with its own typed phases, configuration, validation and tests. Reuse `PoseFrame` and geometry where appropriate. Do not copy camera or session code.
2. Implement the existing `processFrame`, `invalidateTracking` and `reset` methods. Return an exercise-specific `ExerciseAnalysisResult` implementation, including common presentation and feedback. `AnalysisFeedback` can be an exercise-owned enum or immutable value class; no shared enum edit is needed.
3. Add one `ExerciseMonitorDefinition` entry in `ExerciseMonitorRegistry` using the exact existing catalog name, monitoring label, setup guidance and a factory returning a **new** analyzer. Add a catalog entry separately if the exercise does not exist. Never reuse a stateful analyzer singleton.
4. Verify analyzer tests plus existing session/history/squat regressions and Android compilation. Exercise selection, measured screen and history automatically use the registry; there is no per-exercise camera/ViewModel/Profile branch to add.
5. Physically validate orientation, confidence thresholds, movement speed, interruptions and both cameras before claiming reliable counting.

## Contract and safety

- Frames are upright, unmirrored normalized model coordinates. Use image width/height for aspect-correct geometry; preview mirroring is display-only.
- Use frame timestamps for motion timing. Preserve the input key/time in the result. Reject invalid/duplicate/out-of-order frames and cancel uncertain partial movement.
- Emit one `RepetitionCompleted` with a strictly increasing ordinal and the observation timestamp for each valid cycle. The session controller accepts events, not cumulative-count differences.
- `reset()` clears motion and ordinal state. `invalidateTracking()` cancels motion while retaining accepted analyzer count/ordinal. Reacquire the exercise's safe starting posture after interruption; it need not be standing for every exercise.
- The ViewModel owns scheduling and epoch resets. Do not read UI clocks, retain Android camera/views, or mutate analyzer state from multiple threads.
- `AnalysisPresentation.phaseKey` identifies a stable exercise-owned phase; `phaseLabel` and optional `detail` are display text. `ready` describes acquired movement readiness, independently of skeleton visibility and session status.
- Feedback priorities: 1 tracking, 2 positioning, 3 uncertainty, 4 movement guidance, 5 completion, 6 encouragement. Use stable exercise-owned feedback keys and immutable results. The presenter may hold ordinary messages, but never delays counting or interruption.
- Start/Pause/Resume, manual Next Set, whole-session Reset, empty/partial Finish and saved-state restoration remain controller policy. A new analyzer must not implement its own set ledger or history writer.
- History supports measured repetition/set ledgers and legacy/demo classifications. It remains saved-instance state, not durable permanent storage. Timed holds and duration-based workouts require a separate approved session design.

## Team workflow

Base both exercise branches on the integrated shared refactor once reviewed and merged by the team. Keep new analyzers/tests in separate files; coordinate the small registry/catalog edits. This reduces shared changes but does not guarantee conflict-free merges. Test the combined result after integrating either branch.

`ExerciseExtensionTest` supplies a test-only non-squat strategy/result/feedback outside the analysis package and checks generic partial history. `WorkoutSessionViewModelTest` also covers strategy switching and old-frame rejection; it requires authorized Android instrumentation to execute.

## Compact UI validation

On an approved physical-device build, check portrait/landscape and enlarged font settings: stats and bottom controls should leave useful preview space, labels should not clip, and each icon should have a usable touch target and TalkBack action name. Verify Play starts/resumes, Pause stops acceptance, Reset still confirms, lens switching pauses, Next Set stays manual and Finish preserves actual totals. Confirm the skeleton still aligns in both lenses. The removed on-screen 2D paragraph does not change the algorithm's calibration limits.
