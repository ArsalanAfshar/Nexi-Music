package com.nexi.music.diagnostics

import android.content.Context
import android.os.Process
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

/** Small, local-only crash log. Never uploads logs; sharing is an explicit user action. */
object CrashLogStore {
    private const val MAX_LOG_BYTES = 192 * 1024
    private const val MAX_ENTRY_BYTES = 48 * 1024
    private val installed = AtomicBoolean(false)
    private val lock = Any()
    @Volatile private var appContext: Context? = null
    private val file: File get() = File(requireNotNull(appContext).filesDir, "diagnostics/crash-log.txt")

    fun install(context: Context) {
        appContext = context.applicationContext
        if (!installed.compareAndSet(false, true)) return
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            record("Uncaught exception on ${thread.name}", error)
            if (previous != null) previous.uncaughtException(thread, error) else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }

    fun record(context: Context, message: String, error: Throwable? = null) {
        appContext = context.applicationContext
        record(message, error)
    }

    fun read(context: Context): String {
        appContext = context.applicationContext
        return synchronized(lock) {
            runCatching {
                file.takeIf(File::isFile)?.inputStream()?.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    var remaining = MAX_LOG_BYTES
                    while (remaining > 0) {
                        val count = input.read(buffer, 0, minOf(buffer.size, remaining))
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        remaining -= count
                    }
                    output.toByteArray().toString(Charsets.UTF_8)
                }.orEmpty()
            }.getOrDefault("")
        }
    }

    fun clear(context: Context) {
        appContext = context.applicationContext
        synchronized(lock) { runCatching { file.delete() } }
    }

    internal fun boundedAppend(existing: ByteArray, entry: ByteArray, limit: Int): ByteArray {
        require(limit >= 0) { "Log byte limit cannot be negative" }
        val keptEntry = entry.takeLast(limit).toByteArray()
        val keepExisting = (limit - keptEntry.size).coerceAtLeast(0)
        return existing.takeLast(keepExisting).toByteArray() + keptEntry
    }

    private fun record(message: String, error: Throwable?) {
        val context = appContext ?: return
        synchronized(lock) {
            runCatching {
                val directory = File(context.filesDir, "diagnostics").apply { mkdirs() }
                val trace = if (error == null) "" else StringWriter().also { writer -> error.printStackTrace(PrintWriter(writer)) }.toString()
                val entry = "\n--- ${System.currentTimeMillis()} ---\n$message\n$trace".toByteArray(Charsets.UTF_8)
                    .takeLast(MAX_ENTRY_BYTES).toByteArray()
                val existing = file.takeIf(File::isFile)?.readBytes().orEmpty()
                val combined = boundedAppend(existing, entry, MAX_LOG_BYTES)
                val temporary = File(directory, "crash-log.tmp")
                temporary.writeBytes(combined)
                if (!temporary.renameTo(file)) {
                    file.writeBytes(combined)
                    temporary.delete()
                }
            }
        }
    }
}
