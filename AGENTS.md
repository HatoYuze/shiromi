# Repository Guidelines

shiromi is a Compose Multiplatform desktop client for Luogu AI assistance, built on the external [deepseek-helper](https://github.com/HatoYuze/deepseek-helper) library (streaming chat API, declarative tool-calling DSL, pipeline-based tool execution engine). This guide explains how the repository is organized and how to contribute.

## Project Structure & Module Organization

- `composeApp/`: the Compose Multiplatform desktop client — the main body of the project. Shared UI/domain code lives in `src/commonMain/kotlin/com/github/hatoyuze/shiromi/gui/`, JVM entry point and platform actuals in `src/jvmMain`.
- `lib/luogu-protocol/`: the Luogu protocol library — Luogu API client and models (`protocol/api`), caching (`protocol/cache`), platform resources (`protocol/platform`, incl. `luogu_tags.json`), and the coach domain layer (`protocol/coach`). No CLI code lives here.
- DeepSeek chat, tool DSL, and pipeline come from `io.github.hatoyuze:deepseek-helper` (Maven Central; see `gradle/libs.versions.toml`).
- Runtime user data (config TOML with credentials, `chat.db`, image cache) is stored under `~/.luogu-gui/`; never commit real values.

## Build, Test, and Development Commands

Use `./gradlew` (or `.\gradlew.bat` on Windows). CI builds with JDK 17.

- `./gradlew build`: compile, test, and assemble all modules; this is the CI gate in `.github/workflows/gradle.yml`.
- `./gradlew test`: run all tests.
- `./gradlew :composeApp:run`: launch the desktop client locally.

## Coding Style & Naming Conventions

- Use 4-space indentation, a 4-space continuation indent, and a 120-column line limit (see `.editorconfig`); Kotlin official style is set in `gradle.properties`.
- Keep packages under `com.github.hatoyuze.*`. Classes use PascalCase, functions and properties camelCase, constants `UPPER_SNAKE_CASE`.
- No lint tool is configured; match the style of surrounding code and existing DSL conventions.

## Testing Guidelines

- Tests use `kotlin.test` and `kotlinx-coroutines-test`, placed in `src/commonTest/kotlin` (platform-independent) or `src/jvmTest/kotlin`.
- Name test functions descriptively (`method_should_expectedBehavior`) and keep them in the same package as the code under test.
- Run tests with `./gradlew test`; add coverage for new API or tool-pipeline behavior.

## Commit & Pull Request Guidelines

- Follow Conventional Commits: `feat:`, `fix:`, `docs:`, `refactor:`, `test:` (e.g., `feat: add timeout override to retry plugin`).
- Open PRs against `main` with a short summary, a linked issue when applicable, and notes on manual verification; the Gradle CI build must pass.
- Prefer small, focused PRs over large mixed changes.

## Security & Configuration

- Credentials live only in `~/.luogu-gui/config/api_setting.toml` on the user's machine (DeepSeek API key, Luogu cookie). Commit only placeholders; keep real credentials local.
- Never include API keys, cookies, or tokens in issues, PRs, or commit messages.
