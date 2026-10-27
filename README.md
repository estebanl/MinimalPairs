# Minimal Pairs - English Pronunciation & Minimal Pair Trainer

Minimal Pairs is a Kotlin Multiplatform (KMP) app designed to eliminate pronunciation blind spots and accent fossilization through phonemic contrast perception training, articulatory biometrics, narrative context traps, and rhythm ribbon prosody feedback.

## Features & Modules

- **Module 1: The Ear Trainer** — High-entropy ABX phonemic discrimination engine, clear whole-word audio, and SM-2 spaced repetition memory scheduler.
- **Module 2: Articulator Lab** — Live acoustic Sound map, recording/replay with placement guidance, offline mouth-position illustrations, a rotatable 3D teaching model with front/side cutaways, and on-device camera lip feedback on Android/iOS.
- **Module 3: The Meaning Trap** — Narrative branching comic scenarios highlighting high-stakes minimal pair semantic blunders.
- **Module 4: Rhythm & Pitch Ribbon** — Continuous YIN F0 pitch tracking ribbon, real-time prosody overlay, and connected speech assimilation practice.

## Progress

Progress stores listening attempts, mastery, review schedules, and scored speaking results locally. It includes listening accuracy, daily streaks, recent trends, per-contrast confusion matrices, and separate speaking summaries. Practice buttons open the relevant contrast or speaking sound. Failed writes can be retried while the app remains open; failed reads retain the last loaded totals.

Filter results by All time, Last 7 days, or Last 30 days and by contrast. Date ranges include today in the current device time zone. Listening accuracy, trends, sound patterns, and both histories use the selected filters; streaks, daily activity, mastery, and review schedules continue to use all listening practice. Clear filters to return to the full history.

Listening and speaking histories initially show five results. Expand either history to browse all matching results in pages of twenty using Older/Newer controls. Changing filters resets history browsing to the newest matching results; collapsing and reopening a section retains its current page. Paging limits rendered rows, but the repository still loads the complete stored history for summaries.

The behavior contract is in [`specs/features/progress.feature`](specs/features/progress.feature). Database migration, retry, snapshot consistency, and rendered-screen checks run with `./gradlew :composeApp:jvmTest`. Shared Android tests run with `./gradlew :composeApp:testAndroidHostTest`; Android and iOS compilation use `:composeApp:compileAndroidMain` and `:composeApp:compileKotlinIosSimulatorArm64`.

For a runtime check of navigation and persistence after an app restart, follow the [Progress simulator smoke test](docs/PROGRESS_SMOKE_TEST.md).

### Android integration

The Android target is a library, not a standalone launcher app. A host Activity should provide a lifecycle-aware Decompose `ComponentContext` and render `MinimalPairsApp(componentContext)`. Hosts managing the root themselves can call `createAndroidRootComponent(context, componentContext)` and pass the result to `RootScreen`. Both entry points initialize progress storage with the application context before creating the root. Destroy the supplied component lifecycle when the host is finished so database and audio resources are released.

Listening streaks and reviews exclude speaking, Stories, and Rhythm. Speaking history stores scores and target metadata, not recorded audio. Storage is local to the device; cloud sync and cross-device restoration are not implemented. Unsaved retry queues are in memory and cannot survive process termination.

## Documentation & Deployment

- Run on connected iOS device: [`./scripts/run_on_device.sh`](file:///Users/estebanlopez/dev/MinimalPairs/scripts/run_on_device.sh) (defaults to connected iPhone `esteban.lopez`).
- [TestFlight Publishing Guide](file:///Users/estebanlopez/dev/MinimalPairs/docs/TESTFLIGHT_PUBLISHING.md) — Comprehensive guide and CLI workflow for building and distributing to Apple TestFlight.
- Automated TestFlight script: [`./scripts/publish_testflight.sh`](file:///Users/estebanlopez/dev/MinimalPairs/scripts/publish_testflight.sh).

