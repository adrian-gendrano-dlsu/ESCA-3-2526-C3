# DriverGuard UI Demo (separate Android app)

This is a **UI-only prototype** derived from the earlier DriverGuard Android app's Android/Kotlin project structure. It intentionally contains no camera access, MediaPipe, YOLO/TFLite, GPS, fuzzy risk assessment, persistence, Firebase, network, notifications or log synchronization.

## Screens

There are **two launcher icons**, installed by the same APK.

- **DriverGuard Driver** opens `MainActivity`. Its bottom bar switches between **Home** and **History**. Home has three sections, **Risk Level with a live-view placeholder and trip duration**, **Your Metrics**, and **Recent Alerts**. History shows sample sessions and a **Manually Sync Logs to Supervisor** button. Pressing that button displays a message that no sync took place.
- **DriverGuard Supervisor** opens `SupervisorActivity`. It has **Live Monitoring**, **Selected Driver**, and **Recent Alerts**. Tapping Driver 1/2/3 updates the selected-driver details and the map placeholder.

Everything shown is illustrative data. Nothing here is a live risk estimate, actual trip record, stored alert, geographic position or successful synchronization.

## Separation between modes

**Driver mode has no in-app navigation to Supervisor mode.** Android presents a separate launcher entry for Supervisor. **This is UI separation, not authentication or access control**. Anybody who can open the Supervisor launcher icon can access these mock screens. Implement a login with authorization rules before connecting any real driver information.

## Set up on Windows (no PowerShell, no patches)

1. Extract `DriverGuardUIDemo_Standalone.zip`. This creates the `DriverGuardUIDemo` project folder. Do not open the ZIP itself in Android Studio.
2. Find **your existing working** `DriverGuardFaceTrackingDemo` project in File Explorer. Its outer folder contains `app`, `settings.gradle.kts`, `gradlew.bat`, `gradlew`, and `gradle`.
3. **Copy** these three items from that existing project, without removing them from the original.
   - `gradle` (the entire folder, including `wrapper/gradle-wrapper.jar` and `wrapper/gradle-wrapper.properties`)
   - `gradlew` (file)
   - `gradlew.bat` (file)
4. **Paste** the three items directly into the outer `DriverGuardUIDemo` folder, beside `settings.gradle.kts` and `app`. If a `gradle` folder is already present, merge the folders; keep the wrapper files from the working project. Do **not** copy the old `app`, `build.gradle.kts`, or `settings.gradle.kts` files.
5. Check the layout. It should look like this.

   ```text
   DriverGuardUIDemo/
   ├── app/
   ├── gradle/
   │   └── wrapper/
   │       ├── gradle-wrapper.jar
   │       └── gradle-wrapper.properties
   ├── gradlew
   ├── gradlew.bat
   ├── gradle.properties
   ├── build.gradle.kts
   ├── settings.gradle.kts
   └── README.md
   ```

6. In Android Studio, choose **File > Open**, select the **outer `DriverGuardUIDemo` folder**, and wait for Gradle synchronization. Select the connected Redmi and press Run.
7. Once installed, look for the two launcher entries **DriverGuard Driver** and **DriverGuard Supervisor**. Both are in one UI-only APK. Your existing detection app remains separately installed because its application ID is different.

The Gradle Wrapper you copy should already have worked on your computer. For Android Gradle Plugin 9.4.0, `gradle/wrapper/gradle-wrapper.properties` should specify Gradle 9.6.0. This project uses the built-in Kotlin support from AGP 9, so do **not** add `org.jetbrains.kotlin.android` to either build script.

## Project specification

- Namespace `edu.dlsu.driverguard`
- Application ID `edu.dlsu.driverguard.uidemo`
- Android Gradle Plugin 9.4.0 (Gradle 9.6.0 wrapper from the working project)
- Compile SDK 36, minimum SDK 24, target SDK 35
- Kotlin via AGP 9 built-in Kotlin support; **do not reapply** `org.jetbrains.kotlin.android`
- Android framework UI widgets only; no third-party libraries and no runtime permissions

## Evidence and counts

This package keeps the **three Kotlin UI files and all Android project configuration/source files exactly the same** as the preceding PowerShell-patch package. The only changes are deleting the two PowerShell scripts and the now-obsolete change report, and replacing the setup/evidence instructions in this README. A separately downloadable Git-style diff and exact character counts compare both packages.

The original, fully patched detection project is still unavailable here, so no exact edit distance against your locally modified detection app is claimed. A complete on-device build has not been performed here. 

## Limitations

No real-time model processing, face video, speed, GPS, alerts, session database, or synchronization is implemented. This is a clickable UI prototype and design hand-off for later integration.
