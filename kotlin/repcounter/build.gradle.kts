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

// Calibration (spec 002 T034c): replays every labelled phone trace through the
// counter and the placement check, and prints what the thresholds should be
// judged against. `-Ptraces=<dir>` to point it elsewhere than phone_traces/.
tasks.register<JavaExec>("replayTraces") {
    description = "Replays labelled phone traces and reports placement ratios and count accuracy per placement."
    group = "verification"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("holdout.repcounter.ReplayReportKt")
    args(
        providers.gradleProperty("traces").getOrElse(rootProject.file("../phone_traces").absolutePath),
        // -Pdetail: every cycle's verdict and what it was judged on.
        if (providers.gradleProperty("detail").isPresent) "detail" else "summary",
    )
}
