# Change scope

Prior functionality deliberately omitted from the new UI-only APK

- CameraX, front/back camera preview and image-analysis analyzer
- YOLO model/label import, NCHW/NHWC tensor handling and detection drawing
- MediaPipe, OpenCV facial feature extraction
- GPS, speed/motion measurements, fuzzy-risk model
- Real alert generation, recording and alert-history persistence
- Firebase, cloud sync, notifications, HTTP, permissions

Kept from the Android project lineage

- Android/Kotlin project structure, Android Studio and Gradle convention
- `edu.dlsu.driverguard` namespace

Added only to demonstrate requested screens

- Driver launcher entry and Driver Home/History bottom navigation
- Three driver Home sections; sample session history and sync label
- Separate Supervisor launcher entry (no driver-screen route to it)
- Live-monitoring list, tappable selected driver, map placeholder, recent alerts

### Explicit limitations

All statuses, risk labels, times, trip durations, speeds, locations, history, and notifications are **sample values**. `Manually Sync Logs to Supervisor` only explains that sync has not occurred. No permissions are requested.

Separate launcher icons make the supervisor screen unreachable **through the driver screen**, but do not prevent an unauthorized person from launching the supervisor icon via Android's launcher. This is not a security boundary.
