plugins {
    id("com.android.application")
}

android {
    namespace = "com.bghorizon.murglarbypass"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.bghorizon.murglarbypass"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    signingConfigs {
        getByName("debug") {
            val pinned = rootProject.file("../.android/debug.keystore")
            if (pinned.exists()) storeFile = pinned
        }
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
}
