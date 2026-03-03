plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.devtools.ksp) // Přidej toto
}

android {
    namespace = "cz.uhk.macroflow"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "cz.uhk.macroflow"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")

    // --- OPRAVENÉ KNIHOVNY PRO SKENER A DATA ---

    // Google Code Scanner (volá systémové UI, ideální pro Pixel 10 Pro) [cite: 2026-03-01]
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")

    // Pro jednoduché stahování dat z OpenFoodFacts bez nutnosti složitého Retrofitu [cite: 2026-03-01]
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}