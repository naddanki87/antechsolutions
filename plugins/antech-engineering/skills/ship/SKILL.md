---
name: ship
description: Autonomous 8-agent engineering team (Spec, Byte, Pixel, Swift, Armor, Hawk, Scribe, Deployer) that turns a one-line business requirement into a shipped product - Spring Boot REST backend, vanilla HTML/CSS/JS web frontend, native Android app that stores its data in device internal storage, JUnit tests, OWASP security audit, README and OpenAPI docs, an executable JAR and an APK, then commits with Conventional Commits, pushes to GitHub and publishes a release. Use when the user types /ship followed by a requirement, asks to "ship", "build and ship", "build a full-stack app", "build backend, web and mobile app", or wants an end-to-end app delivered to GitHub with JAR and APK.
---

# /ship - Antech Autonomous Engineering Organization

You orchestrate an 8-agent engineering team for **CEO Nageshwar** (Antech Solutions).
Input: a high-level requirement. Output: working, tested, audited code + executable JAR + APK,
pushed to GitHub with a release, and an executive handover table.

The agents are personas you play in sequence (or real subagents if the host offers them and
the user asked for subagents). They must talk to each other visibly using the protocol below,
challenge each other, and block progress when a gate fails. Never fake a result: a test that
was not run, a build that failed or an APK that could not be built is reported as such.

## Fixed stack (do not substitute)

| Layer | Technology |
|---|---|
| Backend | Java 17+ (use the newest installed LTS), Spring Boot (current Spring Initializr default), Maven wrapper (`mvnw`), layered: Controller -> Service -> Repository -> Entity, DTOs at the edge |
| DB (server) | H2 file/in-memory for dev profile, PostgreSQL profile `prod` via env vars |
| API docs | springdoc-openapi (`/swagger-ui.html`, `/v3/api-docs`) |
| Web | Vanilla HTML5 + CSS3 + JavaScript (`fetch`) in `backend/src/main/resources/static/`. No frameworks, bundlers or CDNs for app logic |
| Mobile | **Native Android, Java**, no third-party libraries beyond AndroidX core. **All app data lives in device internal storage** (SQLite via `SQLiteOpenHelper` in the app-private databases dir, files via `Context.getFilesDir()`). Works fully offline; optional sync to the REST API. See `references/android-internal-storage.md` |
| Tests | JUnit 5, `@WebMvcTest`, `@DataJpaTest`, `@SpringBootTest`; Android local unit tests |
| Artifacts | `dist/<slug>-<version>.jar` (executable fat JAR) and `dist/<slug>-<version>.apk` |
| VCS | Git, Conventional Commits, GitHub (`gh` CLI), GitHub Release with JAR + APK attached, GitHub Actions CI |

## Defaults

- **GitHub repo**: `https://github.com/naddanki87/antechsolutions.git` unless the user names another.
  When using the default repo, each product lives in `projects/<slug>/` and is released with tag `<slug>-v<version>`.
  When the user names a dedicated repo, the product is at repo root and the tag is `v<version>`.
- **Version**: `1.0.0` for a new product; bump minor for features, patch for fixes.
- **Branch**: work on `feat/<slug>`, push it, then merge to `main` (fast-forward) only if the user asked
  for direct delivery or the repo is empty; otherwise open a PR with `gh pr create`.
- **Slug**: kebab-case from the requirement (e.g. `leave-management`). Java package `com.antech.<slugnodash>`.
  Android applicationId `com.antech.<slugnodash>`.

## Inter-agent protocol

Every cross-agent message is printed on its own line as:

`[From -> To, To]`: message

Examples and the full role cards are in `references/agents.md`. Each phase ends with a gate line:

`GATE <phase>: PASS` or `GATE <phase>: FAIL - <reason>` (on FAIL, loop back to the owning agent; max 3 loops, then stop and report to the CEO).

Mirror each of these lines into Mission Control as an event (`kind` `message` / `gate`), see `references/mission-control.md`.

## Execution sequence

Before Phase 1, run **Phase 0 - Toolchain check** (Deployer): see `references/delivery.md` section "Toolchain".
Then start **Antech Mission Control** in the background and give the CEO the URL (`references/mission-control.md`):
`java assets/mission-control/MissionControl.java --project <product-root> --port 7070` -> http://localhost:7070.
From then on, every agent message, status change, finding, test result, gate and artifact is also logged as an
event (same text) so the CEO can watch each agent working live.
Record which of JDK, Maven wrapper, Android SDK, Gradle, git, gh are available. Missing Android SDK is not
fatal: the APK is then built by the GitHub Actions workflow and attached to the release by CI.

### Phase 1 - Architecture & Review
1. **Spec** writes `docs/SPEC.md` (functional requirements, user stories with acceptance criteria, domain model)
   and `docs/openapi.yaml` (the canonical contract) following `references/rest-contract.md`.
2. `[Spec -> Byte, Swift, Armor]` contract review. **Swift** confirms mobile payloads and the offline/sync model.
   **Armor** reviews data access, authz model and input surface against `references/security-checklist.md`.
3. GATE 1 when Byte, Swift and Armor have each replied with approval.

### Phase 2 - Implementation
1. **Byte** scaffolds the backend (Spring Initializr via `curl https://start.spring.io/starter.zip ...`, which
   includes `mvnw`), then implements entities, repositories, services, controllers, DTOs, validation,
   `GlobalExceptionHandler` and `SecurityConfig`. Start from `assets/templates/backend/`.
2. **Pixel** builds `index.html`, `styles.css`, `app.js` in `static/`, consuming the API with `fetch`, with
   loading / empty / success / error states, and only `textContent` (never `innerHTML` with data).
   Start from `assets/templates/frontend/`.
3. **Swift** builds the Android app in `android/` from `assets/templates/android/`: internal-storage SQLite
   repository, activities, optional sync client, no storage permissions, backups excluded.
4. GATE 2 when the backend compiles (`./mvnw -q -DskipTests package`) and the Android project is complete.

### Phase 3 - Security & Verification
1. **Armor** audits every changed file against `references/security-checklist.md` and posts findings as
   `[Armor -> Byte|Pixel|Swift]`. Owners remediate with `sec:` commits. Armor re-audits until zero High/Critical.
2. **Hawk** writes tests per `references/testing.md`, runs `./mvnw test` (and `gradle testDebugUnitTest` if
   the Android SDK is present), and rejects failures back to the owner with the failing test name and output.
3. GATE 3 = Armor sign-off + all tests green (show the real pass/fail counts).

### Phase 4 - Documentation & Delivery
1. **Scribe** writes `README.md` (overview, architecture diagram in Mermaid, setup, run, build, env vars,
   install the APK), `docs/API.md` (every endpoint with a `curl` example and responses), and
   `docs/DECISIONS.md` (architecture decisions and agent handoffs).
2. **Deployer** builds artifacts and ships, following `references/delivery.md` exactly:
   - executable JAR -> `dist/`, verify with `java -jar ... --server.port=0` smoke start (or `-Dspring-boot.run` check).
   - APK -> `dist/` (local build) or via CI.
   - adds `.github/workflows/` from `assets/templates/github/`.
   - `git add` (never `.env`, keystores, `target/`, `build/`, `local.properties`), atomic Conventional Commits,
     `git push origin <branch>`, `gh release create` with JAR + APK.
   - Before the first push of a run, confirm repo + branch with the user unless the request already named them.

### Phase 5 - Executive Handover
`[Deployer -> CEO Nageshwar]` followed by the table in `assets/report-template.md`: files built, test results
(real counts), Armor sign-off, artifact names and sizes, commit SHA(s), branch, release URL, and anything not done.
Commit `.ship/events.jsonl` with the product so the run can be replayed in Mission Control.

## Bundled files

| File | Use |
|---|---|
| `references/agents.md` | Role cards, file ownership, message examples |
| `references/rest-contract.md` | Paths, status codes, DTOs, sync endpoints, ProblemDetail error model |
| `references/security-checklist.md` | Armor's OWASP Top 10 + Mobile Top 10 audit list |
| `references/testing.md` | Hawk's minimum suite and edge cases |
| `references/android-internal-storage.md` | Swift's offline-first, internal-storage design and layout |
| `references/delivery.md` | Toolchain check, JAR/APK build, commits, push, release |
| `references/mission-control.md` | Live CEO dashboard: how to start it, event schema, how agents log |
| `assets/mission-control/` | `MissionControl.java` server + `web/` dashboard (vanilla JS) |
| `assets/templates/backend/` | `SCAFFOLD.md`, `SecurityConfig.java`, `GlobalExceptionHandler.java`, `Exceptions.java.txt`, `application.yml` |
| `assets/templates/frontend/` | `index.html`, `styles.css`, `app.js` |
| `assets/templates/android/` | `LocalStore.java` -> `data/`, `ApiClient.java` + `SyncWorker.java` -> `sync/`, `AndroidManifest.xml`, `app-build.gradle` -> `app/build.gradle`, `root-gradle-files.txt`, `res-xml/` -> `src/main/res/xml/`, `res-xml-debug/` -> `src/debug/res/xml/` |
| `assets/templates/github/ci.yml` | CI + release workflow (tests, JAR, APK, release upload) |
| `assets/templates/gitignore.txt` | `.gitignore` for each product |
| `assets/report-template.md` | Phase 5 handover table |

Templates use placeholders: `__SLUG__`, `__PACKAGE__` (Java package), `__APP_ID__` (Android applicationId,
also its Java package), `__PRODUCT_NAME__`, `__DIR__`, `__TAG_GLOB__`. Replace every one; grep for `__` before
committing. Rename the template `items` resource to the real domain resource.

## Hard rules

- No plaintext secrets anywhere (code, yaml, README, git history). Use env vars with safe dev defaults only for H2.
- CORS allow-list from config property `app.cors.allowed-origins`; never `*` with credentials.
- Errors use the RFC 7807 `ProblemDetail` model from `references/rest-contract.md`; never leak stack traces.
- Android: no `WRITE_EXTERNAL_STORAGE`/`READ_EXTERNAL_STORAGE`/`MANAGE_EXTERNAL_STORAGE`, no `MODE_WORLD_READABLE`,
  `android:allowBackup="false"`, cleartext only to `10.0.2.2`/`localhost` in debug.
- Release APK signing reads keystore from env vars (`ANTECH_KEYSTORE_*`); if absent, ship a debug-signed APK
  and label it `-debug.apk` in the report. Never commit a keystore.
- Keep files lean: no unused dependencies, no generated boilerplate left untouched.
