// kotlin/crank — the automation, scripted members and Operator tooling
// (research R12, contracts/crank-cli.md). One runnable JAR, scheduled every
// 10 minutes on Fly.io; anyone may run the same commands (FR-030a).
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core"))
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("holdout.crank.MainKt")
}

tasks.named<Jar>("jar") {
    manifest { attributes["Main-Class"] = "holdout.crank.MainKt" }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from({ configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) } })
}

tasks.test {
    useJUnitPlatform()
}
