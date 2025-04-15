import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    kotlin("plugin.serialization") version "2.0.21"
}

val COUCHBASE_LITE_VERSION = rootProject.extra["COUCHBASE_LITE_VERSION"] as String
val USE_LOCAL_MAVEN = COUCHBASE_LITE_VERSION.endsWith("-SNAPSHOT")

android {
    namespace = "com.couchbase.lite.mobile.android.test.bt"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.couchbase.lite.mobile.android.test.bt"
        minSdk = 30
        targetSdk = 35
        versionCode = 2
        versionName = "2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    kotlinOptions { jvmTarget = "11" }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
        viewBinding = true
    }

    composeOptions { kotlinCompilerExtensionVersion = "1.5.15" }
}

repositories {
    if (USE_LOCAL_MAVEN) {
        mavenLocal()
    } else {
        maven {
            url = URI("https://proget.sc.couchbase.com/maven2/internalmaven/")
            content { includeGroupByRegex("com\\.couchbase\\.lite.*") }
        }
    }
    google()
    mavenCentral()
}

dependencies {
    implementation(libs.koin.core)
    implementation(libs.koin.android)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.json.jvm)

    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.accompanist.permissions)

    implementation(libs.androidx.databinding.runtime)
    implementation(libs.androidx.runtime.livedata)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.runner)
    androidTestImplementation(libs.androidx.core)
    androidTestImplementation(libs.androidx.rules)
}
