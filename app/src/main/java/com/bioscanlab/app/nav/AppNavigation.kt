package com.bioscanlab.app.nav

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bioscanlab.app.camera.CameraScreen
import com.bioscanlab.app.chat.ChatScreen
import com.bioscanlab.app.sheet.EquipmentSheetScreen

object Routes {
    const val ARG_EQUIPMENT = "equipment"

    const val CAMERA = "camera"
    const val SHEET = "sheet/{$ARG_EQUIPMENT}"
    const val CHAT = "chat/{$ARG_EQUIPMENT}"

    /**
     * Los nombres de clase traen espacios y guiones (p.ej. "Cabina de flujo
     * laminar horizontal -PIVAS- ..."), asi que hay que encodearlos antes de
     * meterlos en la ruta o la navegacion falla.
     */
    fun sheetFor(equipmentName: String): String = "sheet/${Uri.encode(equipmentName)}"

    fun chatFor(equipmentName: String): String = "chat/${Uri.encode(equipmentName)}"
}

/**
 * Flujo exigido por la rubrica:
 *   camara (caja + nombre + confianza + boton)
 *     -> ficha tecnica del equipo
 *       -> chat con el asistente
 */
@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.CAMERA) {
        composable(Routes.CAMERA) {
            CameraScreen(
                onViewSheet = { className ->
                    navController.navigate(Routes.sheetFor(className))
                },
                onAskAi = { className ->
                    navController.navigate(Routes.chatFor(className))
                },
            )
        }

        composable(
            route = Routes.SHEET,
            arguments = listOf(navArgument(Routes.ARG_EQUIPMENT) { type = NavType.StringType }),
        ) {
            EquipmentSheetScreen(
                onBack = { navController.popBackStack() },
                onOpenChat = { className -> navController.navigate(Routes.chatFor(className)) },
            )
        }

        composable(
            route = Routes.CHAT,
            arguments = listOf(navArgument(Routes.ARG_EQUIPMENT) { type = NavType.StringType }),
        ) {
            // El nombre de la clase llega al ChatViewModel via SavedStateHandle.
            ChatScreen(onBack = { navController.popBackStack() })
        }
    }
}
