# Flutter → KMP/CMP Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate Daily Satori from Flutter to Kotlin Multiplatform (KMP) + Compose Multiplatform (CMP) with full feature parity.

**Architecture:** Layered shared architecture — KMP shared module (data + services + domain) + Android app module (CMP UI + ViewModels + platform services). Database via SQLDelight, networking via Ktor, DI via Koin, state management via ViewModel + StateFlow.

**Tech Stack:** Kotlin 2.1+, KMP, Compose Multiplatform, SQLDelight 2.1, Ktor 3.0, Koin 4.0, Coil 3.0, Compose Navigation 2.9, kotlinx.serialization, kotlinx-datetime, kaml, kermit

**Spec:** `docs/superpowers/specs/2026-04-25-flutter-to-kmp-cmp-migration-design.md`

---

## Phase 1: Project Scaffold & Foundation

### Task 1: Create KMP Project and Configure Gradle

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts` (root)
- Create: `gradle.properties`
- Create: `shared/build.gradle.kts`
- Create: `app/build.gradle.kts`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/Platform.kt`
- Create: `app/src/main/kotlin/com/dailysatori/MainActivity.kt`
- Create: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Create `android` branch and set up project root**

```bash
git checkout -b android
```

- [ ] **Step 2: Create root `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolution {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "DailySatori"
include(":shared")
include(":app")
```

- [ ] **Step 3: Create root `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.kotlinMultiplatform).apply(false)
    alias(libs.plugins.androidApplication).apply(false)
    alias(libs.plugins.androidLibrary).apply(false)
    alias(libs.plugins.composeMultiplatform).apply(false)
    alias(libs.plugins.composeCompiler).apply(false)
    alias(libs.plugins.sqldelight).apply(false)
    alias(libs.plugins.kotlinxSerialization).apply(false)
}
```

- [ ] **Step 4: Create `gradle/libs.versions.toml`**

```toml
[versions]
kotlin = "2.1.21"
agp = "8.9.2"
compose-multiplatform = "1.8.1"
sqldelight = "2.1.0"
ktor = "3.1.3"
koin = "4.0.4"
koin-compose = "4.0.4"
coil = "3.2.0"
navigation = "2.9.0"
lifecycle = "2.9.0"
activity-compose = "1.10.1"
kotlinx-serialization = "1.8.1"
kotlinx-datetime = "0.6.2"
kotlinx-coroutines = "1.10.2"
kermit = "2.0.5"
kaml = "0.81.0"

[libraries]
# Kotlin
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinx-serialization" }
kotlinx-datetime = { group = "org.jetbrains.kotlinx", name = "kotlinx-datetime", version.ref = "kotlinx-datetime" }
kotlinx-coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "kotlinx-coroutines" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "kotlinx-coroutines" }

# SQLDelight
sqldelight-runtime = { group = "app.cash.sqldelight", name = "runtime", version.ref = "sqldelight" }
sqldelight-android-driver = { group = "app.cash.sqldelight", name = "android-driver", version.ref = "sqldelight" }
sqldelight-coroutines-extensions = { group = "app.cash.sqldelight", name = "coroutines-extensions", version.ref = "sqldelight" }
sqldelight-primitive-adapters = { group = "app.cash.sqldelight", name = "primitive-adapters", version.ref = "sqldelight" }

# Ktor
ktor-client-core = { group = "io.ktor", name = "ktor-client-core", version.ref = "ktor" }
ktor-client-okhttp = { group = "io.ktor", name = "ktor-client-okhttp", version.ref = "ktor" }
ktor-client-content-negotiation = { group = "io.ktor", name = "ktor-client-content-negotiation", version.ref = "ktor" }
ktor-client-logging = { group = "io.ktor", name = "ktor-client-logging", version.ref = "ktor" }
ktor-serialization-json = { group = "io.ktor", name = "ktor-serialization-kotlinx-json", version.ref = "ktor" }
ktor-server-core = { group = "io.ktor", name = "ktor-server-core", version.ref = "ktor" }
ktor-server-netty = { group = "io.ktor", name = "ktor-server-netty", version.ref = "ktor" }
ktor-server-content-negotiation = { group = "io.ktor", name = "ktor-server-content-negotiation", version.ref = "ktor" }
ktor-server-auth = { group = "io.ktor", name = "ktor-server-auth", version.ref = "ktor" }
ktor-server-cors = { group = "io.ktor", name = "ktor-server-cors", version.ref = "ktor" }
ktor-server-status-pages = { group = "io.ktor", name = "ktor-server-status-pages", version.ref = "ktor" }
ktor-server-websockets = { group = "io.ktor", name = "ktor-server-websockets", version.ref = "ktor" }

# Koin
koin-core = { group = "io.insert-koin", name = "koin-core", version.ref = "koin" }
koin-android = { group = "io.insert-koin", name = "koin-android", version.ref = "koin" }
koin-compose = { group = "io.insert-koin", name = "koin-compose", version.ref = "koin-compose" }
koin-androidx-compose = { group = "io.insert-koin", name = "koin-androidx-compose", version.ref = "koin-compose" }

# Compose / AndroidX
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activity-compose" }
androidx-lifecycle-viewmodel-compose = { group = "org.jetbrains.androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { group = "org.jetbrains.androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-navigation-compose = { group = "org.jetbrains.androidx.navigation", name = "navigation-compose", version.ref = "navigation" }

# Image loading
coil-compose = { group = "io.coil-kt.coil3", name = "coil-compose", version.ref = "coil" }
coil-network-okhttp = { group = "io.coil-kt.coil3", name = "coil-network-okhttp", version.ref = "coil" }

# Markdown
markdown-renderer = { group = "com.mikepenz", name = "multiplatform-markdown-renderer", version = "0.30.0" }
markdown-renderer-m3 = { group = "com.mikepenz", name = "multiplatform-markdown-renderer-m3", version = "0.30.0" }

# Logging
kermit = { group = "co.touchlab", name = "kermit", version.ref = "kermit" }

# YAML
kaml = { group = "com.charleskorn.kaml", name = "kaml", version.ref = "kaml" }

# Android
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version = "1.16.0" }
androidx-appcompat = { group = "androidx.appcompat", name = "appcompat", version = "1.7.0" }
jsoup = { group = "org.jsoup", name = "jsoup", version = "1.19.1" }

[plugins]
kotlinMultiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
androidApplication = { id = "com.android.application", version.ref = "agp" }
androidLibrary = { id = "com.android.library", version.ref = "agp" }
composeMultiplatform = { id = "org.jetbrains.compose", version.ref = "compose-multiplatform" }
composeCompiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlinxSerialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
sqldelight = { id = "app.cash.sqldelight", version.ref = "sqldelight" }
```

- [ ] **Step 5: Create `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2048M -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
org.gradle.configuration-cache=true
kotlin.mpp.androidSourceSetLayoutVersion=2
```

- [ ] **Step 6: Create `shared/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlinxSerialization)
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines.extensions)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.serialization.json)
            implementation(libs.koin.core)
            implementation(libs.kermit)
            implementation(libs.kaml)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
            implementation(libs.ktor.client.okhttp)
        }
    }
}

android {
    namespace = "com.dailysatori.shared"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

sqldelight {
    databases {
        create("DailySatoriDatabase") {
            packageName = "com.dailysatori.shared.db"
        }
    }
}
```

- [ ] **Step 7: Create `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinxSerialization)
    kotlin("android")
}

android {
    namespace = "com.dailysatori"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.dailysatori"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.markdown.renderer)
    implementation(libs.markdown.renderer.m3)
    implementation(libs.jsoup)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.websockets)
}
```

- [ ] **Step 8: Create AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application
        android:name=".DailySatoriApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="Daily Satori"
        android:supportsRtl="true"
        android:theme="@style/Theme.DailySatori">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.SEND" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="text/plain" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 9: Create minimal `MainActivity.kt`**

```kotlin
package com.dailysatori

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DailySatoriTheme {
                DailySatoriApp()
            }
        }
    }
}
```

- [ ] **Step 10: Commit project scaffold**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties gradle/ shared/ app/
git commit -m "feat: scaffold KMP project with shared and app modules"
```

---

### Task 2: Theme System

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/theme/Color.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/theme/Spacing.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/theme/Typography.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/theme/Shape.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/theme/Theme.kt`

- [ ] **Step 1: Create `Color.kt` with exact color values from Flutter**

```kotlin
package com.dailysatori.ui.theme

import androidx.compose.ui.graphics.Color

object AppColors {
    val primary = Color(0xFF5E8BFF)
    val primaryLight = Color(0xFF8AB4F8)
    val primaryDark = Color(0xFF3A5CAA)

    val background = Color(0xFFF7F7F7)
    val backgroundDark = Color(0xFF121212)

    val surface = Color(0xFFFFFFFF)
    val surfaceDark = Color(0xFF1E1E1E)

    val surfaceContainer = Color(0xFFF0F0F0)
    val surfaceContainerDark = Color(0xFF2C2C2C)

    val surfaceContainerHighest = Color(0xFFE0E0E0)
    val surfaceContainerHighestDark = Color(0xFF3A3A3A)

    val onBackground = Color(0xFF212121)
    val onBackgroundDark = Color(0xFFE0E0E0)

    val onSurface = Color(0xFF424242)
    val onSurfaceDark = Color(0xFFBDBDBD)

    val onSurfaceVariant = Color(0xFF757575)
    val onSurfaceVariantDark = Color(0xFF9E9E9E)

    val outline = Color(0xFFE0E0E0)
    val outlineDark = Color(0xFF424242)

    val outlineVariant = Color(0xFFBDBDBD)
    val outlineVariantDark = Color(0xFF757575)

    val success = Color(0xFF4CAF50)
    val successDark = Color(0xFF66BB6A)

    val error = Color(0xFFF44336)
    val errorDark = Color(0xFFE57373)

    val warning = Color(0xFFFF9800)
    val warningDark = Color(0xFFFFB74D)

    val info = Color(0xFF2196F3)
    val infoDark = Color(0xFF64B5F6)

    val secondaryContainer = Color(0xFFE8F5E9)
    val secondaryContainerDark = Color(0xFF2E3B2E)

    val onSecondaryContainer = Color(0xFF1B5E20)
    val onSecondaryContainerDark = Color(0xFF81C784)

    val tertiaryContainer = Color(0xFFFFF3E0)
    val tertiaryContainerDark = Color(0xFF3E2723)

    val secondary = Color(0xFF4CAF50)
    val secondaryDark = Color(0xFF66BB6A)

    val tagColors = listOf(
        Color(0xFF5E8BFF), Color(0xFF26A69A), Color(0xFF66BB6A),
        Color(0xFF9CCC65), Color(0xFFD4E157), Color(0xFFFFEE58),
        Color(0xFFFFCA28), Color(0xFFFFB74D), Color(0xFFFF8A65),
        Color(0xFFE57373),
    )

    val tagColorsDark = listOf(
        Color(0xFF8AB4F8), Color(0xFF4DB6AC), Color(0xFF81C784),
        Color(0xFFAED581), Color(0xFFDCE775), Color(0xFFFFF176),
        Color(0xFFFFD54F), Color(0xFFFFCC80), Color(0xFFFFAB91),
        Color(0xFFEF9A9A),
    )
}
```

- [ ] **Step 2: Create `Spacing.kt` with exact dimension values**

```kotlin
package com.dailysatori.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}

object Radius {
    val none = 0.dp
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val circular = 100.dp
}

object IconSize {
    val xs = 16.dp
    val s = 18.dp
    val m = 20.dp
    val l = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}

object Height {
    val button = 48.dp
    val buttonSmall = 36.dp
    val input = 48.dp
    val listItem = 56.dp
    val listItemSmall = 48.dp
    val appBar = 56.dp
    val navBar = 56.dp
    val chip = 32.dp
    val searchBar = 48.dp
}

object BorderWidth {
    val xs = 0.5.dp
    val s = 1.dp
    val m = 1.5.dp
    val l = 2.dp
    val xl = 4.dp
}

object Breakpoint {
    val mobile = 600.dp
    val tablet = 900.dp
    val desktop = 1200.dp
}

object Anim {
    val durationFast = 150
    val durationNormal = 300
    val durationSlow = 500
}
```

- [ ] **Step 3: Create `Typography.kt` using Lato font family**

```kotlin
package com.dailysatori.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.dailysatori.R

val LatoFontFamily = FontFamily(
    Font(R.font.lato_thin, FontWeight.Thin),
    Font(R.font.lato_light, FontWeight.Light),
    Font(R.font.lato_regular, FontWeight.Normal),
    Font(R.font.lato_bold, FontWeight.Bold),
    Font(R.font.lato_black, FontWeight.Black),
    Font(R.font.lato_thin_italic, FontWeight.Thin, androidx.compose.ui.text.font.FontStyle.Italic),
    Font(R.font.lato_light_italic, FontWeight.Light, androidx.compose.ui.text.font.FontStyle.Italic),
    Font(R.font.lato_regular_italic, FontWeight.Normal, androidx.compose.ui.text.font.FontStyle.Italic),
    Font(R.font.lato_bold_italic, FontWeight.Bold, androidx.compose.ui.text.font.FontStyle.Italic),
    Font(R.font.lato_black_italic, FontWeight.Black, androidx.compose.ui.text.font.FontStyle.Italic),
)

val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = LatoFontFamily, fontWeight = FontWeight.Normal, fontSize = 57.sp, lineHeight = 63.8.sp, letterSpacing = 0.15.sp),
    displayMedium = TextStyle(fontFamily = LatoFontFamily, fontWeight = FontWeight.Normal, fontSize = 45.sp, lineHeight = 52.2.sp, letterSpacing = 0.15.sp),
    displaySmall = TextStyle(fontFamily = LatoFontFamily, fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 43.9.sp, letterSpacing = 0.15.sp),
    headlineLarge = TextStyle(fontFamily = LatoFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.15.sp),
    headlineMedium = TextStyle(fontFamily = LatoFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 31.2.sp, letterSpacing = 0.15.sp),
    headlineSmall = TextStyle(fontFamily = LatoFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = 0.15.sp),
    titleLarge = TextStyle(fontFamily = LatoFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 23.sp, letterSpacing = 0.15.sp),
    titleMedium = TextStyle(fontFamily = LatoFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontFamily = LatoFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.15.sp),
    bodyLarge = TextStyle(fontFamily = LatoFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 30.4.sp, letterSpacing = 0.15.sp),
    bodyMedium = TextStyle(fontFamily = LatoFontFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 28.5.sp, letterSpacing = 0.15.sp),
    bodySmall = TextStyle(fontFamily = LatoFontFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 23.4.sp, letterSpacing = 0.15.sp),
    labelLarge = TextStyle(fontFamily = LatoFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.15.sp),
    labelMedium = TextStyle(fontFamily = LatoFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.15.sp),
    labelSmall = TextStyle(fontFamily = LatoFontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.15.sp),
)
```

- [ ] **Step 4: Create `Theme.kt` with light and dark themes matching Flutter exactly**

```kotlin
package com.dailysatori.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = AppColors.primary,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = AppColors.secondary,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    secondaryContainer = AppColors.secondaryContainer,
    onSecondaryContainer = AppColors.onSecondaryContainer,
    tertiaryContainer = AppColors.tertiaryContainer,
    background = AppColors.background,
    onBackground = AppColors.onBackground,
    surface = AppColors.surface,
    onSurface = AppColors.onSurface,
    surfaceVariant = AppColors.surfaceContainer,
    onSurfaceVariant = AppColors.onSurfaceVariant,
    outline = AppColors.outline,
    outlineVariant = AppColors.outlineVariant,
    error = AppColors.error,
    surfaceContainer = AppColors.surfaceContainer,
    surfaceContainerHighest = AppColors.surfaceContainerHighest,
)

private val DarkColorScheme = darkColorScheme(
    primary = AppColors.primaryLight,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = AppColors.secondaryDark,
    secondaryContainer = AppColors.secondaryContainerDark,
    onSecondaryContainer = AppColors.onSecondaryContainerDark,
    tertiaryContainer = AppColors.tertiaryContainerDark,
    background = AppColors.backgroundDark,
    onBackground = AppColors.onBackgroundDark,
    surface = AppColors.surfaceDark,
    onSurface = AppColors.onSurfaceDark,
    surfaceVariant = AppColors.surfaceContainerDark,
    onSurfaceVariant = AppColors.onSurfaceVariantDark,
    outline = AppColors.outlineDark,
    outlineVariant = AppColors.outlineVariantDark,
    error = AppColors.errorDark,
    surfaceContainer = AppColors.surfaceContainerDark,
    surfaceContainerHighest = AppColors.surfaceContainerHighestDark,
)

@Composable
fun DailySatoriTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = AppTypography,
        content = content,
    )
}
```

- [ ] **Step 5: Copy font assets from Flutter project**

```bash
cp -r assets/fonts/google/ app/src/main/res/font/
# Rename Lato font files to lowercase with underscores for Android resource naming
```

- [ ] **Step 6: Commit theme system**

```bash
git add app/src/main/kotlin/com/dailysatori/ui/theme/ app/src/main/res/font/
git commit -m "feat: add theme system with exact color/typography/spacing from Flutter"
```

---

### Task 3: Navigation Skeleton

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/navigation/Routes.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/navigation/NavHost.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/pages/PlaceholderScreen.kt`
- Create: `app/src/main/kotlin/com/dailysatori/DailySatoriApp.kt`

- [ ] **Step 1: Create type-safe route definitions**

```kotlin
package com.dailysatori.ui.navigation

import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object ArticlesRoute
@Serializable data class ArticleDetailRoute(val articleId: Long)
@Serializable data object DiaryRoute
@Serializable data object BooksRoute
@Serializable data object BookSearchRoute
@Serializable data object AiChatRoute
@Serializable data object AiConfigRoute
@Serializable data class AiConfigEditRoute(val configId: Long? = null, val functionType: Int = 0)
@Serializable data object SettingsRoute
@Serializable data class ShareDialogRoute(val url: String)
@Serializable data object WeeklySummaryRoute
@Serializable data object BackupRestoreRoute
@Serializable data object BackupSettingsRoute
@Serializable data object PluginCenterRoute
```

- [ ] **Step 2: Create NavHost with placeholder screens**

```kotlin
package com.dailysatori.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.dailysatori.ui.pages.PlaceholderScreen

@Composable
fun DailySatoriNavHost(navController: NavHostController) {
    NavHost(navController, startDestination = HomeRoute) {
        composable<HomeRoute> { PlaceholderScreen("Home") }
        composable<ArticlesRoute> { PlaceholderScreen("Articles") }
        composable<ArticleDetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<ArticleDetailRoute>()
            PlaceholderScreen("Article Detail #${route.articleId}")
        }
        composable<DiaryRoute> { PlaceholderScreen("Diary") }
        composable<BooksRoute> { PlaceholderScreen("Books") }
        composable<BookSearchRoute> { PlaceholderScreen("Book Search") }
        composable<AiChatRoute> { PlaceholderScreen("AI Chat") }
        composable<AiConfigRoute> { PlaceholderScreen("AI Config") }
        composable<AiConfigEditRoute> { PlaceholderScreen("AI Config Edit") }
        composable<SettingsRoute> { PlaceholderScreen("Settings") }
        composable<ShareDialogRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<ShareDialogRoute>()
            PlaceholderScreen("Share: ${route.url}")
        }
        composable<WeeklySummaryRoute> { PlaceholderScreen("Weekly Summary") }
        composable<BackupRestoreRoute> { PlaceholderScreen("Backup Restore") }
        composable<BackupSettingsRoute> { PlaceholderScreen("Backup Settings") }
        composable<PluginCenterRoute> { PlaceholderScreen("Plugin Center") }
    }
}
```

- [ ] **Step 3: Create placeholder screen**

```kotlin
package com.dailysatori.ui.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun PlaceholderScreen(name: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = name, style = MaterialTheme.typography.headlineMedium)
    }
}
```

- [ ] **Step 4: Create app composable entry point**

```kotlin
package com.dailysatori

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.dailysatori.ui.navigation.DailySatoriNavHost

@Composable
fun DailySatoriApp() {
    val navController = rememberNavController()
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        DailySatoriNavHost(navController)
    }
}
```

- [ ] **Step 5: Verify build compiles**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit navigation skeleton**

```bash
git add app/src/main/kotlin/com/dailysatori/
git commit -m "feat: add navigation skeleton with all routes"
```

---

### Task 4: Koin DI Setup

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt`
- Create: `app/src/main/kotlin/com/dailysatori/di/AppModule.kt`
- Create: `app/src/main/kotlin/com/dailysatori/di/ViewModelModule.kt`
- Create: `app/src/main/kotlin/com/dailysatori/DailySatoriApplication.kt`

- [ ] **Step 1: Create shared Koin module (placeholder for now)**

```kotlin
package com.dailysatori.di

import org.koin.core.module.Module
import org.koin.dsl.module

val sharedModule: Module = module {
    // Will be populated in Phase 2 with repositories and services
}
```

- [ ] **Step 2: Create platform module and app module**

```kotlin
// di/AppModule.kt
package com.dailysatori.di

import org.koin.core.module.Module
import org.koin.dsl.module

val platformModule: Module = module {
    // Will be populated with expect/actual implementations
}

val appModule: Module = module {
    // Will be populated with Android-specific services
}
```

```kotlin
// di/ViewModelModule.kt
package com.dailysatori.di

import org.koin.core.module.Module
import org.koin.dsl.module

val viewModelModule: Module = module {
    // Will be populated with ViewModels in Phase 3
}
```

- [ ] **Step 3: Create Application class to initialize Koin**

```kotlin
package com.dailysatori

import android.app.Application
import com.dailysatori.di.appModule
import com.dailysatori.di.platformModule
import com.dailysatori.di.sharedModule
import com.dailysatori.di.viewModelModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class DailySatoriApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@DailySatoriApplication)
            modules(sharedModule, platformModule, appModule, viewModelModule)
        }
    }
}
```

- [ ] **Step 4: Update AndroidManifest.xml to reference Application class**

Ensure `<application android:name=".DailySatoriApplication" ...>` (already set in Task 1).

- [ ] **Step 5: Verify build**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit DI setup**

```bash
git add shared/src/ app/src/main/kotlin/com/dailysatori/di/ app/src/main/kotlin/com/dailysatori/DailySatoriApplication.kt app/src/main/AndroidManifest.xml
git commit -m "feat: add Koin DI setup with shared, platform, app, and viewmodel modules"
```

---

## Phase 2: Data Layer

### Task 5: SQLDelight Schema Definitions

**Files:**
- Create: `shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq`

- [ ] **Step 1: Create complete SQLDelight schema file**

```sql
-- Articles
CREATE TABLE article (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT,
    ai_title TEXT,
    content TEXT,
    ai_content TEXT,
    html_content TEXT,
    ai_markdown_content TEXT,
    url TEXT UNIQUE,
    is_favorite INTEGER DEFAULT 0,
    comment TEXT,
    status TEXT DEFAULT 'pending',
    cover_image TEXT,
    cover_image_url TEXT,
    pub_date INTEGER,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE TABLE tag (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT UNIQUE NOT NULL,
    icon TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE TABLE article_tag (
    article_id INTEGER NOT NULL REFERENCES article(id) ON DELETE CASCADE,
    tag_id INTEGER NOT NULL REFERENCES tag(id) ON DELETE CASCADE,
    PRIMARY KEY (article_id, tag_id)
);

CREATE TABLE image (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    url TEXT,
    path TEXT,
    article_id INTEGER REFERENCES article(id) ON DELETE CASCADE,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

-- Books
CREATE TABLE book (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL,
    author TEXT NOT NULL,
    category TEXT NOT NULL,
    cover_image TEXT NOT NULL,
    introduction TEXT NOT NULL,
    has_update INTEGER DEFAULT 0,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE TABLE book_viewpoint (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    book_id INTEGER NOT NULL REFERENCES book(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    example TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

-- Diary
CREATE TABLE diary (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    content TEXT NOT NULL,
    tags TEXT,
    mood TEXT,
    images TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

-- AI Config
CREATE TABLE ai_config (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    api_address TEXT NOT NULL,
    api_token TEXT NOT NULL,
    model_name TEXT NOT NULL,
    function_type INTEGER DEFAULT 0,
    inherit_from_general INTEGER DEFAULT 0,
    is_default INTEGER DEFAULT 0,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

-- Settings
CREATE TABLE setting (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    key TEXT UNIQUE,
    value TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

-- Weekly Summary
CREATE TABLE weekly_summary (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    week_start_date INTEGER NOT NULL,
    week_end_date INTEGER NOT NULL,
    content TEXT NOT NULL,
    article_count INTEGER DEFAULT 0,
    diary_count INTEGER DEFAULT 0,
    viewpoint_count INTEGER DEFAULT 0,
    article_ids TEXT,
    diary_ids TEXT,
    viewpoint_ids TEXT,
    app_ideas TEXT,
    status TEXT DEFAULT 'pending',
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

-- Session
CREATE TABLE session (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id TEXT UNIQUE NOT NULL,
    is_authenticated INTEGER DEFAULT 0,
    username TEXT,
    last_accessed_at INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

-- Article queries
selectArticles:
SELECT * FROM article ORDER BY created_at DESC;

selectArticlesPaginated:
SELECT * FROM article ORDER BY created_at DESC LIMIT ? OFFSET ?;

selectArticleById:
SELECT * FROM article WHERE id = ?;

selectArticlesByStatus:
SELECT * FROM article WHERE status = ? ORDER BY created_at DESC;

selectArticlesByTag:
SELECT a.* FROM article a
INNER JOIN article_tag at2 ON a.id = at2.article_id
WHERE at2.tag_id = ?
ORDER BY a.created_at DESC;

searchArticles:
SELECT * FROM article
WHERE title LIKE '%' || ? || '%'
   OR ai_title LIKE '%' || ? || '%'
   OR content LIKE '%' || ? || '%'
ORDER BY created_at DESC;

selectArticlesByDateRange:
SELECT * FROM article
WHERE created_at >= ? AND created_at <= ?
ORDER BY created_at DESC;

selectFavoriteArticles:
SELECT * FROM article WHERE is_favorite = 1 ORDER BY created_at DESC;

selectArticleDailyCounts:
SELECT date(created_at / 1000, 'unixepoch') as day, COUNT(*) as count
FROM article
GROUP BY day
ORDER BY day;

insertArticle:
INSERT INTO article (title, ai_title, content, ai_content, html_content, ai_markdown_content, url, is_favorite, comment, status, cover_image, cover_image_url, pub_date, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

updateArticle:
UPDATE article SET title = ?, ai_title = ?, content = ?, ai_content = ?, html_content = ?, ai_markdown_content = ?, url = ?, is_favorite = ?, comment = ?, status = ?, cover_image = ?, cover_image_url = ?, pub_date = ?, updated_at = ?
WHERE id = ?;

updateArticleField:
UPDATE article SET updated_at = ? WHERE id = ?;

deleteArticle:
DELETE FROM article WHERE id = ?;

toggleFavorite:
UPDATE article SET is_favorite = CASE WHEN is_favorite = 1 THEN 0 ELSE 1 END, updated_at = ? WHERE id = ?;

articleCount:
SELECT COUNT(*) FROM article;

-- Tag queries
selectAllTags:
SELECT * FROM tag ORDER BY name;

selectTagById:
SELECT * FROM tag WHERE id = ?;

selectTagByName:
SELECT * FROM tag WHERE name = ?;

getTagsByArticle:
SELECT t.* FROM tag t
INNER JOIN article_tag at2 ON t.id = at2.tag_id
WHERE at2.article_id = ?;

insertTag:
INSERT INTO tag (name, icon, created_at, updated_at) VALUES (?, ?, ?, ?);

deleteTag:
DELETE FROM tag WHERE id = ?;

insertArticleTag:
INSERT OR IGNORE INTO article_tag (article_id, tag_id) VALUES (?, ?);

deleteArticleTags:
DELETE FROM article_tag WHERE article_id = ?;

-- Image queries
selectImagesByArticle:
SELECT * FROM image WHERE article_id = ?;

insertImage:
INSERT INTO image (url, path, article_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?);

deleteImagesByArticle:
DELETE FROM image WHERE article_id = ?;

-- Book queries
selectAllBooks:
SELECT * FROM book ORDER BY created_at DESC;

selectBookById:
SELECT * FROM book WHERE id = ?;

searchBooks:
SELECT * FROM book
WHERE title LIKE '%' || ? || '%'
   OR author LIKE '%' || ? || '%'
ORDER BY created_at DESC;

insertBook:
INSERT INTO book (title, author, category, cover_image, introduction, has_update, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?);

updateBook:
UPDATE book SET title = ?, author = ?, category = ?, cover_image = ?, introduction = ?, has_update = ?, updated_at = ?
WHERE id = ?;

deleteBook:
DELETE FROM book WHERE id = ?;

-- Book viewpoint queries
selectViewpointsByBook:
SELECT * FROM book_viewpoint WHERE book_id = ? ORDER BY created_at DESC;

selectAllViewpoints:
SELECT * FROM book_viewpoint ORDER BY created_at DESC;

selectViewpointById:
SELECT * FROM book_viewpoint WHERE id = ?;

insertViewpoint:
INSERT INTO book_viewpoint (book_id, title, content, example, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, ?);

updateViewpoint:
UPDATE book_viewpoint SET title = ?, content = ?, example = ?, updated_at = ? WHERE id = ?;

deleteViewpoint:
DELETE FROM book_viewpoint WHERE id = ?;

deleteViewpointsByBook:
DELETE FROM book_viewpoint WHERE book_id = ?;

-- Diary queries
selectAllDiaries:
SELECT * FROM diary ORDER BY created_at DESC;

selectDiariesPaginated:
SELECT * FROM diary ORDER BY created_at DESC LIMIT ? OFFSET ?;

selectDiaryById:
SELECT * FROM diary WHERE id = ?;

searchDiaries:
SELECT * FROM diary
WHERE content LIKE '%' || ? || '%'
   OR tags LIKE '%' || ? || '%'
ORDER BY created_at DESC;

selectDiariesByDateRange:
SELECT * FROM diary
WHERE created_at >= ? AND created_at <= ?
ORDER BY created_at DESC;

insertDiary:
INSERT INTO diary (content, tags, mood, images, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, ?);

updateDiary:
UPDATE diary SET content = ?, tags = ?, mood = ?, images = ?, updated_at = ? WHERE id = ?;

deleteDiary:
DELETE FROM diary WHERE id = ?;

diaryCount:
SELECT COUNT(*) FROM diary;

-- AI Config queries
selectAllAiConfigs:
SELECT * FROM ai_config ORDER BY function_type, name;

selectAiConfigById:
SELECT * FROM ai_config WHERE id = ?;

selectDefaultAiConfig:
SELECT * FROM ai_config WHERE is_default = 1 AND function_type = ?;

selectGeneralAiConfig:
SELECT * FROM ai_config WHERE function_type = 0 AND is_default = 1;

insertAiConfig:
INSERT INTO ai_config (name, api_address, api_token, model_name, function_type, inherit_from_general, is_default, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);

updateAiConfig:
UPDATE ai_config SET name = ?, api_address = ?, api_token = ?, model_name = ?, function_type = ?, inherit_from_general = ?, is_default = ?, updated_at = ?
WHERE id = ?;

deleteAiConfig:
DELETE FROM ai_config WHERE id = ?;

-- Setting queries
selectSettingByKey:
SELECT * FROM setting WHERE key = ?;

selectAllSettings:
SELECT * FROM setting;

insertSetting:
INSERT INTO setting (key, value, created_at, updated_at) VALUES (?, ?, ?, ?);

updateSetting:
UPDATE setting SET value = ?, updated_at = ? WHERE key = ?;

upsertSetting:
INSERT OR REPLACE INTO setting (key, value, created_at, updated_at)
VALUES (?, ?, ?, ?);

deleteSetting:
DELETE FROM setting WHERE key = ?;

-- Weekly Summary queries
selectWeeklySummaries:
SELECT * FROM weekly_summary ORDER BY week_start_date DESC;

selectLatestWeeklySummary:
SELECT * FROM weekly_summary ORDER BY week_start_date DESC LIMIT 1;

selectWeeklySummaryByWeekRange:
SELECT * FROM weekly_summary WHERE week_start_date = ? AND week_end_date = ?;

insertWeeklySummary:
INSERT INTO weekly_summary (week_start_date, week_end_date, content, article_count, diary_count, viewpoint_count, article_ids, diary_ids, viewpoint_ids, app_ideas, status, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

updateWeeklySummary:
UPDATE weekly_summary SET content = ?, article_count = ?, diary_count = ?, viewpoint_count = ?, article_ids = ?, diary_ids = ?, viewpoint_ids = ?, app_ideas = ?, status = ?, updated_at = ?
WHERE id = ?;

-- Session queries
selectSessionBySessionId:
SELECT * FROM session WHERE session_id = ?;

insertSession:
INSERT INTO session (session_id, is_authenticated, username, last_accessed_at, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, ?);

updateSessionAccess:
UPDATE session SET last_accessed_at = ? WHERE session_id = ?;

deleteSession:
DELETE FROM session WHERE session_id = ?;

deleteExpiredSessions:
DELETE FROM session WHERE last_accessed_at < ?;

-- Stats queries
statsOverview:
SELECT
    (SELECT COUNT(*) FROM article) as total_articles,
    (SELECT COUNT(*) FROM diary) as total_diaries,
    (SELECT COUNT(*) FROM book) as total_books,
    (SELECT COUNT(*) FROM book_viewpoint) as total_viewpoints,
    (SELECT COUNT(*) FROM tag) as total_tags,
    (SELECT COUNT(*) FROM article WHERE is_favorite = 1) as total_favorites;
```

- [ ] **Step 2: Run SQLDelight code generation**

```bash
./gradlew :shared:generateCommonMainDailySatoriDatabaseInterface
```

Expected: Generates `DailySatoriDatabase.kt` and query accessor classes

- [ ] **Step 3: Commit schema**

```bash
git add shared/src/commonMain/sqldelight/
git commit -m "feat: add complete SQLDelight schema with all tables and queries"
```

---

### Task 6: Repository Implementations

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/ArticleRepository.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/DiaryRepository.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/BookRepository.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/BookViewpointRepository.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/TagRepository.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/ImageRepository.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/AIConfigRepository.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/SettingRepository.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/WeeklySummaryRepository.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/SessionRepository.kt`

- [ ] **Step 1: Create ArticleRepository with Flow-based reactive queries**

Each repository follows the same pattern: wraps SQLDelight queries, exposes `Flow<List<T>>` via `asFlow().mapToList()`, and provides synchronous CRUD. The ArticleRepository is the most complex with search, filtering, and pagination:

```kotlin
package com.dailysatori.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

class ArticleRepository(private val db: DailySatoriDatabase) {
    private val queries get() = db.dailySatoriQueries

    fun getAll(): Flow<List<Article>> =
        queries.selectArticles().asFlow().mapToList(Dispatchers.IO)

    fun getPaginated(limit: Long, offset: Long): Flow<List<Article>> =
        queries.selectArticlesPaginated(limit, offset).asFlow().mapToList(Dispatchers.IO)

    fun getByStatus(status: String): Flow<List<Article>> =
        queries.selectArticlesByStatus(status).asFlow().mapToList(Dispatchers.IO)

    fun getByTag(tagId: Long): Flow<List<Article>> =
        queries.selectArticlesByTag(tagId).asFlow().mapToList(Dispatchers.IO)

    fun search(query: String): Flow<List<Article>> =
        queries.searchArticles(query, query, query).asFlow().mapToList(Dispatchers.IO)

    fun getByDateRange(start: Instant, end: Instant): Flow<List<Article>> =
        queries.selectArticlesByDateRange(start.toEpochMilliseconds(), end.toEpochMilliseconds())
            .asFlow().mapToList(Dispatchers.IO)

    fun getFavorites(): Flow<List<Article>> =
        queries.selectFavoriteArticles().asFlow().mapToList(Dispatchers.IO)

    fun getDailyCounts(): Flow<Map<String, Long>> =
        queries.selectArticleDailyCounts().asFlow().mapToList(Dispatchers.IO)
            .map { list -> list.associate { it.day to it.count } }

    fun getById(id: Long): Article? =
        queries.selectArticleById(id).executeAsOneOrNull()

    fun insert(
        title: String? = null, aiTitle: String? = null, content: String? = null,
        aiContent: String? = null, htmlContent: String? = null,
        aiMarkdownContent: String? = null, url: String? = null,
        isFavorite: Boolean = false, comment: String? = null,
        status: String = "pending", coverImage: String? = null,
        coverImageUrl: String? = null, pubDate: Long? = null,
    ): Long {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        queries.insertArticle(
            title, aiTitle, content, aiContent, htmlContent, aiMarkdownContent,
            url, if (isFavorite) 1L else 0L, comment, status, coverImage,
            coverImageUrl, pubDate, now, now
        )
        return queries.lastInsertRowId().executeAsOne()
    }

    fun update(
        id: Long, title: String?, aiTitle: String?, content: String?,
        aiContent: String?, htmlContent: String?, aiMarkdownContent: String?,
        url: String?, isFavorite: Boolean, comment: String?, status: String,
        coverImage: String?, coverImageUrl: String?, pubDate: Long?,
    ) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        queries.updateArticle(
            title, aiTitle, content, aiContent, htmlContent, aiMarkdownContent,
            url, if (isFavorite) 1L else 0L, comment, status, coverImage,
            coverImageUrl, pubDate, now, id
        )
    }

    fun updateField(id: Long) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        queries.updateArticleField(now, id)
    }

    fun delete(id: Long) = queries.deleteArticle(id)
    fun toggleFavorite(id: Long) {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        queries.toggleFavorite(now, id)
    }
    fun count(): Long = queries.articleCount().executeAsOne()
}
```

Note: The exact types (`Article` etc.) are generated by SQLDelight from the schema. The import paths will match `com.dailysatori.shared.db.*`.

- [ ] **Step 2: Create remaining repositories following the same pattern**

Each repository mirrors the Flutter `BaseRepository` CRUD pattern. Key repositories:

**DiaryRepository** - CRUD + search + date range filtering
**BookRepository** - CRUD + search
**BookViewpointRepository** - CRUD by book ID + random selection
**TagRepository** - CRUD + article association management (article_tag join table)
**ImageRepository** - CRUD by article ID
**AIConfigRepository** - CRUD + default config per function type + default initialization
**SettingRepository** - key-value CRUD with upsert
**WeeklySummaryRepository** - CRUD + by week range + get or create
**SessionRepository** - CRUD + expiry management

All follow the same structural pattern: private `db.dailySatoriQueries`, `Flow` for reads, synchronous for writes.

- [ ] **Step 3: Register all repositories in shared Koin module**

Update `sharedModule` in `SharedModule.kt`:

```kotlin
val sharedModule = module {
    single { ArticleRepository(get()) }
    single { DiaryRepository(get()) }
    single { BookRepository(get()) }
    single { BookViewpointRepository(get()) }
    single { TagRepository(get()) }
    single { ImageRepository(get()) }
    single { AIConfigRepository(get()) }
    single { SettingRepository(get()) }
    single { WeeklySummaryRepository(get()) }
    single { SessionRepository(get()) }
}
```

- [ ] **Step 4: Verify compilation**

```bash
./gradlew :shared:build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit repositories**

```bash
git add shared/src/commonMain/kotlin/com/dailysatori/data/
git commit -m "feat: add all repository implementations with Flow-based queries"
```

---

### Task 7: Platform Interfaces (expect/actual)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/platform/PlatformContext.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/platform/DatabaseDriverFactory.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/platform/FileManager.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/platform/AppInfoProvider.kt`
- Create: `shared/src/androidMain/kotlin/com/dailysatori/platform/PlatformContext.android.kt`
- Create: `shared/src/androidMain/kotlin/com/dailysatori/platform/DatabaseDriverFactory.android.kt`
- Create: `shared/src/androidMain/kotlin/com/dailysatori/platform/FileManager.android.kt`
- Create: `shared/src/androidMain/kotlin/com/dailysatori/platform/AppInfoProvider.android.kt`

- [ ] **Step 1: Create expect declarations in commonMain**

```kotlin
// platform/PlatformContext.kt
package com.dailysatori.platform
expect class PlatformContext

// platform/DatabaseDriverFactory.kt
package com.dailysatori.platform
import app.cash.sqldelight.db.SqlDriver
expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

// platform/FileManager.kt
package com.dailysatori.platform
expect class FileManager() {
    fun getAppDataDir(): String
    fun getImagesDir(): String
    fun getDiaryImagesDir(): String
    fun getBackupDir(): String
    fun getCacheDir(): String
    fun writeFile(path: String, data: ByteArray)
    fun readFile(path: String): ByteArray
    fun deleteFile(path: String): Boolean
    fun exists(path: String): Boolean
    fun listFiles(path: String): List<String>
    fun copyFile(src: String, dest: String)
    fun fileSize(path: String): Long
    fun createDirectory(path: String): Boolean
}

// platform/AppInfoProvider.kt
package com.dailysatori.platform
expect class AppInfoProvider {
    fun getAppVersion(): String
    fun getAppName(): String
    fun getPackageName(): String
    fun isDebugMode(): Boolean
}
```

- [ ] **Step 2: Create Android actual implementations**

```kotlin
// platform/PlatformContext.android.kt
package com.dailysatori.platform
actual typealias PlatformContext = android.content.Context

// platform/DatabaseDriverFactory.android.kt
package com.dailysatori.platform
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.dailysatori.shared.db.DailySatoriDatabase
actual class DatabaseDriverFactory(private val context: PlatformContext) {
    actual fun createDriver(): SqlDriver =
        AndroidSqliteDriver(DailySatoriDatabase.Schema, context, "daily_satori.db")
}

// platform/FileManager.android.kt
package com.dailysatori.platform
import java.io.File
actual class FileManager actual constructor() {
    private lateinit var context: android.content.Context
    fun init(context: android.content.Context) { this.context = context }
    private fun appDir() = File(context.filesDir, "DailySatori")
    actual fun getAppDataDir(): String = appDir().absolutePath
    actual fun getImagesDir(): String = File(appDir(), "images").apply { mkdirs() }.absolutePath
    actual fun getDiaryImagesDir(): String = File(appDir(), "diary_images").apply { mkdirs() }.absolutePath
    actual fun getBackupDir(): String = File(appDir(), "backups").apply { mkdirs() }.absolutePath
    actual fun getCacheDir(): String = context.cacheDir.absolutePath
    actual fun writeFile(path: String, data: ByteArray) = File(path).apply { parentFile?.mkdirs() }.writeBytes(data)
    actual fun readFile(path: String): ByteArray = File(path).readBytes()
    actual fun deleteFile(path: String): Boolean = File(path).delete()
    actual fun exists(path: String): Boolean = File(path).exists()
    actual fun listFiles(path: String): List<String> = File(path).listFiles()?.map { it.absolutePath } ?: emptyList()
    actual fun copyFile(src: String, dest: String) { File(src).copyTo(File(dest), overwrite = true) }
    actual fun fileSize(path: String): Long = File(path).length()
    actual fun createDirectory(path: String): Boolean = File(path).mkdirs()
}

// platform/AppInfoProvider.android.kt
package com.dailysatori.platform
actual class AppInfoProvider(private val context: PlatformContext) {
    actual fun getAppVersion(): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
    actual fun getAppName(): String =
        context.applicationInfo.loadLabel(context.packageManager).toString()
    actual fun getPackageName(): String = context.packageName
    actual fun isDebugMode(): Boolean = BuildConfig.DEBUG
}
```

- [ ] **Step 3: Register in platform Koin module**

```kotlin
val platformModule = module {
    single { DatabaseDriverFactory(get()).createDriver() }
    single { DailySatoriDatabase(get()) }
    single<FileManager> { FileManager().apply { init(get<PlatformContext>()) } }
    single<AppInfoProvider> { AppInfoProvider(get()) }
}
```

- [ ] **Step 4: Verify compilation**

```bash
./gradlew :shared:build :app:assembleDebug
```

- [ ] **Step 5: Commit platform interfaces**

```bash
git add shared/src/
git commit -m "feat: add expect/actual platform interfaces for database, files, app info"
```

---

### Task 8: Core Services (I18n + Settings + Time)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/i18n/I18nService.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/setting/SettingService.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/config/Config.kt`
- Create: `shared/src/commonMain/resources/i18n/zh.yaml` (copy from Flutter)
- Create: `shared/src/commonMain/resources/i18n/en.yaml` (copy from Flutter)

- [ ] **Step 1: Copy i18n YAML files from Flutter**

```bash
cp assets/i18n/zh.yaml shared/src/commonMain/resources/i18n/
cp assets/i18n/en.yaml shared/src/commonMain/resources/i18n/
```

- [ ] **Step 2: Create config constants file**

```kotlin
package com.dailysatori.config

object AIConfig {
    const val timeoutMs = 30_000L
    const val maxSummaryLength = 500
    const val maxContentLength = 10_000
    const val maxTitleLength = 100
    const val maxTagsPerArticle = 10
    const val defaultTemperature = 0.5
    const val maxProcessContentLength = 50_000
    const val minHtmlLength = 50
    const val minTextLength = 20
    const val longTitleThreshold = 50
    const val randomRecommendationCount = 10
}

object BackupConfig {
    const val productionIntervalHours = 6L
    const val developmentIntervalHours = 24L
    const val fileExtension = ".zip"
    const val dateFormat = "yyyy-MM-dd_HH-mm-ss"
}

object DatabaseConfig {
    const val name = "daily_satori.db"
    const val objectBoxDir = "obx-daily"
}

object DirectoryConfig {
    const val appDocuments = "DailySatori"
    const val backup = "backups"
    const val cache = "cache"
    const val images = "images"
    const val diaryImages = "diary_images"
}

object ImageConfig {
    const val maxUploadSizeBytes = 5 * 1024 * 1024L
    const val maxWidth = 1920
    const val maxHeight = 1080
    const val cacheDurationDays = 7L
    const val downloadTimeoutMs = 30_000L
}

object InputConfig {
    const val maxLength = 120
    const val maxLines = 8
    const val minLines = 1
    const val commentMaxLength = 500
    const val searchMinLength = 2
}

object NetworkConfig {
    const val timeoutMs = 30_000L
    const val maxRetries = 3
    const val retryDelayMs = 1_000L
}

object PaginationConfig {
    const val defaultPageSize = 20L
    const val maxPageSize = 100L
    const val minPageSize = 5L
}

object SearchConfig {
    const val debounceTimeMs = 300L
    const val minLength = 2
    const val maxLength = 100
}

object SessionConfig {
    const val expireTimeMs = 30 * 60 * 1000L
    const val inactivityTimeoutMs = 90 * 1000L
    const val checkIntervalMs = 15 * 60 * 1000L
}

object WebServiceConfig {
    const val httpPort = 8888
}

object WebViewConfig {
    const val timeoutMs = 25_000L
    const val sessionMaxLifetimeMs = 240_000L
    const val maxConcurrentSessions = 2
    const val maxRedirects = 10
    const val domStabilityCheckDelayMs = 1500L
    const val loadProgressCheckDelayMs = 4_000L
}
```

- [ ] **Step 3: Create SettingService**

```kotlin
package com.dailysatori.service.setting

import com.dailysatori.data.repository.SettingRepository
import kotlinx.coroutines.runBlocking

class SettingService(private val repo: SettingRepository) {
    companion object {
        const val openAITokenKey = "openai_token"
        const val openAIAddressKey = "openai_address"
        const val backupDirKey = "backup_directory"
        const val lastBackupTimeKey = "last_backup_time"
        const val appLanguageKey = "app_language"
        const val webServerPasswordKey = "web_server_password"
        const val webSocketUrlKey = "web_socket_url"
        const val deviceIdKey = "device_id"
        const val pluginServerUrlKey = "plugin_server_url"
        const val isFirstLaunchKey = "is_first_launch"
    }

    fun get(key: String): String? = repo.get(key)
    fun set(key: String, value: String) = repo.upsert(key, value)
    fun getString(key: String, default: String = ""): String = get(key) ?: default
    fun getLong(key: String, default: Long = 0): Long = get(key)?.toLongOrNull() ?: default
    fun getBool(key: String, default: Boolean = false): Boolean = get(key)?.toBooleanStrictOrNull() ?: default
    fun remove(key: String) = repo.delete(key)
}
```

- [ ] **Step 4: Create I18nService**

The I18nService loads YAML translation files from resources, provides dot-notation key lookup, and a `t()` extension function. Implementation uses `kaml` to parse YAML maps from commonMain resources.

- [ ] **Step 5: Register services in Koin**

Update `sharedModule`:
```kotlin
single { SettingService(get()) }
single { I18nService(get()) }
```

- [ ] **Step 6: Commit core services**

```bash
git add shared/src/
git commit -m "feat: add core services (i18n, settings, config constants)"
```

---

## Phase 3: Core Pages (Articles + Diary + Books)

### Task 9: Shared UI Components

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/components/SAppBar.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/components/FeatureIcon.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/components/EmptyState.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/components/LoadingIndicator.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/components/FilterIndicator.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/components/SearchBar.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/components/CustomCard.kt`

- [ ] **Step 1: Create SAppBar component**

Matches Flutter's custom SAppBar with light/dark background color support, centered title, and optional actions.

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SAppBar(
    title: @Composable () -> Unit,
    onNavigationClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    backgroundColorLight: Color = AppColors.primary,
    backgroundColorDark: Color = AppColors.backgroundDark,
    elevation: Dp = 0.dp,
) {
    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) backgroundColorDark else backgroundColorLight
    TopAppBar(
        title = title,
        navigationIcon = {
            onNavigationClick?.let { onClick ->
                IconButton(onClick = onClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = bgColor,
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White,
            actionIconContentColor = Color.White,
        ),
    )
}
```

- [ ] **Step 2: Create FeatureIcon component**

```kotlin
@Composable
fun FeatureIcon(
    icon: ImageVector,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconTint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    containerSize: Dp = IconSize.xl,
    iconSize: Dp = IconSize.s,
) {
    Box(
        modifier = Modifier.size(containerSize).clip(CircleShape).background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(iconSize))
    }
}
```

- [ ] **Step 3: Create remaining components (EmptyState, LoadingIndicator, FilterIndicator, SearchBar, CustomCard)**

Each mirrors the corresponding Flutter widget with exact layout structure.

- [ ] **Step 4: Commit shared components**

```bash
git add app/src/main/kotlin/com/dailysatori/ui/components/
git commit -m "feat: add shared UI components (SAppBar, FeatureIcon, EmptyState, etc.)"
```

---

### Task 10: HomeScreen (Bottom Navigation)

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/pages/home/HomeScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/navigation/NavHost.kt`

- [ ] **Step 1: Create HomeScreen with 5-tab bottom navigation**

The HomeScreen is a Scaffold with a BottomNavigationBar containing 5 tabs: Articles, Diary, Books, AI Chat, Weekly Summary. It uses a custom IndexedStack-like approach to preserve state across tab switches (similar to Flutter's `_LazyIndexedStack`).

The key structure:
- Scaffold with bottomBar
- When tab is selected, navigate to the corresponding route
- Each tab destination preserves its own back stack

Implementation approach: Use a `mutableStateOf` for selected tab index, and display the content for the selected tab directly (not nested navigation), wrapped in `remember` to preserve state.

- [ ] **Step 2: Update NavHost to use HomeScreen as root**

Remove individual tab routes from NavHost top level, nest them inside HomeScreen instead.

- [ ] **Step 3: Verify on device**

```bash
./gradlew :app:installDebug
```

Expected: App launches with 5-tab bottom navigation, switching tabs shows placeholder content.

- [ ] **Step 4: Commit HomeScreen**

```bash
git add app/src/main/kotlin/com/dailysatori/ui/
git commit -m "feat: add HomeScreen with 5-tab bottom navigation"
```

---

### Task 11: ArticlesScreen + ArticleDetailScreen

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/pages/articles/ArticlesScreen.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/pages/articles/ArticleCard.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/pages/article_detail/ArticleDetailScreen.kt`
- Create: `app/src/main/kotlin/com/dailysatori/viewmodel/ArticlesViewModel.kt`
- Create: `app/src/main/kotlin/com/dailysatori/viewmodel/ArticleDetailViewModel.kt`

- [ ] **Step 1: Create ArticlesViewModel**

State: articles list, isLoading, searchQuery, selectedTagId, showFavoritesOnly, showSearchBar, isSearchVisible. Methods: loadArticles, search, filterByTag, filterByDate, toggleFavorite, refresh.

- [ ] **Step 2: Create ArticlesScreen**

Layout matches Flutter exactly:
- SAppBar with calendar icon (leading), title (center), search + popup menu actions
- Conditionally shows SearchBar and FilterIndicator
- ArticlesList with pull-to-refresh and pagination

- [ ] **Step 3: Create ArticleCard component**

Matches Flutter's ArticleCard: title (aiTitle or title), subtitle (date + tags), favorite icon, cover image.

- [ ] **Step 4: Create ArticleDetailScreen with tabs**

Two tabs at bottom: Summary (Markdown rendered from aiMarkdownContent) and Original (WebView or HTML rendered content).

- [ ] **Step 5: Register ViewModels in Koin**

```kotlin
viewModel { ArticlesViewModel(get(), get()) }
viewModel { (articleId: Long) -> ArticleDetailViewModel(articleId, get(), get()) }
```

- [ ] **Step 6: Verify and commit**

```bash
git add app/src/main/kotlin/com/dailysatori/ui/pages/articles/ app/src/main/kotlin/com/dailysatori/ui/pages/article_detail/ app/src/main/kotlin/com/dailysatori/viewmodel/
git commit -m "feat: add articles list and detail screens with ViewModels"
```

---

### Task 12: DiaryScreen + DiaryEditorSheet

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/pages/diary/DiaryScreen.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/pages/diary/DiaryCard.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/pages/diary/DiaryEditorSheet.kt`
- Create: `app/src/main/kotlin/com/dailysatori/viewmodel/DiaryViewModel.kt`

- [ ] **Step 1: Create DiaryViewModel**

State: diaries list, isLoading, searchQuery, selectedTagId, dateRange filter, showSearchBar. Methods: loadDiaries, search, filterByTag, filterByDate, createDiary, updateDiary, deleteDiary.

- [ ] **Step 2: Create DiaryScreen**

Matches Flutter's DiaryView exactly:
- SAppBar with calendar (leading), "我的日记" title, search + tag actions
- Stack layout: DiaryList underneath, DiarySearchBar overlay, FAB at bottom-right
- FilterIndicator when filters active

- [ ] **Step 3: Create DiaryCard**

Shows diary content preview, date, mood, tag chips, image thumbnails.

- [ ] **Step 4: Create DiaryEditorSheet**

BottomSheetScaffold for creating/editing diaries:
- Content TextField (Markdown)
- MarkdownToolbar (bold, italic, heading, list, etc.)
- Tags section
- Mood selector
- Image picker button
- Save/Cancel buttons

- [ ] **Step 5: Verify and commit**

```bash
git add app/src/main/kotlin/com/dailysatori/ui/pages/diary/ app/src/main/kotlin/com/dailysatori/viewmodel/DiaryViewModel.kt
git commit -m "feat: add diary screen with editor sheet"
```

---

### Task 13: BooksScreen + BookSearchScreen

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/pages/books/BooksScreen.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/pages/books/ViewpointCard.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/pages/books/BookSearchScreen.kt`
- Create: `app/src/main/kotlin/com/dailysatori/viewmodel/BooksViewModel.kt`

- [ ] **Step 1: Create BooksViewModel**

State: books, viewpoints, currentBookId, currentViewpointIndex, displayMode. Methods: loadBooks, loadViewpoints, filterByBook, shuffleViewpoints, deleteBook, refreshBook.

- [ ] **Step 2: Create BooksScreen**

Matches Flutter's BooksView:
- SAppBar with book filter (leading), title, add + popup menu actions
- HorizontalPager showing ViewpointCards
- Small FAB for quick diary entry from viewpoint
- Book filter bottom sheet

- [ ] **Step 3: Create ViewpointCard**

Shows viewpoint title, content, example, book info.

- [ ] **Step 4: Create BookSearchScreen**

Search bar + results list. Uses BookSearchService (will be implemented in Phase 4).

- [ ] **Step 5: Verify and commit**

```bash
git add app/src/main/kotlin/com/dailysatori/ui/pages/books/ app/src/main/kotlin/com/dailysatori/viewmodel/BooksViewModel.kt
git commit -m "feat: add books and viewpoint screens with pager"
```

---

## Phase 4: AI Features

### Task 14: AI Service

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/ai/AiService.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/ai/AiArticleProcessor.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/ai/AiConfigService.kt`

- [ ] **Step 1: Create AiService with Ktor-based OpenAI client**

Implements: complete(), translate(), summarize(), summarizeOneLine(), htmlToMarkdown(). Uses Ktor HttpClient with JSON content negotiation. Supports configurable base URL for any OpenAI-compatible provider.

- [ ] **Step 2: Create AiConfigService**

Manages per-function AI configuration with inheritance from general config. Methods: getGeneralConfig(), getDefaultConfig(type), getApiAddressForFunction(type), etc.

- [ ] **Step 3: Create AiArticleProcessor**

Orchestrates article AI processing: title, summary, markdown, image. Runs in parallel via coroutine async.

- [ ] **Step 4: Register in Koin and commit**

```bash
git add shared/src/commonMain/kotlin/com/dailysatori/service/ai/
git commit -m "feat: add AI service with OpenAI-compatible API client"
```

---

### Task 15: WebView Content Extractor (Android)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/platform/WebViewLoader.kt`
- Create: `app/src/main/kotlin/com/dailysatori/platform/AndroidWebViewLoader.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/webcontent/WebpageParserService.kt`

- [ ] **Step 1: Create expect WebViewLoader**

```kotlin
// shared commonMain
expect class WebViewLoader {
    fun loadContent(url: String, timeoutMs: Long = 25_000, callback: (Result<String>) -> Unit)
}
```

- [ ] **Step 2: Create Android WebViewLoader using android.webkit.WebView**

Headless WebView approach: create WebView programmatically, load URL, inject Readability.js, extract content via evaluateJavascript callback.

- [ ] **Step 3: Copy JS assets from Flutter**

```bash
cp -r assets/js/ app/src/main/assets/js/
cp -r assets/css/ app/src/main/assets/css/
```

- [ ] **Step 4: Create WebpageParserService pipeline**

Full pipeline: create article → fetch via WebView → extract content → AI process → download images.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/dailysatori/platform/WebViewLoader.kt shared/src/commonMain/kotlin/com/dailysatori/service/webcontent/ app/src/main/kotlin/com/dailysatori/platform/AndroidWebViewLoader.kt app/src/main/assets/
git commit -m "feat: add WebView content extractor and webpage parser pipeline"
```

---

### Task 16: Book Search Service

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/book/BookSearchService.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/book/GoogleBooksSearchEngine.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/book/OpenLibrarySearchEngine.kt`

- [ ] **Step 1: Implement BookSearchEngine interface and engines**

Each engine uses Ktor HttpClient to call its respective API. BookSearchService coordinates multiple engines.

- [ ] **Step 2: Commit**

```bash
git add shared/src/commonMain/kotlin/com/dailysatori/service/book/
git commit -m "feat: add book search service with Google Books and OpenLibrary engines"
```

---

## Phase 5: AI UI Pages

### Task 17: AI Chat Screen

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/pages/aichat/AiChatScreen.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/components/ChatInput.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/components/MessageBubble.kt`
- Create: `app/src/main/kotlin/com/dailysatori/viewmodel/AiChatViewModel.kt`

- [ ] **Step 1: Create AiChatViewModel**

State: messages list, isLoading, processingStep. Methods: sendMessage, retryMessage, clearChat. Integrates with AiService for intent recognition and knowledge base search.

- [ ] **Step 2: Create chat UI components**

ChatInput: text field + send button at bottom. MessageBubble: user/AI message rendering. SearchResultCard: collapsible search result cards.

- [ ] **Step 3: Create AiChatScreen**

Scaffold with SAppBar (refresh + info actions), ChatInterface body with message list + input.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/dailysatori/ui/pages/aichat/ app/src/main/kotlin/com/dailysatori/ui/components/Chat*.kt app/src/main/kotlin/com/dailysatori/ui/components/MessageBubble.kt app/src/main/kotlin/com/dailysatori/viewmodel/AiChatViewModel.kt
git commit -m "feat: add AI chat screen with message UI"
```

---

### Task 18: AI Config + Config Edit Screens

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/pages/aiconfig/AiConfigScreen.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/pages/aiconfig/AiConfigEditScreen.kt`
- Create: `app/src/main/kotlin/com/dailysatori/viewmodel/AiConfigViewModel.kt`
- Create: `app/src/main/kotlin/com/dailysatori/viewmodel/AiConfigEditViewModel.kt`

- [ ] **Step 1: Create AI config screens matching Flutter layout**

AiConfigScreen: ListView of config cards with function type icons. AiConfigEditScreen: form with name, provider, model, token, API address fields + inherit toggle.

- [ ] **Step 2: Commit**

```bash
git add app/src/main/kotlin/com/dailysatori/ui/pages/aiconfig/ app/src/main/kotlin/com/dailysatori/viewmodel/AiConfig*
git commit -m "feat: add AI config management screens"
```

---

### Task 19: Share Dialog Screen

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/pages/share_dialog/ShareDialogScreen.kt`
- Create: `app/src/main/kotlin/com/dailysatori/viewmodel/ShareDialogViewModel.kt`

- [ ] **Step 1: Create ShareDialogScreen matching Flutter layout**

URL display, title TextField, tags section with Chip widgets, comment TextField, AI re-analysis toggle, bottom bar with Cancel + Save buttons.

- [ ] **Step 2: Handle incoming share intents in MainActivity**

Read `Intent.ACTION_SEND` text from intent extras, navigate to ShareDialogRoute.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/dailysatori/ui/pages/share_dialog/ app/src/main/kotlin/com/dailysatori/viewmodel/ShareDialogViewModel.kt app/src/main/kotlin/com/dailysatori/MainActivity.kt
git commit -m "feat: add share dialog screen with intent handling"
```

---

## Phase 6: Settings & Auxiliary

### Task 20: Settings Screen

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/pages/settings/SettingsScreen.kt`
- Create: `app/src/main/kotlin/com/dailysatori/viewmodel/SettingsViewModel.kt`

- [ ] **Step 1: Create SettingsScreen**

Two sections in cards: Function (AI Config, Plugin Center, Google Books API Key) and System (Backup & Restore, Download Images, Web Server, Check Update). Each item is FeatureIcon + title/subtitle + chevron.

- [ ] **Step 2: Implement Web Server management dialog**

DraggableScrollableSheet-like bottom sheet with server info (HTTP/WS addresses, connection status) and management (password, restart).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/dailysatori/ui/pages/settings/ app/src/main/kotlin/com/dailysatori/viewmodel/SettingsViewModel.kt
git commit -m "feat: add settings screen with web server dialog"
```

---

### Task 21: Backup + Plugin Center + Weekly Summary Screens

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/pages/backup_restore/BackupRestoreScreen.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/pages/backup_settings/BackupSettingsScreen.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/pages/plugin_center/PluginCenterScreen.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/pages/weekly_summary/WeeklySummaryScreen.kt`
- Create: corresponding ViewModels

- [ ] **Step 1: Implement BackupRestoreScreen**

Backup file selection with radio-style items, restore button. Matches Flutter layout exactly.

- [ ] **Step 2: Implement BackupSettingsScreen**

Directory picker prompt → main content with directory card, backup/restore actions, tip card.

- [ ] **Step 3: Implement PluginCenterScreen**

ListView of PluginCards with pull-to-refresh, empty state, server URL dialog.

- [ ] **Step 4: Implement WeeklySummaryScreen**

Markdown content rendering, header with stats badges, history bottom sheet, generate/regenerate actions.

- [ ] **Step 5: Commit all auxiliary screens**

```bash
git add app/src/main/kotlin/com/dailysatori/ui/pages/ app/src/main/kotlin/com/dailysatori/viewmodel/
git commit -m "feat: add backup, plugin center, and weekly summary screens"
```

---

## Phase 7: Platform-Specific Features

### Task 22: Backup Service

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/backup/BackupService.kt`

- [ ] **Step 1: Implement BackupService with ZIP compression**

3-item backup (database, web images, diary images). Uses java.util.zip via expect/actual. Matches Flutter's timestamp-based folder naming and progress tracking.

- [ ] **Step 2: Commit**

```bash
git add shared/src/commonMain/kotlin/com/dailysatori/service/backup/
git commit -m "feat: add backup service with ZIP compression"
```

---

### Task 23: Web Server (Ktor Server)

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/service/WebServerService.kt`
- Create: `app/src/main/kotlin/com/dailysatori/webserver/Routes.kt`
- Create: `app/src/main/kotlin/com/dailysatori/webserver/Auth.kt`
- Create: `app/src/main/kotlin/com/dailysatori/webserver/Controllers.kt`

- [ ] **Step 1: Implement Ktor embedded server**

Replace Flutter's shelf-based server with Ktor Server (Netty). Routes match exactly:
- `/ping` → pong
- `/api/v2/auth/*` → login, logout, status
- `/api/v2/articles/*` → CRUD with pagination and search
- `/api/v2/diary/*` → CRUD
- `/api/v2/books/*` → CRUD with viewpoints
- `/api/v2/stats/*` → overview, recent, weekly-report
- Static file serving for images and website admin

- [ ] **Step 2: Copy website admin assets**

```bash
cp -r assets/website/ app/src/main/assets/website/
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/dailysatori/service/WebServerService.kt app/src/main/kotlin/com/dailysatori/webserver/ app/src/main/assets/website/
git commit -m "feat: add web server with Ktor embedded server and REST API"
```

---

### Task 24: Plugin Service + Weekly Summary Service

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/plugin/PluginService.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/weekly/WeeklySummaryService.kt`

- [ ] **Step 1: Implement PluginService**

YAML config loading from assets + remote server. Prompt template management. Matches Flutter's caching and update logic.

- [ ] **Step 2: Implement WeeklySummaryService**

Auto-detect last completed week, generate summary via AI, extract app ideas.

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/dailysatori/service/plugin/ shared/src/commonMain/kotlin/com/dailysatori/service/weekly/
git commit -m "feat: add plugin and weekly summary services"
```

---

### Task 25: Clipboard Monitor + Share Receive + App Upgrade

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/service/ClipboardMonitorService.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ShareReceiveActivity.kt`
- Create: `app/src/main/kotlin/com/dailysatori/service/AppUpgradeService.kt`

- [ ] **Step 1: Implement ClipboardMonitorService**

Lifecycle-aware service that monitors clipboard on app resume, validates URLs, shows confirmation dialog.

- [ ] **Step 2: Implement ShareReceiveActivity**

Handle incoming `ACTION_SEND` intents, extract URL, navigate to ShareDialogScreen.

- [ ] **Step 3: Implement AppUpgradeService**

Check GitHub releases API, compare versions, download APK.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/dailysatori/service/ app/src/main/kotlin/com/dailysatori/ShareReceiveActivity.kt app/src/main/AndroidManifest.xml
git commit -m "feat: add clipboard monitor, share receive, and app upgrade services"
```

---

## Phase 8: Polish & Testing

### Task 26: Theme Parity Verification

- [ ] **Step 1: Verify all colors match Flutter exactly (light + dark)**

Compare screenshots side by side. Adjust any discrepancies in Color.kt.

- [ ] **Step 2: Verify typography matches (font sizes, weights, line heights)**

Check all Text composables use the correct MaterialTheme.typography style.

- [ ] **Step 3: Verify spacing, padding, and radii**

Check all Spacing.* and Radius.* usages match Flutter's Dimensions constants.

- [ ] **Step 4: Commit any fixes**

---

### Task 27: Copy All Assets

**Files:**
- Copy: `assets/js/*` → `app/src/main/assets/js/`
- Copy: `assets/css/*` → `app/src/main/assets/css/`
- Copy: `assets/images/*` → `app/src/main/assets/images/`
- Copy: `assets/easylistchina+easylist.txt` → `app/src/main/assets/`
- Copy: `assets/configs/*` → `shared/src/commonMain/resources/config/`

- [ ] **Step 1: Copy all asset files from Flutter project**

```bash
mkdir -p app/src/main/assets/js app/src/main/assets/css app/src/main/assets/images
cp assets/js/* app/src/main/assets/js/
cp assets/css/* app/src/main/assets/css/
cp assets/images/* app/src/main/assets/images/
cp assets/easylistchina+easylist.txt app/src/main/assets/
cp assets/configs/* shared/src/commonMain/resources/config/
```

- [ ] **Step 2: Commit assets**

```bash
git add app/src/main/assets/ shared/src/commonMain/resources/config/
git commit -m "feat: copy all assets (JS, CSS, images, configs, ADBlock rules)"
```

---

### Task 28: Final Integration Test

- [ ] **Step 1: Full build and install**

```bash
./gradlew :app:assembleDebug :app:installDebug
```

- [ ] **Step 2: Test all 5 bottom navigation tabs**

Verify: Articles, Diary, Books, AI Chat, Weekly Summary all load correctly.

- [ ] **Step 3: Test article flow: share URL → parse → AI process → view**

- [ ] **Step 4: Test diary CRUD with markdown editing**

- [ ] **Step 5: Test book viewpoint browsing and search**

- [ ] **Step 6: Test AI chat**

- [ ] **Step 7: Test settings → AI config → backup/restore → web server**

- [ ] **Step 8: Test light/dark theme toggle**

- [ ] **Step 9: Final commit**

```bash
git add -A
git commit -m "feat: complete Flutter to KMP/CMP migration"
```

---

## Summary

| Phase | Tasks | Key Deliverables |
|-------|-------|-----------------|
| 1 | Tasks 1-4 | Project scaffold, theme, navigation, DI |
| 2 | Tasks 5-8 | SQLDelight schema, repositories, platform interfaces, core services |
| 3 | Tasks 9-13 | Shared components, Home, Articles, Diary, Books screens |
| 4 | Tasks 14-16 | AI service, WebView extractor, book search |
| 5 | Tasks 17-19 | AI Chat, AI Config, Share Dialog |
| 6 | Tasks 20-21 | Settings, Backup, Plugin Center, Weekly Summary |
| 7 | Tasks 22-25 | Backup service, Web server, Plugins, Weekly, Clipboard, Upgrade |
| 8 | Tasks 26-28 | Theme verification, assets, integration testing |

Total: 28 tasks across 8 phases.
