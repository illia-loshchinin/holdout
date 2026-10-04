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
        rootProject.file(providers.gradleProperty("traces").getOrElse("../phone_traces")).absolutePath,
        // -Pdetail: every cycle's verdict and what it was judged on.
        if (providers.gradleProperty("detail").isPresent) "detail" else "summary",
    )
}

// The test bench: every sitting with a labels file (scripts/label-reps.mjs),
// replayed and scored rep by rep against what a person saw on the video.
// `-Ptraces=<dir>` (relative to kotlin/) to point it elsewhere than
// fixtures/rep-traces/; `-Pgate`
// to fail when honest reps count under 98 % or any cheat over 2 %.
tasks.register<JavaExec>("testBench") {
    description = "Scores the counter rep by rep against video-labelled sittings, by label, person and sitting."
    group = "verification"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("holdout.repcounter.TestBenchKt")
    args(
        rootProject.file(providers.gradleProperty("traces").getOrElse("../fixtures/rep-traces")).absolutePath,
        if (providers.gradleProperty("gate").isPresent) "gate" else "report",
    )
}
