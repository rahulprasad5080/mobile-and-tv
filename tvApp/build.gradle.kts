plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains-kotlin-android)
}

android {
    namespace = "com.antigravity.videoplayer.tv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.antigravity.videoplayer.tv"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        vectorDrawables {
            useSupportLibrary = true
        }
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx-core-ktx)
    implementation(libs.androidx-lifecycle-runtime-ktx)
    implementation(libs.androidx-activity-compose)
    implementation(libs.androidx-navigation-compose)
    
    // TV Compose
    implementation(libs.androidx-tv-foundation)
    implementation(libs.androidx-tv-material)
    
    implementation(libs.androidx-ui)
    implementation(libs.androidx-ui-tooling-preview)
    
    implementation(libs.androidx-media3-ui)
    implementation(libs.androidx-media3-common)

    debugImplementation(libs.androidx-ui-tooling)
}
