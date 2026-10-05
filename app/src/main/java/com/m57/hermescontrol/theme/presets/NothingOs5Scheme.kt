package com.m57.hermescontrol.theme.presets

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.m57.hermescontrol.theme.HermesStatusColors

private val NothingRedDark = Color(0xFFD71921)
private val NothingRedLight = Color(0xFFC81018)

val NothingOs5DarkColorScheme =
    darkColorScheme(
        primary = NothingRedDark,
        onPrimary = Color.White,
        primaryContainer = Color(0xFF3A090C),
        onPrimaryContainer = Color(0xFFFFDAD9),
        inversePrimary = NothingRedLight,
        secondary = Color(0xFFD0D0CE),
        onSecondary = Color(0xFF202020),
        secondaryContainer = Color(0xFF2A2A2A),
        onSecondaryContainer = Color(0xFFF1F1EF),
        tertiary = Color(0xFFFFFFFF),
        onTertiary = Color.Black,
        tertiaryContainer = Color(0xFF252525),
        onTertiaryContainer = Color.White,
        background = Color(0xFF000000),
        onBackground = Color(0xFFF4F4F2),
        surface = Color(0xFF0A0A0A),
        onSurface = Color(0xFFF4F4F2),
        surfaceVariant = Color(0xFF1B1B1B),
        onSurfaceVariant = Color(0xFFBDBDB9),
        surfaceTint = NothingRedDark,
        surfaceContainerLowest = Color(0xFF000000),
        surfaceContainerLow = Color(0xFF090909),
        surfaceContainer = Color(0xFF101010),
        surfaceContainerHigh = Color(0xFF181818),
        surfaceContainerHighest = Color(0xFF222222),
        inverseSurface = Color(0xFFF4F4F2),
        inverseOnSurface = Color(0xFF151515),
        error = Color(0xFFFF5449),
        onError = Color(0xFF000000),
        errorContainer = Color(0xFF4A0B0D),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = Color(0xFF6E6E6A),
        outlineVariant = Color(0xFF30302E),
        scrim = Color.Black,
    )

val NothingOs5LightColorScheme =
    lightColorScheme(
        primary = NothingRedLight,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFDAD9),
        onPrimaryContainer = Color(0xFF410004),
        inversePrimary = NothingRedDark,
        secondary = Color(0xFF4A4A48),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE5E5E2),
        onSecondaryContainer = Color(0xFF1B1B1A),
        tertiary = Color(0xFF111111),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFDCDCD8),
        onTertiaryContainer = Color(0xFF111111),
        background = Color(0xFFF4F4F2),
        onBackground = Color(0xFF111111),
        surface = Color(0xFFFCFCFA),
        onSurface = Color(0xFF111111),
        surfaceVariant = Color(0xFFE6E6E2),
        onSurfaceVariant = Color(0xFF51514E),
        surfaceTint = NothingRedLight,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF8F8F5),
        surfaceContainer = Color(0xFFF0F0ED),
        surfaceContainerHigh = Color(0xFFE8E8E4),
        surfaceContainerHighest = Color(0xFFDEDEDA),
        inverseSurface = Color(0xFF202020),
        inverseOnSurface = Color(0xFFF4F4F2),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        outline = Color(0xFF747470),
        outlineVariant = Color(0xFFC8C8C3),
        scrim = Color.Black,
    )

val NothingOs5DarkStatusColors =
    HermesStatusColors(
        success = Color(0xFF7ED991),
        successContainer = Color(0xFF10351A),
        onSuccess = Color.Black,
        warning = Color(0xFFFFC857),
        warningContainer = Color(0xFF3C2D00),
        onWarning = Color.Black,
        error = Color(0xFFFF5449),
        errorContainer = Color(0xFF4A0B0D),
        onError = Color.Black,
        info = Color(0xFFBDBDB9),
        infoContainer = Color(0xFF292929),
        onInfo = Color.Black,
    )

val NothingOs5LightStatusColors =
    HermesStatusColors(
        success = Color(0xFF246B35),
        successContainer = Color(0xFFD4F5DA),
        onSuccess = Color.White,
        warning = Color(0xFF805600),
        warningContainer = Color(0xFFFFE2A7),
        onWarning = Color.White,
        error = Color(0xFFBA1A1A),
        errorContainer = Color(0xFFFFDAD6),
        onError = Color.White,
        info = Color(0xFF51514E),
        infoContainer = Color(0xFFE6E6E2),
        onInfo = Color.White,
    )
