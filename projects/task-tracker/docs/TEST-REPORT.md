# Test report - Task Tracker v1.0.0 (Hawk)

**Result: GREEN** - 29 tests run, 0 failed, 0 skipped.

## Backend (`./mvnw test`, JDK 21, Spring Boot 4.1.2)
| Class | Type | Tests | Result | Covers |
|---|---|---|---|---|
| `TaskServiceTest` | JUnit 5 + Mockito | 7 | PASS | AC-1, AC-6, AC-7, AC-8, AC-9, AC-10, AC-11 |
| `TaskRepositoryTest` | `@DataJpaTest` | 3 | PASS | AC-3, AC-4, AC-12, optimistic version |
| `TaskApiIntegrationTest` | `@SpringBootTest` + MockMvc | 12 | PASS | AC-1..AC-12, SEC-1, SEC-2, error leakage, security headers, CORS, OpenAPI |
| `TaskTrackerApplicationTests` | Context load | 1 | PASS | - |

## Android (local JVM unit tests)
| Class | Tests | Result | Covers |
|---|---|---|---|
| `TaskValidatorTest` | 3 | PASS | AC-13 input rules (title, notes, due date, priority) |
| `TaskJsonTest` | 3 | PASS | AC-14 sync mapping (versionless offline create, versioned update, deleted flag) |

Android tests were run locally with JUnit 4.13.2 and org.json against the pure-Java classes; the full
Gradle `testDebugUnitTest` + APK build runs in GitHub Actions (no Android SDK on the build machine).

## Rejections during the run
1. Build failure: `PropertyReferenceException` moved to `org.springframework.data.core` in Spring Data 4 - fixed by Byte, re-run green.
2. Armor SEC-6 (generated password in log) - fixed by Byte, suite re-run green, logs verified clean.

## Not covered by automated tests
- Android UI flows (activities) and on-device sync - manual check after installing the APK (AC-13, AC-14).
- Web UI (AC-15) - checked manually in a browser during delivery.
