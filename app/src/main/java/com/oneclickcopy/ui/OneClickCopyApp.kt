package com.oneclickcopy.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oneclickcopy.AppContainer
import com.oneclickcopy.ui.editor.EditorScreen
import com.oneclickcopy.ui.editor.EditorViewModel
import com.oneclickcopy.ui.home.HomeScreen
import com.oneclickcopy.ui.home.HomeViewModel

@Composable
fun OneClickCopyApp(
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    val navigator = rememberSaveable(saver = AppNavigator.Saver) { AppNavigator() }
    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(
            repository = container.documentRepository,
            syncManager = container.syncManager,
            gitHubBackup = container.gitHubBackup,
            documentTransfer = container.documentTransfer,
        ),
    )
    // Holds the open editor's ViewModel. Lives in the activity's store, so the
    // editor survives rotation; cleared when the user leaves the editor.
    val editorStore: EditorStore = viewModel()

    BackHandler(enabled = navigator.editor != null) { navigator.back() }

    AnimatedContent(
        targetState = navigator.editor,
        transitionSpec = { fadeIn(tween(FADE_MS)) togetherWith fadeOut(tween(FADE_MS)) },
        modifier = modifier,
        label = "screen",
    ) { route ->
        if (route == null) {
            HomeScreen(
                viewModel = homeViewModel,
                onDocumentClick = { id -> navigator.openEditor(id, isNew = false) },
                onCreateDocument = { id -> navigator.openEditor(id, isNew = true) },
            )
        } else {
            val owner = remember(route) { editorStore.ownerFor(route) }
            DisposableEffect(owner) {
                onDispose {
                    // Only a real exit clears the editor; on rotation the route
                    // is unchanged and the same owner is handed back.
                    if (navigator.editor != route) editorStore.release(owner)
                }
            }
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                val editorViewModel: EditorViewModel = viewModel(
                    factory = EditorViewModel.factory(
                        container.documentRepository,
                        route.documentId,
                        route.isNew,
                    ),
                )
                EditorScreen(
                    viewModel = editorViewModel,
                    onNavigateBack = { navigator.back() },
                )
            }
        }
    }
}

/**
 * Activity-scoped holder for the editor's ViewModelStore: the job Navigation
 * Compose's back-stack entries used to do. One store per opened document.
 */
class EditorStore : ViewModel() {

    private val owners = HashMap<EditorRoute, ViewModelStoreOwner>()

    fun ownerFor(route: EditorRoute): ViewModelStoreOwner =
        owners.getOrPut(route) {
            object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        }

    fun release(owner: ViewModelStoreOwner) {
        owners.entries.removeAll { it.value === owner }
        owner.viewModelStore.clear()
    }

    override fun onCleared() {
        owners.values.forEach { it.viewModelStore.clear() }
        owners.clear()
    }
}

private const val FADE_MS = 220
