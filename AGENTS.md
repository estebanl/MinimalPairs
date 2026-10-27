# Repository Guidelines

## Project Structure & Module Organization

- `composeApp/src/commonMain/` holds shared Kotlin/Compose UI, domain logic, data storage, and `composeResources/` assets. Platform implementations live in `androidMain`, `iosMain`, and `jvmMain`.
- `composeApp/src/commonTest/` contains shared tests; `jvmTest/` adds persistence and rendered UI checks. Room schema snapshots live in `composeApp/schemas/`.
- `iosApp/` contains the Xcode host application. The Android target is a library requiring a host Activity.
- `backend/app/main.py` implements the FastAPI phoneme scorer; `backend/tests/` contains API tests.
- `specs/` defines behavior and contracts; `docs/` provides operational guides. `scripts/`, `design/`, and `third_party/` hold tooling, design references, and vendored resources.

## Build, Test, and Development Commands

Use the Gradle wrapper from the repository root. The JVM toolchain targets Java 17; iOS builds require macOS and Xcode.

- `./gradlew :composeApp:run` — launch the desktop app.
- `./gradlew :composeApp:jvmTest` — run shared and JVM-specific tests.
- `./gradlew :composeApp:testAndroidHostTest` — run Android host tests.
- `./gradlew :composeApp:compileAndroidMain` — compile the Android library.
- `./gradlew :composeApp:compileKotlinIosSimulatorArm64` — compile Kotlin for the Apple Silicon iOS simulator.

From `backend/`, install dependencies in a virtual environment with `python -m pip install -r requirements.txt`. Run `python -m uvicorn app.main:app --reload` for local development and `python -m pytest tests` for API tests.

## Coding Style & Naming Conventions

Follow surrounding code with four-space indentation. Use PascalCase for Kotlin types and composables, camelCase for ordinary functions and properties, and snake_case for Python functions. Keep shared logic in `commonMain` and platform APIs in their corresponding source sets. Dependencies are centralized in `gradle/libs.versions.toml`. No dedicated formatter or linter is configured.

## Testing Guidelines

Use `kotlin.test`, coroutine test utilities, and Turbine for shared tests; backend tests use pytest and FastAPI TestClient. Name Kotlin test classes `*Test` with descriptive behavior methods; name Python tests `test_*`. Add regression coverage for changed behavior, including persistence failures where relevant. No numerical coverage threshold is configured. Follow `docs/PROGRESS_SMOKE_TEST.md` for progress navigation and restart checks.

## Commit & Pull Request Guidelines

Use concise imperative commit subjects, matching history: `Add ...`, `Fix ...`, or `Preserve ...`. Keep changes focused. PRs should describe the problem, resulting behavior, and validation commands/results; link relevant issues and include screenshots for UI changes. Update affected specs and schema snapshots alongside implementation changes.
