# Android app with internal-storage persistence (Swift)

## Principle
The phone is the source of truth for the user's data. Everything is saved to **app-private internal
storage** first, so the app works with no network and data never touches shared/external storage.
Sync with the Spring Boot API is optional, best-effort, and configured by a base URL setting.

| Data | Where | API |
|---|---|---|
| Records | `/data/data/<appId>/databases/<slug>.db` | `SQLiteOpenHelper` (`LocalStore.java`) |
| Settings (base URL, last sync time) | `/data/data/<appId>/shared_prefs/` | `getSharedPreferences(name, MODE_PRIVATE)` |
| Files / exports / attachments | `/data/data/<appId>/files/` | `context.getFilesDir()`, `openFileOutput(name, MODE_PRIVATE)` |
| Temporary data | `/data/data/<appId>/cache/` | `context.getCacheDir()` |

Uninstalling the app removes all of it. No runtime permissions are needed for any of these paths.

## Project layout (`android/`)
```
android/
  settings.gradle
  build.gradle                 (root: AGP plugin version)
  gradle.properties
  app/
    build.gradle               (from assets/templates/android/app-build.gradle)
    proguard-rules.pro
    src/main/AndroidManifest.xml
    src/main/java/com/antech/<slug>/
      App.java                 (optional Application)
      MainActivity.java        (list screen)
      EditActivity.java        (create/edit form)
      data/LocalStore.java     (SQLiteOpenHelper + DAO methods)
      data/<Thing>.java        (model with id(UUID), fields, updatedAt, version, dirty, deleted)
      sync/ApiClient.java      (HttpURLConnection + org.json, off the main thread)
      sync/SyncWorker.java     (ExecutorService-based; pushes dirty rows, pulls /changes)
    src/main/res/layout/*.xml
    src/main/res/values/strings.xml, themes.xml
    src/main/res/xml/network_security_config.xml
    src/main/res/xml/data_extraction_rules.xml
    src/main/res/xml/backup_rules.xml
    src/test/java/...          (local JUnit tests for model/validation/JSON mapping)
```
Start each file from `assets/templates/android/`.

## Rules
- Java only, no Kotlin, no Room, no Retrofit/OkHttp - framework `SQLiteOpenHelper`, `HttpURLConnection`,
  `org.json`, AndroidX `appcompat` + `recyclerview` only. Keeps the APK small (target < 5 MB).
- All DB and network work runs on an `ExecutorService`; results posted to the UI with `runOnUiThread`.
- Every row carries `dirty` (1 = needs upload) and `deleted` (soft delete) so sync is lossless.
- SQL uses `ContentValues` and `?` selection args only.
- Bump `DB_VERSION` and implement `onUpgrade` with `ALTER TABLE` migrations; never drop user data.
- Release builds: `minifyEnabled true`, `shrinkResources true`, signing from env vars (see `delivery.md`).
- Emulator talks to the local backend at `http://10.0.2.2:8080` (debug only).

## Build commands
```bash
cd android
gradle wrapper --gradle-version 8.10.2   # once, if gradle is installed and no wrapper exists
./gradlew testDebugUnitTest
./gradlew assembleRelease                # or assembleDebug when no keystore env vars
```
Output: `app/build/outputs/apk/release/app-release.apk` -> copy to `dist/<slug>-<version>.apk`.
