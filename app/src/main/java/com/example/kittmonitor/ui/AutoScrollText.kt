package com.example.kittmonitor.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text

@Composable
fun AutoScrollText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle(fontSize = 14.sp)
) {
    var autoScroll by remember { mutableStateOf(true) }
    val scrollState = rememberScrollState()

    LaunchedEffect(text, autoScroll) {
        if (autoScroll) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Box(
        modifier
            .pointerInput(Unit) {
                detectVerticalDragGestures(onDragStart = {
                    autoScroll = false
                }, onDrag = { change, dragAmount ->
                    change.consume()
                    scrollState.dispatchRawDelta(-dragAmount)
                })
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    autoScroll = true
                })
            }
            .verticalScroll(scrollState)
    ) {
        Text(text, style = style)
    }
}
