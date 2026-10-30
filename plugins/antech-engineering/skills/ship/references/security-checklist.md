# Armor security checklist (OWASP Top 10 2021 + Mobile Top 10)

Audit every item; record PASS / FINDING / N/A in `docs/SECURITY-AUDIT.md`.

## Backend
| # | Check | How to verify |
|---|---|---|
| A01 | Broken access control | Every non-public endpoint covered by `SecurityConfig` rules or `@PreAuthorize`; object-level checks (user can only touch own records) in the service layer |
| A02 | Crypto failures | Passwords (if any) with `BCryptPasswordEncoder`/`DelegatingPasswordEncoder`; no custom crypto; HTTPS expected in prod (HSTS header) |
| A03 | Injection | Only Spring Data derived queries or parameterized `@Query` with `:params`; grep for `+ "` near `createQuery`/`nativeQuery`; `@Valid` on every `@RequestBody`; `@Validated` + constraints on path/query params |
| A04 | Insecure design | Rate-sensitive endpoints noted; size limits (`@Size`, `spring.servlet.multipart.max-file-size`, page `size` max 100) |
| A05 | Misconfiguration | `server.error.include-stacktrace=never`, `include-message=never`; H2 console disabled outside `dev`; actuator exposes only `health,info`; CORS allow-list from `app.cors.allowed-origins` |
| A06 | Vulnerable components | Latest Spring Boot 3.x patch; no unused deps; note `./mvnw dependency:tree` reviewed |
| A07 | Auth failures | If auth exists: no default users in prod, session/JWT secret from env, generic login error messages |
| A08 | Integrity | CI uses pinned action major versions; no `curl | sh` in build |
| A09 | Logging | No passwords, tokens or full PII in logs; errors logged server-side with a correlation id, client gets generic 500 |
| A10 | SSRF | No server-side fetch of user-supplied URLs, or allow-list if needed |
| S1 | Secrets | grep repo for `password=`, `secret`, `token`, `apikey`, `BEGIN PRIVATE KEY`, `.jks`, `.keystore`; all must be env vars |
| S2 | Headers | CSP `default-src 'self'`, `X-Content-Type-Options: nosniff`, `frame-ancestors 'none'`, `Referrer-Policy: no-referrer` |
| S3 | CSRF | Stateless JSON API without cookies -> CSRF disabled is acceptable and documented; cookie session -> CSRF enabled |

## Web frontend
- No `innerHTML`, `outerHTML`, `insertAdjacentHTML`, `document.write`, `eval`, `new Function` with data.
- No inline `<script>` or `on*=` attributes (CSP compatible). No third-party script tags.
- No tokens in `localStorage`; no secrets in JS.

## Android (Mobile Top 10)
- M1/M9 Insecure storage: data only in internal storage (`getDatabasePath`, `getFilesDir`), opened with
  `Context.MODE_PRIVATE`; no external storage APIs, no storage permissions in the manifest.
- `android:allowBackup="false"` and `android:dataExtractionRules` excluding the database (API 31+).
- M5 Insecure communication: `network_security_config.xml` - cleartext disabled except debug hosts
  `10.0.2.2` / `localhost`; release base URL must be `https://`.
- M4 Input validation: validate in the form before saving; parameterized SQLite (`?` placeholders,
  `ContentValues`), never string-concatenated SQL.
- M7 Code quality: `minifyEnabled true` for release; no `Log.d` of user data in release.
- Exported components: only the launcher activity has `android:exported="true"`.

## Severity
Critical = remote exploit / data leak; High = exploitable with low effort; Medium = defense-in-depth gap;
Low = hygiene. Gate 3 requires zero Critical and zero High.
