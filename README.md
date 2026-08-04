# Kids Kiosk TV

Native Android TV launcher app for the [kids-kiosk](../kids-kiosk) web app. It's a thin
full-screen WebView wrapper — all the actual UI/content lives in the Next.js app at
`kids-kiosk.vercel.app/tv`. This app just makes that reachable from an Android TV's
Leanback launcher, with a proper TV banner, immersive fullscreen, and remote-friendly
back-button handling (including exiting fullscreen video before backing out of the app).

- Package: `com.kidskiosk.tv`
- Min SDK 24, target/compile SDK 34
- Single file: [`MainActivity.java`](app/src/main/java/com/kidskiosk/tv/MainActivity.java)

## Build

No Gradle wrapper is checked in yet. Open the project root in Android Studio and let it
sync/build, or run Gradle from Android Studio's bundled distribution. A debug build has
been produced before, so the toolchain is known-good.

To install on a device/emulator once built:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Notes

- The kiosk URL is hardcoded in `MainActivity.java` (`KIOSK_URL`). Point it at a local
  dev server instead of the deployed Vercel URL when iterating on the web app.
- Leanback launcher intent filter + `tv_banner` drawable are required for the app to show
  up on an Android TV home screen.
