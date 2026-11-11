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

// Unified Brand Signature: App Icon Turquoise (#6BD0C2) in BOTH Light Mode and Dark Mode!
val OttoTurquoise = Color(0xFF6BD0C2)
val OttoTurquoiseDeep = Color(0xFF2E9E8F)
val OttoTurquoiseSoftLight = Color(0xFFE3F7F4)
val OttoTurquoiseSoftDark = Color(0xFF1A3332)
val BinanceGreen = Color(0xFF0ECB81)
val BinanceRed = Color(0xFFF6465D)
val BinanceDarkBg = Color(0xFF0B0E11)
val BinanceDarkCard = Color(0xFF151921)
val BinanceDarkTrack = Color(0xFF232934)
val BinanceLightText = Color(0xFFEAECEF)
val BinanceMutedText = Color(0xFF8A94A6)
val TransferBlue = Color(0xFF3B82F6)
val VaultEmerald = Color(0xFF10B981)
val InkDark = Color(0xFF0F1720)
val MonochromeBoxLight = Color(0xFFEBF5F3)
val MonochromeBoxDark = Color(0xFF232934)

val SansFontFamily = FontFamily(
    Font(R.font.sans_regular, FontWeight.Normal),
    Font(R.font.sans_regular, FontWeight.Medium),
    Font(R.font.sans_bold, FontWeight.SemiBold),
    Font(R.font.sans_bold, FontWeight.Bold),
    Font(R.font.sans_bold, FontWeight.ExtraBold)
)

// Light Mode now uses OttoTurquoise (#6BD0C2) + White surfaces to maintain the brand signature!
private val OttoLightScheme = lightColorScheme(
    primary = OttoTurquoise,
    onPrimary = InkDark,
    secondary = OttoTurquoiseDeep,
    tertiary = OttoTurquoiseSoftLight,
    background = Color(0xFFF5FAF9),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEAF4F2),
    onBackground = InkDark,
    onSurface = InkDark,
    onSurfaceVariant = Color(0xFF5A6B68),
    outline = Color(0xFFD9EBE7)
)

// Dark Mode kept as-is (#0B0E11 / #151921 + #6BD0C2 OttoTurquoise)
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
        colorScheme = if (darkTheme) OttoDarkScheme else OttoLightScheme,
        typography = OttoTypography,
        content = content
    )
}
