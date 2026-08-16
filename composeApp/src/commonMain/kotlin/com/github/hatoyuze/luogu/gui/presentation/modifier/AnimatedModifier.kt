package com.github.hatoyuze.luogu.gui.presentation.modifier

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Animated rainbow border using a rotating sweep-gradient stroke, clipped to
 * [shape] and layered over [backgroundColor].
 *
 * @param borderColors gradient colors of the animated border
 * @param backgroundColor fill color inside the shape
 * @param shape clipping shape of the whole modifier
 * @param borderWidth stroke width of the animated border
 * @param animationDurationInMillis duration of one full rotation
 * @param easing easing of the rotation animation
 */
@Composable
fun Modifier.animatedBorder(
    borderColors: List<Color>,
    backgroundColor: Color,
    shape: Shape,
    borderWidth: Dp = 1.dp,
    animationDurationInMillis: Int = 3000,
    easing: Easing = LinearEasing,
): Modifier {
    val brush = remember(borderColors) { Brush.sweepGradient(borderColors) }
    val infiniteTransition = rememberInfiniteTransition(label = "animatedBorder")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(animationDurationInMillis, easing = easing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "angleAnimation",
    )
    return this
        .clip(shape)
        .padding(borderWidth)
        .drawWithContent {
            drawContent()
            rotate(angle, center) {
                drawRect(
                    brush = brush,
                    size = size,
                    style = Stroke(width = borderWidth.toPx()),
                )
            }
        }
        .background(backgroundColor, shape)
}
