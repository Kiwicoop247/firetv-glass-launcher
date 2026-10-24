# Build, configure and undo

These steps target the tested Fire TV Stick Lite AFTSS, Android 9 / Fire OS 7.7.1.7. Stop if the SDK differs or sideload verification is refused. Keep Amazon Home enabled throughout.

## Build from source

Install JDK 17, Android SDK platform 35 and build-tools 35.0.0 through Android Studio or official Android tools.

```powershell
git clone --branch violet-horizon --single-branch https://github.com/Kiwicoop247/firetv-glass-launcher.git
cd firetv-glass-launcher
powershell.exe -NoProfile -ExecutionPolicy Bypass -File build-os7.ps1 -JdkHome '<JDK folder>' -SdkRoot '<Android SDK folder>'
```

Output: `launcher/app/build/outputs/apk/debug/app-debug.apk`. The wrapper runs 50 Java checks, lint and assembly. They must pass before installation. On other platforms configure the SDK and run `./gradlew --no-daemon lintDebug assembleDebug` in `launcher/`; only the Windows wrapper was exercised for publication.

Keep your own signing key private for updates. Gradle accepts `-PglassSigningStore=<absolute path to your debug keystore>` to reuse your earlier key. Your new key cannot replace someone else's signed app. No signing key or APK is supplied.

## Back up and install as an ordinary app

Temporarily enable ADB, authorize the connection and use a trusted network.

```powershell
python -m venv .venv
.venv\Scripts\python.exe -m pip install -r tools/adb/requirements.txt
$env:TV_HOST = '<tv ip>'
.venv\Scripts\python.exe control-os7.py install
```

The SDK-28 helper backs up package/settings/Home state to private `local-state/before-glass.json`, checksum-verifies the transfer, installs and opens the app. It does not enable accessibility or disable stock Home. Keep the backup and `tools/adb/adbkey/` private. Test D-pad navigation, app launching and native settings before enabling Home redirection.

## Wallpaper

Back up any existing private wallpaper before replacing it. A `files/wallpaper.jpg` takes precedence over PNG, so rename your own old JPG first if one exists.

```powershell
.venv\Scripts\python.exe tools/adb/b64push.py assets/violet-horizon.png /data/local/tmp/violet-wallpaper.png
.venv\Scripts\python.exe tools/adb/tv.py 'run-as com.ivantrj.glasslauncher.os7 cp /data/local/tmp/violet-wallpaper.png files/wallpaper.png'
.venv\Scripts\python.exe tools/adb/tv.py 'rm -f /data/local/tmp/violet-wallpaper.png'
```

A valid wallpaper hides/pauses gradients. The app applies a dark filter without altering the original image. Rename/remove your custom wallpapers to restore gradients after reload.

## Private location and dock

Create `local-state/dashboard-location.json`, replacing this illustrative schema with your own approximate location and a valid IANA timezone:

```json
{"name":"Your city","timezone":"Your IANA timezone","latitude":0.0,"longitude":0.0}
```

The zero coordinates above are placeholders, not a preset. Location is not detected automatically and no location permission is requested. Missing location gives a clear weather/radar message; time/sports fall back to the device timezone.

```powershell
.venv\Scripts\python.exe tools/adb/b64push.py local-state/dashboard-location.json /data/local/tmp/dashboard-location.json
.venv\Scripts\python.exe tools/adb/tv.py 'run-as com.ivantrj.glasslauncher.os7 cp /data/local/tmp/dashboard-location.json files/dashboard-location.json'
.venv\Scripts\python.exe tools/adb/tv.py 'rm -f /data/local/tmp/dashboard-location.json'
```

Location is loaded when the activity is created. After first configuration, restart the TV before enabling the Home helper. If you instead force-stop the app, that can switch accessibility off; re-enable/recheck it afterward. `am start ... --ez reload true` reloads wallpaper/app files, but does not recreate an already-running location provider.

For four fixed favorites, write up to four installed package names, one per line, in private `local-state/dock.txt`, then copy it by the same method to `files/dock.txt`. Missing/disabled apps are skipped. No apps are downloaded. The inherited `configure.sh` targets the original OS 8 package, so do not use it here.

## Optional Spotify access

Spotify must already be installed. Android notification-listener access allows reading media sessions. Its OS grant covers all notifications; this code does not inspect notification contents and filters sessions to `com.spotify.tv.android`. Enable only if you accept that scope.

Use the TV's notification-access UI where available. For firmware hiding it, an explicitly consented command is:

```powershell
.venv\Scripts\python.exe tools/adb/tv.py 'cmd notification allow_listener com.ivantrj.glasslauncher.os7/com.ivantrj.glasslauncher.SpotifyAccessService'
```

Undo with `cmd notification disallow_listener` and the same component. The grant is optional; the greeting works without it. Only actively playing Spotify shows its card.

## Optional physical Home redirect

```powershell
.venv\Scripts\python.exe control-os7.py redirect-on
```

The helper preserves other accessibility services and verifies its binding. It listens only to matched Amazon Home window events, without key filtering or window-content access. Amazon Home stays the system launcher. Test the **physical remote's Home**, from both a running app and the drawer; allow about five seconds on this firmware. Reboot once and verify Home, ADB and ordinary remote/app operation.

## Undo and close ADB

```powershell
$env:TV_HOST = '<tv ip>'
.venv\Scripts\python.exe control-os7.py redirect-off
# Or:
powershell.exe -NoProfile -ExecutionPolicy Bypass -File restore-os7.ps1 -TvHost '<tv ip>'
```

Undo removes only this Home helper, preserves other services, restores the recorded global setting when none remain, and opens Amazon Home. Revoke optional notification access separately. Restore private custom files from your own copies. The launcher can remain installed as a manual app.

After final setup, switch USB/ADB/network debugging off, revoke authorizations and reboot. Verify the port is closed with CleanYourTV's `tools/adb-closed.py <tv ip>` if available. Home and the widgets do not need ADB running.

Do not use the inherited OS 8 `install.sh`, `restore.sh` or `setup.sh` for this OS 7 application. Screensaver/sleep preferences are personal device settings and are not changed by this build.
