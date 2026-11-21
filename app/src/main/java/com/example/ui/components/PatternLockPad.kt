package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

/**
 * Interactive 3x3 touch-and-drag Android Pattern Lock pad.
 * Emits row-major node indices (0..8) as a list when the user connects nodes.
 */
@Composable
fun PatternLockPad(
    selectedNodes: List<Int>,
    onPatternChanged: (List<Int>) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.outline
    val ringColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    var currentPointer by remember { mutableStateOf<Offset?>(null) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 240.dp)
            .aspectRatio(1f)
            .testTag("pattern_lock_pad")
            .pointerInput(selectedNodes) {
                detectTapGestures { offset ->
                    val hit = findHitNode(offset, size.width.toFloat(), size.height.toFloat())
                    if (hit != null && hit !in selectedNodes) {
                        onPatternChanged(selectedNodes + hit)
                    }
                }
            }
            .pointerInput(selectedNodes) {
                detectDragGestures(
                    onDragStart = { start ->
                        currentPointer = start
                        val hit = findHitNode(start, size.width.toFloat(), size.height.toFloat())
                        if (hit != null) {
                            onPatternChanged(listOf(hit))
                        } else {
                            onPatternChanged(emptyList())
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        currentPointer = change.position
                        val hit = findHitNode(change.position, size.width.toFloat(), size.height.toFloat())
                        if (hit != null && hit !in selectedNodes) {
                            onPatternChanged(selectedNodes + hit)
                        }
                    },
                    onDragEnd = { currentPointer = null },
                    onDragCancel = { currentPointer = null }
                )
            }
    ) {
        val centers = computeNodeCenters(size.width, size.height)
        val dotRadius = size.minDimension * 0.045f
        val haloRadius = size.minDimension * 0.11f

        // Draw connecting lines
        for (i in 0 until selectedNodes.size - 1) {
            val start = centers[selectedNodes[i]]
            val end = centers[selectedNodes[i + 1]]
            drawLine(
                color = activeColor,
                start = start,
                end = end,
                strokeWidth = 8f,
                cap = StrokeCap.Round
            )
        }

        // Draw live drag tail
        val lastSelected = selectedNodes.lastOrNull()
        val pointer = currentPointer
        if (lastSelected != null && pointer != null) {
            drawLine(
                color = activeColor.copy(alpha = 0.55f),
                start = centers[lastSelected],
                end = pointer,
                strokeWidth = 6f,
                cap = StrokeCap.Round
            )
        }

        // Draw 3x3 nodes
        centers.forEachIndexed { idx, center ->
            val isSelected = idx in selectedNodes
            if (isSelected) {
                drawCircle(
                    color = ringColor,
                    radius = haloRadius,
                    center = center
                )
            }
            drawCircle(
                color = if (isSelected) activeColor else inactiveColor,
                radius = dotRadius,
                center = center
            )
        }
    }
}

private fun computeNodeCenters(width: Float, height: Float): List<Offset> {
    val cellW = width / 3f
    val cellH = height / 3f
    return List(9) { index ->
        val row = index / 3
        val col = index % 3
        Offset(
            x = (col + 0.5f) * cellW,
            y = (row + 0.5f) * cellH
        )
    }
}

private fun findHitNode(point: Offset, width: Float, height: Float): Int? {
    val centers = computeNodeCenters(width, height)
    val hitRadius = minOf(width, height) * 0.14f
    centers.forEachIndexed { index, center ->
        if (hypot(point.x - center.x, point.y - center.y) <= hitRadius) {
            return index
        }
    }
    return null
}
