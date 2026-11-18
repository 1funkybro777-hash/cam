package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.camera.CameraHudState
import com.example.camera.DetectedTarget
import com.example.ui.theme.EmeraldGlow
import com.example.ui.theme.EmeraldLock
import com.example.ui.theme.LaserAmber
import com.example.ui.theme.LaserAmberDim
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SignalCyan

@Composable
fun TrackingOverlayCanvas(
    hudState: CameraHudState,
    modifier: Modifier = Modifier,
) {
    val target = hudState.detectedTarget
    val isAligned = hudState.isAligned

    val activeColor by animateColorAsState(
        targetValue = when {
            target == null -> Color.White.copy(alpha = 0.45f)
            isAligned -> EmeraldLock
            else -> LaserAmber
        },
        label = "reticleColor",
    )

    val animatedProgress by animateFloatAsState(
        targetValue = hudState.holdProgress,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "holdProgress",
    )

    val targetRingScale by animateFloatAsState(
        targetValue = if (isAligned) 1.12f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "ringScale",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val center = Offset(centerX, centerY)

        if (hudState.isGridVisible) {
            drawRuleOfThirdsGrid()
        }

        if (target != null && hudState.hasSmoothPosition) {
            drawBoundingBrackets(
                target = target,
                color = if (isAligned) EmeraldGlow else SignalCyan.copy(alpha = 0.8f),
            )

            val dotOffset = Offset(hudState.smoothDotX, hudState.smoothDotY)

            // Dashed guide line connecting Center Ring to AI Tracking Dot
            drawLine(
                color = activeColor,
                start = center,
                end = dotOffset,
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(
                    intervals = floatArrayOf(7.dp.toPx(), 6.dp.toPx()),
                    phase = 0f,
                ),
            )

            // Center Target Ring
            val baseRadius = 30.dp.toPx() * targetRingScale
            drawCenterReticle(
                center = center,
                radius = baseRadius,
                ringColor = if (isAligned) EmeraldLock else Color.White.copy(alpha = 0.85f),
                progress = animatedProgress,
                isAligned = isAligned,
            )

            // AI Tracking Dot
            val dotRadius = 11.dp.toPx()
            drawCircle(
                color = if (isAligned) EmeraldLock.copy(alpha = 0.28f) else LaserAmberDim,
                radius = dotRadius * 1.9f,
                center = dotOffset,
            )
            drawCircle(
                color = activeColor,
                radius = dotRadius,
                center = dotOffset,
            )
            drawCircle(
                color = ObsidianBg,
                radius = dotRadius,
                center = dotOffset,
                style = Stroke(width = 2.5.dp.toPx()),
            )
        } else {
            // Idle scanning reticle
            drawCenterReticle(
                center = center,
                radius = 26.dp.toPx(),
                ringColor = Color.White.copy(alpha = 0.42f),
                progress = 0f,
                isAligned = false,
            )
        }
    }
}

private fun DrawScope.drawRuleOfThirdsGrid() {
    val gridColor = Color.White.copy(alpha = 0.14f)
    val stroke = 1.dp.toPx()
    val thirdW = size.width / 3f
    val thirdH = size.height / 3f

    drawLine(gridColor, Offset(thirdW, 0f), Offset(thirdW, size.height), strokeWidth = stroke)
    drawLine(gridColor, Offset(thirdW * 2f, 0f), Offset(thirdW * 2f, size.height), strokeWidth = stroke)
    drawLine(gridColor, Offset(0f, thirdH), Offset(size.width, thirdH), strokeWidth = stroke)
    drawLine(gridColor, Offset(0f, thirdH * 2f), Offset(size.width, thirdH * 2f), strokeWidth = stroke)
}

private fun DrawScope.drawCenterReticle(
    center: Offset,
    radius: Float,
    ringColor: Color,
    progress: Float,
    isAligned: Boolean,
) {
    val strokeWidth = if (isAligned) 3.5.dp.toPx() else 2.dp.toPx()
    drawCircle(
        color = ringColor,
        radius = radius,
        center = center,
        style = Stroke(width = strokeWidth),
    )

    // Precision tick marks on N/S/E/W
    val tickLen = 6.dp.toPx()
    val tickStroke = 1.8.dp.toPx()
    drawLine(
        color = ringColor,
        start = Offset(center.x, center.y - radius - tickLen),
        end = Offset(center.x, center.y - radius),
        strokeWidth = tickStroke,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = ringColor,
        start = Offset(center.x, center.y + radius),
        end = Offset(center.x, center.y + radius + tickLen),
        strokeWidth = tickStroke,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = ringColor,
        start = Offset(center.x - radius - tickLen, center.y),
        end = Offset(center.x - radius, center.y),
        strokeWidth = tickStroke,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = ringColor,
        start = Offset(center.x + radius, center.y),
        end = Offset(center.x + radius + tickLen, center.y),
        strokeWidth = tickStroke,
        cap = StrokeCap.Round,
    )

    if (progress > 0f) {
        val arcRadius = radius + 6.dp.toPx()
        drawArc(
            color = EmeraldGlow,
            startAngle = -90f,
            sweepAngle = 360f * progress,
            useCenter = false,
            topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
            size = Size(arcRadius * 2f, arcRadius * 2f),
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

private fun DrawScope.drawBoundingBrackets(
    target: DetectedTarget,
    color: Color,
) {
    val bracketLen = 18.dp.toPx().coerceAtMost(
        minOf((target.right - target.left) * 0.25f, (target.bottom - target.top) * 0.25f),
    )
    if (bracketLen <= 4f) return

    val stroke = 2.5.dp.toPx()

    // Top-left
    drawLine(color, Offset(target.left, target.top), Offset(target.left + bracketLen, target.top), stroke, StrokeCap.Round)
    drawLine(color, Offset(target.left, target.top), Offset(target.left, target.top + bracketLen), stroke, StrokeCap.Round)

    // Top-right
    drawLine(color, Offset(target.right - bracketLen, target.top), Offset(target.right, target.top), stroke, StrokeCap.Round)
    drawLine(color, Offset(target.right, target.top), Offset(target.right, target.top + bracketLen), stroke, StrokeCap.Round)

    // Bottom-left
    drawLine(color, Offset(target.left, target.bottom - bracketLen), Offset(target.left, target.bottom), stroke, StrokeCap.Round)
    drawLine(color, Offset(target.left, target.bottom), Offset(target.left + bracketLen, target.bottom), stroke, StrokeCap.Round)

    // Bottom-right
    drawLine(color, Offset(target.right - bracketLen, target.bottom), Offset(target.right, target.bottom), stroke, StrokeCap.Round)
    drawLine(color, Offset(target.right, target.bottom - bracketLen), Offset(target.right, target.bottom), stroke, StrokeCap.Round)
}
