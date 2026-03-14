package com.module.notelycompose.notes.ui.theme

import androidx.compose.ui.graphics.Color

enum class AccentTheme(
    val displayName: String,
    val darkColor: Color,
    val lightColor: Color
) {
    GREEN("Grün", Color(0xFF5E8040), Color(0xFF7A9A50)),
    RED("Rot", Color(0xFFB33A3A), Color(0xFF9C2D2D)),
    GOLD("Gold", Color(0xFFB8860B), Color(0xFF9A7209)),
    MONO("Monochrom", Color(0xFF888888), Color(0xFF555555))
}
