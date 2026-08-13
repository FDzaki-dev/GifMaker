package com.gifmaker.app

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel

private val BackgroundDark = Color(0xFF0A0A0D)
private val SurfaceDark = Color(0xFF16161C)
private val SurfaceRaised = Color(0xFF1E1E26)
private val BrandPrimary = Color(0xFF7B5CFA)
private val OnSurfaceDark = Color(0xFFF2F2F5)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setContent {
            GifMakerTheme {
                GifMakerRoot()
            }
        }
    }
}

@Composable
private fun GifMakerTheme(content: @Composable () -> Unit) {
    val colorScheme = darkColorScheme(
        primary = BrandPrimary,
        onPrimary = Color.White,
        background = BackgroundDark,
        surface = SurfaceDark,
        onSurface = OnSurfaceDark,
        onBackground = OnSurfaceDark
    )
    MaterialTheme(colorScheme = colorScheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GifMakerRoot(viewModel: GifMakerViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()

    val videoPicker = rememberLauncherForVideoPicker { uri ->
        if (uri != null) viewModel.onIntent(GifMakerIntent.PickVideo(uri))
    }

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("GifMaker", fontWeight = FontWeight.Black, color = OnSurfaceDark)
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = BackgroundDark,
                    titleContentColor = OnSurfaceDark
                )
            )
        },
        bottomBar = {
            BottomActionBar(
                enabled = state.videoUri != null && !state.isGenerating,
                isGenerating = state.isGenerating,
                onClick = { viewModel.onIntent(GifMakerIntent.GenerateGif) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            Text(
                text = "Ubah video jadi GIF, 100% lokal di perangkat.",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceDark.copy(alpha = 0.7f)
            )

            VideoPreviewCard(
                thumbnail = state.videoThumbnail,
                hasVideo = state.videoUri != null,
                isLoading = state.isLoadingVideoInfo,
                onPickClick = { videoPicker() }
            )

            if (state.videoUri != null && state.videoDurationMs > 0L) {
                TrimCard(
                    durationMs = state.videoDurationMs,
                    startMs = state.startMs,
                    endMs = state.endMs,
                    onRangeChange = { start, end ->
                        viewModel.onIntent(GifMakerIntent.SetTrimRange(start, end))
                    }
                )
            }

            SettingsCard(
                fps = state.fps,
                outputWidth = state.outputWidth,
                onFpsChange = { viewModel.onIntent(GifMakerIntent.SetFps(it)) },
                onWidthChange = { viewModel.onIntent(GifMakerIntent.SetOutputWidth(it)) }
            )

            state.errorMessage?.let { message ->
                ErrorBanner(message = message, onDismiss = { viewModel.onIntent(GifMakerIntent.DismissError) })
            }

            state.resultFile?.let { file ->
                ResultCard(sizeBytes = state.resultSizeBytes, path = file.absolutePath)
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun VideoPreviewCard(
    thumbnail: Bitmap?,
    hasVideo: Boolean,
    isLoading: Boolean,
    onPickClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            when {
                thumbnail != null -> {
                    Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = "Preview video",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                    ) {
                        OutlinedButton(onClick = onPickClick) {
                            Text("Ganti Video")
                        }
                    }
                }
                isLoading -> {
                    CircularProgressIndicator(color = BrandPrimary)
                }
                else -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Movie,
                            contentDescription = null,
                            tint = BrandPrimary,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(if (hasVideo) "Memuat video…" else "Belum ada video dipilih", color = OnSurfaceDark)
                        OutlinedButton(onClick = onPickClick) {
                            Text("Pilih Video")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrimCard(
    durationMs: Long,
    startMs: Long,
    endMs: Long,
    onRangeChange: (Long, Long) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.ContentCut, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(20.dp))
                Text("Potong Video", color = OnSurfaceDark, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(
                    "${formatMs(startMs)} – ${formatMs(endMs)}",
                    color = OnSurfaceDark.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            RangeSlider(
                value = startMs.toFloat()..endMs.toFloat(),
                onValueChange = { range ->
                    onRangeChange(range.start.toLong(), range.endInclusive.toLong())
                },
                valueRange = 0f..durationMs.toFloat(),
                colors = SliderDefaults.colors(thumbColor = BrandPrimary, activeTrackColor = BrandPrimary)
            )
        }
    }
}

@Composable
private fun SettingsCard(
    fps: Int,
    outputWidth: Int,
    onFpsChange: (Int) -> Unit,
    onWidthChange: (Int) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.Speed, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(20.dp))
                Text("FPS: $fps", color = OnSurfaceDark, fontWeight = FontWeight.SemiBold)
            }
            Slider(
                value = fps.toFloat(),
                onValueChange = { onFpsChange(it.toInt()) },
                valueRange = 1f..24f,
                colors = SliderDefaults.colors(thumbColor = BrandPrimary, activeTrackColor = BrandPrimary)
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.AspectRatio, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(20.dp))
                Text("Lebar output: ${outputWidth}px", color = OnSurfaceDark, fontWeight = FontWeight.SemiBold)
            }
            Slider(
                value = outputWidth.toFloat(),
                onValueChange = { onWidthChange(it.toInt()) },
                valueRange = 120f..960f,
                colors = SliderDefaults.colors(thumbColor = BrandPrimary, activeTrackColor = BrandPrimary)
            )
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF3A1414)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(message, color = Color(0xFFFFB4A9), modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("Tutup", color = Color(0xFFFFB4A9)) }
        }
    }
}

@Composable
private fun ResultCard(sizeBytes: Long, path: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("GIF berhasil dibuat", color = OnSurfaceDark, fontWeight = FontWeight.Bold)
            Text("Ukuran: ${sizeBytes / 1024} KB", color = OnSurfaceDark.copy(alpha = 0.7f))
            Text(path, color = OnSurfaceDark.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun BottomActionBar(enabled: Boolean, isGenerating: Boolean, onClick: () -> Unit) {
    Surface(color = SurfaceRaised) {
        Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Membuat GIF…")
                } else {
                    Text("Buat GIF", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun rememberLauncherForVideoPicker(onResult: (Uri?) -> Unit): () -> Unit {
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> onResult(uri) }
    return { launcher.launch("video/*") }
}

private fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return String.format("%d:%02d", m, s)
}
