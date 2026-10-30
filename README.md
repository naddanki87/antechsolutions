# Antech Solutions - Claude Skills

Reusable Claude skills for Antech Solutions. The first skill, **`ship`**, is an autonomous 8-agent
engineering team that turns one business requirement into a shipped product:

| Agent | Role |
|---|---|
| 📋 Spec | Architect / PM - spec, OpenAPI contract |
| ⚙️ Byte | Spring Boot backend |
| 🎨 Pixel | Vanilla HTML/CSS/JS web UI |
| 📱 Swift | Native Android app (Java), data stored in **device internal storage** |
| 🛡️ Armor | OWASP Top 10 + Mobile Top 10 security audit |
| 🔍 Hawk | JUnit 5 tests, build gate |
| 📝 Scribe | README, API reference, decisions log |
| 🚀 Deployer | Executable JAR + APK, Conventional Commits, push, GitHub Release, CI |

**Antech Mission Control** - while the team works, open http://localhost:7070 to watch every agent live:
status and current task on each node, message pulses between agents, the joint dialogue log, phase gates,
tests, security findings, artifacts, commits and GitHub Actions runs
(see [`references/mission-control.md`](plugins/antech-engineering/skills/ship/references/mission-control.md)).
Replay a finished run, e.g. the sample:
```bash
java plugins/antech-engineering/skills/ship/assets/mission-control/MissionControl.java --project projects/task-tracker
```

Each run delivers: backend + web + Android source, tests (real results), security audit,
docs, `dist/<app>-<version>.jar`, `dist/<app>-<version>.apk`, a GitHub Actions workflow and a GitHub Release.

## Install

### Option A - Claude (claude.ai / Claude Desktop): upload the skill
1. Download [`dist/ship.zip`](dist/ship.zip).
2. In Claude open **Settings > Capabilities > Skills** (or **Customize > Skills**) and choose **Upload skill**.
3. Select `ship.zip` and turn the skill on.

Code execution must be enabled for skills. Building and pushing needs a machine with JDK, git and `gh`,
so the full pipeline runs best in **Claude Code**; in the claude.ai chat the skill can still design and write the code.

### Option B - Claude Code: install as a plugin (recommended)
```bash
claude plugin marketplace add naddanki87/antechsolutions
claude plugin install antech-engineering@antechsolutions
```
Inside a Claude Code session you can do the same with `/plugin marketplace add naddanki87/antechsolutions`
and `/plugin install antech-engineering@antechsolutions`. The skill is then available as
`/antech-engineering:ship`, and Claude also uses it automatically when you ask to ship an app.

### Option C - Claude Code: personal skill (short `/ship` command)
Copy the skill folder into your personal skills directory:
```bash
git clone https://github.com/naddanki87/antechsolutions.git
cp -r antechsolutions/plugins/antech-engineering/skills/ship ~/.claude/skills/ship
```
On Windows the folder is `%USERPROFILE%\.claude\skills\ship`.

## Use
```
/ship "Employee leave management: employees apply for leave, managers approve or reject, balances per year"
```
Prerequisites on the machine that runs it: JDK 17+, git, GitHub CLI logged in (`gh auth login`).
Optional: Android SDK (`ANDROID_HOME`) to build the APK locally; without it the APK is built by GitHub
Actions on the release tag. For a release-signed APK add the repository secrets `ANTECH_KEYSTORE_BASE64`,
`ANTECH_KEYSTORE_PASSWORD`, `ANTECH_KEY_ALIAS`, `ANTECH_KEY_PASSWORD`; otherwise a debug-signed APK is shipped.

By default products are delivered into this repository under `projects/<app>/` with release tag
`<app>-v<version>`. Name another repository in the request to ship there instead.

## Repository layout
```
.claude-plugin/marketplace.json           Claude Code marketplace
plugins/antech-engineering/
  .claude-plugin/plugin.json
  skills/ship/
    SKILL.md                               orchestration: agents, phases, gates, rules
    references/                            contract, security, testing, Android storage, delivery
    assets/templates/                      backend, frontend, android, CI, .gitignore
    assets/report-template.md              executive handover table
    assets/mission-control/                live CEO dashboard (Java server + vanilla JS UI)
projects/task-tracker/                     sample product shipped by the team (JAR, APK via CI, docs, agent log)
scripts/package_skills.py                  validate + zip skills into dist/
dist/ship.zip                              uploadable skill package
```

## Updating the skill
Edit files under `plugins/antech-engineering/skills/ship/`, then rebuild the zip and bump the
version in `plugin.json` and `.claude-plugin/marketplace.json`:
```bash
python scripts/package_skills.py
```
