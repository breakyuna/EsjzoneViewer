import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.google.ksp) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.androidx.room) apply false

    id("com.android.test") version "9.1.1" apply false
    id("androidx.baselineprofile") version "1.4.1" apply false
}

subprojects {
    pluginManager.withPlugin("org.jetbrains.kotlin.plugin.compose") {
        extensions.configure<ComposeCompilerGradlePluginExtension> {
            val reportsDirectory = layout.buildDirectory.dir("compose_compiler")
            reportsDestination.set(reportsDirectory)
            metricsDestination.set(reportsDirectory)
        }
    }
}
