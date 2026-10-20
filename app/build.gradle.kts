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
        versionCode = 6
        versionName = "0.4.0"
    }

    // Release signing comes from environment variables (GitHub secrets in CI) when they're set.
    // Otherwise every build uses the shared development key in signing/dev.keystore, so CI builds
    // can be installed over each other. Swap in your own key (via the secrets) before distributing widely.
    val keystorePath: String? = System.getenv("GROMP_KEYSTORE")
    signingConfigs {
        create("dev") {
            storeFile = rootProject.file("signing/dev.keystore")
            storePassword = "gromp-dev"
            keyAlias = "gromp-dev"
            keyPassword = "gromp-dev"
        }
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
        debug {
            signingConfig = signingConfigs.getByName("dev")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("dev")
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
    testOptions {
        unitTests.isIncludeAndroidResources = true
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
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
