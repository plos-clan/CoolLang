plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.dagger.hilt)
    alias(libs.plugins.google.devtools)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "org.plos.edu.lang"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }
    buildFeatures.compose = true

    defaultConfig {
        applicationId = "org.plos.edu.lang"
        minSdk = 26
        targetSdk = 37
        versionCode = 13
        versionName = "2.0.0"
        androidResources.localeFilters += listOf("en", "zh")
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles("proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.compose.ui)
    implementation(libs.compose.animation)
    implementation(libs.compose.foundation)
    implementation(libs.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.navigation3)
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.datastore.preferences)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.hilt.android)
    implementation(libs.hilt.viewmodel.compose)
    ksp(libs.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
}
