# Soft neutral glass frontend

Implemented on `feature/material3-glassmorphism-redesign`, based on the approved four-screen reference.
The existing `ComponentActivity → AppNavigation()` entry point, package, SDK targets,
exercise catalogue, recommendation rules, and MediaPipe model are retained.

## Design system

| Role | Value |
| --- | --- |
| Solid app background / sheet surface | `#F4EFE6` / `#FAF7F1` |
| Main / supporting text | `#29251F` / `#675F56` |
| Primary / progress accent | `#376846` / `#92C987` |
| Action / selected controls | `#BCE6A9` |
| Selected bottom destination | `#FFE0B8` |
| Error | `#B94D45` |
| Standard glass tint | warm sand `#D8C5AA` at 28.8% opacity, plus a cream highlight fading from 6% to transparent |
| Backdrop blur / grain | 22dp / 2.5% |
| Platform fallback tint | warm sand at 28.8% opacity |
| Glass outline | warm cream `#FAF7F1` at 65% opacity, 1dp |
| Camera glass | `#29251F` at 52% opacity |
| Camera text / tracking | `#FFFCF6` / `#BCEFA0` |

Supporting text is darker than the reference's suggested muted token to improve readability.
Cards use 24dp corners, exercise rows and stats 20dp, search and navigation 28dp.
Glass uses zero native shadow and tonal elevation. Haze captures the shared background
and blurs it behind each card, then draws the card's text and controls sharply on top.
A faint diagonal highlight and fading white rim give it depth. Native Android elevation on these translucent
cards produced gray frames and rectangular patches on the connected Samsung phone,
so it is deliberately avoided.
Nunito's existing four local weights are retained, with shared headline, title, body and label roles.
Spacing uses 4, 8, 12, 16, 20, 24 and 32dp tokens.

The approved light palette is used consistently, including on devices in system dark mode.
Camera screens deliberately use dark translucent controls and white system-bar icons.
All non-camera pages use solid warm ivory (`#F4EFE6`). The curved satin-fold pattern,
radial highlights and background gradient have been removed. Card highlights and glass styling remain;
background blur is naturally subtler over this uniform color.
Neutral cards, search fields and navigation now use a deeper shade of the same warm foundation,
rather than a white veil. The shared `GlassSurface` token uses sand at 72% source strength;
Haze applies 40% of that alpha (28.8% effective tint). Search and navigation use 32% and 32.8%
effective tint respectively. Green selected states and dark camera overlays remain distinct.
Supporting text was slightly darkened to retain contrast on the warmer surfaces.
Haze 1.6.10 supplies a shared backdrop layer, 22dp blur, automatically scaled rendering,
and subtle grain. Only the background Canvas is captured: cards cannot recursively sample
themselves or blur their own foreground. Platform safety defaults are retained; unsupported
devices (including Android 12/API 31's RenderNode fallback) use a translucent tint instead.
Camera previews are deliberately excluded from this backdrop. Their existing dark translucent
monitoring panels remain, without attempting to blur CameraX's external preview surface.
Selections, navigation colors, onboarding changes and progress use standard Compose animations;
there are no repeating animations or custom motion clocks.

## Screen coverage and behavior

| Screen / state | Implementation |
| --- | --- |
| Home | Greeting fallback, working catalogue search, real weekly completion indicators and statistics, selected-plan movements, recommendation fallback, quick start |
| Your plan | Pinned add-exercises card, real category filters, selected exercises only, details, start and remove |
| Plan builder | Alphabetical checkbox selection, profile defaults, search, editable filters, selected count and Done action |
| Full library | Compact glass exercise rows, working search and five independent filter facets |
| Exercise details | About text, actual exercise instructions, sets/reps, equipment, and camera-start action |
| Profile | Avatar fallback, real workout/streak/plan counts, weekly activity, current training preferences and milestones |
| Profile edit | Editable age, gender, goal, level and equipment in a scrollable sheet with validation |
| History | Actual recorded sessions and timestamps; explicit empty state |
| Onboarding | Existing five questions with saved input and progress; a snapping age wheel replaces keyboard entry (10–100, initial selection 25) |
| Workout session | Full camera preview, live pose status, timed rep/set progress, pause/resume, finish confirmation |
| Pose preview | Existing standalone camera/overlay accessible from Profile |
| Filters | Shared scrollable sheet with draft changes, Clear all, Apply and profile-default reset where relevant |
| Empty states / dialogs | Shared neutral colors, clear actions and consistent rounded surfaces |
| Permissions | Glass camera explanation and functional system permission request |
| Navigation | Existing Home, Plan, Exercises and Profile destinations with peach selection, translucent rounded bar, system insets |

Home search searches the full catalogue. Full-library difficulty and equipment filters are exact.
Plan filters retain the recommendation engine's existing maximum-level, bodyweight-equipment,
and age rules. Gender remains a profile detail without arbitrary exercise exclusions.
Filter changes apply when Apply is pressed; dismissing the sheet discards draft changes.
Checkbox changes retain the established immediate plan-update behavior, and Done returns to the plan.

## Changed files, grouped by purpose

Paths below are relative to `app/src/main/java/com/kuet/gymtest/` unless otherwise stated.

### Shared design and components

- Modified: `ui/theme/Color.kt`, `ui/theme/Theme.kt`, `ui/theme/Type.kt`.
- Added: `ui/theme/Shape.kt`.
- Added: `ui/components/WorkoutComponents.kt` — background, glass surfaces, headers, search, buttons, chips, icons, exercise rows, stats, activity chart, navigation.
- Added: `ui/components/GlassBackdrop.kt` — shared Haze state, backdrop sampling, blur, tint, grain and platform fallback.
- Added: `ui/components/AgeWheelPicker.kt` — font-scale-aware snapping wheel with tap selection, accessible age adjustment and restored scroll state.
- Added: `ui/components/ExerciseFilterSheet.kt` — shared search/filter bar and applied/draft filter interaction.
- Added: `ui/components/ExerciseDetails.kt` — exercise details and library rows.
- Added: `ui/components/CameraComponents.kt` — camera glass, scrims, permission panel and system-bar handling.

### Screens, navigation and UI state

- Modified: `MainActivity.kt`, `HomeScreen.kt`, `RecommendationScreen.kt`, `ExercisePoolScreen.kt`,
  `ProfileScreen.kt`, `OnboardingScreen.kt`, `WorkoutSessionScreen.kt`, `CameraScreen.kt`.
- Added: `ExerciseFilters.kt` — facet state, saver and combined search/filter behavior.
- Added: `UiStateSavers.kt` — profile, selected-plan identifiers and history saved-instance restoration.

### Camera integration

- Modified: `CameraPreview.kt` — binds stable use cases and unbinds only its own use cases on disposal;
  ignores late provider callbacks after exit.
- Modified: `PoseCamera.kt` — current callback reference, main-thread result delivery and disposal guard.
- Modified: `PoseDetector.kt` — synchronizes detect/close to protect native resource lifetime.
- Modified: `PoseOverlay.kt` — tracking color only; coordinate transform and connections are retained.
- Unchanged: model asset, detection options, frame conversion, rotation/mirroring and landmark-coordinate calculations.

### Android resources, verification and repository hygiene

- Modified: `app/src/main/res/values/colors.xml`, `themes.xml`, `strings.xml` — warm launch surface, accent, system-bar defaults, app display name.
- Added: `app/src/test/java/com/kuet/gymtest/ExerciseFiltersTest.kt` — six filter and eligibility regressions.
- Added: `app/src/androidTest/java/com/kuet/gymtest/FrontendFlowTest.kt` — plan selection and filter-sheet interaction tests.
- Added: `app/src/androidTest/java/com/kuet/gymtest/GlassBackdropTest.kt` — pixel checks distinguish real backdrop blur from plain transparency and verify sharp foreground text.
- Added: `app/src/androidTest/java/com/kuet/gymtest/AgeWheelPickerTest.kt` — age swiping, endpoints, large fonts, restoration, back navigation and profile submission.
- Modified: `app/build.gradle.kts`, `gradle/libs.versions.toml` — pinned Haze 1.6.10 dependency; no Compose or SDK upgrade.
- Modified: `.gitignore` — ignores generated Kotlin compiler state.
- Added: this document.

One new dependency: `dev.chrisbanes.haze:haze:1.6.10`.

## Verification

Commands use Android Studio's bundled JDK:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:compileDebugKotlin '-Pkotlin.compiler.execution.strategy=in-process'
.\gradlew.bat clean assembleDebug '-Pkotlin.compiler.execution.strategy=in-process'
.\gradlew.bat test lintDebug :app:assembleDebugAndroidTest '-Pkotlin.compiler.execution.strategy=in-process'
.\gradlew.bat assembleDebug test lintDebug :app:assembleDebugAndroidTest '-Pkotlin.compiler.execution.strategy=in-process'
.\gradlew.bat assembleDebug :app:assembleDebugAndroidTest '-Pkotlin.compiler.execution.strategy=in-process'
.\gradlew.bat :app:assembleDebugAndroidTest '-Pkotlin.compiler.execution.strategy=in-process'
& 'C:\Users\USER\AppData\Local\Android\Sdk\platform-tools\adb.exe' -s RFCNA0YM21F shell am instrument -w -r com.kuet.gymtest.test/androidx.test.runner.AndroidJUnitRunner
git diff --check
```

The in-process compiler option avoids the shell's existing Kotlin-daemon directory permission issue;
it does not change the project's Gradle configuration.
Verification completed successfully: a clean debug assembly, seven passing JVM tests,
debug lint with zero errors and 39 warnings, and compilation/packaging of the Android
UI-test APK. The warnings match the existing dependency-update, legacy-resource,
manifest-label, version-catalog and bitmap-KTX advisory categories.
The real-backdrop pixel regression has passed on the Samsung SM-G781W (Android 13/API 33):
high-contrast background stripes are smoothed under the card while the foreground stays black and sharp.
All nine device tests pass, including age swiping and endpoints at 1.6 font scale, saved-state
restoration, age retention/submission through onboarding, solid-background pixels, plan selection,
category filtering, backdrop blur pixels, warm-card color/contrast and package context.
The plan test's selector was made specific to the checkbox row, avoiding a match on its identical search text.
App and test APKs were signed with the existing Android Studio debug certificate and installed in place;
no app data was cleared and no installed package was removed.

Artifacts:

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`
- `app/build/reports/lint-results-debug.html`
- `app/build/reports/tests/testDebugUnitTest/index.html`

## Limits requiring follow-up

- No permanent account/storage layer was introduced. Saved-instance state survives configuration changes and supported process restoration; it is not durable profile/history storage.
- The existing rep counter advances on a timer. The redesigned camera screen labels it accordingly.
  The landmark overlay and tracking indicator use live MediaPipe results; there are no fabricated joint angles or form scores.
- No exercise photos, profile photos or user names exist in the current data. The implementation uses exercise-specific icons and profile/greeting fallbacks.
- The existing weekly target remains three recorded workouts. Session history does not contain duration, calories or per-rep accuracy, so these statistics are not invented.
- The phone reconnected during the card-rendering correction. The updated APK was installed
  in place using the matching Android Studio debug certificate. Onboarding, Home, Profile,
  the library, navigation, and plan checkbox selection were checked on the Samsung SM-G781W
  at its existing 1.1 font scale. The gray-frame artifacts are absent in the resulting captures.
- Screenshots are available in `app/build/reports/visual/`: `before.png`,
  `after-onboarding.png`, `after-choices.png`, `after-home.png`,
  `after-profile.png`, `after-library.png`, `after-plan.png`, and `after-picker.png`.
  These `after-*` images record the earlier artifact correction, before real backdrop blur was added.
- New frosted-glass captures use the `glass-*` prefix in the same directory. Device test output is
  `app/build/reports/visual/glass-device-tests.txt`.
- The later solid-background and age-wheel update is verified by
  `app/build/reports/visual/age-wheel-device-tests.txt`, with a device screenshot at
  `app/build/reports/visual/solid-age-wheel.png`; earlier `glass-*` captures still show the removed background pattern.
- The subsequent warm-beige card treatment is verified in
  `app/build/reports/visual/warm-card-device-tests.txt` and `app/build/reports/visual/warm-cards.png`.
  A pixel regression confirms the neutral card is deeper than ivory and supporting text retains at least 4.5:1 contrast.
- Physical-camera tracking remains unverified in this visual-effect pass. It was not changed.

## Git

The redesign starts from `a0d398f` on a dedicated local branch.
The completed redesign is committed locally on `feature/material3-glassmorphism-redesign`.
No push, merge or pull request is performed.
