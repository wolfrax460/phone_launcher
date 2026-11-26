# EaglePort OS — native Android preview

A Java / Android Views launcher for Android 8–11 and the Tecno POP 5 GO (BD1), using no third-party runtime libraries, no WebView, no custom keyboard, and no always-running service.

## Build on Windows
Install Android Studio (SDK Manager: Android SDK Platform 35 and Android SDK Build-Tools 35.0.0), JDK 17, and Gradle 8.9. Add Gradle to PATH. Set JAVA_HOME and ANDROID_HOME to your actual installation paths. If Android Studio has generated local.properties with sdk.dir, that also works.

```powershell
git clone https://github.com/wolfrax460/phone_launcher.git
cd phone_launcher
java -version
gradle --version
gradle assembleDebug
```

APK: app/build/outputs/apk/debug/app-debug.apk.
Optional: run `gradle wrapper --gradle-version 8.9` locally to generate wrapper files, then `.\gradlew.bat assembleDebug`. This repo does not include a wrapper JAR.

Install with the standard Android package installer, or with platform-tools and an authorized USB connection:
```powershell
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Open EaglePort and test it as an ordinary app first. Settings > Default launcher lets Android ask you to choose a Home app. Keep the original Tecno launcher installed. Use Android Settings > Apps > Default apps > Home app to return to it.

## Controls
- Swipe up on Home: searchable library. Swipe left: launcher settings. Swipe right: Device Center. Swipe down: Control Center.
- Hold app icons to add/remove Home or Dock shortcuts. Home supports 36 shortcuts across pages, Dock supports four.
- Gesture mode: only a center line is drawn, with invisible left/center/right upward swipe zones. Android's own navigation may reappear on system gestures or while the keyboard is open.
- Library Grid/Honeycomb, theme, accent, photo wallpaper (fit/fill and optional tint), and shortcuts persist in private storage.
- The keyboard is the existing Android input method.
- Device Center reads real battery level, battery temperature, available RAM and storage. No invented battery runtime or weather readings.
- Control Center links to Android's Wi-Fi/Bluetooth/network/DND settings where direct control is restricted. Brightness is launcher-window only; media volume uses the system audio API.
- Recent Apps is launcher launch history, not Android's real task switcher. Removing entries does not kill apps. Android manages the real lock screen, power button, shade and global navigation.

## In-place updates
Settings > Check for launcher updates opens the repository's Releases page. This is a manual update path, not an automatic installer. Install a compatible APK over the existing app. Do not uninstall or clear storage. Keep applicationId com.eagleport.launcher, the same signing certificate and monotonically increasing versionCode. Keep the eagleport_settings SharedPreferences file and wallpaper-v1.webp file stable. Schema changes must be additive or migrated in place. Test upgrade before release.

For a consistent debug update use the same development machine and its debug keystore. For releases create and securely back up a dedicated private signing key before the first release:
```powershell
keytool -genkeypair -v -keystore eagleport-release.jks -alias eagleport -keyalg RSA -keysize 2048 -validity 10000
```
Create signing.properties locally (never commit it) using storeFile=eagleport-release.jks, storePassword, keyAlias=eagleport, keyPassword. Set a higher versionCode in app/build.gradle on subsequent releases and run gradle assembleRelease. Do not switch from a debug-signed installation to a release-signed update expecting the data to survive; signatures differ.

## Validation
No physical Tecno test or local Android SDK build has been completed. Treat this as an alpha.
1. Build and install; verify stock Home remains accessible.
2. Verify gestures, keyboard, library search, long-press shortcut editing, all themes, wallpaper fit/fill and restarts.
3. Set a custom wallpaper and home shortcuts, build an update with versionCode 2 and identical signing key, install -r and confirm the settings survive.
4. Use `adb shell dumpsys meminfo com.eagleport.launcher` after cold startup, library search and repeated app switching. Check logcat for crashes, ANRs and memory pressure. Test reboot and recovery.
5. Target launcher PSS under 60 MB, but this is not measured or guaranteed.

The interactive HTML prototype remains at index.html; native Android screens are an approximation, not pixel-identical.
