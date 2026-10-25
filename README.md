# FREE CHANNEL – Fire TV / Google TV app

A full-screen app that opens https://iptv-player-stream-navigator.ai.studio/ and is tuned for TV:

- Shows up on the **Fire TV** and **Google TV / Android TV** home screens (banner + leanback launcher)
- **Landscape only**, immersive full screen, screen stays on
- Works with a **remote**: D-pad navigation, visible focus ring, OK = click, Back = history / press twice to exit
- Remote keys: **Play/Pause**, **Fast-forward / Rewind** (±10 s), **Menu** = reload page
- Fullscreen video support, autoplay allowed, `http://` streams allowed
- Offline screen with a Retry button and automatic retry
- Supports Fire OS 5 and newer (Android 5.1+), Google TV, Fire tablets

## Get the APK

### Option A – no software to install (GitHub Actions)
1. Create a free GitHub account and a new repository.
2. Upload everything in this folder (keep the folder structure, including `.github`).
3. Open the **Actions** tab → **Build APK** → **Run workflow**.
4. When it finishes (~3–5 min), open the run and download **FREE-CHANNEL-apk** → `app-debug.apk`.

### Option B – Android Studio
1. Open this folder in Android Studio (let it sync).
2. **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
3. The file is at `app/build/outputs/apk/debug/app-debug.apk`.

## Install on the TV
**Fire TV:** Settings → My Fire TV → Developer options → turn on *ADB debugging* and *Install unknown apps* (for the Downloader app). Then either open the APK link in the **Downloader** app, or from a computer on the same network:
```
adb connect <TV-IP-ADDRESS>:5555
adb install app-debug.apk
```
**Google TV:** Settings → System → About → tap *Build* 7 times → Developer options → enable *USB/Wireless debugging*, then use the same `adb` commands (or a "Send files to TV" app).

## Change the website
Edit `START_URL` at the top of `app/src/main/java/com/freechannel/tv/MainActivity.java`.
Set `ENABLE_TV_HELPER` to `false` if the site already handles remote navigation itself.
