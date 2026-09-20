package ouija.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ouija.app.core.effects.EffectManager
import ouija.app.core.realtime.RealtimeManager
import ouija.app.core.realtime.SupabaseConfig
import ouija.app.core.utils.CodeGenerator
import ouija.app.ui.ModeSelectionScreen
import ouija.app.ui.client.ClientScreen
import ouija.app.ui.server.ServerScreen
import ouija.app.ui.theme.OuijaTheme

class MainActivity : ComponentActivity() {

    private lateinit var effectManager: EffectManager
    private lateinit var realtimeManager: RealtimeManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        effectManager = EffectManager(this)
        val supabaseClient = SupabaseConfig.createClient()
        realtimeManager = RealtimeManager(supabaseClient)

        setContent {
            OuijaTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    var roomCode by remember { mutableStateOf(CodeGenerator.generateCode()) }

                    NavHost(
                        navController = navController,
                        startDestination = "mode_selection"
                    ) {
                        composable("mode_selection") {
                            ModeSelectionScreen(
                                onSelectClient = { navController.navigate("client_mode") },
                                onSelectServer = { navController.navigate("server_mode") }
                            )
                        }

                        composable("client_mode") {
                            ClientScreen(
                                roomCode = roomCode,
                                realtimeManager = realtimeManager,
                                effectManager = effectManager,
                                onRegenerateCode = { roomCode = CodeGenerator.generateCode() },
                                onExit = { navController.popBackStack() }
                            )
                        }

                        composable("server_mode") {
                            ServerScreen(
                                realtimeManager = realtimeManager,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        effectManager.release()
        realtimeManager.disconnect()
    }
}
