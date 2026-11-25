plugins {
    id("com.android.application")
}

val unsignedRelease = providers.gradleProperty("unsignedRelease").orNull == "true"

android {
    namespace = "com.dalong.alipaynfc"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dalong.alipaynfc"
        minSdk = 27
        targetSdk = 34
        versionCode = 8
        versionName = "1.0.8"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (!unsignedRelease) {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "META-INF/**"
        }
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    compileOnly(project(":xposed-stub"))
}
