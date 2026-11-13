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
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.3")
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
        manifestPlaceholders["generationEndpoint"] = providers.gradleProperty("generationEndpoint").orElse("").get()
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        debug {
            manifestPlaceholders["generationEndpoint"] = providers.gradleProperty("generationEndpoint")
                .orElse("http://10.0.2.2:18088/v1/trips/generate").get()
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
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}
