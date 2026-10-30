# Security audit - Task Tracker v1.0.0 (Armor)

**Result: SIGNED OFF** - 0 Critical, 0 High open. 4 findings fixed, 2 Low accepted.

## Findings
| ID | Severity | Location | Issue | Status |
|---|---|---|---|---|
| SEC-1 | Medium | `GET /api/v1/tasks?sort=<unknown>` | Unknown sort property hit the catch-all handler and returned 500 | Fixed - `PropertyReferenceException` -> 400; regression test `badInputNeverReturns500` |
| SEC-2 | Medium | `GET /api/v1/tasks/changes` without `since`; other Spring MVC errors | Returned 500 instead of 4xx | Fixed - `MissingServletRequestParameterException` -> 400, any `ErrorResponse` 4xx keeps its status (415, 405, 406...) |
| SEC-3 | Low | `pom.xml` | Initializr added `spring-boot-h2console` | Fixed - dependency removed, console disabled |
| SEC-6 | Medium | Startup log | Spring Boot's generated default user printed `Using generated security password` to the log | Fixed - empty `InMemoryUserDetailsManager` bean; verified no password in test logs |
| SEC-4 | Low | API | No authentication in v1 (single tenant, no personal data) | Accepted by Spec/CEO scope - planned for v1.1 |
| SEC-5 | Low | Android | Debug build allows cleartext to `10.0.2.2`/`localhost` | Accepted - debug-only `src/debug` network config; release is HTTPS-only |

## Checklist
| Check | Result | Evidence |
|---|---|---|
| A01 Access control | N/A v1 (SEC-4) | `SecurityConfig`: only static files, docs, health and `/api/v1/**` exposed; everything else `denyAll` |
| A02 Crypto | PASS | No credentials stored; HTTPS required for Android release |
| A03 Injection | PASS | Spring Data derived queries only; `@Valid` on every body; UUID/`Instant`/enum binding rejects bad input with 400 |
| A04 Insecure design | PASS | `@Size` limits, page size capped at 100, `/changes` capped at 500 rows |
| A05 Misconfiguration | PASS | No stack traces/messages in errors (`errorsDoNotLeakInternals`), actuator only `health,info`, CORS allow-list (`securityHeadersAndCors`) |
| A06 Components | PASS | Spring Boot 4.1.2, springdoc 3.2.0, no unused dependencies |
| A08 Integrity | PASS | CI uses pinned action major versions, no `curl | sh` |
| A09 Logging | PASS | 500s logged server-side with correlation id; client gets generic detail |
| S1 Secrets | PASS | Secret scan of `*.java, *.yml, *.js, *.xml, *.gradle`: none. Prod DB and keystore come from env vars |
| S2 Headers | PASS | CSP `default-src 'self'`, `nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer` |
| S3 CSRF | PASS | Stateless JSON API without cookies; CSRF disabled by design |
| Web XSS | PASS | No `innerHTML`/`eval`/`document.write`; all data rendered via `textContent` |
| Mobile storage | PASS | SQLite + SharedPreferences in app-private internal storage only; no storage permissions; `allowBackup=false`; backup/extraction rules exclude all data |
| Mobile network | PASS | Release `cleartextTrafficPermitted=false`; base URL setter rejects non-HTTPS in release |
| Mobile SQL | PASS | `ContentValues` and `?` args only |
| Exported components | PASS | Only the launcher activity is exported |
