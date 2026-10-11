package de.clopcounter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF7B5733),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCBD),
    onPrimaryContainer = Color(0xFF2C1600),
    secondary = Color(0xFF725A42),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFEDCBE),
    onSecondaryContainer = Color(0xFF2A1707),
    tertiary = Color(0xFF58633A),
    tertiaryContainer = Color(0xFFDCE8B4),
    onTertiaryContainer = Color(0xFF161E01),
    background = Color(0xFFFFF8F5),
    surface = Color(0xFFFFF8F5),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFEFBD8F),
    onPrimary = Color(0xFF472A0B),
    primaryContainer = Color(0xFF61401E),
    onPrimaryContainer = Color(0xFFFFDCBD),
    secondary = Color(0xFFE1C1A4),
    onSecondary = Color(0xFF402C18),
    secondaryContainer = Color(0xFF594330),
    onSecondaryContainer = Color(0xFFFEDCBE),
    tertiary = Color(0xFFC0CC9A),
    tertiaryContainer = Color(0xFF414B25),
    onTertiaryContainer = Color(0xFFDCE8B4),
)

@Composable
fun ClopTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
