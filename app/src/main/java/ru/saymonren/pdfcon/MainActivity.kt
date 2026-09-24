package ru.saymonren.pdfcon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ru.saymonren.pdfcon.model.ThemeMode
import ru.saymonren.pdfcon.ui.PdfViewModel
import ru.saymonren.pdfcon.ui.screens.BuildScreen
import ru.saymonren.pdfcon.ui.screens.EditorScreen
import ru.saymonren.pdfcon.ui.screens.HomeScreen
import ru.saymonren.pdfcon.ui.screens.PreviewScreen
import ru.saymonren.pdfcon.ui.theme.PDFconTheme

class MainActivity : ComponentActivity() {

    private val vm: PdfViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val theme by vm.theme.collectAsState()
            PDFconTheme(
                darkTheme = when (theme) {
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                }
            ) {
                AppNav(vm)
            }
        }
    }
}

@Composable
private fun AppNav(vm: PdfViewModel) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                vm = vm,
                onEdit = { id -> nav.navigate("editor/$id") },
                onBuild = { nav.navigate("build") }
            )
        }
        composable(
            "editor/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { backStack ->
            EditorScreen(
                vm = vm,
                pageId = backStack.arguments?.getString("id").orEmpty(),
                onBack = { nav.popBackStack() }
            )
        }
        composable("build") {
            BuildScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onPreview = { nav.navigate("preview") }
            )
        }
        composable("preview") {
            PreviewScreen(vm = vm, onBack = { nav.popBackStack() })
        }
    }
}
