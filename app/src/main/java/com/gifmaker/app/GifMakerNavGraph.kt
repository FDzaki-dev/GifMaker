package com.gifmaker.app

import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

private const val ROUTE_HOME = "home"
private const val ROUTE_EDITOR = "editor"

@Composable
fun GifMakerNavGraph(viewModel: GifMakerViewModel = viewModel()) {
    val navController = rememberNavController()
    val state by viewModel.state.collectAsState()

    // Navigasi dipicu imperatif tepat saat video baru dipilih — bukan reactive
    // LaunchedEffect(state.videoUri), supaya tombol back di Editor tidak langsung
    // ke-redirect balik ke Editor lagi (videoUri masih non-null setelah pop).
    val videoPicker = rememberLauncherForVideoPicker { uri ->
        if (uri != null) {
            viewModel.onIntent(GifMakerIntent.PickVideo(uri))
            navController.navigate(ROUTE_EDITOR)
        }
    }

    NavHost(navController = navController, startDestination = ROUTE_HOME) {
        composable(ROUTE_HOME) {
            HomeScreen(
                state = state,
                onPickVideo = { videoPicker() },
                onContinueToEditor = { navController.navigate(ROUTE_EDITOR) }
            )
        }
        composable(ROUTE_EDITOR) {
            EditorScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onPickVideo = { videoPicker() },
                onIntent = { intent -> viewModel.onIntent(intent) }
            )
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
