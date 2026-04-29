plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains-kotlin-android)
}

android {
    namespace = "com.antigravity.videoplayer.common_ui"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }
}

dependencies {
    implementation(libs.androidx-core-ktx)
    implementation(platform(libs.androidx-compose-bom))
    implementation(libs.androidx-ui)
    implementation(libs.androidx-material3)
}
