// kotlin/repcounter — pre-flight checks, palm liveness and the push-up state
// machine, consuming landmark frames (research R4, R5).
//
// Plain JVM on purpose: the rules are tested against recorded landmark traces,
// including deliberate cheats, without a phone (T049).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    // Recorded landmark traces live with the other shared fixtures, at the repo root.
    val traces = rootProject.file("../fixtures/rep-traces")
    inputs.dir(traces)
    systemProperty("holdout.repTraces", traces.absolutePath)
}
