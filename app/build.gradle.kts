plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.fitnova"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // New package id: installs next to the old in.fitnova.app build and uses the same accounts.
        applicationId = "app.fitnova"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0-m1"

        // Publishable (client-side) key from the existing Fit Nova Supabase project.
        buildConfigField("String", "SUPABASE_URL", "\"https://intwuixajqddizrdzbce.supabase.co\"")
        buildConfigField("String", "SUPABASE_KEY", "\"sb_publishable_zcF0ZvKMre70erXJ9dRSAg_jM-Lu058\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // ponytail: debug key so release builds install for testing; swap in your upload key before publishing.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // One APK per CPU type: pose detection ships ~20 MB of native code per ABI, so this keeps downloads small.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.mlkit.vision)
    implementation(libs.mlkit.pose)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
