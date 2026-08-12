package com.gifmaker.app

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

sealed class GifResult {
    data class Success(val file: File, val sizeBytes: Long) : GifResult()
    data class Failure(val message: String) : GifResult()
}

data class GifRequest(
    val videoUri: Uri,
    val startMs: Long,
    val endMs: Long,
    val fps: Int,
    val outputWidth: Int,
    val maxColors: Int = 256
)

/**
 * Mesin pembuat GIF 100% offline dan lokal: seluruh ekstraksi frame dan encoding berjalan
 * di perangkat memakai MediaMetadataRetriever + Bitmap bawaan Android, tanpa API jaringan
 * atau dependensi eksternal apa pun. Semua operasi dibungkus try-catch defensif supaya
 * kegagalan pada satu frame atau satu tahap tidak meng-crash UI thread.
 */
class GifEngine(private val context: Context) {

    suspend fun generate(request: GifRequest, cacheDir: File): GifResult = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, request.videoUri)

            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            if (durationMs <= 0L) {
                return@withContext GifResult.Failure("Tidak bisa membaca durasi video.")
            }

            val safeStart = request.startMs.coerceIn(0L, durationMs)
            val safeEnd = request.endMs.coerceIn(safeStart + 1, durationMs)
            val safeFps = request.fps.coerceIn(1, 30)
            val frameIntervalMs = (1000L / safeFps).coerceAtLeast(1L)

            val targetWidth = request.outputWidth.coerceIn(64, 1080)
            val pixelArrays = mutableListOf<IntArray>()
            var frameWidth = 0
            var frameHeight = 0

            var t = safeStart
            while (t < safeEnd) {
                val rawFrame = try {
                    retriever.getFrameAtTime(t * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
                } catch (e: Exception) {
                    null
                }
                if (rawFrame != null) {
                    // Frame mentah langsung di-scale lalu di-recycle di sini juga, supaya
                    // tidak pernah ada lebih dari satu bitmap resolusi asli hidup di memori
                    // secara bersamaan (mencegah OutOfMemoryError untuk video berdurasi/beresolusi besar).
                    val ratio = targetWidth.toFloat() / rawFrame.width.toFloat()
                    val targetHeight = (rawFrame.height * ratio).toInt().coerceAtLeast(1)
                    val scaledFrame = Bitmap.createScaledBitmap(rawFrame, targetWidth, targetHeight, true)
                    if (scaledFrame !== rawFrame) rawFrame.recycle()

                    frameWidth = scaledFrame.width
                    frameHeight = scaledFrame.height
                    val pixels = IntArray(frameWidth * frameHeight)
                    scaledFrame.getPixels(pixels, 0, frameWidth, 0, 0, frameWidth, frameHeight)
                    scaledFrame.recycle()
                    pixelArrays.add(pixels)
                }
                t += frameIntervalMs
            }

            if (pixelArrays.isEmpty()) {
                return@withContext GifResult.Failure("Tidak ada frame yang berhasil diambil dari video.")
            }

            val palette = ColorQuantizer.buildPalette(pixelArrays, request.maxColors)

            val outFile = File(cacheDir, "gif_${System.currentTimeMillis()}.gif")
            FileOutputStream(outFile).use { fos ->
                val encoder = GifEncoder(fos)
                encoder.start(frameWidth, frameHeight, palette)
                val delayCentiseconds = (frameIntervalMs / 10).toInt().coerceAtLeast(2)
                for (pixels in pixelArrays) {
                    val indices = ColorQuantizer.mapToIndices(pixels, palette)
                    encoder.writeFrame(indices, palette, delayCentiseconds)
                }
                encoder.finish()
            }

            GifResult.Success(outFile, outFile.length())
        } catch (e: Throwable) {
            // Throwable (bukan cuma Exception) supaya OutOfMemoryError dari video besar pun
            // berakhir jadi pesan error yang rapi, bukan crash mentah ke layar putih.
            GifResult.Failure(e.message ?: "Terjadi kesalahan tak terduga saat membuat GIF.")
        } finally {
            runCatching { retriever.release() }
        }
    }
}
