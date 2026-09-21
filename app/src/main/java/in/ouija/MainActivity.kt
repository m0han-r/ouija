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
import ouija.app.core.utils.CodeGenerator
import ouija.app.ui.client.ClientScreen
import ouija.app.ui.server.ServerScreen
import ouija.app.ui.theme.OuijaTheme

class MainActivity : ComponentActivity() {

    private val effectManager by lazy { (application as OuijaApp).effectManager }
    private val realtimeManager by lazy { (application as OuijaApp).realtimeManager }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            OuijaTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    var roomCode by remember { mutableStateOf(CodeGenerator.generateCode()) }

                    NavHost(
                        navController = navController,
                        startDestination = "client_mode"
                    ) {
                        composable("client_mode") {
                            ClientScreen(
                                roomCode = roomCode,
                                realtimeManager = realtimeManager,
                                effectManager = effectManager,
                                onRegenerateCode = { roomCode = CodeGenerator.generateCode() },
                                onNavigateToServer = { navController.navigate("server_mode") }
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
}
