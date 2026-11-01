package com.oneclickcopy.desktop

import java.nio.file.Path
import java.nio.file.Paths

/** Where the app keeps its data: %APPDATA%\OneClickCopy on Windows. */
object AppPaths {

    val dataDir: Path by lazy {
        System.getProperty("oneclickcopy.dataDir")?.let { return@lazy Paths.get(it) }
        val appData = System.getenv("APPDATA")
        if (!appData.isNullOrBlank()) {
            Paths.get(appData, APP_DIR)
        } else {
            val xdg = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
                ?: Paths.get(System.getProperty("user.home"), ".local", "share").toString()
            Paths.get(xdg, APP_DIR)
        }
    }

    val documents: Path get() = dataDir.resolve("documents.json")
    val githubSession: Path get() = dataDir.resolve("github.properties")
    val settings: Path get() = dataDir.resolve("settings.properties")

    private const val APP_DIR = "OneClickCopy"
}
