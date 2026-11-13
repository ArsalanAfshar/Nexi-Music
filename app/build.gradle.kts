plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.nexi.music"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nexi.music"
        minSdk = 23
        targetSdk = 35
        versionCode = providers.gradleProperty("appVersionCode").orElse("1").get().toInt()
        versionName = providers.gradleProperty("appVersionName").orElse("0.1.0").get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Universal APK plus architecture-specific release APKs for smaller downloads.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")
    implementation("androidx.media3:media3-common:1.5.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

// Release signing is intentionally environment-only; never place credentials in source control.
val releaseKeystore = System.getenv("ANDROID_KEYSTORE_FILE")
android {
    signingConfigs {
        create("release") {
            if (!releaseKeystore.isNullOrBlank()) {
                storeFile = file(releaseKeystore)
                storeType = System.getenv("ANDROID_KEYSTORE_TYPE") ?: "JKS"
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
