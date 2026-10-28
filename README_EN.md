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
- `postUpdate.restartDelaySeconds`: Seconds to wait after an update before running the restart command (default: `0`)
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
  - To enable automatic restarts, set `postUpdate.runRestartCommand` to `true` and configure `restartCommand` and `restartDelaySeconds` to match your environment
- MCXboxBroadcast is placed in the Geyser extensions folder
  - Example: `plugins/Geyser-Spigot/extensions`
  - Velocity example: `plugins/Geyser-Velocity/extensions`

Notes / Known Limitations
- If a download fails due to network issues or other errors, the existing plugin files will not be affected
- The plugin cannot detect Geyser/Floodgate files if they have unusual names or are not located directly within the plugins folder (it only scans for *.jar files in the root of the plugins directory)
- MCXboxBroadcast is placed in the platform-specific Geyser extensions folder
- The plugin does not display version numbers; it relies solely on hash comparison to determine if an update is available

License
- Apache License 2.0

### Via plugin updates (1.2.0)

Add `viaversion`, `viabackwards`, and `viarewind` under `targets` in config.yml.
Each defaults to `false`; set individual targets to `true` to enable updates. Existing configs are preserved.
Restart after changing configuration. All automatic triggers and `/geyserupdate` use these switches; no new commands or permissions.
Enable/update the required ViaVersion dependency alongside ViaBackwards/ViaRewind. Use a supported server and Java version for each upstream release.
ViaRewind also requires ViaBackwards. Via updates are skipped on BungeeCord because current official JARs require a separate Bungee integration; existing JARs are preserved.
The updater selects stable Release-channel builds from official ViaVersion Hangar projects whose explicit platform metadata includes the exact detected Paper/Spigot Minecraft or Velocity version,
checks upstream SHA-256 when available, and preserves existing filenames. Missing targets are installed using the standard plugin name.
Paper/Spigot locked JARs are staged in `plugins/update`. Restart to apply updates. API/download failures preserve existing JARs.

Compatibility checks are mandatory for Via targets: unknown runtime versions, missing platform metadata, unsupported patch versions and API failures preserve existing JARs.
Paper 1.21 and 1.21.1 are treated separately. Velocity uses its own proxy version, not a connected client's Minecraft version.
Downloaded JARs are checked for the correct platform descriptor and Java class-file requirements before replacement.
If the selected build needs a newer Java version, the update is stopped; older Java-compatible artifacts are not searched automatically.
Downgrades are skipped, and an unreadable installed Via version also prevents replacement.
