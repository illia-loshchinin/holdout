// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.android) apply false
    // Declared here so :core, :repcounter and :crank can apply it without
    // Gradle re-resolving a plugin the Android plugin already put on the
    // classpath (research R12).
    alias(libs.plugins.kotlin.jvm) apply false
}
