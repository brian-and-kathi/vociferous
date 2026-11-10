package com.brian.dialbuddy.ui.components

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.flow.distinctUntilChanged

internal const val PagerEdgeSettle = 0.001f

fun pagerAcceptsUserScroll(pageCount: Int): Boolean = pageCount > 1

fun pagerShouldSettle(scrolling: Boolean, offsetFraction: Float): Boolean =
    !scrolling && abs(offsetFraction) > PagerEdgeSettle

@Composable
fun CardPager(
    state: PagerState,
    modifier: Modifier = Modifier,
    pageContent: @Composable PagerScope.(page: Int) -> Unit,
) {
    LaunchedEffect(state) {
        snapshotFlow { pagerShouldSettle(state.isScrollInProgress, state.currentPageOffsetFraction) }
            .distinctUntilChanged()
            .collect { settle ->
                if (settle) state.animateScrollToPage(state.currentPage)
            }
    }
    HorizontalPager(
        state = state,
        modifier = modifier,
        pageSpacing = 20.dp,
        userScrollEnabled = pagerAcceptsUserScroll(state.pageCount),
    ) { page ->
        pageContent(page)
    }
}
