package com.rafsan.ottobudget.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rafsan.ottobudget.R

val OttoTurquoise = Color(0xFF6BD0C2)
val OttoTurquoiseDeep = Color(0xFF45B3A4)
val OttoTurquoiseSoftDark = Color(0xFF1A3332)
val SoftLavenderPill = Color(0xFFE8DEF8)
val SoftLavenderText = Color(0xFF1D192B)
val BinanceGreen = Color(0xFF0ECB81)
val BinanceRed = Color(0xFFF6465D)
val BinanceDarkBg = Color(0xFF0B0E11)
val BinanceDarkCard = Color(0xFF151921)
val BinanceDarkTrack = Color(0xFF232934)
val BinanceLightText = Color(0xFFEAECEF)
val BinanceMutedText = Color(0xFF8A94A6)
val TransferBlue = Color(0xFF3B82F6)
val VaultEmerald = Color(0xFF10B981)
val InkDark = Color(0xFF11141A)
val MonochromeBoxLight = Color(0xFFECEEF2)
val MonochromeBoxDark = Color(0xFF232934)

val SansFontFamily = FontFamily(
    Font(R.font.sans_regular, FontWeight.Normal),
    Font(R.font.sans_regular, FontWeight.Medium),
    Font(R.font.sans_bold, FontWeight.SemiBold),
    Font(R.font.sans_bold, FontWeight.Bold),
    Font(R.font.sans_bold, FontWeight.ExtraBold)
)

private val CleanLightScheme = lightColorScheme(
    primary = InkDark,
    onPrimary = Color(0xFFFFFFFF),
    secondary = OttoTurquoiseDeep,
    tertiary = SoftLavenderPill,
    background = Color(0xFFF7F8FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFECEEF2),
    onBackground = InkDark,
    onSurface = InkDark,
    onSurfaceVariant = Color(0xFF68707E),
    outline = Color(0xFFE2E6EC)
)

private val OttoDarkScheme = darkColorScheme(
    primary = OttoTurquoise,
    onPrimary = InkDark,
    secondary = BinanceGreen,
    tertiary = OttoTurquoiseSoftDark,
    background = BinanceDarkBg,
    surface = BinanceDarkCard,
    surfaceVariant = BinanceDarkTrack,
    onBackground = BinanceLightText,
    onSurface = BinanceLightText,
    onSurfaceVariant = BinanceMutedText,
    outline = BinanceDarkTrack
)

val OttoTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = SansFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp,
        fontFeatureSettings = "tnum"
    ),
    headlineLarge = TextStyle(
        fontFamily = SansFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.3).sp,
        fontFeatureSettings = "tnum"
    ),
    titleLarge = TextStyle(
        fontFamily = SansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontFeatureSettings = "tnum"
    ),
    titleMedium = TextStyle(
        fontFamily = SansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        fontFeatureSettings = "tnum"
    ),
    bodyMedium = TextStyle(
        fontFamily = SansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontFeatureSettings = "tnum"
    ),
    labelSmall = TextStyle(
        fontFamily = SansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.2.sp,
        fontFeatureSettings = "tnum"
    )
)

@Composable
fun OttoBudgetTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) OttoDarkScheme else CleanLightScheme,
        typography = OttoTypography,
        content = content
    )
}
