package ouija.app

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ouija.app.core.effects.EffectManager
import ouija.app.core.realtime.RealtimeManager
import ouija.app.core.utils.CodeGenerator
import ouija.app.ui.client.ClientScreen
import ouija.app.ui.server.ServerScreen
import ouija.app.ui.splash.SplashScreen
import ouija.app.ui.theme.OuijaTheme

class MainActivity : ComponentActivity() {

    private val effectManager by lazy { (application as OuijaApp).effectManager }
    private val realtimeManager by lazy { (application as OuijaApp).realtimeManager }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureFullScreen()

        setContent {
            OuijaTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    val context = androidx.compose.ui.platform.LocalContext.current
                    var roomCode by remember { mutableStateOf(CodeGenerator.getOrGenerateRoomCode(context)) }

                    NavHost(
                        navController = navController,
                        startDestination = "splash"
                    ) {
                        composable("splash") {
                            SplashScreen(
                                onAccept = {
                                    navController.navigate("client_mode") {
                                        popUpTo("splash") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("client_mode") {
                            ClientScreen(
                                roomCode = roomCode,
                                realtimeManager = realtimeManager,
                                effectManager = effectManager,
                                onRegenerateCode = { roomCode = CodeGenerator.regenerateRoomCode(context) },
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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    private fun configureFullScreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()
    }

    private fun hideSystemBars() {
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

