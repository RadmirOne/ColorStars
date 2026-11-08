# ColorStars

Minimal Kotlin Multiplatform starter project targeting Android.

## Modules
- `androidApp`: Android launcher and Activity.
- `shared`: Kotlin Multiplatform shared UI (Compose Multiplatform).

## Requirements
- JDK 17
- Android SDK 35
- Android Studio with Kotlin Multiplatform support

Open the repository in Android Studio, sync Gradle and run the `androidApp` configuration.

The Gradle wrapper JAR is not included yet. Use a local Gradle 8.9 installation to run `gradle wrapper --gradle-version 8.9`, then commit `gradle/wrapper/gradle-wrapper.jar`.
