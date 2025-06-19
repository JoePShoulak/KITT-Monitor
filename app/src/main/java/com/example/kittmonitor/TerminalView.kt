package com.example.kittmonitor

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect

@Composable
fun TerminalView(
    logs: List<AnnotatedString>,
    followBottom: Boolean,
    onFollowBottomChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var programmaticScroll by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(logs.size, followBottom) {
        if (followBottom && logs.isNotEmpty()) {
            programmaticScroll = true
            listState.animateScrollToItem(logs.size - 1)
            programmaticScroll = false
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { inProgress ->
                if (inProgress && !programmaticScroll) {
                    val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                    if (lastVisible < logs.size - 1) {
                        onFollowBottomChange(false)
                    }
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (logs.isNotEmpty()) {
                        onFollowBottomChange(true)
                        programmaticScroll = true
                        scope.launch {
                            listState.animateScrollToItem(logs.size - 1)
                            programmaticScroll = false
                        }
                    }
                })
            }
    ) {
        items(logs) { message ->
            Text(
                text = message,
                color = Color.Unspecified,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
