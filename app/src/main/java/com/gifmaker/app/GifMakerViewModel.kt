package com.gifmaker.app

import android.app.Application
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
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
                recycleThumbnail()
                _state.value = _state.value.copy(
                    videoUri = intent.uri,
                    videoThumbnail = null,
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

    /** Ambil durasi + thumbnail frame pertama secara async agar UI (preview + trim range) bisa dipakai. */
    private fun loadVideoInfo(uri: Uri) {
        viewModelScope.launch {
            val (duration, thumbnail) = withContext(Dispatchers.IO) {
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
                    durationMs to thumb
                } catch (e: Exception) {
                    0L to null
                } finally {
                    runCatching { retriever.release() }
                }
            }
            val defaultEnd = if (duration > 0L) minOf(3000L, duration) else 3000L
            _state.value = _state.value.copy(
                videoDurationMs = duration,
                videoThumbnail = thumbnail,
                isLoadingVideoInfo = false,
                startMs = 0L,
                endMs = defaultEnd
            )
        }
    }

    private fun recycleThumbnail() {
        val bmp = _state.value.videoThumbnail
        if (bmp != null && !bmp.isRecycled) bmp.recycle()
    }

    override fun onCleared() {
        super.onCleared()
        recycleThumbnail()
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
