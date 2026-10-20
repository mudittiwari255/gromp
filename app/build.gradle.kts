plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "app.gromp"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.gromp"
        minSdk = 28
        targetSdk = 36
        versionCode = 4
        versionName = "0.2.2"
    }

    // Release signing comes from environment variables (set from GitHub secrets in CI).
    // Without them the release build falls back to the debug key.
    val keystorePath: String? = System.getenv("GROMP_KEYSTORE")
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("GROMP_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("GROMP_KEY_ALIAS")
                keyPassword = System.getenv("GROMP_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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

    // "full" has soft locks and notification counting. "lite" leaves out the accessibility and
    // notification-listener services, so it installs from a browser even where Play Protect
    // blocks sideloaded apps that request those permissions (e.g. its India fraud-protection pilot).
    // Both share one applicationId, so moving from lite to full keeps your history.
    flavorDimensions += "edition"
    productFlavors {
        create("full") {
            dimension = "edition"
            buildConfigField("boolean", "LOCKS", "true")
        }
        create("lite") {
            dimension = "edition"
            versionNameSuffix = "-lite"
            buildConfigField("boolean", "LOCKS", "false")
        }
    }
    lint {
        abortOnError = false
    }
}

kotlin {
    jvmToolchain(17)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit)
}
