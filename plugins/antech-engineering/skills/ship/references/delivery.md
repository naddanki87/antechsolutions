# Deployer delivery runbook

## Toolchain (Phase 0)
Run and record the result of each (Windows: use Git Bash or the `.cmd` variants):
```bash
java -version            # need 17+
git --version
gh auth status           # must be logged in to push and create releases
echo "$ANDROID_HOME" "$ANDROID_SDK_ROOT"   # Android SDK present?
gradle --version         # only needed to create the Gradle wrapper the first time
```
Maven does not need to be installed: the Spring Initializr project ships `mvnw`.
If `gh` is not authenticated, stop and ask the user to run `gh auth login` (never handle tokens yourself).

## Repository layout produced by /ship
```
<root>/                     (repo root, or projects/<slug>/ in the shared antechsolutions repo)
  backend/                  Spring Boot (mvnw, pom.xml, src/)
  android/                  Native Android app (gradlew, app/)
  docs/                     SPEC.md, openapi.yaml, API.md, DECISIONS.md, SECURITY-AUDIT.md, TEST-REPORT.md
  dist/                     <slug>-<version>.jar, <slug>-<version>.apk
  README.md
  .gitignore                (from assets/templates/gitignore.txt)
.github/workflows/<slug>-ci.yml   (always at the repository root)
```

## Building artifacts
```bash
# Executable JAR (spring-boot-maven-plugin repackage)
cd backend && ./mvnw -q clean package
cp target/<artifactId>-<version>.jar ../dist/<slug>-<version>.jar
# smoke test: starts, answers /actuator/health, then stop it
java -jar ../dist/<slug>-<version>.jar --server.port=18080 &
curl -fsS http://localhost:18080/actuator/health && kill %1

# APK (only if Android SDK present; otherwise CI builds it)
cd ../android
if [ -n "$ANTECH_KEYSTORE_FILE" ]; then ./gradlew assembleRelease; APK=app/build/outputs/apk/release/app-release.apk; SUFFIX=""
else ./gradlew assembleDebug; APK=app/build/outputs/apk/debug/app-debug.apk; SUFFIX="-debug"; fi
cp "$APK" ../dist/<slug>-<version>$SUFFIX.apk
```
Release signing env vars (read by `app/build.gradle`): `ANTECH_KEYSTORE_FILE`, `ANTECH_KEYSTORE_PASSWORD`,
`ANTECH_KEY_ALIAS`, `ANTECH_KEY_PASSWORD`. In CI they come from GitHub secrets; the keystore is stored as
base64 secret `ANTECH_KEYSTORE_BASE64`.

## Artifact policy
- Artifacts are **committed in `dist/`** (so the repo itself carries the shipment) **and attached to a GitHub Release**.
- Size guard: GitHub rejects files > 100 MB. If a file is > 95 MB, skip committing it, attach it to the release
  only, and say so in the report.
- Record SHA-256 of each artifact in `dist/SHA256SUMS.txt` (`sha256sum dist/*.jar dist/*.apk`).

## Git
```bash
git status
git checkout -b feat/<slug>         # or stay on main if the repo is empty and the user wants direct delivery
```
Atomic Conventional Commits in this order (skip any that are empty):
1. `docs(spec): add <slug> specification and OpenAPI contract`
2. `feat(backend): implement <slug> REST API`
3. `feat(web): add vanilla JS frontend for <slug>`
4. `feat(android): add offline-first Android app with internal storage`
5. `sec: remediate audit findings SEC-1..SEC-n`
6. `test: add unit and integration tests (<n> passing)`
7. `docs: add README, API reference and decisions log`
8. `ci: add build and release workflow`
9. `build(release): add <slug> v<version> JAR and APK`

Every commit message ends with any attribution trailer the host requires. Stage paths explicitly
(`git add backend docs ...`) rather than `git add .` and check `git status` before each commit for
stray files (`target/`, `build/`, `.gradle/`, `local.properties`, `*.jks`, `*.keystore`, `.env`).

```bash
git push -u origin <branch>
```
If on a feature branch: `gh pr create --fill --base main` unless the user asked for direct merge.

## Release
```bash
gh release create <tag> dist/<slug>-<version>.jar dist/<slug>-<version>*.apk dist/SHA256SUMS.txt \
  --title "<Product name> v<version>" --notes-file docs/RELEASE-NOTES.md --target <branch>
```
Tag: `<slug>-v<version>` in the shared antechsolutions repo, `v<version>` in a dedicated repo.
If the APK was not built locally, push the tag only and let the CI workflow attach the APK; report the
workflow run URL (`gh run list --limit 1`).

## CI
Copy `assets/templates/github/ci.yml` to `.github/workflows/<slug>-ci.yml`, replace `__SLUG__` and
`__DIR__` (`.` or `projects/<slug>`). It runs tests on every push/PR and, on tags, builds the JAR and APK and
attaches them to the release.
