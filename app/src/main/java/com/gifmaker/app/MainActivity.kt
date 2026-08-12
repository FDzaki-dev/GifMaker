package com.gifmaker.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel

private val BackgroundDark = Color(0xFF0A0A0D)
private val SurfaceDark = Color(0xFF16161C)
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

@Composable
private fun GifMakerRoot(viewModel: GifMakerViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()

    val videoPicker = rememberLauncherForVideoPicker { uri ->
        if (uri != null) viewModel.onIntent(GifMakerIntent.PickVideo(uri))
    }

    Scaffold(containerColor = BackgroundDark) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "GifMaker",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = OnSurfaceDark
            )
            Text(
                text = "Ubah video jadi GIF, 100% lokal di perangkat.",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceDark.copy(alpha = 0.7f)
            )

            VideoPickerCard(
                hasVideo = state.videoUri != null,
                onPickClick = { videoPicker() }
            )

            SettingsCard(
                fps = state.fps,
                outputWidth = state.outputWidth,
                onFpsChange = { viewModel.onIntent(GifMakerIntent.SetFps(it)) },
                onWidthChange = { viewModel.onIntent(GifMakerIntent.SetOutputWidth(it)) }
            )

            Button(
                onClick = { viewModel.onIntent(GifMakerIntent.GenerateGif) },
                enabled = state.videoUri != null && !state.isGenerating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary)
            ) {
                if (state.isGenerating) {
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

            state.errorMessage?.let { message ->
                ErrorBanner(message = message, onDismiss = { viewModel.onIntent(GifMakerIntent.DismissError) })
            }

            state.resultFile?.let { file ->
                ResultCard(sizeBytes = state.resultSizeBytes, path = file.absolutePath)
            }
        }
    }
}

@Composable
private fun VideoPickerCard(hasVideo: Boolean, onPickClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Movie,
                contentDescription = null,
                tint = BrandPrimary,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = if (hasVideo) "Video terpilih" else "Belum ada video dipilih",
                color = OnSurfaceDark
            )
            OutlinedButton(onClick = onPickClick) {
                Text("Pilih Video")
            }
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
            Text("FPS: $fps", color = OnSurfaceDark, fontWeight = FontWeight.SemiBold)
            Slider(
                value = fps.toFloat(),
                onValueChange = { onFpsChange(it.toInt()) },
                valueRange = 1f..24f,
                colors = SliderDefaults.colors(thumbColor = BrandPrimary, activeTrackColor = BrandPrimary)
            )
            Text("Lebar output: ${outputWidth}px", color = OnSurfaceDark, fontWeight = FontWeight.SemiBold)
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
private fun rememberLauncherForVideoPicker(onResult: (Uri?) -> Unit): () -> Unit {
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> onResult(uri) }
    return { launcher.launch("video/*") }
}


