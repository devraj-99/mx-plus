plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val versionCodeEnv = providers.environmentVariable("MXPLUS_VERSION_CODE").orNull?.toIntOrNull() ?: 1
val storeFileEnv = providers.environmentVariable("MXPLUS_STORE_FILE").orNull

android {
    namespace = "com.mxplus.app"
    compileSdk = 37
    compileSdkMinor = 0

    signingConfigs {
        create("release") {
            if (storeFileEnv != null) {
                storeFile = rootProject.file(storeFileEnv)
                storePassword = providers.environmentVariable("MXPLUS_STORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("MXPLUS_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("MXPLUS_KEY_PASSWORD").get()
            }
        }
    }

    defaultConfig {
        applicationId = "com.mxplus.app"
        minSdk = 26
        targetSdk = 36
        versionCode = versionCodeEnv
        versionName = "0.4.0"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val compose = "1.11.1"
    implementation("org.jetbrains.compose.runtime:runtime:$compose")
    implementation("org.jetbrains.compose.foundation:foundation:$compose")
    implementation("org.jetbrains.compose.ui:ui:$compose")
    implementation("org.jetbrains.compose.material3:material3:1.11.0-alpha07")
    implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-ui:1.8.0")
    implementation("androidx.media3:media3-common:1.8.0")
    implementation("androidx.media3:media3-session:1.8.0")
    implementation("dev.chrisbanes.haze:haze:1.7.2")
    implementation("io.github.kyant0:backdrop:2.0.0")
}
