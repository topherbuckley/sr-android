# ModernizeGradleConfig (Milestone 1)

## Phase 2: Modernize Gradle Configuration

### Goal
Convert all Gradle build scripts to Kotlin DSL and replace legacy implementations with modern Gradle best practices.

---

## Expected Pull Requests (PRs)

### 1) Convert all build scripts from Groovy to Kotlin DSL

#### Target files
- `apps/backAndForth/build.gradle`
- `apps/basicAssembler/build.gradle`
- `apps/basicCharger/build.gradle`
- `apps/basicQRReceiver/build.gradle`
- `apps/basicQRTransmitter/build.gradle`
- `apps/basicServer/build.gradle`
- `apps/basicSubscriber/build.gradle`
- `apps/compoundController/build.gradle`
- `apps/handsOnApp/build.gradle`
- `apps/pidBalancer/build.gradle`
- `apps/serverLearning/build.gradle`
- `build.gradle`
- `settings.gradle`
- `common-buildconfig.gradle`
- `libs/abcvlib/download_models.gradle`
- `libs/abcvlib/build.gradle`
- `libs/abcvlib/settings.gradle`

#### Goal
- Fully migrate scripts to Kotlin DSL (`build.gradle.kts`, `settings.gradle.kts`)

---

### 2) Update core SDK, dependencies, and deprecated APIs

#### Tasks
- Update Gradle version to latest stable
- Update Kotlin version
- Update all project dependencies to latest compatible versions

---

### 3) Add Version Catalog

#### Tasks
- Centralize dependency versions using `libs.versions.toml`

---

### 4) Replace `common-buildconfig.gradle` with Convention Plugins

#### Tasks
- Move shared configuration into convention plugins under `build-logic`
- Explicitly apply to only relevant modules
- Remove legacy global `subprojects {}` blocks

#### Goal
- Improve build performance and maintainability
- Enable modular, opt-in configuration per module
