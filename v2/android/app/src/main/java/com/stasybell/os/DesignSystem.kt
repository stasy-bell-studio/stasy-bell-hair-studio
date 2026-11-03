package com.stasybell.os

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object StasyColors {
    val Primary = Color(0xFF1F6342)
    val PrimaryDeep = Color(0xFF0B3825)
    val Forest = Color(0xFF092D1E)
    val Mint = Color(0xFFCFE6D2)
    val MintSoft = Color(0xFFE9F3E9)
    val Canvas = Color(0xFFF5F7F3)
    val CanvasSoft = Color(0xFFEDF2EC)
    val Surface = Color(0xFFFFFFFF)
    val Ink = Color(0xFF17221B)
    val Body = Color(0xFF344238)
    val Muted = Color(0xFF66756B)
    val Border = Color(0xFFDEE7DE)
    val Error = Color(0xFFB3261E)
}

object StasySpacing {
    val small = 8.dp
    val medium = 16.dp
    val large = 24.dp
}

private val colors = lightColorScheme(
    primary = StasyColors.Primary, onPrimary = Color.White,
    primaryContainer = StasyColors.MintSoft, onPrimaryContainer = StasyColors.Forest,
    secondary = StasyColors.PrimaryDeep, onSecondary = Color.White,
    secondaryContainer = StasyColors.Mint, onSecondaryContainer = StasyColors.Forest,
    background = StasyColors.Canvas, onBackground = StasyColors.Ink,
    surface = StasyColors.Surface, onSurface = StasyColors.Ink,
    surfaceVariant = StasyColors.CanvasSoft, onSurfaceVariant = StasyColors.Body,
    outline = StasyColors.Muted, outlineVariant = StasyColors.Border,
    error = StasyColors.Error, onError = Color.White,
)

private val typography = Typography().let { base ->
    base.copy(
        headlineSmall = base.headlineSmall.copy(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = base.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
        labelLarge = base.labelLarge.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    )
}

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

@Composable
fun StasyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes, content = content)
}

@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, StasyColors.Border),
    ) {
        Column(Modifier.padding(StasySpacing.medium), content = content)
    }
}

@Composable
fun KpiValue(label: String, value: String, modifier: Modifier = Modifier) {
    AppCard(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = StasyColors.Body)
        Text(value, style = MaterialTheme.typography.headlineSmall)
    }
}
