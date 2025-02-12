package com.couchbase.lite.mobile.android.test.bt.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext


private val DarkColorScheme = darkColorScheme(
    primary = darkPrimary,
    secondary = darkSecondary,
    tertiary = darkTertiary,
    background = darkBackground,
    onPrimary = darkOnPrimary,
    onSecondary = darkOnSecondary,
    onTertiary = darkOnTertiary,
    surface = darkSurface,
    onSurface = darkOnSurface
)

private val LightColorScheme = lightColorScheme(
    primary = litePrimary,
    secondary = liteSecondary,
    tertiary = liteTertiary,
    background = liteBackground,
    onPrimary = liteOnPrimary,
    onSecondary = liteOnSecondary,
    onTertiary = liteOnTertiary,
    surface = liteSurface,
    onSurface = liteOnSurface

    /* Other default colors to override
    onBackground = Color(0xFF1C1B1F),
    */
)

@Composable
fun BTDreamTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}