# GeyserUpdater

An automatic updater plugin for GeyserMC / Floodgate. Supports Spigot (and forks like Paper), BungeeCord, and Velocity.

Features
- Fetches the latest stable versions of GeyserMC and Floodgate from the Jenkins/download API and overwrites the existing JARs in the plugins directory
- Fetches the latest MCXboxBroadcast (from Modrinth) and places it in the Geyser extensions folder (optional)
- Automatic update checks
  - On server startup
  - At a specified interval (e.g., every 12 hours)
  - When a player with a specific permission logs in (can be enabled/disabled)
- Manual command: /geyserupdate (Permission: geyserupdater.admin)
- Configurable language, messages, targets, intervals, and restart commands

Build
- Prerequisites: Java 17, Maven 3.8+
- Instructions:
  - Run `mvn package` in the directory where you cloned the repository
  - Artifacts:
    - `spigot/target/geyserupdater-spigot.jar`
    - `bungee/target/geyserupdater-bungee.jar`
    - `velocity/target/geyserupdater-velocity.jar`

Installation
- Place the appropriate JAR for your server platform into the plugins folder
- Start the server to generate the `config.yml` file (it will be identical to the default config included in this README)
- The plugin automatically detects existing Geyser / Floodgate JARs by searching for files in the plugins directory that contain "geyser" or "floodgate" in their names
  - If no existing JARs are found, it will create new ones with standard names (e.g., `Geyser-Spigot.jar`, `floodgate-velocity.jar`)

Commands & Permissions
- `/geyserupdate`
  - Description: Immediately runs an update check
  - Permission: `geyserupdater.admin`

Configuration File (`config.yml`)
- `enabled`: Enables or disables the plugin
- `language`: Language setting (`ja` / `en` / `de` / `ko`, default: `ja`)
- `checkOnStartup`: Enables or disables the update check on startup
- `periodic.enabled`: Enables or disables the periodic update check
- `periodic.intervalHours`: The interval for periodic checks (in hours)
- `adminLogin.enabled`: Enables or disables the update check when a privileged player logs in
- `adminLogin.permission`: The permission that triggers the check on login (default: `geyserupdater.admin`)
- `targets.geyser | targets.floodgate | targets.mcxboxbroadcast`: Choose which plugins to update
- `postUpdate.notifyConsole`: Notifies the console after an update
- `postUpdate.notifyPlayersWithPermission`: Sends an in-game chat notification to players with permission
- `postUpdate.runRestartCommand`: Automatically executes a restart command after an update
- `postUpdate.restartCommand`: The restart command to execute (e.g., `restart` or `end`)
- `messages.<lang>.*`: Customize messages (e.g., `messages.ja.*`)
  - For compatibility, `messages.*` is also read when `language=ja`

How it Works
- Download URLs:
  - Geyser: `https://download.geysermc.org/v2/projects/geyser/versions/latest/builds/latest/downloads/{platform}`
  - Floodgate: `https://download.geysermc.org/v2/projects/floodgate/versions/latest/builds/latest/downloads/{platform}`
  - MCXboxBroadcast: `https://api.modrinth.com/v2/project/mcxboxbroadcast/version`
  - `{platform}` is `spigot` | `bungeecord` | `velocity`
- The latest JAR is downloaded to a temporary file and its SHA-256 hash is compared with the existing JAR
  - If the hashes are identical, it is considered "up to date," and the file is not replaced
  - If they differ, the existing file is overwritten using an atomic replacement operation
- A server or proxy restart is required after an update is applied
  - To enable automatic restarts, set `postUpdate.runRestartCommand` to `true` and configure `restartCommand` to match your environment
- MCXboxBroadcast is placed in the Geyser extensions folder
  - Example: `plugins/Geyser-Spigot/extensions`

Notes / Known Limitations
- If a download fails due to network issues or other errors, the existing plugin files will not be affected
- The plugin cannot detect Geyser/Floodgate files if they have unusual names or are not located directly within the plugins folder (it only scans for *.jar files in the root of the plugins directory)
- MCXboxBroadcast is always placed in `plugins/Geyser-Spigot/extensions`
- The plugin does not display version numbers; it relies solely on hash comparison to determine if an update is available

License
- Apache License 2.0
