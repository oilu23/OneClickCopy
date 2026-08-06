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
    const val EDITOR = "editor/{documentId}"
    const val ARG_DOCUMENT_ID = "documentId"

    fun editor(documentId: Long) = "editor/$documentId"
}

@Composable
fun OneClickCopyNavHost(
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val repository = container.documentRepository

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
    ) {
        composable(Routes.HOME) {
            val homeViewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.factory(repository),
            )
            HomeScreen(
                viewModel = homeViewModel,
                onDocumentClick = { documentId ->
                    // Guard against duplicate destinations from rapid double taps.
                    if (navController.currentDestination?.route == Routes.HOME) {
                        navController.navigate(Routes.editor(documentId)) {
                            launchSingleTop = true
                        }
                    }
                },
            )
        }

        composable(
            route = Routes.EDITOR,
            arguments = listOf(
                navArgument(Routes.ARG_DOCUMENT_ID) { type = NavType.LongType },
            ),
        ) { backStackEntry ->
            val documentId = backStackEntry.arguments
                ?.getLong(Routes.ARG_DOCUMENT_ID)
                ?: return@composable

            val editorViewModel: EditorViewModel = viewModel(
                factory = EditorViewModel.factory(repository, documentId),
            )
            EditorScreen(
                viewModel = editorViewModel,
                onNavigateBack = {
                    editorViewModel.saveNow()
                    navController.popBackStack()
                },
            )
        }
    }
}
