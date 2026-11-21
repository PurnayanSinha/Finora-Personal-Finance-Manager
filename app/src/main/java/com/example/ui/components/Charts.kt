package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.CategorySlice
import com.example.domain.IndianCurrencyFormatter
import com.example.domain.PeriodBarPoint
import com.example.domain.TrendPoint
import com.example.ui.theme.ChartPalette
import com.example.ui.theme.LocalFinoraColors
import kotlin.math.atan2
import kotlin.math.hypot

@Composable
fun CategoryDonutChart(
    slices: List<CategorySlice>,
    centerTitle: String,
    modifier: Modifier = Modifier
) {
    if (slices.isEmpty()) {
        EmptyStateCard(
            title = "No Category Data",
            subtitle = "Record transactions in this period to visualize category breakdown."
        )
        return
    }

    var selectedIndex by remember(slices) { mutableIntStateOf(-1) }
    var animateTrigger by remember(slices) { mutableStateOf(false) }
    LaunchedEffect(slices) { animateTrigger = true }
    val sweepProgress by animateFloatAsState(
        targetValue = if (animateTrigger) 1f else 0f,
        animationSpec = tween(durationMillis = 650),
        label = "donut_sweep"
    )

    val totalPaise = remember(slices) { slices.sumOf { it.amountPaise } }
    val selectedSlice = slices.getOrNull(selectedIndex)

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .size(196.dp)
                    .pointerInput(slices) {
                        detectTapGestures { tap ->
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val dx = tap.x - cx
                            val dy = tap.y - cy
                            val radius = hypot(dx, dy)
                            val outerR = minOf(size.width, size.height).toFloat() / 2f
                            if (radius in (outerR * 0.42f)..outerR) {
                                var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                                if (angle < 0f) angle += 360f
                                var accum = 0f
                                slices.forEachIndexed { idx, s ->
                                    val sweep = (s.amountPaise.toFloat() / totalPaise.toFloat()) * 360f
                                    if (angle in accum..(accum + sweep)) {
                                        selectedIndex = if (selectedIndex == idx) -1 else idx
                                        return@detectTapGestures
                                    }
                                    accum += sweep
                                }
                            } else {
                                selectedIndex = -1
                            }
                        }
                    }
            ) {
                val strokeWidth = size.minDimension * 0.22f
                val diameter = size.minDimension - strokeWidth
                val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                var startAngle = -90f

                slices.forEachIndexed { idx, slice ->
                    val fraction = slice.amountPaise.toFloat() / totalPaise.toFloat()
                    val fullSweep = fraction * 360f
                    val drawnSweep = (fullSweep * sweepProgress - 1.5f).coerceAtLeast(1f)
                    val color = ChartPalette[slice.colorIndex % ChartPalette.size]
                    val activeStroke = if (idx == selectedIndex) strokeWidth * 1.16f else strokeWidth

                    drawArc(
                        color = color,
                        startAngle = startAngle,
                        sweepAngle = drawnSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(diameter, diameter),
                        style = Stroke(width = activeStroke, cap = StrokeCap.Butt)
                    )
                    startAngle += fullSweep
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = selectedSlice?.category ?: centerTitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = IndianCurrencyFormatter.formatCompactPaise(
                        selectedSlice?.amountPaise ?: totalPaise
                    ),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (selectedSlice != null) {
                    Text(
                        text = "${selectedSlice.percentage}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            slices.forEachIndexed { idx, slice ->
                val color = ChartPalette[slice.colorIndex % ChartPalette.size]
                val isSelected = idx == selectedIndex
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) color.copy(alpha = 0.14f) else Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { selectedIndex = if (isSelected) -1 else idx }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = slice.category,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = IndianCurrencyFormatter.formatPaise(slice.amountPaise),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = color.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "${slice.percentage}%",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = color,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IncomeVsExpenseBarChart(
    points: List<PeriodBarPoint>,
    modifier: Modifier = Modifier
) {
    val semantic = LocalFinoraColors.current
    val maxVal = remember(points) {
        points.maxOfOrNull { maxOf(it.incomePaise, it.expensePaise) }?.coerceAtLeast(100L) ?: 100L
    }
    var selectedIdx by remember(points) { mutableIntStateOf(-1) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendChip(color = semantic.income, label = "Income")
            LegendChip(color = semantic.expense, label = "Expenses")
        }

        if (selectedIdx in points.indices) {
            val pt = points[selectedIdx]
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = "${pt.label}: Income ${IndianCurrencyFormatter.formatPaise(pt.incomePaise)} • Expense ${IndianCurrencyFormatter.formatPaise(pt.expensePaise)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val gridColor = semantic.cardBorder
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(165.dp)
                .pointerInput(points) {
                    detectTapGestures { tap ->
                        if (points.isNotEmpty()) {
                            val slotW = size.width / points.size.toFloat()
                            val idx = (tap.x / slotW).toInt().coerceIn(0, points.lastIndex)
                            selectedIdx = if (selectedIdx == idx) -1 else idx
                        }
                    }
                }
        ) {
            if (points.isEmpty()) return@Canvas
            val lines = 3
            for (i in 0..lines) {
                val y = size.height * (i.toFloat() / lines.toFloat())
                drawLine(
                    color = gridColor.copy(alpha = 0.55f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            val slotW = size.width / points.size.toFloat()
            val barW = (slotW * 0.28f).coerceAtMost(22.dp.toPx())
            val gap = (barW * 0.25f).coerceAtLeast(2.dp.toPx())

            points.forEachIndexed { idx, pt ->
                val centerX = slotW * idx + slotW / 2f
                val incH = ((pt.incomePaise.toFloat() / maxVal.toFloat()) * size.height * 0.92f)
                    .coerceAtLeast(if (pt.incomePaise > 0) 4.dp.toPx() else 0f)
                val expH = ((pt.expensePaise.toFloat() / maxVal.toFloat()) * size.height * 0.92f)
                    .coerceAtLeast(if (pt.expensePaise > 0) 4.dp.toPx() else 0f)

                if (incH > 0f) {
                    drawRoundRect(
                        color = semantic.income,
                        topLeft = Offset(centerX - barW - gap / 2f, size.height - incH),
                        size = Size(barW, incH),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }
                if (expH > 0f) {
                    drawRoundRect(
                        color = semantic.expense,
                        topLeft = Offset(centerX + gap / 2f, size.height - expH),
                        size = Size(barW, expH),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            val step = if (points.size > 7) 2 else 1
            points.forEachIndexed { idx, pt ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (idx % step == 0) {
                        Text(
                            text = pt.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TrendLineChart(
    points: List<TrendPoint>,
    lineColor: Color,
    seriesLabel: String,
    modifier: Modifier = Modifier
) {
    val semantic = LocalFinoraColors.current
    if (points.isEmpty()) {
        EmptyStateCard(
            title = "No $seriesLabel Data",
            subtitle = "Add transactions to inspect the $seriesLabel trend."
        )
        return
    }
    val maxVal = remember(points) {
        points.maxOfOrNull { it.amountPaise }?.coerceAtLeast(100L) ?: 100L
    }
    var selectedIdx by remember(points) { mutableIntStateOf(-1) }

    Column(modifier = modifier.fillMaxWidth()) {
        if (selectedIdx in points.indices) {
            val pt = points[selectedIdx]
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = lineColor.copy(alpha = 0.12f)
            ) {
                Text(
                    text = "${pt.label}: ${IndianCurrencyFormatter.formatPaise(pt.amountPaise)}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = lineColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        val gridColor = semantic.cardBorder
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .pointerInput(points) {
                    detectTapGestures { tap ->
                        if (points.size > 1) {
                            val slot = size.width / (points.size - 1).toFloat()
                            val idx = ((tap.x + slot / 2f) / slot).toInt().coerceIn(0, points.lastIndex)
                            selectedIdx = if (selectedIdx == idx) -1 else idx
                        } else if (points.size == 1) {
                            selectedIdx = 0
                        }
                    }
                }
        ) {
            for (i in 0..3) {
                val y = size.height * (i / 3f)
                drawLine(
                    color = gridColor.copy(alpha = 0.5f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            val padX = 12.dp.toPx()
            val usableW = (size.width - padX * 2f).coerceAtLeast(1f)
            val usableH = size.height * 0.88f

            val coords = points.mapIndexed { idx, pt ->
                val x = if (points.size == 1) size.width / 2f else padX + (idx.toFloat() / (points.size - 1).toFloat()) * usableW
                val y = size.height - ((pt.amountPaise.toFloat() / maxVal.toFloat()) * usableH) - 4.dp.toPx()
                Offset(x, y)
            }

            if (coords.size >= 2) {
                val strokePath = Path().apply {
                    moveTo(coords.first().x, coords.first().y)
                    for (i in 1 until coords.size) {
                        val prev = coords[i - 1]
                        val curr = coords[i]
                        val midX = (prev.x + curr.x) / 2f
                        cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                    }
                }
                val fillPath = Path().apply {
                    addPath(strokePath)
                    lineTo(coords.last().x, size.height)
                    lineTo(coords.first().x, size.height)
                    close()
                }
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(lineColor.copy(alpha = 0.28f), lineColor.copy(alpha = 0.02f))
                    )
                )
                drawPath(
                    path = strokePath,
                    color = lineColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            coords.forEachIndexed { idx, pt ->
                val isSel = idx == selectedIdx
                drawCircle(
                    color = Color.White,
                    radius = if (isSel) 6.dp.toPx() else 4.dp.toPx(),
                    center = pt
                )
                drawCircle(
                    color = lineColor,
                    radius = if (isSel) 4.5.dp.toPx() else 2.8.dp.toPx(),
                    center = pt
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            val step = if (points.size > 7) 2 else 1
            points.forEachIndexed { idx, pt ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (idx % step == 0) {
                        Text(
                            text = pt.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HorizontalDistributionChart(
    slices: List<CategorySlice>,
    modifier: Modifier = Modifier
) {
    if (slices.isEmpty()) {
        EmptyStateCard(
            title = "No Income Recorded",
            subtitle = "Add income entries in this period to view source distribution."
        )
        return
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        slices.forEach { slice ->
            val color = ChartPalette[slice.colorIndex % ChartPalette.size]
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = slice.category,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${IndianCurrencyFormatter.formatPaise(slice.amountPaise)} (${slice.percentage}%)",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = color
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(color.copy(alpha = 0.14f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((slice.percentage.toFloat() / 100f).coerceIn(0.03f, 1f))
                            .height(10.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(color)
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendChip(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
