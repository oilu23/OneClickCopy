package com.oneclickcopy.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/** Small key/value preferences persisted to a properties file. */
class Settings(private val file: Path) {

    private val props = Properties().apply {
        if (Files.exists(file)) runCatching { Files.newInputStream(file).use { load(it) } }
    }

    var lastSyncedAt: Long
        get() = long("lastSyncedAt") ?: 0L
        set(value) = put("lastSyncedAt", value.toString())

    var alwaysOnTop: Boolean
        get() = props.getProperty("alwaysOnTop") == "true"
        set(value) = put("alwaysOnTop", value.toString())

    var windowBounds: WindowBounds?
        get() {
            val parts = props.getProperty("window")?.split(',')?.mapNotNull { it.toIntOrNull() }
            return if (parts?.size == 4) WindowBounds(parts[0], parts[1], parts[2], parts[3]) else null
        }
        set(value) = put("window", value?.let { "${it.x},${it.y},${it.width},${it.height}" })

    private fun long(key: String) = props.getProperty(key)?.toLongOrNull()

    @Synchronized
    private fun put(key: String, value: String?) {
        if (value == null) props.remove(key) else props.setProperty(key, value)
        runCatching {
            Files.createDirectories(file.parent)
            Files.newOutputStream(file).use { props.store(it, null) }
        }
    }
}

data class WindowBounds(val x: Int, val y: Int, val width: Int, val height: Int)
