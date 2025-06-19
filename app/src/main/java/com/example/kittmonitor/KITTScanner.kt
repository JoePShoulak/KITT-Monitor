package com.example.kittmonitor

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

@Composable
fun KITTScanner(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "scanner")
    val position by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scannerPosition"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val segment = width * 0.1f
        val center = position * width
        val alphas = listOf(0.2f, 0.5f, 1f, 0.5f, 0.2f)
        alphas.forEachIndexed { index, alpha ->
            val x = center + (index - 2) * segment
            drawRoundRect(
                color = Color.Red.copy(alpha = alpha),
                topLeft = Offset(x - segment / 2f, 0f),
                size = Size(segment, height),
                cornerRadius = CornerRadius(height / 2f, height / 2f)
            )
        }
    }
}
