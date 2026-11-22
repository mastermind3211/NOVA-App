package com.nova.assistant.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.assistant.model.NovaOrbState
import com.nova.assistant.ui.theme.NovaColors
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun NovaHolographicOrb(
    state: NovaOrbState,
    audioLevel: Float,
    modifier: Modifier = Modifier,
    size: Dp = 230.dp,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_transition")

    val rotationSpeedMs = when (state) {
        NovaOrbState.IDLE -> 16000
        NovaOrbState.LISTENING_WAKE_WORD -> 10000
        NovaOrbState.LISTENING_COMMAND -> 4500
        NovaOrbState.THINKING -> 2200
        NovaOrbState.SPEAKING -> 3500
    }

    val outerAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(rotationSpeedMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outer_angle"
    )

    val innerAngle by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween((rotationSpeedMs * 0.7f).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "inner_angle"
    )

    val pulseBase by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_base"
    )

    val reactiveScale by animateFloatAsState(
        targetValue = (1f + audioLevel.coerceIn(0f, 1f) * 0.25f) * pulseBase,
        animationSpec = tween(100),
        label = "reactive_scale"
    )

    val primaryColor by animateColorAsState(
        targetValue = when (state) {
            NovaOrbState.IDLE -> NovaColors.ElectricBlue
            NovaOrbState.LISTENING_WAKE_WORD -> NovaColors.NeonCyan
            NovaOrbState.LISTENING_COMMAND -> NovaColors.EmeraldActive
            NovaOrbState.THINKING -> NovaColors.CyberPurple
            NovaOrbState.SPEAKING -> NovaColors.NeonCyan
        },
        animationSpec = tween(350),
        label = "orb_color"
    )

    Box(
        modifier = modifier
            .size(size)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val maxRadius = this.size.minDimension / 2f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.35f),
                        NovaColors.CyberPurple.copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxRadius * reactiveScale
                ),
                radius = maxRadius * reactiveScale,
                center = center
            )

            rotate(degrees = outerAngle, pivot = center) {
                val tickRadius = maxRadius * 0.90f
                for (i in 0 until 36) {
                    val angleRad = Math.toRadians((i * 10).toDouble())
                    val isMajor = i % 3 == 0
                    val startR = tickRadius - if (isMajor) 12f else 6f
                    val start = Offset(
                        center.x + (startR * cos(angleRad)).toFloat(),
                        center.y + (startR * sin(angleRad)).toFloat()
                    )
                    val end = Offset(
                        center.x + (tickRadius * cos(angleRad)).toFloat(),
                        center.y + (tickRadius * sin(angleRad)).toFloat()
                    )
                    drawLine(
                        color = primaryColor.copy(alpha = if (isMajor) 0.85f else 0.35f),
                        start = start,
                        end = end,
                        strokeWidth = if (isMajor) 3f else 1.5f,
                        cap = StrokeCap.Round
                    )
                }
            }

            rotate(degrees = innerAngle, pivot = center) {
                val r = maxRadius * 0.74f
                val topLeft = Offset(center.x - r, center.y - r)
                val arcSize = Size(r * 2f, r * 2f)
                val segments = listOf(0f to 75f, 110f to 60f, 200f to 85f, 310f to 35f)
                segments.forEach { (startDeg, sweepDeg) ->
                    drawArc(
                        color = primaryColor.copy(alpha = 0.85f),
                        startAngle = startDeg,
                        sweepAngle = sweepDeg,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = 4.5f, cap = StrokeCap.Round)
                    )
                }
            }

            rotate(degrees = outerAngle * 1.4f, pivot = center) {
                val r = maxRadius * 0.56f
                val topLeft = Offset(center.x - r, center.y - r)
                val arcSize = Size(r * 2f, r * 2f)
                drawArc(
                    color = NovaColors.CyberPurple.copy(alpha = 0.8f),
                    startAngle = 20f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = 3.5f, cap = StrokeCap.Round)
                )
                drawArc(
                    color = NovaColors.NeonCyan.copy(alpha = 0.8f),
                    startAngle = 200f,
                    sweepAngle = 120f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = 3.5f, cap = StrokeCap.Round)
                )
            }

            val coreRadius = maxRadius * 0.38f * reactiveScale.coerceAtMost(1.22f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.85f),
                        primaryColor.copy(alpha = 0.9f),
                        NovaColors.ObsidianBg.copy(alpha = 0.95f)
                    ),
                    center = center,
                    radius = coreRadius
                ),
                radius = coreRadius,
                center = center
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "NOVA",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 4.sp
            )
            Text(
                text = state.label,
                color = primaryColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.5.sp
            )
        }
    }
}
