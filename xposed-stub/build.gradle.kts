plugins {
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // Only the android.* types referenced by stub signatures; never packaged.
    compileOnly(files("${System.getProperty("user.home")}/Library/Android/sdk/platforms/android-36/android.jar"))
}
