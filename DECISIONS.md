# Architecture decision log

2026-11-07 - Start Lateral Raise monitoring with a front-view bilateral cycle. Require both visible arms to reach the raised zone and return to a lowered start for one counted rep; one-arm-only or partial motion does not count. Keep approximate arm-height/bend quality separate from counting, and reuse the generic per-set/session summary and uncounted-attempt counters. Thresholds are provisional 2D engineering defaults pending actual camera traces; do not infer weight, load or medically correct form. This supersedes the catalog-only stage, not the existing Biceps Curl algorithm.

2026-11-07 - Track observed uncounted curl attempts separately from reps. A visibly started curl that returns to extension without contraction is incomplete; tracking loss during an active curl is tracking-interrupted. Neither is a counted rep or a confirmed missed repetition. Keep per-set counters in saved session progress and show them in the reusable Biceps Curl summary. Avoid fabricating outcomes for motion not confidently observed; other exercise analyzers remain unchanged. Backward decode v1/v2 progress with zero attempt counts.

2026-11-06 — Separate curl rep detection from form assessment. A complete confidently tracked extended → contracted → extended cycle remains a counted rep even when the elbow shifts; shoulder-relative displacement produces an `Elbow moved` form note instead of cancelling the cycle. Missing/invalid tracking, geometry, long gaps and incomplete motion still prevent counting. Store accepted-rep quality totals per set in saved session progress and provide generic set/session summary presentation, initially enabled only for Biceps Curl. Other analyzers report unassessed form internally until they supply assessments and retain their existing UI flow. The threshold and wording are provisional pending phone testing; summary UI is not a clinical or technique score. Existing workout-history records retain only their current measured rep/set fields.

2026-11-06 — Biceps Curl measured integration: use the existing measured screen only with a real analyzer and exact registry entry, rather than routing catalog-only exercises into a UI that would label timed/demo progress as measured. First version observes one visible arm from a side view, locks that side for an attempt and counts only extended → contracted → extended. No automatic arm switching, dumbbell recognition or per-arm target accounting is implied; these need later design and physical validation. Existing `Dumbbell Curl` stays separate. This follows the user's request to show the shared Start/Pause/Reset/Finish UI for Biceps Curl.

## ADR-008 — Registry-selected strategies and generic measured presentation

- Date: 2026-11-01.
- Status: User-approved plan; implemented on `refactor/exercise-monitoring`.
- Problem: A reusable analyzer interface still had squat-specific callers in ViewModel, feedback, routing and UI, creating shared-file edits for each new exercise.
- Selected approach: Exact-name registry of fresh analyzer factories and setup metadata; ordinary result/feedback interfaces; common phase/readiness/detail presentation; generic ViewModel and routing. Preserve the analyzer's three methods, squat counting rules and existing generic rep/set/history logic. Keep numeric feedback priorities compatible with existing tests.
- Alternatives: Per-exercise UI/ViewModel branches; mutable singleton analyzers; plugin/DI framework; full catalog ID migration. These add coupling or scope without a present need.
- Reasoning: New rep/set analyzers can be independent strategies registered at one composition point. Camera and session/history internals remain reusable. Exact names preserve current catalog compatibility.
- Consequences: Registration is still a shared edit and may cause Git conflicts. Every analyzer must implement readiness/continuity/event contracts and its own tests. Duration/hold-based exercise semantics need a future design. No extra production analyzer is implemented.

## ADR-009 — Compact measured camera controls

- Date: 2026-11-01.
- Status: Approved by user; implemented, device visual review pending.
- Problem: Stats, explanatory copy and two control rows covered too much of the live preview.
- Selected approach: Opt-in compact stat cards, smaller feedback-card padding, one row with accessible Start/Pause/Resume, Reset and camera icons; retain Next Set/Finish labels and reset confirmation. Remove the persistent 2D-estimate paragraph while preserving limitations in docs.
- Alternatives: Full camera-screen redesign; hiding critical feedback; removing confirmation. Not selected.
- Reasoning: Reduces occupied space using the existing theme and components.
- Consequences: Icon discoverability, small screens and large font sizes need physical UI checks. No counting or camera transformation change.

This log begins on 2026-10-25. It records decisions confirmed in the current work; it does not reconstruct earlier undocumented decisions. Accepted design direction does not imply implementation or authorization to bypass checkpoints.

Current status: Checkpoint C was explicitly approved on 2026-10-25. ADR-001/002 are implemented. ADR-004's canonical-pose/continuity/session policies are accepted and implemented, **except its minimal-history recommendation, superseded by ADR-005**. Earlier pending/proposed wording below is retained as the design-review record, not the current authorization gate.

Checkpoint D update (2026-10-26): the user authorized one local checkpoint commit, `feat: implement bodyweight squat monitoring`, and Samsung-only test preparation. No emulator or phone deployment is authorized tonight. This supersedes the earlier no-commit status, not the no-push/merge/rebase/reset boundaries. A separate test application ID is proposed for a signing mismatch but remains unapproved/unimplemented; see `docs/samsung-device-testing.md`.

## ADR-005 — Actual measured history with legacy saved-state compatibility

- Date: 2026-10-25.
- Status: Accepted explicitly in final Checkpoint C authorization; implemented.
- Problem: Timestamp/name-only history cannot preserve actual reps, full sets or partial completion.
- Selected approach: Extend the existing value model with session ID, MEASURED/DEMO/LEGACY classification, actual per-set ledger and configured targets. Derive totals/full sets/early completion from the ledger. Version the primitive saved-state format; read existing alternating timestamp/name values unchanged. Deduplicate insertion by session ID and prevent repeated Finish in the controller/UI.
- Alternatives: Keep two fields (explicitly rejected by user); introduce durable storage (outside scope); invent counts for legacy/demo entries (prohibited).
- Reasoning: Meets approved measured-summary requirements while preserving existing saved-state and timestamp-based profile/activity/streak consumers.
- Consequences: Additional history/saver/partial-completion tests and compact history labels. No durable exactly-once database guarantee; Android saved-instance-state is not permanent history across arbitrary termination/restart/reinstall. Payload size grows with history; durable storage remains a separate future task.

## ADR-006 — Local branch safety and verification ownership

- Date: 2026-10-25.
- Status: Explicitly authorized and performed.
- Problem: Implementation must not modify main or lose existing documentation work.
- Selected approach: Create only `feat/squat-monitoring` from current main HEAD `e891615`, retaining all seven untracked context/design files. No commit, push, merge, rebase, reset or clean.
- Alternatives: Direct main edits or additional branches (not authorized).
- Reasoning: User's mandatory Git safety requirements.
- Consequences: All implementation remains uncommitted on the local feature branch; inspect status before further work and obtain approval before any commit/merge.

## ADR-001 — Reusable exercise-analysis contract

- Date: 2026-10-25.
- Status: Accepted and implemented; Checkpoint C approved.
- Problem: Multiple exercise monitors need the same camera and pose inference while keeping exercise-specific motion rules independently testable.
- Selected approach: Introduce a reusable `ExerciseAnalyzer` contract, conceptually `processFrame(pose): analysisResult` and `reset()`. Implement an independent `SquatAnalyzer` first; a future independent `PushUpAnalyzer` will consume the same pose pipeline. Separate validation/pose representation, geometry, exercise analysis, session state and Compose rendering.
- Alternatives considered for this decision: Put exercise rules directly in composables; mix them into `PoseDetector`; create a separate camera/inference pipeline per exercise. These are alternatives evaluated now, not historical implementations.
- Reasoning: The existing camera/inference and overlay can be extended. Pure Kotlin analysis allows meaningful local tests without coupling movement rules to UI or MediaPipe native resources.
- Consequences: Frame timing, coordinate conventions, result/state types, concurrency and lifecycle/reset policy must be explicit. API signatures, package paths, normalization details and ViewModel integration still require design review. Avoid excessive abstractions or unnecessary modules/dependencies. Push-up analysis remains deferred.
- Confirmation: Required in the user's master prompt and explicitly approved with Phase 2 authorization.

## ADR-002 — Initial measured exercise is Bodyweight Squat only

- Date: 2026-10-25.
- Status: Accepted and implemented.
- Problem: The catalog contains multiple squat variants whose movement and counting rules cannot safely be assumed equivalent.
- Selected approach: Enable the initial squat analyzer only for the existing **Bodyweight Squat** exercise, using sideways body positioning and planned front/rear camera support.
- Alternatives: Apply one analyzer to every name containing "Squat"; support several variants immediately; introduce a separate exercise catalog. None is selected.
- Reasoning: A narrow initial movement supports reviewable behavior, focused tests and real-device calibration while preserving the existing catalog and frontend.
- Consequences: Split/pistol/goblet/jumping/combination squats do not implicitly gain measured analysis. Other exercises remain in the catalog. Their session presentation requires a clear policy at Checkpoint C; no extra analyzer or removal is authorized. Do not claim automatic orientation recognition or movement accuracy without validation.
- Confirmation: Explicitly approved by the user with Phase 2 authorization.

## ADR-003 — Preserve the existing app and frontend

- Date: 2026-10-25.
- Status: Accepted constraint; applies throughout the task.
- Problem: Adding analysis could otherwise cause unrelated navigation, styling or camera regressions in the existing application.
- Selected approach: Extend the current single-module app, existing CameraX/MediaPipe pipeline, session screen and shared components. Preserve design tokens, Nunito typography, glass treatment, preview, skeleton overlay and working interactions. Keep edits within the approved feature scope.
- Alternatives: Create a new application; redesign screens; replace the camera subsystem; broadly migrate navigation/state. None is selected.
- Reasoning: The user explicitly requires preservation; repository inspection identifies usable frontend and pipeline infrastructure.
- Consequences: Prefer small targeted changes. New dependencies, broad refactors and unrelated migrations require justification and approval. Preserve disposal/lifecycle guards when extending camera behavior, and verify existing workflows alongside squat-specific tests.
- Confirmation: User's non-negotiable preservation requirement, reiterated in Phase 2 approval.

## Historical open choices at the initial design review

Checkpoint C subsequently settled the contract, state owner, coordinates, validation/counting defaults, interruption behavior, controls, restoration and demo presentation. Final authorization requires measured history (ADR-005). Numerical values are accepted engineering defaults requiring physical calibration.

## ADR-004 — Canonical pose, continuity and measured-session policies

- Date: 2026-10-25.
- Status: Accepted and implemented except the original minimal-history option, superseded by ADR-005. Original proposal wording below is historical rationale.
- Problem: Front/rear display transforms, async callbacks and interrupted movement can corrupt measurement timing or complete an old partial repetition; current timer/session policies need explicit measured equivalents.
- Proposed approach: Upright unmirrored analysis coordinates; shared CameraX viewport and explicit display mapping; per-frame timestamps/provenance; pure state machine and serialized session controller; cancel uncertain partial motion and reacquire standing; explicit Start/Resume; confirmed whole-session Reset; manual Next set; retain clearly labeled demos for unsupported exercises. Keep the current history schema initially and save a measured partial/full session only if it contains at least one accepted repetition.
- Alternatives: Mirrored inference with ad hoc overlay corrections; bridging tracking gaps; automatic timed set changes; disabling demos; expanding history immediately. Tradeoffs are documented in the linked proposal.
- Reasoning: Reuse current infrastructure with explicit timing and count ownership while keeping the initial scope reviewable.
- Consequences if accepted: Targeted camera/session edits and focused tests; conservative tracking may miss repetitions during occlusion; history will not retain rep/set details; transforms and defaults still require device calibration. This proposal does not authorize a branch switch, commit or dependency upgrade.

## ADR-007 — Immediate tracking-loss gate and safe verification

- Date: 2026-10-26.
- Status: Implemented during authorized verification review.
- Problem: Queuing watchdog cancellation alone allows an already-running analyzer result to commit first. Connected-test installation also encountered a signature mismatch against the phone's existing package.
- Selected approach: Watchdog/transform-loss advances the analysis epoch under the commit lock, retaining RUNNING and completed counts; fresh standing is required. Compare APK certificates before updating an existing installation. Use disposable test targets where runner cleanup could uninstall packages.
- Alternatives: Worker-only delayed invalidation; uninstall/reinstall to bypass signing (rejected because it risks app data).
- Reasoning: Enforces the approved interruption boundary and data-preservation requirements. Sandbox and USER debug certificates differ; the installed-device certificate remains unavailable while disconnected.
- Consequences: Focused controller/worker race tests; conservative cancellation may miss a borderline real rep. No production dependency or signing configuration changed. Physical accuracy remains unverified.
