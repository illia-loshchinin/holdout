// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.android) apply false
    // Declared here so :core, :repcounter and :crank can apply it without
    // Gradle re-resolving a plugin the Android plugin already put on the
    // classpath (research R12).
    alias(libs.plugins.kotlin.jvm) apply false
    // One Kotlin style across the app, the shared client, the rep counter and
    // the crank (T008). `./gradlew ktlintCheck` is what CI runs;
    // `./gradlew ktlintFormat` fixes what can be fixed.
    alias(libs.plugins.ktlint)
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        // Generated Compose previews and the Android resource classes are not
        // ours to format.
        filter {
            exclude { it.file.path.contains("${'$'}{File.separator}build${'$'}{File.separator}") }
        }
    }
}
