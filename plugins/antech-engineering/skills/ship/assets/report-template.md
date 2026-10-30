`[Deployer -> CEO Nageshwar]`: <one-line status, e.g. "All tests green, security certified, v1.0.0 pushed and released.">

## Executive handover - <Product name> v<version>

| Area | Result |
|---|---|
| Requirement | <one line> |
| Repository / branch | <repo URL> / `<branch>` |
| Commit SHA(s) | `<short sha>` <subject>, ... |
| Release | <release URL> (tag `<tag>`) |
| Tests | Backend: <run> run, <failed> failed, <skipped> skipped · Android: <run> run / not run (reason) |
| Security (Armor) | <Signed off / Blocked> - <n> findings fixed, <n> Medium/Low accepted (see docs/SECURITY-AUDIT.md) |
| Executable JAR | `dist/<slug>-<version>.jar` (<size> MB) - smoke-started: yes/no |
| Android APK | `dist/<slug>-<version>.apk` (<size> MB, release-signed / debug-signed) or "built by CI: <run URL>" |
| Mobile storage | Internal storage only: SQLite `<slug>.db` + private SharedPreferences; no storage permissions |
| Docs | README.md, docs/QUICKSTART.md, docs/API.md, docs/SECURITY-REPORT.md, docs/openapi.yaml, docs/DECISIONS.md |
| Downloadable docs | `dist/docs/`: documentation, quick start, security report (`.pdf` + `.html`), `<slug>-<version>-docs.zip` - attached to the release |

### Files built
| Owner | Files |
|---|---|
| Spec | ... |
| Byte | ... |
| Pixel | ... |
| Swift | ... |
| Hawk | ... |
| Scribe | ... |
| Deployer | ... |

### How to run
```bash
java -jar dist/<slug>-<version>.jar            # http://localhost:8080 , Swagger at /swagger-ui.html
adb install dist/<slug>-<version>.apk
```

### Not done / follow-ups
- <anything skipped, failed, or needing the CEO's decision - never leave this out if something is missing>
