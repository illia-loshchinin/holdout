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
    implementation(libs.rpc.solana)
    implementation(libs.rpc.ktordriver)
    implementation(libs.multimult)

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
