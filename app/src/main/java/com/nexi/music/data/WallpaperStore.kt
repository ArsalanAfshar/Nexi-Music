package com.nexi.music.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
/** Bounded image handling for user-selected and imported wallpaper files. */
object WallpaperStore {
    const val MAX_SOURCE_BYTES = 20 * 1024 * 1024
    private const val MAX_DIMENSION = 30_000
    private const val MAX_PIXELS = 500_000_000L
    private const val MAX_DECODED_PIXELS = 16_000_000L
    private const val MAX_SAVED_BYTES = 12 * 1024 * 1024

    fun readUri(context: Context, uri: Uri): ByteArray = context.contentResolver.openInputStream(uri)?.use(::readBounded)
        ?: error("Android could not open the selected image")

    fun readFile(file: File): ByteArray {
        require(file.isFile) { "Wallpaper file is missing" }
        require(file.length() <= MAX_SAVED_BYTES) { "Wallpaper file is too large to export" }
        return file.inputStream().use(::readBounded)
    }

    fun save(context: Context, source: ByteArray): File {
        val jpeg = normalizeJpeg(source)
        val directory = File(context.filesDir, "wallpapers").apply { mkdirs() }
        val destination = File(directory, "wallpaper-${System.currentTimeMillis()}-${System.nanoTime()}.jpg")
        val temporary = File(directory, destination.name + ".tmp")
        temporary.writeBytes(jpeg)
        if (!temporary.renameTo(destination)) {
            temporary.delete()
            error("Processed wallpaper could not be saved")
        }
        return destination
    }

    fun normalizeJpeg(source: ByteArray): ByteArray {
        require(source.isNotEmpty() && source.size <= MAX_SOURCE_BYTES) { "Wallpaper must be smaller than 20 MiB" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
        require(bounds.outWidth in 1..MAX_DIMENSION && bounds.outHeight in 1..MAX_DIMENSION) { "Unsupported or malformed wallpaper image" }
        require(bounds.outWidth.toLong() * bounds.outHeight.toLong() <= MAX_PIXELS) { "Wallpaper dimensions are too large" }
        var sample = 1
        while (bounds.outWidth / sample > 4096 || bounds.outHeight / sample > 4096 ||
            (bounds.outWidth.toLong() / sample) * (bounds.outHeight.toLong() / sample) > MAX_DECODED_PIXELS
        ) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(source, 0, source.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("Wallpaper image could not be decoded")
        return try {
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, output)) { "Wallpaper could not be encoded" }
                val bytes = output.toByteArray()
                require(bytes.size <= MAX_SAVED_BYTES) { "Processed wallpaper exceeds 12 MiB" }
                bytes
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun readBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) {
                val single = input.read()
                if (single < 0) break
                total += 1
                require(total <= MAX_SOURCE_BYTES) { "Image file exceeds the 20 MiB limit" }
                output.write(single)
            } else {
                total += count
                require(total <= MAX_SOURCE_BYTES) { "Image file exceeds the 20 MiB limit" }
                output.write(buffer, 0, count)
            }
        }
        return output.toByteArray()
    }
}
