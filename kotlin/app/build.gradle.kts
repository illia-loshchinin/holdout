import java.io.File
import java.net.URI
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// The MediaPipe pose model: 9 MB of binary, downloaded at build time to keep
// it out of git — a repository judges clone should not carry a blob it could
// fetch — while a clean checkout still builds with one command (AC-19). It
// lands in `src/main/assets/`, which `.gitignore` excludes.
//
// **Pinned by SHA-256.** Google serves it at a `latest` URL that is replaced
// in place (the only versioned artefact, `1`, is different bytes), and every
// threshold in `repcounter` was tuned on this exact file: it is the model in
// the APK the phone traces were recorded with (checked 26 September). A file
// with any other digest is re-downloaded, and if the host now serves other
// bytes the build fails rather than silently ship an untuned model. If the
// host is unreachable, drop the file in by hand; its digest is checked.
val poseModel = "pose_landmarker_full.task"
val poseModelUrl = "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_full/float16/latest/pose_landmarker_full.task"
val poseModelSha256 = "4eaa5eb7a98365221087693fcc286334cf0858e2eb6e15b506aa4a7ecdcec4ad"

// `tasks.register(...)`, not `val x by tasks.registering { }`: Gradle 9
// deprecated that delegate syntax, and it is the only warning this build
// produces.
val downloadMediaPipeModels = tasks.register("downloadMediaPipeModels") {
    description = "Fetches the pinned pose model into src/main/assets (research R4)."

    // Everything the action touches is captured as a plain value here. A lambda
    // that reaches back into the build script cannot be serialised, and the
    // configuration cache refuses the build rather than silently disabling
    // itself.
    val target = File(layout.projectDirectory.dir("src/main/assets").asFile, poseModel)
    val url = poseModelUrl
    val expected = poseModelSha256
    val name = poseModel

    outputs.file(target)
    outputs.upToDateWhen {
        target.isFile &&
            MessageDigest.getInstance("SHA-256")
                .digest(target.readBytes())
                .joinToString("") { "%02x".format(it) } == expected
    }

    doLast {
        fun digestOf(file: File): String = MessageDigest.getInstance("SHA-256")
            .digest(file.readBytes())
            .joinToString("") { "%02x".format(it) }

        if (target.isFile && digestOf(target) == expected) return@doLast
        target.parentFile.mkdirs()
        println("Downloading $name…")
        // Into a side file, renamed only once complete and verified: an
        // interrupted or replaced download never becomes the shipped model.
        val partial = File(target.parentFile, "${target.name}.part")
        URI(url).toURL().openStream().use { input ->
            partial.outputStream().use { output -> input.copyTo(output) }
        }
        val got = digestOf(partial)
        if (got != expected) {
            partial.delete()
            throw GradleException(
                "$name from $url has SHA-256 $got, not the pinned $expected. The model changed upstream; " +
                    "re-record the phone traces on the new one before re-pinning it (app/build.gradle.kts).",
            )
        }
        target.delete()
        if (!partial.renameTo(target)) {
            partial.delete()
            throw GradleException("$name: could not move the download into place")
        }
    }
}

tasks.named("preBuild") { dependsOn(downloadMediaPipeModels) }

android {
    namespace = "holdout.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "holdout.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        // So the spike affordance below can be compiled out of a release build.
        buildConfig = true
    }
}

dependencies {
    // The rep rules, tested on the JVM without a phone (T046–T049), and the
    // one client the app and the crank share — codecs, PDAs, chain time (R12).
    implementation(project(":core"))
    implementation(project(":repcounter"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.mobile.wallet.adapter.clientlib.ktx)
    implementation(libs.multimult)
    implementation(libs.web3.solana)
    // Reading chain state and a recent blockhash (research R3, R8). The app and
    // the crank talk to the RPC through the same two libraries.
    implementation(libs.rpc.core)
    implementation(libs.rpc.solana)
    implementation(libs.rpc.ktordriver)

    // The camera and the pose model. Frames are analysed in process and
    // discarded — nothing is written and nothing is uploaded (FR-022).
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mediapipe.tasks.vision)

    // Banked reps and the streak, written synchronously before the UI shows
    // them (T045, FR-023).
    implementation(libs.mmkv)
    // The banking and streak rules are plain Kotlin and are tested as such,
    // with an in-memory Store standing in for MMKV (T051, T052). JUnit 4 is
    // what the Android plugin wires up by default; `kotlin-test-junit` maps
    // `kotlin.test` onto it, so these read like the tests in the JVM modules.
    testImplementation(libs.junit)
    testImplementation(kotlin("test-junit"))
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
