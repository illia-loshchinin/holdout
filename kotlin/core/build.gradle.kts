// kotlin/core — the one Kotlin client the app and the crank share (research R12).
// Account codecs, PDAs, instruction builders, derived figures and chain time.
// Plain JVM: no Android dependency, so it is unit-testable against the golden
// fixtures without a phone (constitution VII).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(libs.web3.solana)
    api(libs.rpc.core)
    api(libs.rpc.solana)
    api(libs.rpc.ktordriver)
    // The client under rpc-ktordriver, built here with timeouts and rebuilt when it gets stuck (FR-069i).
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.multimult)
    // The RPC libraries speak kotlinx-serialization; building a raw JSON-RPC
    // request needs it on the compile classpath, not only at runtime.
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
