import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.serialization") version "2.4.21"
    kotlin("plugin.compose") version "2.4.21"
    id("org.jetbrains.compose") version "1.12.2"
}

val composeVersion = "1.12.2"

group = "com.oneclickcopy"
version = "2.3.0"

// Platform-independent code shared verbatim with the Android app: snippet
// parsing, copied-state encoding, the repository, the backup format and the
// GitHub gist transport. Copied into a generated source dir rather than added
// as a raw srcDir, because the same Android packages also hold Context-bound
// files that cannot compile here. The desktop build supplies its own
// GitHubTokenStore in their place.
val sharedSources = tasks.register<Sync>("sharedSources") {
    from("../app/src/main/java") {
        include(
            "com/oneclickcopy/domain/**",
            "com/oneclickcopy/data/DocumentDao.kt",
            "com/oneclickcopy/data/DocumentEntity.kt",
            "com/oneclickcopy/data/DocumentRepository.kt",
            "com/oneclickcopy/data/JsonFileDocumentDao.kt",
            "com/oneclickcopy/backup/BackupPayload.kt",
            "com/oneclickcopy/backup/BackupTransport.kt",
            "com/oneclickcopy/backup/github/GitHubBackup.kt",
            "com/oneclickcopy/backup/github/GitHubDeviceFlow.kt",
            "com/oneclickcopy/backup/github/GitHubGistClient.kt",
            "com/oneclickcopy/sync/SyncState.kt",
            "com/oneclickcopy/ui/icons/AppIcons.kt",
        )
    }
    into(layout.buildDirectory.dir("generated/shared/kotlin"))
}

// The pure-JVM domain tests from the Android app run here as well.
val sharedTests = tasks.register<Sync>("sharedTests") {
    from("../app/src/test/java") {
        include(
            "com/oneclickcopy/domain/**",
            "com/oneclickcopy/backup/BackupPayloadTest.kt",
            "com/oneclickcopy/data/JsonFileDocumentDaoTest.kt",
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
            "windows" -> "org.jetbrains.compose.desktop:desktop-jvm-windows-x64:$composeVersion"
            "linux" -> "org.jetbrains.compose.desktop:desktop-jvm-linux-x64:$composeVersion"
            null -> compose.desktop.currentOs
            else -> error("Unknown target: $target")
        }
    )
    implementation("org.jetbrains.compose.material3:material3:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("sh.calvin.reorderable:reorderable:3.1.0")
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
            packageVersion = "2.3.0"
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
