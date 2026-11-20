# VORTIX — AGP 9 built-in Kotlin migration

## Reported failure

The first Android build stopped while applying `org.jetbrains.kotlin.android`:

> The `org.jetbrains.kotlin.android` plugin in project `:app` is no longer required for Kotlin support since AGP 9.0.

## Root cause

The project uses Android Gradle Plugin 9.3.1, which enables built-in Kotlin by default. The project still applied the external Kotlin Android plugin in both `app/build.gradle.kts` and the root `build.gradle.kts` plugin declarations. The old opt-out flags in `gradle.properties` also kept the project on the legacy integration path.

## Changes made

1. Removed `alias(libs.plugins.kotlin.android)` from `app/build.gradle.kts`.
2. Removed `alias(libs.plugins.kotlin.android) apply false` from root `build.gradle.kts`.
3. Removed the `kotlin-android` plugin alias from `gradle/libs.versions.toml`.
4. Removed `android.builtInKotlin=false` and `android.newDsl=false` from `gradle.properties`.
5. Kept `alias(libs.plugins.kotlin.compose)` because the Compose compiler Gradle plugin remains required for Compose compiler configuration; it is distinct from `org.jetbrains.kotlin.android`.
6. Kept KSP 2.3.12 and Hilt 2.60.1. The Android AGP 9 migration guidance calls for KSP 2.3.6+ and Hilt 2.59.2+.
7. Kept Kotlin 2.4.21 for the Compose compiler plugin. The Kotlin compatibility table lists Kotlin 2.4.20–2.4.21 as compatible with AGP through 9.3.1 and Gradle through 9.7.0. The project's Gradle wrapper is 9.6.1.

## Static verification

- No remaining Android Kotlin plugin aliases or AGP 9 Kotlin/DSL opt-out flags were found in Gradle scripts, properties, or the version catalog.
- `kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_17 } }` remains in place.
- The Gradle wrapper remains at 9.6.1.

## Not claimed

This patch has not been run through Android Gradle Plugin configuration, Android Studio sync, Kotlin/Compose compilation, KSP/Hilt/Room generation, unit tests, APK assembly, or device tests in this workspace. No build pass is claimed. Re-run the two requested tasks on the Android SDK-equipped machine and share the next complete failure output if any:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

## References

- Android Developers — Migrate to built-in Kotlin: https://developer.android.com/build/migrate-to-built-in-kotlin
- Android AGP 9 upgrade skill: https://developer.android.com/agents/skills/build-system/agp/agp-9-upgrade/skill
- Kotlin Gradle compatibility table: https://kotlinlang.org/docs/gradle-configure-project.html
- Kotlin KSP requirements: https://kotlinlang.org/docs/ksp-overview.html
