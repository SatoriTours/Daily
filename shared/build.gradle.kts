plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlinxSerialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
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
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.langchain4j.open.ai)
            implementation(libs.langchain4j.anthropic)
            implementation(libs.langchain4j.google.ai.gemini)
            implementation("dev.langchain4j:langchain4j-http-client-okhttp:${libs.versions.langchain4j.http.client.okhttp.get()}") {
                exclude(group = "com.squareup.okhttp3", module = "okhttp-jvm")
            }
        }
    }
}

android {
    namespace = "com.dailysatori.shared"
    compileSdk = 36
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
            packageName.set("com.dailysatori.shared.db")
            dialect(libs.sqldelight.sqlite.dialect)
        }
    }
}
