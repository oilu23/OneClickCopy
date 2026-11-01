package com.oneclickcopy.desktop

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.concurrent.thread

/**
 * Keeps a single copy of the app running.
 *
 * Two processes rewriting the same documents file would silently drop each
 * other's edits, so a second launch hands off to the first one (which brings
 * its window forward) and exits.
 */
class SingleInstance private constructor(
    private val lock: FileLock,
    private val server: ServerSocket,
) {
    /** Calls [onActivate] whenever another launch asks this instance to show itself. */
    fun listen(onActivate: () -> Unit) {
        thread(isDaemon = true, name = "single-instance") {
            while (!server.isClosed) {
                try {
                    server.accept().use { onActivate() }
                } catch (e: IOException) {
                    if (server.isClosed) break
                }
            }
        }
    }

    companion object {
        /** Returns the guard if this is the first instance, or null after activating the existing one. */
        fun acquire(dataDir: Path): SingleInstance? {
            Files.createDirectories(dataDir)
            val portFile = dataDir.resolve("instance.port")
            val channel = FileChannel.open(
                dataDir.resolve("instance.lock"),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
            )
            val lock = runCatching { channel.tryLock() }.getOrNull()
            if (lock == null) {
                channel.close()
                runCatching {
                    val port = Files.readString(portFile).trim().toInt()
                    Socket(InetAddress.getLoopbackAddress(), port).close()
                }
                return null
            }
            val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
            Files.writeString(portFile, server.localPort.toString())
            return SingleInstance(lock, server)
        }
    }
}
