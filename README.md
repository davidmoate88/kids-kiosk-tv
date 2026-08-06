# Kids Kiosk TV

Native Android TV launcher for the [kids-kiosk](../kids-kiosk) web app. It is a
full-screen WebView shell for the TV UI, with a Leanback launcher banner,
immersive playback, D-pad focus, native voice search, a branded loading screen,
and automatic recovery when the kiosk server is temporarily unavailable.

- Package: `com.kidskiosk.tv`
- Min SDK 24, target/compile SDK 34
- Release kiosk: `https://kids-kiosk.vercel.app/tv`
- Debug kiosk: `http://192.168.1.58/tv`

## Build

The Gradle wrapper is checked in, so the project builds without a machine-wide
Gradle installation. Android Studio's bundled JDK is also suitable.

```bash
./gradlew lint test assembleDebug
```

Install the debug APK on a device or emulator with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

CI runs lint, unit tests, and debug APK assembly for pushes and pull requests.

## Kiosk endpoints

Build-time Gradle properties override the defaults without editing Java source:

```bash
./gradlew assembleDebug -PdebugKioskUrl=https://dev-kiosk.example.com/tv
./gradlew assembleRelease -PreleaseKioskUrl=https://kiosk.example.com/tv
```

`releaseKioskUrl` must use HTTPS. Release builds deny all cleartext traffic.
Debug builds permit cleartext traffic only for the default `192.168.1.58`
endpoint; custom debug endpoints should use HTTPS. Top-level WebView navigation
and the voice bridge are always restricted to the configured origin.

## Security model

- Top-level WebView navigation is restricted to the configured kiosk origin.
- File/content URL access and mixed content are disabled.
- Voice search uses an origin-scoped AndroidX WebKit message listener rather
  than `addJavascriptInterface`; messages from subframes are ignored.
- The release network security policy rejects cleartext traffic.
- If an installed WebView is too old to support origin-scoped messaging, voice
  search is disabled instead of falling back to an unrestricted native bridge.

## Runtime behavior

The app shows a branded splash until the main kiosk document loads. Main-frame
network and HTTP failures display a remote-friendly retry screen and trigger an
automatic retry every five seconds. Fullscreen video exits on Back before normal
WebView history navigation.
