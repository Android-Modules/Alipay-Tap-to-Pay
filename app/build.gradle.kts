plugins {
    id("com.android.application")
}

val unsignedRelease = providers.gradleProperty("unsignedRelease").orNull == "true"

android {
    namespace = "com.dalong.alipaynfc"
    compileSdk {
        version = release(37)
    }

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
            merges += "META-INF/xposed/*"
            excludes += setOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/DEPENDENCIES",
                "META-INF/*.version",
                "kotlin/**"
            )
        }
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    compileOnly(project(":xposed-stub"))
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("io.github.libxposed:service:102.0.0")
}
