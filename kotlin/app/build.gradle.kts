import java.io.File
import java.io.StringReader
import java.net.URI
import java.security.MessageDigest
import java.util.Properties

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

// The release signing key (T115; constitution Hackathon Constraints, FR-066,
// AC-19: the submitted APK is signed). The keystore and its passwords live
// OUTSIDE the repository and reach the build only through four Gradle
// properties - preferably in `~/.gradle/gradle.properties`, or as `-P` flags
// (which leave the passwords in shell history and the process list); the
// runbook (docs/holdout_setup_runbook.md §4) has the keytool recipe and
// the backup rule:
//
//   holdout.keystore          absolute path of the keystore, forward slashes
//   holdout.keystorePassword
//   holdout.keyAlias
//   holdout.keyPassword       a PKCS12 keystore forces it equal to the store's
//
// All four set: the release variants are signed, and `assembleMemberRelease`
// produces app-member-release.apk (labRelease shares the config - one key, two
// package names). None set, which is CI and every machine without the owner's
// keystore: the release still builds, unsigned, as
// app-member-release-unsigned.apk, and the assemble and bundle tasks say so in
// one warning (below, in `onVariants`). Some but not all, a path that is not
// absolute or not a file, or a path inside the repository is a slip that
// `checkReleaseSigning` refuses - and only the release assemble, bundle and
// package tasks depend on it, so debug builds, tests, ktlint and an IDE sync
// never look at the keystore. Only the tracked-file rule below is checked at
// configuration time.
val releaseSigningProperties = listOf(
    "holdout.keystore",
    "holdout.keystorePassword",
    "holdout.keyAlias",
    "holdout.keyPassword",
).associateWith { providers.gradleProperty(it).orNull?.trim()?.takeIf { value -> value.isNotEmpty() } }
val releaseSigningConfigured = releaseSigningProperties.values.all { it != null }

// The one configuration-time check: the tracked `kotlin/gradle.properties` is
// the file Android Studio opens as "Project Properties" and the one a generic
// "put it in gradle.properties" instruction lands in; a signing property there
// would publish the keystore password with the next `git commit -a`. It does not
// depend on the machine, so it fails every build, not only a release.
val trackedSigningProperties =
    Properties().apply {
        providers.fileContents(rootProject.layout.projectDirectory.file("gradle.properties"))
            .asText.orNull?.let { load(StringReader(it)) }
    }.stringPropertyNames().filter { it.startsWith("holdout.key") }
if (trackedSigningProperties.isNotEmpty()) {
    throw GradleException(
        "kotlin/gradle.properties is tracked by git and must not define the release signing properties " +
            "(${trackedSigningProperties.joinToString()}); move them to ~/.gradle/gradle.properties (docs/holdout_setup_runbook.md §4).",
    )
}

// Plain values only (nothing from the script or the project), for the
// configuration cache. The keystore file is looked at when the task runs, not
// when it is configured.
val checkReleaseSigning = tasks.register("checkReleaseSigning") {
    description = "Fails if the release signing properties are partly set or name no usable keystore (T115)."
    val names = releaseSigningProperties.keys.toList()
    val missing = releaseSigningProperties.filterValues { it == null }.keys.toList()
    val keystorePath = releaseSigningProperties["holdout.keystore"]
    val repositoryRoot = rootDir.parentFile.canonicalPath
    doLast {
        if (missing.size == names.size) return@doLast // none set: an unsigned release is allowed
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Release signing needs all four Gradle properties or none; missing: ${missing.joinToString()} " +
                    "(docs/holdout_setup_runbook.md §4).",
            )
        }
        val keystore = File(keystorePath!!)
        if (!keystore.isAbsolute) {
            throw GradleException(
                "holdout.keystore must be an absolute path with forward slashes, e.g. C:/Users/<you>/holdout-release.jks " +
                    "(a leading ~ is not expanded, backslashes are eaten by a .properties file, a relative path is not used) " +
                    "(docs/holdout_setup_runbook.md §4).",
            )
        }
        if (!keystore.isFile) {
            throw GradleException("holdout.keystore does not name an existing file (docs/holdout_setup_runbook.md §4).")
        }
        if (keystore.canonicalFile.toPath().startsWith(File(repositoryRoot).toPath())) {
            throw GradleException("holdout.keystore points inside the repository; keep the release keystore outside the clone (docs/holdout_setup_runbook.md §4).")
        }
    }
}

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

        // The deployment this build talks to — devnet, always (README). Both
        // can be overridden with Gradle properties for the local validator (quickstart §5a).
        buildConfigField("String", "RPC_URL", "\"${providers.gradleProperty("holdout.rpcUrl").getOrElse("https://api.devnet.solana.com")}\"")
        buildConfigField("String", "PROGRAM_ID", "\"${providers.gradleProperty("holdout.programId").getOrElse("Ho1d924DTXrCgVfbW1U2NkAc3rGk1F723weN8rduyoy4")}\"")

        // Seeker ID names (spec 003): the one mainnet endpoint, used only by
        // the read-only SeekerNameReader to look up .skr names for display —
        // it can neither sign nor send (research R5). A keyed provider can be
        // set with -Pholdout.namesRpcUrl=... (research R4).
        buildConfigField("String", "NAMES_RPC_URL", "\"${providers.gradleProperty("holdout.namesRpcUrl").getOrElse("https://api.mainnet-beta.solana.com")}\"")

        // The faucet's fee sponsor (research R13): its secret SHIPS IN THE APK by
        // design — a README known weakness, bounded on-chain by a 0.05 SOL float,
        // a daily lamport budget and a permissionless refill. It is read at build
        // time from the Solana CLI keypair file `holdout.sponsorKeypair` names
        // (see docs/holdout_setup_runbook.md) and never committed. Without it the
        // build still works: a wallet that holds SOL pays for its own claim, and
        // one that holds none is told the faucet cannot pay out (ES-0b).
        val sponsorHex =
            providers.gradleProperty("holdout.sponsorKeypair").orNull?.let { path ->
                File(path).readText().trim().removeSurrounding("[", "]").split(',')
                    .map { it.trim().toInt() }
                    .also { require(it.size == 64) { "$path is not a 64-byte Solana keypair" } }
                    .joinToString("") { "%02x".format(it) }
            } ?: ""
        buildConfigField("String", "SPONSOR_KEYPAIR_HEX", "\"$sponsorHex\"")
    }

    buildTypes {
        // Staked Holdouts are Seeker-exclusive (001 FR-003b, research R17): the
        // app checks the wallet's Seeker Genesis Token on mainnet before a join.
        // A debug build may turn that off with -Pholdout.seekerGate=off, so the
        // emulator, a local validator (quickstart §5a) and a phone without a
        // Seeker can still reach the stake confirm. A release build always checks.
        debug {
            buildConfigField("boolean", "SEEKER_GATE", "${providers.gradleProperty("holdout.seekerGate").orNull != "off"}")
        }
        release {
            buildConfigField("boolean", "SEEKER_GATE", "true")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Created and wired only when the owner's keystore is configured
            // (above); an unset signingConfig is what makes the APK `-unsigned`.
            // storeFile is not checked here - `checkReleaseSigning` does that.
            if (releaseSigningConfigured) {
                signingConfig =
                    signingConfigs.create("release") {
                        storeFile = File(releaseSigningProperties.getValue("holdout.keystore"))
                        storePassword = releaseSigningProperties.getValue("holdout.keystorePassword")
                        keyAlias = releaseSigningProperties.getValue("holdout.keyAlias")
                        keyPassword = releaseSigningProperties.getValue("holdout.keyPassword")
                    }
            }
        }
    }
    // Two apps from one module (spec 004, research R1). `member` is the app
    // members use and the APK that is submitted; `lab` is Holdout Lab, the
    // tester app that records and labels sets, never given to members. Both
    // compile src/main, so they count with the same rules; Lab-only code
    // lives in src/lab, where member code cannot reach it (constitution X).
    flavorDimensions += "app"
    productFlavors {
        create("member") {
            dimension = "app"
        }
        create("lab") {
            dimension = "app"
            applicationIdSuffix = ".lab"
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
    // Holdout Lab only: a set's camera saved as video for testing rep
    // recognition and training the knee classifier (constitution X v2.1.0).
    // camera-view already brings it in transitively; this only makes the Lab
    // recorder's dependency explicit. checkMemberHasNoLabCode keeps member code off it.
    "labImplementation"(libs.androidx.camera.video)

    // Ed25519 for the one key the app itself signs with — the faucet's fee
    // sponsor (research R13). The JDK's own Ed25519 needs API 33; this runs on
    // every level from minSdk 26. Member transactions are signed by the wallet.
    implementation(libs.bouncycastle.prov)

    // Banked reps and the streak, written synchronously before the UI shows
    // them (T045, FR-023).
    implementation(libs.mmkv)
    // The banking and streak rules are plain Kotlin and are tested as such,
    // with an in-memory Store standing in for MMKV (T051, T052). JUnit 4 is
    // what the Android plugin wires up by default; `kotlin-test-junit` maps
    // `kotlin.test` onto it, so these read like the tests in the JVM modules.
    testImplementation(libs.junit)
    testImplementation(kotlin("test-junit"))
    // The view model's chain check runs on viewModelScope; a test Main dispatcher drives it (005 T019).
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// FR-022a: the app never declares RECORD_AUDIO — not in its own manifest and
// not through any library's (T101). Checked on the *merged* manifest of every
// variant, and wired into assemble, so an audio permission cannot ship by
// accident: the build fails instead.
abstract class CheckNoAudioPermission : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @TaskAction
    fun check() {
        val text = mergedManifest.get().asFile.readText()
        if ("android.permission.RECORD_AUDIO" in text) {
            throw GradleException("RECORD_AUDIO appears in the merged manifest (${mergedManifest.get().asFile}). Holdout never records audio (FR-022a).")
        }
    }
}

val checkNoAudioPermission = tasks.register("checkNoAudioPermission") {
    description = "Fails the build if RECORD_AUDIO appears in any variant's merged manifest (FR-022a, T101)."
}

androidComponents {
    onVariants { variant ->
        val name = variant.name.replaceFirstChar { it.uppercase() }
        val check = tasks.register<CheckNoAudioPermission>("check${name}NoAudioPermission") {
            mergedManifest.set(variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.MERGED_MANIFEST))
        }
        checkNoAudioPermission.configure { dependsOn(check) }
        tasks.matching { it.name == "assemble$name" }.configureEach { dependsOn(check) }

        if (variant.buildType == "release") {
            // T115: the signing rules are checked before anything is packaged
            // or signed - and so before AGP's own errors, which print the path.
            tasks.matching { it.name in setOf("assemble$name", "bundle$name", "package$name", "validateSigning$name") }
                .configureEach { dependsOn(checkReleaseSigning) }

            // A release built without a signing config says so, once, after it
            // is assembled or bundled - rather than being found out at install
            // time. Asked of the variant, not of the four properties: Android
            // Studio's "Generate Signed App Bundle / APK" signs through
            // `android.injected.signing.*` and must not see the warning.
            // Plain values only, for the configuration cache.
            val signed =
                android.buildTypes.getByName("release").signingConfig != null ||
                    variant.productFlavors.any { (_, flavor) -> android.productFlavors.findByName(flavor)?.signingConfig != null } ||
                    providers.gradleProperty("android.injected.signing.store.file").isPresent
            if (!signed) {
                val propertyNames = releaseSigningProperties.keys.joinToString()
                tasks.matching { it.name == "assemble$name" || it.name == "bundle$name" }.configureEach {
                    val taskName = this.name
                    doLast {
                        logger.warn(
                            "WARNING: $taskName is UNSIGNED: no release keystore is configured. " +
                                "Set $propertyNames in ~/.gradle/gradle.properties to sign it (docs/holdout_setup_runbook.md §4).",
                        )
                    }
                }
            }
        }
    }
}

// Constitution X (v2.1.0), spec 004 FR-010: only Holdout Lab may record a set's
// camera, keep a trace, ask for a label or show a tester switch. No build of the
// member app, developer or release, may contain any of them; if it does, the
// build fails here rather than ship it.
//
// Lab's code lives in src/lab, which member variants do not compile, so a
// reference to it already fails to compile. This is the constitution's build
// check and the backstop for the one library member code can still see:
// camera-view (PreviewView) depends on camera-video, so the library cannot be
// kept off the classpath — what is forbidden is member code using it.
abstract class CheckMemberHasNoLabCode : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val memberSources: ConfigurableFileCollection

    @TaskAction
    fun check() {
        val forbidden = listOf(
            "androidx.camera.video",
            "TraceRecorder",
            "TraceLabelDialog",
            "TestSwitch",
            "LabApp",
            "LabReadouts",
            "\"rec · ",
        )
        val offenders = memberSources.asFileTree
            .filter { it.extension in setOf("kt", "java") && forbidden.any { word -> word in it.readText() } }
            .files
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "Member-app code contains Holdout Lab code: $offenders. Only src/lab may record, label, show " +
                    "readouts or show tester switches (constitution X v2.1.0, spec 004 FR-010).",
            )
        }
    }
}

val checkMemberHasNoLabCode = tasks.register<CheckMemberHasNoLabCode>("checkMemberHasNoLabCode") {
    description = "Fails if code compiled into any member-app build records, labels or shows tester switches (constitution X, spec 004)."
    memberSources.from(
        listOf("main", "member", "debug", "release", "memberDebug", "memberRelease")
            .flatMap { set -> listOf("src/$set/kotlin", "src/$set/java") }
            .map { layout.projectDirectory.dir(it) },
    )
}
tasks.matching { it.name.startsWith("assembleMember") || it.name.startsWith("bundleMember") }.configureEach { dependsOn(checkMemberHasNoLabCode) }
