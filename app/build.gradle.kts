plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.vg.grabber"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.vg.grabber"
        minSdk = 26
        targetSdk = 33
        versionCode = 2
        versionName = "1.1"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    // Har GitHub build ek hi key se sign hoga => naya APK purani app ke upar update ho jata hai
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"; keyAlias = "androiddebugkey"; keyPassword = "android"
        }
    }
    packaging { jniLibs { useLegacyPackaging = true } }
}
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("io.github.junkfood02.youtubedl-android:library:0.17.2")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.17.2")
}
