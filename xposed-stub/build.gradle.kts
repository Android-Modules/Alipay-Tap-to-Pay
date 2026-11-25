import java.util.Properties

plugins {
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

val localSdkDir = providers.fileContents(rootProject.layout.projectDirectory.file("local.properties"))
    .asText.map { contents ->
        Properties().apply { load(contents.reader()) }.getProperty("sdk.dir", "")
    }.filter { it.isNotBlank() }

val androidSdkDir = localSdkDir
    .orElse(providers.environmentVariable("ANDROID_HOME"))
    .orElse(providers.environmentVariable("ANDROID_SDK_ROOT"))
    .orElse(providers.systemProperty("user.home").map { "$it/Library/Android/sdk" })

dependencies {
    // Only the android.* types referenced by stub signatures; never packaged.
    compileOnly(files(androidSdkDir.map {
        val sdk37 = file("$it/platforms/android-37.0/android.jar")
        if (sdk37.exists()) sdk37 else file("$it/platforms/android-36/android.jar")
    }))
}
