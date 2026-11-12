import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

val supabaseVersion = "3.6.0"
val ktorVersion = "3.4.3"

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    jvm()

    js {
        browser()
        binaries.executable()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    android {
        namespace = "com.example.rnd_transit_mtl.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }

        androidResources {
            enable = true
        }

        withHostTest {
            isIncludeAndroidResources = true
        }

        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.viewmodelNavigation3)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.kotlinx.serializationJson)
            implementation("io.ktor:ktor-client-core:$ktorVersion")
            implementation(libs.androidx.navigation3.ui)

            implementation("io.coil-kt.coil3:coil-compose:3.6.3")
            implementation("io.coil-kt.coil3:coil-network-ktor3:3.6.3")

            implementation("io.github.jan-tennert.supabase:auth-kt:$supabaseVersion")
            implementation("io.github.jan-tennert.supabase:postgrest-kt:$supabaseVersion")
        }

        androidMain.dependencies {
            implementation(libs.maplibre.compose)
            implementation(libs.androidx.activity.compose)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
            implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
        }

        jvmMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
        }

        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:$ktorVersion")
        }

        jsMain.dependencies {
            implementation(libs.wrappers.browser)
            implementation("io.ktor:ktor-client-js:$ktorVersion")
        }

        wasmJsMain.dependencies {
            implementation("io.ktor:ktor-client-cio:$ktorVersion")
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            // runTest drives suspend generation and routing in shared tests.
            implementation(libs.kotlinx.coroutinesTest)
        }

        jvmTest.dependencies {
            // Native graphics runtime for interaction and animation regression checks on this host.
            runtimeOnly(compose.desktop.currentOs)
            // Match the desktop app's UI dispatcher for lifecycle-aware coordinator tests.
            runtimeOnly(libs.kotlinx.coroutinesSwing)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
