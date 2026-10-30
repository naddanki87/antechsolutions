# Antech Mission Control - live agent dashboard

The CEO watches the run live: the 8 agents in a ring around the CEO, each showing its status
(idle / working / waiting / blocked / done) and current task, message pulses between agents, the joint
dialogue log, phase gates, test and security counters, artifacts, git commits and GitHub Actions runs.

Files: `assets/mission-control/MissionControl.java` (zero-dependency JDK 17+ server, binds 127.0.0.1 only)
and `assets/mission-control/web/` (vanilla HTML/CSS/JS).

## Start it (Deployer, Phase 0)
Run in the background, then tell the CEO the URL:
```bash
java <skill>/assets/mission-control/MissionControl.java --project <product-root> --port 7070
# -> http://localhost:7070
```
If `java` reports `Could not find or load main class ...MissionControl.java` the path is too long for the
Windows launcher (> 260 chars). Compile to a short folder instead:
```bash
javac -d "$TMP/antech-mc" <skill>/assets/mission-control/MissionControl.java
java -cp "$TMP/antech-mc" MissionControl --project <product-root> --web <skill>/assets/mission-control/web --port 7070
```
Optional: put `ceo.jpg` in the `web/` folder to show the CEO's photo in the center.

## Log every agent action
Events are JSON lines in `<product-root>/.ship/events.jsonl` (committed with the product as the audit trail).
Every `[From -> To]` message, status change, finding, test result, gate and artifact printed in the chat must
also be logged, with the same text. Use any of:

```bash
java <skill>/assets/mission-control/MissionControl.java log --project <product-root> \
  --agent Armor --to Byte --phase 3 --kind security --status blocked \
  --text "SEC-1 [Medium] ..." --data '{"open":2,"fixed":1}'
```
```bash
curl -s -X POST http://localhost:7070/api/events -H "Content-Type: application/json" \
  -d '{"agent":"Hawk","to":["Byte"],"phase":3,"kind":"message","status":"blocked","text":"..."}'
```
or append the JSON line to the file directly (one object per line, UTF-8).

## Event schema
| Field | Required | Values |
|---|---|---|
| `ts` | yes (added automatically by `log` and POST) | ISO-8601 UTC |
| `agent` | yes | `Spec`, `Byte`, `Pixel`, `Swift`, `Armor`, `Hawk`, `Scribe`, `Deployer`, or `CEO` |
| `to` | no | array of agent names / `CEO` - animates a message pulse |
| `phase` | yes | `0` Toolchain, `1` Architecture, `2` Implementation, `3` Security & QA, `4` Delivery, `5` Handover |
| `kind` | yes | `message`, `status`, `gate`, `test`, `security`, `artifact`, `commit`, `done` |
| `status` | no | `idle`, `working`, `waiting`, `blocked`, `done` (default: `working`; gates: `done`/`blocked`) |
| `text` | yes | what the agent is doing or saying (shown on the node and in the log) |
| `data` | no | `test`: `{"run","failed","skipped"}` · `security`: `{"open","fixed","signoff"}` · `artifact`: `{"name","size"}` |

A `gate` event whose text contains `FAIL` marks the phase red; otherwise green.

## Rules
- Log before or as the work happens, not in a batch at the end - the dashboard is live.
- Never log secrets, tokens or personal data in `text`/`data`.
- The dashboard only shows; the source of truth stays the chat transcript and the repository.
