package com.gifmaker.app

import android.app.Application
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** State immutable — satu-satunya sumber kebenaran UI, tidak pernah dimutasi, selalu diganti utuh via copy(). */
data class GifMakerState(
    val videoUri: Uri? = null,
    val videoDurationMs: Long = 0L,
    val videoThumbnail: Bitmap? = null,
    val filmstripFrames: List<Bitmap> = emptyList(),
    val isLoadingVideoInfo: Boolean = false,
    val fps: Int = 12,
    val outputWidth: Int = 480,
    val startMs: Long = 0L,
    val endMs: Long = 3000L,
    val isGenerating: Boolean = false,
    val resultFile: File? = null,
    val resultSizeBytes: Long = 0L,
    val errorMessage: String? = null
)

/** Semua aksi yang bisa dikirim UI ke ViewModel — satu arah, tidak ada mutasi langsung. */
sealed class GifMakerIntent {
    data class PickVideo(val uri: Uri) : GifMakerIntent()
    data class SetFps(val fps: Int) : GifMakerIntent()
    data class SetOutputWidth(val width: Int) : GifMakerIntent()
    data class SetTrimRange(val startMs: Long, val endMs: Long) : GifMakerIntent()
    object GenerateGif : GifMakerIntent()
    object DismissError : GifMakerIntent()
    object ResetResult : GifMakerIntent()
}

class GifMakerViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = GifEngine(application)

    private val _state = MutableStateFlow(GifMakerState())
    val state: StateFlow<GifMakerState> = _state.asStateFlow()

    fun onIntent(intent: GifMakerIntent) {
        when (intent) {
            is GifMakerIntent.PickVideo -> {
                recycleBitmaps()
                _state.value = _state.value.copy(
                    videoUri = intent.uri,
                    videoThumbnail = null,
                    filmstripFrames = emptyList(),
                    videoDurationMs = 0L,
                    isLoadingVideoInfo = true,
                    resultFile = null,
                    errorMessage = null
                )
                loadVideoInfo(intent.uri)
            }
            is GifMakerIntent.SetFps -> {
                _state.value = _state.value.copy(fps = intent.fps.coerceIn(1, 30))
            }
            is GifMakerIntent.SetOutputWidth -> {
                _state.value = _state.value.copy(outputWidth = intent.width.coerceIn(64, 1080))
            }
            is GifMakerIntent.SetTrimRange -> {
                _state.value = _state.value.copy(
                    startMs = intent.startMs.coerceAtLeast(0L),
                    endMs = intent.endMs.coerceAtLeast(intent.startMs + 100L)
                )
            }
            GifMakerIntent.GenerateGif -> generateGif()
            GifMakerIntent.DismissError -> {
                _state.value = _state.value.copy(errorMessage = null)
            }
            GifMakerIntent.ResetResult -> {
                _state.value = _state.value.copy(resultFile = null, resultSizeBytes = 0L)
            }
        }
    }

    /** Ambil durasi + thumbnail frame pertama + frame filmstrip (untuk UI trim) secara async, fail-safe. */
    private fun loadVideoInfo(uri: Uri) {
        viewModelScope.launch {
            val (duration, thumbnail, frames) = withContext(Dispatchers.IO) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(getApplication(), uri)
                    val durationMs = retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull() ?: 0L
                    val thumb = try {
                        retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST)
                    } catch (e: Exception) {
                        null
                    }
                    val filmstrip = try {
                        extractFilmstripFrames(retriever, durationMs)
                    } catch (e: Exception) {
                        emptyList()
                    }
                    Triple(durationMs, thumb, filmstrip)
                } catch (e: Exception) {
                    Triple(0L, null, emptyList())
                } finally {
                    runCatching { retriever.release() }
                }
            }
            val defaultEnd = if (duration > 0L) minOf(3000L, duration) else 3000L
            _state.value = _state.value.copy(
                videoDurationMs = duration,
                videoThumbnail = thumbnail,
                filmstripFrames = frames,
                isLoadingVideoInfo = false,
                startMs = 0L,
                endMs = defaultEnd
            )
        }
    }

    /** Ekstrak [count] frame terdistribusi merata sepanjang durasi video, untuk strip thumbnail trim UI. */
    private fun extractFilmstripFrames(
        retriever: MediaMetadataRetriever,
        durationMs: Long,
        count: Int = 10
    ): List<Bitmap> {
        if (durationMs <= 0L) return emptyList()
        val stepUs = (durationMs * 1000L) / count
        val targetPx = 120
        val frames = mutableListOf<Bitmap>()
        for (i in 0 until count) {
            val timeUs = (stepUs * i).coerceIn(0L, (durationMs * 1000L) - 1000L)
            val frame = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(
                        timeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        targetPx,
                        targetPx
                    )
                } else {
                    val full = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    full?.let { src ->
                        val h = (targetPx * src.height / src.width).coerceAtLeast(1)
                        val scaled = Bitmap.createScaledBitmap(src, targetPx, h, true)
                        if (scaled != src) src.recycle()
                        scaled
                    }
                }
            } catch (e: Exception) {
                null
            }
            if (frame != null) frames.add(frame)
        }
        return frames
    }

    private fun recycleBitmaps() {
        val state = _state.value
        state.videoThumbnail?.let { if (!it.isRecycled) it.recycle() }
        state.filmstripFrames.forEach { if (!it.isRecycled) it.recycle() }
    }

    override fun onCleared() {
        super.onCleared()
        recycleBitmaps()
    }

    private fun generateGif() {
        val current = _state.value
        val uri = current.videoUri
        if (uri == null) {
            _state.value = current.copy(errorMessage = "Pilih video terlebih dahulu.")
            return
        }
        if (current.isGenerating) return

        _state.value = current.copy(isGenerating = true, errorMessage = null)

        viewModelScope.launch {
            try {
                val request = GifRequest(
                    videoUri = uri,
                    startMs = current.startMs,
                    endMs = current.endMs,
                    fps = current.fps,
                    outputWidth = current.outputWidth
                )
                val cacheDir = getApplication<Application>().cacheDir
                when (val result = engine.generate(request, cacheDir)) {
                    is GifResult.Success -> {
                        _state.value = _state.value.copy(
                            isGenerating = false,
                            resultFile = result.file,
                            resultSizeBytes = result.sizeBytes,
                            errorMessage = null
                        )
                    }
                    is GifResult.Failure -> {
                        _state.value = _state.value.copy(
                            isGenerating = false,
                            errorMessage = result.message
                        )
                    }
                }
            } catch (e: Throwable) {
                // Lapis pertahanan terakhir: apa pun yang lolos dari GifEngine (termasuk
                // OutOfMemoryError) tidak boleh menjatuhkan proses aplikasi.
                _state.value = _state.value.copy(
                    isGenerating = false,
                    errorMessage = e.message ?: "Gagal membuat GIF."
                )
            }
        }
    }
}
