package ouija.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFD4AF37),
    secondary = Color(0xFFE6C280),
    background = Color(0xFF0F0B08),
    surface = Color(0xFF1E1510),
    error = Color(0xFFB71C1C)
)

@Composable
fun OuijaTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
