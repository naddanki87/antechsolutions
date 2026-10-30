# Architecture decisions and agent handoffs (Scribe)

The complete, timestamped agent conversation is in [`../.ship/events.jsonl`](../.ship/events.jsonl) and can be
replayed in Antech Mission Control.

## Decisions
| # | Decision | Why | Owner |
|---|---|---|---|
| D1 | UUID ids, client-generated allowed via `PUT /tasks/{id}` | Android creates tasks offline without id collisions | Spec + Swift |
| D2 | Optimistic locking with `version`; stale -> 409, server wins | Simple, lossless conflict rule for two clients | Spec + Byte |
| D3 | Soft delete + `/changes?since=` feed | Sync clients must learn about deletions | Spec + Swift |
| D4 | Sync checkpoint = last server `updatedAt`, not the device clock | Avoids clock-skew gaps | Swift |
| D5 | `markSynced` only clears `dirty` when `updated_at` is unchanged | An edit made during an upload is never lost | Swift |
| D6 | Android uses framework SQLite + `HttpURLConnection`, no Room/Retrofit | Small APK, no third-party code, internal storage only | Swift |
| D7 | No authentication in v1 | Single tenant, no personal data; planned for v1.1 (SEC-4) | Spec + Armor |
| D8 | Spring Boot 4.1.2 (Initializr default) + springdoc 3.2.0 | Current supported line | Byte |
| D9 | Stable `PageResponse` DTO instead of serializing Spring's `Page` | Contract stays stable across Spring versions | Byte |
| D10 | APK built in GitHub Actions | No Android SDK on the build machine; CI is reproducible | Deployer |

## Handoffs
1. CEO -> Spec: requirement. Spec -> Byte, Swift, Armor: contract review, all approved (Gate 1).
2. Byte, Pixel, Swift implemented in parallel against `openapi.yaml` (Gate 2).
3. Armor -> Byte: SEC-1, SEC-2 (500 on bad input), SEC-6 (generated password in log). Byte fixed all three
   and updated the `/ship` skill templates so future products inherit the fixes.
4. Hawk -> Byte: build failure (`PropertyReferenceException` moved in Spring Data 4). Fixed, suite green (Gate 3).
5. Scribe documented from the code; Deployer built, smoke-tested, committed, pushed and released (Gate 4).
