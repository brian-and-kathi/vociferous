package com.brian.dialbuddy.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

// Oldest row at the top. The first frame opens on the newest row, and a new row
// stays in view only while that end was already visible.
@Composable
internal fun rememberChronologicalListState(itemCount: Int, newestKey: Any?): LazyListState {
    val state = rememberLazyListState()
    var opened by remember { mutableStateOf(false) }
    val priorCount = remember { intArrayOf(0) }
    if (!opened && itemCount > 0) {
        state.requestScrollToItem(itemCount - 1)
    }
    LaunchedEffect(itemCount, newestKey) {
        if (itemCount <= 0) {
            priorCount[0] = 0
            opened = false
            return@LaunchedEffect
        }
        val info = state.layoutInfo
        val lastVisible = info.visibleItemsInfo.lastOrNull()?.index
        val total = info.totalItemsCount
        val prior = priorCount[0]
        val atEnd = total == 0 || lastVisible == null ||
            lastVisible >= total - 1 ||
            (prior > 0 && total != prior && lastVisible >= prior - 1)
        if (!opened || atEnd) {
            state.scrollToItem(itemCount - 1)
        }
        opened = true
        priorCount[0] = itemCount
    }
    return state
}
