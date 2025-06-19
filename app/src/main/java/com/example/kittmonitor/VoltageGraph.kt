package com.example.kittmonitor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas

@Composable
fun VoltageGraph(
    data: List<Pair<Long, Float>>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No data",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        return
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val padding = 16.dp.toPx()
        val width = size.width - 2 * padding
        val height = size.height - 2 * padding

        val xMin = data.first().first.toFloat()
        val xMax = data.last().first.toFloat()
        val yMin = data.minOf { it.second }
        val yMax = data.maxOf { it.second }

        val xRange = if (xMax - xMin == 0f) 1f else xMax - xMin
        val yRange = if (yMax - yMin == 0f) 1f else yMax - yMin

        val path = Path()
        data.forEachIndexed { index, (time, value) ->
            val x = padding + ((time - xMin) / xRange) * width
            val y = size.height - padding - ((value - yMin) / yRange) * height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = Color.Cyan,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}
