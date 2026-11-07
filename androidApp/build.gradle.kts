import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    runtimeOnly(libs.maplibre.runtime)
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.foundation)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
    debugImplementation(libs.kotlinx.coroutinesCore)
}

android {
    namespace = "com.example.rnd_transit_mtl"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.example.rnd_transit_mtl"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        debug {
            val endpoint = providers.gradleProperty("stmOtpEndpoint")
                .orElse("http://10.0.2.2:18086/otp/gtfs/v1").get()
            buildConfigField("String", "STM_OTP_ENDPOINT", "\"${endpoint.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}
