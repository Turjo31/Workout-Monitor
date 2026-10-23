package com.kuet.gymtest.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.kuet.gymtest.ui.theme.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val AgeRange = 10..100

/** Bounded, snapping wheel. The caller retains the chosen age between onboarding steps. */
@Composable
fun AgeWheelPicker(
    initialAge: Int,
    onAgeSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val ages = remember { AgeRange.toList() }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initialAge.coerceIn(AgeRange) - AgeRange.first)
    val scope = rememberCoroutineScope()
    val onSelected by rememberUpdatedState(onAgeSelected)
    val density = LocalDensity.current
    val numberStyle = MaterialTheme.typography.displaySmall
    // Allow large font settings without clipping the centered number.
    val rowHeight = maxOf(56.dp, with(density) { numberStyle.lineHeight.toDp() } + Space.Sm)
    val selectedIndex by remember {
        derivedStateOf {
            val layout = state.layoutInfo
            val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
            layout.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - center) }?.index
                ?: state.firstVisibleItemIndex
        }
    }
    val selectedAge = ages[selectedIndex.coerceIn(ages.indices)]
    LaunchedEffect(state) {
        snapshotFlow { selectedIndex }.distinctUntilChanged().collect { index -> onSelected(ages[index]) }
    }
    fun chooseAge(age: Int): Boolean {
        if (age !in AgeRange) return false
        scope.launch { state.animateScrollToItem(age - AgeRange.first) }
        return true
    }

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Your age", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(Space.Md))
        Box(
            Modifier.widthIn(max = 240.dp).fillMaxWidth().height(rowHeight * 3)
                .clearAndSetSemantics {
                    contentDescription = "Age"
                    stateDescription = "$selectedAge years old"
                    progressBarRangeInfo = ProgressBarRangeInfo(selectedAge.toFloat(), 10f..100f, steps = 89)
                    setProgress { target -> chooseAge(target.roundToInt().coerceIn(AgeRange)) }
                    customActions = listOf(
                        CustomAccessibilityAction("Increase age") { chooseAge(selectedAge + 1) },
                        CustomAccessibilityAction("Decrease age") { chooseAge(selectedAge - 1) }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.fillMaxWidth().height(rowHeight)
                .background(ActionGreen.copy(alpha = .32f), WorkoutShapes.Control)
                .border(1.dp, Leaf.copy(alpha = .6f), WorkoutShapes.Control))
            LazyColumn(
                state = state,
                flingBehavior = rememberSnapFlingBehavior(lazyListState = state),
                contentPadding = PaddingValues(vertical = rowHeight),
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(ages, key = { it }) { age ->
                    Box(
                        Modifier.fillMaxWidth().height(rowHeight).clickable { chooseAge(age) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            age.toString(), style = numberStyle, color = Forest,
                            modifier = Modifier.graphicsLayer {
                                val layout = state.layoutInfo
                                val item = layout.visibleItemsInfo.find { it.key == age }
                                val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
                                val distance = item?.let { abs(it.offset + it.size / 2f - center) / it.size } ?: 1f
                                alpha = (1f - distance * .65f).coerceIn(.15f, 1f)
                                val scale = (1f - distance * .22f).coerceIn(.65f, 1f)
                                scaleX = scale
                                scaleY = scale
                            }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(Space.Sm))
        Text("years old", color = Forest, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(Space.Md))
        Text("Swipe up or down to choose", color = MutedInk, style = MaterialTheme.typography.bodySmall)
    }
}
