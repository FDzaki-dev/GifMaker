package com.gifmaker.app

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

private val FpsOptions = listOf(24, 20, 15, 12, 10, 8)

private enum class SettingsTab { TRIM, FPS, WIDTH }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    state: GifMakerState,
    onBack: () -> Unit,
    onPickVideo: () -> Unit,
    onIntent: (GifMakerIntent) -> Unit
) {
    var selectedTab by remember { mutableStateOf(SettingsTab.TRIM) }

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            TopAppBar(
                title = { Text("Edit", fontWeight = FontWeight.Black, color = OnSurfaceDark) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali", tint = OnSurfaceDark)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onIntent(GifMakerIntent.GenerateGif) },
                        enabled = state.videoUri != null && !state.isGenerating
                    ) {
                        if (state.isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = BrandPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Buat GIF",
                                tint = if (state.videoUri != null) BrandPrimary else OnSurfaceDark.copy(alpha = 0.3f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundDark,
                    titleContentColor = OnSurfaceDark
                )
            )
        },
        bottomBar = {
            BottomToolBar(selected = selectedTab, onSelect = { selectedTab = it })
        }
    ) { padding ->
        // FIX: fillMaxWidth() (bukan fillMaxSize()) — kalau dipadu verticalScroll,
        // fillMaxSize memaksa Column setinggi viewport walau konten lebih pendek,
        // hasilnya gap kosong raksasa di bawah konten. fillMaxWidth membiarkan
        // tinggi Column mengikuti konten, baru scroll aktif kalau konten melebihi layar.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                VideoPreviewCard(
                    thumbnail = state.videoThumbnail,
                    hasVideo = state.videoUri != null,
                    isLoading = state.isLoadingVideoInfo,
                    onPickClick = onPickVideo
                )
            }

            Surface(
                color = SurfaceDark,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(vertical = 18.dp)) {
                    when (selectedTab) {
                        SettingsTab.TRIM -> TrimPanel(
                            hasVideo = state.videoUri != null,
                            durationMs = state.videoDurationMs,
                            startMs = state.startMs,
                            endMs = state.endMs,
                            frames = state.filmstripFrames,
                            onRangeChange = { start, end -> onIntent(GifMakerIntent.SetTrimRange(start, end)) }
                        )
                        SettingsTab.FPS -> FpsChipPanel(
                            selectedFps = state.fps,
                            onFpsChange = { onIntent(GifMakerIntent.SetFps(it)) }
                        )
                        SettingsTab.WIDTH -> WidthPanel(
                            outputWidth = state.outputWidth,
                            onWidthChange = { onIntent(GifMakerIntent.SetOutputWidth(it)) }
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(top = 16.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                state.errorMessage?.let { message ->
                    ErrorBanner(message = message, onDismiss = { onIntent(GifMakerIntent.DismissError) })
                }
                state.resultFile?.let { file ->
                    ResultCard(sizeBytes = state.resultSizeBytes, path = file.absolutePath)
                }
            }
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
                .height(220.dp)
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
                    Box(modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)) {
                        OutlinedButton(onClick = onPickClick) { Text("Ganti Video") }
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
                        Icon(Icons.Filled.Movie, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(40.dp))
                        Text(if (hasVideo) "Memuat video…" else "Belum ada video dipilih", color = OnSurfaceDark)
                        OutlinedButton(onClick = onPickClick) { Text("Pilih Video") }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrimPanel(
    hasVideo: Boolean,
    durationMs: Long,
    startMs: Long,
    endMs: Long,
    frames: List<Bitmap>,
    onRangeChange: (Long, Long) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.ContentCut, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(20.dp))
            Text("Potong Video", color = OnSurfaceDark, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            if (hasVideo && durationMs > 0L) {
                Text(
                    "${formatMs(startMs)} – ${formatMs(endMs)}",
                    color = OnSurfaceDark.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        if (hasVideo && durationMs > 0L) {
            FilmstripTrimmer(
                frames = frames,
                durationMs = durationMs,
                startMs = startMs,
                endMs = endMs,
                onRangeChange = onRangeChange
            )
        } else {
            Text("Pilih video dulu untuk mengatur potongan.", color = OnSurfaceDark.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * Filmstrip trim UI: strip thumbnail frame video sebagai pengganti seekbar polos,
 * dengan 2 handle simetris (kiri = start, kanan = end) yang bisa di-drag independen.
 */
@Composable
private fun FilmstripTrimmer(
    frames: List<Bitmap>,
    durationMs: Long,
    startMs: Long,
    endMs: Long,
    onRangeChange: (Long, Long) -> Unit
) {
    val density = LocalDensity.current
    val minGapMs = 200L
    val handleWidth = 16.dp

    // Selalu baca nilai terbaru di dalam gesture drag tanpa perlu relaunch pointerInput.
    val latestStart by rememberUpdatedState(startMs)
    val latestEnd by rememberUpdatedState(endMs)
    val latestOnRangeChange by rememberUpdatedState(onRangeChange)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(14.dp))
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val handleWidthPx = with(density) { handleWidth.toPx() }

        // Strip thumbnail frame video, tersusun horizontal mengisi lebar penuh.
        Row(modifier = Modifier.fillMaxSize().background(SurfaceRaised)) {
            frames.forEach { frame ->
                Image(
                    bitmap = frame.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        }

        val startFrac = (startMs.toFloat() / durationMs).coerceIn(0f, 1f)
        val endFrac = (endMs.toFloat() / durationMs).coerceIn(0f, 1f)
        val startX = startFrac * widthPx
        val endX = endFrac * widthPx

        // Overlay gelap di luar rentang terpilih (kiri & kanan).
        if (startX > 0f) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(0, 0) }
                    .width(with(density) { startX.toDp() })
                    .fillMaxHeight()
                    .background(Color.Black.copy(alpha = 0.6f))
            )
        }
        if (endX < widthPx) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(endX.toInt(), 0) }
                    .width(with(density) { (widthPx - endX).toDp() })
                    .fillMaxHeight()
                    .background(Color.Black.copy(alpha = 0.6f))
            )
        }

        // Bingkai rentang terpilih.
        Box(
            modifier = Modifier
                .offset { IntOffset(startX.toInt(), 0) }
                .width(with(density) { (endX - startX).coerceAtLeast(0f).toDp() })
                .fillMaxHeight()
                .border(2.dp, BrandPrimary, RectangleShape)
        )

        // Handle kiri (start) — simetris dengan handle kanan.
        TrimHandle(
            modifier = Modifier
                .offset { IntOffset((startX - handleWidthPx / 2).toInt(), 0) }
                .width(handleWidth)
                .fillMaxHeight()
                .pointerInput(durationMs) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val deltaMs = (dragAmount.x / widthPx * durationMs).toLong()
                        val newStart = (latestStart + deltaMs).coerceIn(0L, latestEnd - minGapMs)
                        latestOnRangeChange(newStart, latestEnd)
                    }
                }
        )

        // Handle kanan (end) — simetris dengan handle kiri.
        TrimHandle(
            modifier = Modifier
                .offset { IntOffset((endX - handleWidthPx / 2).toInt(), 0) }
                .width(handleWidth)
                .fillMaxHeight()
                .pointerInput(durationMs) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val deltaMs = (dragAmount.x / widthPx * durationMs).toLong()
                        val newEnd = (latestEnd + deltaMs).coerceIn(latestStart + minGapMs, durationMs)
                        latestOnRangeChange(latestStart, newEnd)
                    }
                }
        )
    }
}

/** Grip handle trim: bentuk & ukuran identik dipakai untuk sisi kiri maupun kanan (simetris). */
@Composable
private fun TrimHandle(modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(BrandPrimary),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.85f))
        )
    }
}

@Composable
private fun FpsChipPanel(selectedFps: Int, onFpsChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Filled.Speed, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(20.dp))
            Text("Kecepatan Animasi (FPS)", color = OnSurfaceDark, fontWeight = FontWeight.SemiBold)
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FpsOptions.forEach { value ->
                val isSelected = value == selectedFps
                FilterChip(
                    selected = isSelected,
                    onClick = { onFpsChange(value) },
                    label = { Text("$value FPS") },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = SurfaceRaised,
                        labelColor = OnSurfaceDark.copy(alpha = 0.7f),
                        selectedContainerColor = BrandPrimary.copy(alpha = 0.18f),
                        selectedLabelColor = BrandPrimary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = OnSurfaceDark.copy(alpha = 0.2f),
                        selectedBorderColor = BrandPrimary
                    )
                )
            }
        }
    }
}

@Composable
private fun WidthPanel(outputWidth: Int, onWidthChange: (Int) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.AspectRatio, contentDescription = null, tint = BrandPrimary, modifier = Modifier.size(20.dp))
            Text("Lebar Output", color = OnSurfaceDark, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("${outputWidth}px", color = OnSurfaceDark.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
        }
        Slider(
            value = outputWidth.toFloat(),
            onValueChange = { onWidthChange(it.toInt()) },
            valueRange = 120f..960f,
            colors = SliderDefaults.colors(thumbColor = BrandPrimary, activeTrackColor = BrandPrimary)
        )
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
private fun BottomToolBar(selected: SettingsTab, onSelect: (SettingsTab) -> Unit) {
    Surface(color = SurfaceRaised) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ToolbarTabItem(Icons.Filled.ContentCut, "Trim", selected == SettingsTab.TRIM) { onSelect(SettingsTab.TRIM) }
            ToolbarTabItem(Icons.Filled.Speed, "FPS", selected == SettingsTab.FPS) { onSelect(SettingsTab.FPS) }
            ToolbarTabItem(Icons.Filled.AspectRatio, "Lebar", selected == SettingsTab.WIDTH) { onSelect(SettingsTab.WIDTH) }
        }
    }
}

@Composable
private fun ToolbarTabItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val tint = if (selected) BrandPrimary else OnSurfaceDark.copy(alpha = 0.55f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 4.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = tint, style = MaterialTheme.typography.labelSmall)
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return String.format("%d:%02d", m, s)
}
