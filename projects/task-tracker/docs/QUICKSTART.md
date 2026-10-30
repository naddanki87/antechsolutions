# Quick Start Guide - Task Tracker v1.0.0

This guide takes you from download to a working Task Tracker on your computer and your Android phone, one step
at a time. No programming knowledge is needed.

**What you get**

| Part | What it does |
|---|---|
| Task Tracker server (`task-tracker-1.0.0.jar`) | Stores tasks and serves the web app at `http://localhost:8080` |
| Web app | Create, complete, filter and delete tasks in any browser |
| Android app (`task-tracker-1.0.0-debug.apk`) | The same tasks on your phone. Works without internet, keeps all data in the phone's internal storage, syncs when you tap **Sync** |

## Before you start

You need:

1. A Windows, macOS or Linux computer with **Java 17 or newer**. To check, open a terminal (Windows: press
   **Win + R**, type `cmd`, press **Enter**) and run `java -version`. If it says "not recognized" or shows a
   version below 17, install the free **Eclipse Temurin 21** from https://adoptium.net and open a new terminal.
2. For the mobile app: an **Android phone with Android 8.0 or newer**, and a USB cable or another way to copy a
   file to the phone.
3. The release files from GitHub: https://github.com/naddanki87/antechsolutions/releases/tag/task-tracker-v1.0.0

## Step 1 - Download the files

1. Open the release page above.
2. Under **Assets**, download `task-tracker-1.0.0.jar` (the server) and `task-tracker-1.0.0-debug.apk` (the
   Android app). `SHA256SUMS.txt` lets you check that the downloads are intact (optional, see Step 9).
3. Create a folder, for example `C:\TaskTracker`, and move `task-tracker-1.0.0.jar` into it.

## Step 2 - Start the server

1. Open a terminal in the folder. On Windows: open the folder in File Explorer, click the address bar, type `cmd`
   and press **Enter**.
2. Run:
   ```bash
   java -jar task-tracker-1.0.0.jar
   ```
3. Wait until the last lines include `Started TaskTrackerApplication`. This takes about 10 seconds.
4. Leave this window open. Closing it stops Task Tracker. Your tasks are saved in a `data` folder next to the
   JAR and are still there the next time you start it.

> If you see `Port 8080 was already in use`, start it on another port instead:
> `java -jar task-tracker-1.0.0.jar --server.port=8090` and use `8090` instead of `8080` in the next steps.

## Step 3 - Use the web app

1. Open a browser and go to **http://localhost:8080**.
2. **Add a task:** type a **Title** (required), optionally choose a **Due date** and a **Priority** (Low, Medium,
   High) and add **Notes**, then click **Add task**.
3. **Complete a task:** tick the checkbox next to it. The title is struck through. Untick to reopen it.
4. **Filter:** use **All**, **Open** or **Done** above the list.
5. **Delete:** click **Delete** next to a task.

If the page shows an error such as "Title is required", correct the field and click **Add task** again.

## Step 4 - Install the Android app

1. Copy `task-tracker-1.0.0-debug.apk` to your phone (USB cable, Google Drive, email to yourself, ...).
2. On the phone, open the file (for example in **Files** > **Downloads**).
3. Android asks to allow installing apps from this source: tap **Settings**, turn on **Allow from this source**,
   go back and tap **Install**.
4. If Google Play Protect warns about an unknown app, tap **More details** > **Install anyway**. This appears
   because this test build is not signed for the Play Store.
5. Open **Task Tracker** from the app drawer.

Developers can install from a computer instead: `adb install task-tracker-1.0.0-debug.apk`.

## Step 5 - Use the app offline

The app works without any server or internet connection:

1. Tap **Add task**, fill in the title, notes, due date (tap the date button) and priority, then tap **Save**.
2. Tap a task to edit it, tick **Done**, or tap **Delete**.
3. Tick the checkbox in the list to complete a task quickly.
4. Use **All / Open / Done** at the top to filter.

Tasks you have not synced yet show **"not synced"** under the title. They are already saved on the phone.

**Where your data is stored:** in the app's private internal storage
(`/data/data/com.antech.tasktracker/databases/task-tracker.db`). Other apps cannot read it, it is not copied
to cloud backups, and it is removed only when you uninstall the app.

## Step 6 - Sync the phone with the server (optional)

Sync shares tasks between the phone and the web app.

1. Find your computer's address on your Wi-Fi network. On Windows run `ipconfig` and look for **IPv4 Address**,
   for example `192.168.1.20`. The phone must be on the same Wi-Fi.
2. The app only sends tasks over secure `https://` connections. The single exception is the Android emulator,
   which reaches your computer at `http://10.0.2.2:8080` (already preset in this test build). For a real phone,
   publish the server behind HTTPS (see Step 8), or use a tunnelling tool that gives you an `https://` address
   for `http://localhost:8080`. A plain `http://192.168...` address is blocked by Android and shows "Offline".
3. In the app, open the menu (**⋮**, top right) > **Server URL**, enter the `https://` address and tap **OK**.
4. Tap **Sync**. You see "Synced: N sent, M received". Tasks created in the web app now appear on the phone,
   and phone tasks appear in the web app after you refresh the page.
5. Without a connection you see "Offline - changes are saved on this device". Nothing is lost; tap **Sync**
   again later.

If the same task was changed in both places, the server's version is kept and the phone updates to match.

## Step 7 - Explore the API (for developers)

- Interactive API documentation: **http://localhost:8080/swagger-ui.html**
- Health check: **http://localhost:8080/actuator/health** (shows `"status":"UP"`)
- Example:
  ```bash
  curl -X POST http://localhost:8080/api/v1/tasks -H "Content-Type: application/json" -d '{"title":"First task","priority":"HIGH"}'
  ```
See the API Reference section for every endpoint.

## Step 8 - Run it for a team (production)

1. Create a PostgreSQL database and user.
2. Start the server with production settings (one line):
   ```bash
   SPRING_PROFILES_ACTIVE=prod DB_URL=jdbc:postgresql://db-host:5432/tasks DB_USERNAME=tasks DB_PASSWORD=your-password APP_CORS_ALLOWED_ORIGINS=https://tasks.example.com JPA_DDL_AUTO=update java -jar task-tracker-1.0.0.jar
   ```
   On Windows `cmd`, set each variable first with `set NAME=value`, then run `java -jar task-tracker-1.0.0.jar`.
3. Put it behind an HTTPS reverse proxy (for example Nginx, Caddy or a cloud load balancer) so phones can sync.
4. After the first start, set `JPA_DDL_AUTO=validate`.
5. Note: v1.0.0 has **no login**. Run it only on a trusted network or behind a proxy that requires sign-in until
   v1.1 adds authentication (see the Security Report, SEC-4).

## Step 9 - Verify your downloads (optional)

On Windows PowerShell:
```bash
Get-FileHash task-tracker-1.0.0.jar -Algorithm SHA256
```
On macOS/Linux: `shasum -a 256 task-tracker-1.0.0.jar`. The value must match the line in `SHA256SUMS.txt`.

## Troubleshooting

| Problem | Fix |
|---|---|
| `'java' is not recognized` | Install Java 21 (Before you start, item 1) and open a new terminal |
| `Unsupported class file major version` / `UnsupportedClassVersionError` | Java is older than 17. Install Java 21 |
| `Port 8080 was already in use` | Add `--server.port=8090` and use port 8090 |
| Web page does not load | Make sure the terminal from Step 2 is still open and shows `Started TaskTrackerApplication` |
| Android: "App not installed" | Uninstall an older Task Tracker first, then install again |
| Android: "Use an https:// address" | Release builds only accept `https://` server addresses (Step 6) |
| Android: "Offline - changes are saved on this device" | Check Wi-Fi and the Server URL; the server must be running, reachable and use `https://` (Step 6) |
| Android: "Sync failed: ..." | The server rejected a change; open the task, check the title (max 120 characters), save and sync again |

## Stop and uninstall

- **Stop the server:** press **Ctrl + C** in its terminal, or close the window.
- **Remove server data:** delete the `data` folder next to the JAR.
- **Remove the app:** long-press **Task Tracker** > **Uninstall**. This also deletes all tasks stored on the phone.
