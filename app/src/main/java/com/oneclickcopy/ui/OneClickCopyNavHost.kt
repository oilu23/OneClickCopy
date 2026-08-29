package com.oneclickcopy.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.oneclickcopy.AppContainer
import com.oneclickcopy.ui.editor.EditorScreen
import com.oneclickcopy.ui.editor.EditorViewModel
import com.oneclickcopy.ui.home.HomeScreen
import com.oneclickcopy.ui.home.HomeViewModel

private object Routes {
    const val HOME = "home"
    const val EDITOR = "editor/{documentId}?new={new}"
    const val ARG_DOCUMENT_ID = "documentId"
    const val ARG_IS_NEW = "new"

    fun editor(documentId: Long, isNew: Boolean) =
        "editor/$documentId?new=$isNew"
}

@Composable
fun OneClickCopyNavHost(
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val repository = container.documentRepository

    val navigateToEditor: (Long, Boolean) -> Unit = { documentId, isNew ->
        // Guard against duplicate destinations from rapid double taps.
        if (navController.currentDestination?.route == Routes.HOME) {
            navController.navigate(Routes.editor(documentId, isNew)) {
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
    ) {
        composable(Routes.HOME) {
            val homeViewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.factory(
                    repository = repository,
                    syncManager = container.syncManager,
                    driveBackupManager = container.driveBackupManager,
                    documentTransfer = container.documentTransfer,
                ),
            )
            HomeScreen(
                viewModel = homeViewModel,
                onDocumentClick = { documentId ->
                    navigateToEditor(documentId, false)
                },
                onCreateDocument = { documentId ->
                    navigateToEditor(documentId, true)
                },
            )
        }

        composable(
            route = Routes.EDITOR,
            arguments = listOf(
                navArgument(Routes.ARG_DOCUMENT_ID) { type = NavType.LongType },
                navArgument(Routes.ARG_IS_NEW) {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { backStackEntry ->
            val documentId = backStackEntry.arguments
                ?.getLong(Routes.ARG_DOCUMENT_ID)
                ?: return@composable
            val isNew = backStackEntry.arguments
                ?.getBoolean(Routes.ARG_IS_NEW)
                ?: false

            val editorViewModel: EditorViewModel = viewModel(
                factory = EditorViewModel.factory(repository, documentId, isNew),
            )
            EditorScreen(
                viewModel = editorViewModel,
                onNavigateBack = {
                    // A newly opened destination may still be STARTED. Do not
                    // save or latch the editor as leaving unless Navigation
                    // actually accepts the pop; the user must be able to retry.
                    navController.popBackStackOnce(backStackEntry).also { popped ->
                        if (popped) editorViewModel.saveNow()
                    }
                },
            )
        }
    }
}
