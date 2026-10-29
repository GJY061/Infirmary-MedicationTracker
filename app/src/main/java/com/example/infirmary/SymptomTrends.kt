package com.example.infirmary

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.example.infirmary.viewmodel.SymptomTrendPoint
import com.example.infirmary.viewmodel.SymptomTrendViewModel

/** Displays the reactive severity chart and category counts, or an empty/error state. */
@Composable
fun SymptomTrendScreen(
    viewModel: SymptomTrendViewModel,
    onBackClick: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Text("Symptom Trends") }
        if (state.errorMessage.isNotBlank()) {
            item { Text(state.errorMessage) }
        } else if (state.points.isEmpty()) {
            item { Text("No symptom history available yet.") }
        } else {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Severity Over Time")
                        Spacer(Modifier.height(12.dp))
                        SeverityChart(points = state.points)
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(state.points.first().label.take(10))
                            Text(state.points.last().label.take(10))
                        }
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Category Summary")
                        state.categoryCounts.forEach { (category, count) ->
                            Text("$category: $count")
                        }
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = onBackClick, modifier = Modifier.fillMaxWidth()) {
                Text("Back to Settings")
            }
        }
    }
}

/**
 * Draws axes, connected severity points, and markers with Compose Canvas.
 * Points are evenly spaced by record index, not by elapsed time between timestamps.
 */
@Composable
private fun SeverityChart(points: List<SymptomTrendPoint>) {
    val lineColor = Color(0xFF1565C0)
    val axisColor = Color(0xFF616161)
    val pointColor = Color(0xFFC62828)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
    ) {
        // Canvas coordinates are pixels; reserve margins so axes and markers fit inside the drawing area.
        val left = 28f
        val right = size.width - 12f
        val top = 12f
        val bottom = size.height - 28f
        val width = right - left
        val height = bottom - top

        drawLine(
            color = axisColor,
            start = Offset(left, top),
            end = Offset(left, bottom),
            strokeWidth = 3f
        )
        drawLine(
            color = axisColor,
            start = Offset(left, bottom),
            end = Offset(right, bottom),
            strokeWidth = 3f
        )

        val offsets = points.mapIndexed { index, point ->
            // Centre a single record; otherwise spread records across the available width.
            val x = if (points.size == 1) {
                left + width / 2f
            } else {
                left + (width * index / (points.lastIndex).toFloat())
            }
            // Map severity 1-10 onto chart height; subtract because screen Y increases downwards.
            val clampedSeverity = point.severity.coerceIn(1, 10)
            val y = bottom - ((clampedSeverity - 1) / 9f * height)
            Offset(x, y)
        }

        // Join neighbouring records in the chronological order supplied by the DAO.
        offsets.zipWithNext().forEach { (start, end) ->
            drawLine(
                color = lineColor,
                start = start,
                end = end,
                strokeWidth = 5f,
                cap = StrokeCap.Round
            )
        }
        offsets.forEach {
            drawCircle(color = pointColor, radius = 7f, center = it)
        }
    }
}
