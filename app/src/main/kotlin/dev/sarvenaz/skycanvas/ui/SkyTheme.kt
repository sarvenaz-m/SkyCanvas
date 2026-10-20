package dev.sarvenaz.skycanvas.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.sp
import dev.sarvenaz.skycanvas.R

internal val Navy = Color(0xFF183754)
internal val Blue = Color(0xFF2C5C7D)
internal val Muted = Color(0xFF52697C)
internal val Paper = Color(0xFFF5F7FA)
internal val Border = Color(0xFFD9E2EB)
internal val Ochre = Color(0xFF9A6123)
private val family =
    FontFamily(
        Font(R.font.manrope_regular, FontWeight.Normal),
        Font(R.font.manrope_medium, FontWeight.Medium),
        Font(R.font.manrope_semibold, FontWeight.SemiBold),
        Font(R.font.manrope_bold, FontWeight.Bold),
    )

@Composable
fun SkyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme =
            lightColorScheme(
                primary = Blue,
                onPrimary = Color.White,
                primaryContainer = Color(0xFFE0EDF5),
                onPrimaryContainer = Navy,
                secondary = Ochre,
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFF8ECD9),
                onSecondaryContainer = Color(0xFF684019),
                tertiary = Color(0xFF59795E),
                tertiaryContainer = Color(0xFFE2EDE2),
                onTertiaryContainer = Navy,
                background = Paper,
                onBackground = Navy,
                surface = Color.White,
                onSurface = Navy,
                surfaceVariant = Color(0xFFEAF0F5),
                onSurfaceVariant = Muted,
                outline = Color(0xFF74899B),
                outlineVariant = Border,
                surfaceTint = Blue,
                error = Color(0xFF9E3440),
                onError = Color.White,
            ),
        typography =
            Typography(
                headlineLarge =
                    TextStyle(
                        fontFamily = family,
                        fontWeight = FontWeight.Bold,
                        fontSize = 30.sp,
                        lineHeight = 37.sp,
                        letterSpacing = (-.8).sp,
                    ),
                headlineMedium =
                    TextStyle(
                        fontFamily = family,
                        fontWeight = FontWeight.Bold,
                        fontSize = 25.sp,
                        lineHeight = 32.sp,
                    ),
                titleLarge =
                    TextStyle(
                        fontFamily = family,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        lineHeight = 27.sp,
                    ),
                titleMedium =
                    TextStyle(
                        fontFamily = family,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        lineHeight = 23.sp,
                    ),
                titleSmall =
                    TextStyle(
                        fontFamily = family,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                    ),
                bodyLarge =
                    TextStyle(
                        fontFamily = family,
                        fontWeight = FontWeight.Normal,
                        fontSize = 16.sp,
                        lineHeight = 25.sp,
                    ),
                bodyMedium =
                    TextStyle(
                        fontFamily = family,
                        fontWeight = FontWeight.Normal,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                    ),
                bodySmall =
                    TextStyle(
                        fontFamily = family,
                        fontWeight = FontWeight.Normal,
                        fontSize = 12.sp,
                        lineHeight = 19.sp,
                    ),
                labelLarge =
                    TextStyle(
                        fontFamily = family,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    ),
                labelMedium =
                    TextStyle(
                        fontFamily = family,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    ),
                labelSmall =
                    TextStyle(
                        fontFamily = family,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    ),
            ),
        content = content,
    )
}
