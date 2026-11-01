import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "1.9.24"
    kotlin("plugin.serialization") version "1.9.24"
    id("org.jetbrains.compose") version "1.6.11"
}

group = "com.oneclickcopy"
version = "2.2.0"

// Platform-independent code shared verbatim with the Android app: snippet
// parsing, copied-state encoding, the repository, the backup format and the
// GitHub gist transport. Copied into a generated source dir rather than added
// as a raw srcDir, because the same Android packages also hold Room- and
// Context-bound files that cannot compile here. The desktop build supplies its
// own DocumentEntity, DocumentDao and GitHubTokenStore in their place.
val sharedSources by tasks.registering(Sync::class) {
    from("../app/src/main/java") {
        include(
            "com/oneclickcopy/domain/**",
            "com/oneclickcopy/data/DocumentRepository.kt",
            "com/oneclickcopy/backup/BackupPayload.kt",
            "com/oneclickcopy/backup/BackupTransport.kt",
            "com/oneclickcopy/backup/github/GitHubBackup.kt",
            "com/oneclickcopy/backup/github/GitHubDeviceFlow.kt",
            "com/oneclickcopy/backup/github/GitHubGistClient.kt",
            "com/oneclickcopy/sync/SyncState.kt",
        )
    }
    into(layout.buildDirectory.dir("generated/shared/kotlin"))
}

// The pure-JVM domain tests from the Android app run here as well.
val sharedTests by tasks.registering(Sync::class) {
    from("../app/src/test/java") {
        include(
            "com/oneclickcopy/domain/**",
            "com/oneclickcopy/backup/BackupPayloadTest.kt",
        )
    }
    into(layout.buildDirectory.dir("generated/sharedTest/kotlin"))
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        // Same opt-ins as the Android app.
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
        )
    }
    sourceSets {
        main { kotlin.srcDir(sharedSources) }
        test { kotlin.srcDir(sharedTests) }
    }
}

dependencies {
    // Which native Skia binaries to bundle. Defaults to the build machine so
    // `run` works anywhere; pass -Ptarget=windows to produce the Windows jar.
    implementation(
        when (val target = findProperty("target")?.toString()) {
            "windows" -> compose.desktop.windows_x64
            "linux" -> compose.desktop.linux_x64
            null -> compose.desktop.currentOs
            else -> error("Unknown target: $target")
        }
    )
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("sh.calvin.reorderable:reorderable:2.1.1")
    // Windows DPAPI, used to encrypt the GitHub token at rest.
    implementation("net.java.dev.jna:jna-platform:5.14.0")

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.truth:truth:1.4.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

compose.desktop {
    application {
        mainClass = "com.oneclickcopy.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "OneClickCopy"
            packageVersion = "2.2.0"
            description = "One-tap text copying with per-item tracking"
            vendor = "OneClickCopy"
            windows {
                iconFile.set(project.file("packaging/OneClickCopy.ico"))
                menuGroup = "OneClickCopy"
                perUserInstall = true
                shortcut = true
                upgradeUuid = "6b0d4c4e-7f3a-4c55-9f0e-2d1b9a3c8e71"
            }
        }
    }
}
