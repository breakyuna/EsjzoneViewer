plugins {
    id("com.android.test")
}

android {
    namespace = "com.breakyuna.esjzone.macrobenchmark"
    compileSdk = 37

    defaultConfig {
        minSdk = 23
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }

    targetProjectPath = ":app"
}

dependencies {
    implementation("androidx.benchmark:benchmark-macro-junit4:1.5.0-rc02")
    implementation("androidx.test.ext:junit:1.3.0")
    implementation("androidx.test.uiautomator:uiautomator:2.4.0-rc02")
}
