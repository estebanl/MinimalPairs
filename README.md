# Minimal Pairs - English Pronunciation & Minimal Pair Trainer

Minimal Pairs is a Kotlin Multiplatform (KMP) app designed to eliminate pronunciation blind spots and accent fossilization through phonemic contrast perception training, articulatory biometrics, narrative context traps, and rhythm ribbon prosody feedback.

## Features & Modules

- **Module 1: The Ear Trainer** — High-entropy ABX phonemic discrimination engine, clear whole-word audio, and SM-2 spaced repetition memory scheduler.
- **Module 2: Articulator Lab** — Contrast and example-word selection, live acoustic Sound map, recording/replay with placement guidance, offline mouth-position illustrations, rotatable 3D teaching references with acoustic comparison, and on-device lip opening/circularity feedback on Android/iOS.
- **Module 3: The Meaning Trap** — Narrative branching comic scenarios highlighting high-stakes minimal pair semantic blunders.
- **Module 4: Rhythm & Pitch Ribbon** — Continuous YIN F0 pitch tracking ribbon, real-time prosody overlay, and connected speech assimilation practice.

## Progress

Progress stores listening attempts, mastery, review schedules, and scored speaking, Stories, and Rhythm results locally. It includes listening accuracy, daily streaks, recent trends, per-contrast confusion matrices, and separate module summaries. Practice buttons open the relevant exercise. Failed reads retain the last loaded totals.

Filter results by All time, Last 7 days, or Last 30 days and by contrast. Date ranges include today in the current device time zone. Listening accuracy, trends, sound patterns, and both histories use the selected filters; streaks, daily activity, mastery, and review schedules continue to use all listening practice. Clear filters to return to the full history.

Histories initially show five results. Expand a history to browse all matching results in pages of twenty using Older/Newer controls. Changing filters resets history browsing to the newest matching results; collapsing and reopening a section retains its current page. Stories use the date and contrast filters; Rhythm uses the date range only. Generated demonstrations, silence, and cancelled or invalid module evaluations are not saved.

Storage loads a full snapshot initially, then reads changed rows from a transactional change journal. Unchanged histories are reused; listening counts, sound-pattern cells, and activity counts update incrementally. Full history remains in memory, and filter/time-zone changes rebuild the relevant summaries.

Accepted practice results are first staged in a durable queue and then applied atomically. Successfully staged results retry after reopening, without duplicating completed writes. If staging itself fails (for example, because storage is full), the result remains in memory and the UI explicitly asks you to keep the app open and retry; restart safety is not promised for those results.

**Manage progress** provides native JSON file export/import and a confirmed reset. Backups include all saved results and review schedules, regardless of filters, but never audio or pending writes. Finish saving pending results before transfer. Import merges identical results once, rejects conflicting data atomically, and keeps the newer review schedule. Backups are limited to 100,000 records and 20 MB. Reset removes saved results, review schedules, and pending saves on this device, while preserving exercises and settings.

The behavior contract is in [`specs/features/progress.feature`](specs/features/progress.feature). Database migration, retry, snapshot consistency, and rendered-screen checks run with `./gradlew :composeApp:jvmTest`. Shared Android tests run with `./gradlew :composeApp:testAndroidHostTest`; Android and iOS compilation use `:composeApp:compileAndroidMain` and `:composeApp:compileKotlinIosSimulatorArm64`.

For a runtime check of navigation and persistence after an app restart, follow the [Progress simulator smoke test](docs/PROGRESS_SMOKE_TEST.md).

### Android integration

The Android target is a library, not a standalone launcher app. A host Activity should provide a lifecycle-aware Decompose `ComponentContext` and render `MinimalPairsApp(componentContext)`. Hosts managing the root themselves can call `createAndroidRootComponent(context, componentContext)` and pass the result to `RootScreen`. Both entry points initialize progress storage with the application context before creating the root. Destroy the supplied component lifecycle when the host is finished so database and audio resources are released.

Listening streaks and reviews exclude speaking, Stories, and Rhythm. Histories store scores and target metadata, not recorded audio. Exporting a backup and importing it on another device provides manual transfer. Automatic cloud synchronization and account infrastructure are not implemented.

## Documentation & Deployment

- Articulator Lab: [practice selection](docs/ARTICULATOR_PRACTICE.md), [camera measurements](docs/ARTICULATOR_CAMERA.md), [acoustic comparison](docs/ARTICULATOR_COMPARISON.md), [model assets](docs/MOUTH_POSITION_ARTWORK.md), [scoring setup](docs/ARTICULATOR_SCORING.md), and [device validation](docs/ARTICULATOR_DEVICE_VALIDATION.md). The behavior contract is in [articulator_lab.feature](specs/features/articulator_lab.feature). Camera measurements and acoustic estimates do not reconstruct internal anatomy or provide calibrated jaw distances.

- Run on connected iOS device: `./scripts/run_ios.sh` (alias of `./scripts/run_on_device.sh`; defaults to connected iPhone `esteban.lopez` and Azure scoring). Azure CLI must be signed in. The connection is provisioned at runtime and retained in device Keychain for app-icon launches. Use `--no-azure-scorer` to use a saved or caller-provided connection. See [iPhone scorer setup](docs/AZURE_SCORER.md#iphone-speak-connection).
- [TestFlight Publishing Guide](file:///Users/estebanlopez/dev/MinimalPairs/docs/TESTFLIGHT_PUBLISHING.md) — Comprehensive guide and CLI workflow for building and distributing to Apple TestFlight.
- Automated TestFlight script: [`./scripts/publish_testflight.sh`](file:///Users/estebanlopez/dev/MinimalPairs/scripts/publish_testflight.sh).

