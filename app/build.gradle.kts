plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-kapt")
}

android {
    namespace = "com.zsolutions.peerlinkyz"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zsolutions.peerlinkyz"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
        }

        multiDexEnabled = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes.add("META-INF/INDEX.LIST")
        resources.excludes.add("META-INF/io.netty.versions.properties")
        resources.excludes.add("META-INF/*.kotlin_module")
    }
}

dependencies {
    implementation("org.bouncycastle:bcprov-jdk15on:1.70")
    implementation("org.bouncycastle:bcpkix-jdk15on:1.70")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("com.google.android.material:material:1.11.0")
    
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    kaptTest("org.mockito:mockito-core:4.11.0")
    kaptTest("junit:junit:4.13.2")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // Ktor
    implementation("io.ktor:ktor-client-core:2.3.10")
    implementation("io.ktor:ktor-client-okhttp:2.3.10")
    
    implementation("io.ktor:ktor-client-websockets:2.3.10")
    implementation("io.ktor:ktor-server-core:2.3.10")
    implementation("io.ktor:ktor-server-netty:2.3.10")
    implementation("io.ktor:ktor-server-websockets:2.3.10")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.multidex:multidex:2.0.1")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Tink
    implementation("com.google.crypto.tink:tink-android:1.11.0")
    
    // Embedded Tor
    implementation("info.guardianproject.netcipher:netcipher:2.1.0")
}