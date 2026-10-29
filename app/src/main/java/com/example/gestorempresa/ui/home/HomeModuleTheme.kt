package com.example.gestorempresa.ui.home

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun HomeModuleTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(
        primary = Color(0xFF355F83), onPrimary = Color.White,
        secondary = Color(0xFF39735F), background = Color(0xFFFFFEFC),
        onBackground = Color(0xFF242321), surface = Color.White, onSurface = Color(0xFF242321),
        surfaceVariant = Color(0xFFF2EFE9), onSurfaceVariant = Color(0xFF5F5A53),
        outline = Color(0xFF81796E), error = Color(0xFFB3261E),
        primaryContainer = Color(0xFFE2EDF6), secondaryContainer = Color(0xFFE2F0E8)
    ), typography = MaterialTheme.typography, content = content)
}
