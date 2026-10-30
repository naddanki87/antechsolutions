# Hawk test plan

## Minimum suite per product
| Layer | Test type | What it proves |
|---|---|---|
| Controller | `@WebMvcTest(ThingController.class)` + `MockMvc`, service `@MockitoBean` | Status codes, JSON shape, validation 400 with ProblemDetail, 404, 409 |
| Service | Plain JUnit 5 + Mockito | Business rules for every acceptance criterion, state transitions, edge cases |
| Repository | `@DataJpaTest` | Custom queries, paging, `@Version` optimistic locking |
| Integration | `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate` | Full create -> read -> update -> delete flow, security headers, CORS rejection of unknown origin |
| Android | Local JUnit (`src/test`) | Model validation, JSON <-> model mapping, sync merge rules |

## Mandatory edge cases
- Null / empty body, blank strings, over-long strings, negative numbers, invalid dates, unknown enum values.
- Unknown id -> 404. Stale version -> 409. Page size > 100 -> clamped or 400 (as Spec decided).
- Malformed JSON -> 400 `malformed-request`, not 500.
- Response never contains `trace`, `exception` or Java class names.

## Running
```bash
cd backend && ./mvnw test            # Windows: mvnw.cmd test
cd android && ./gradlew testDebugUnitTest   # only when the Android SDK is present
```
Parse `target/surefire-reports/*.txt` for real counts. Write `docs/TEST-REPORT.md`:
tests run / failures / errors / skipped, per class, plus which acceptance criteria each test covers.
A red build blocks Gate 3; Hawk sends the failure to the owner and re-runs after the fix.
