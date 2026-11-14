package com.example.rnd_transit_mtl

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.rnd_transit_mtl.ui.theme.TransitComplementary
import com.example.rnd_transit_mtl.ui.theme.TransitMain
import com.example.rnd_transit_mtl.ui.theme.TransitSelected
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private const val LogoWidth = 637f
private const val LogoHoldEnd = 0.14f
private const val BadgesExit = 0.54f
private const val CompassStops = 0.73f
private const val RevealStart = 0.79f
private const val BadgeRadius = 39.7f
private const val RingMaskRadius = 228f
private const val WrapRadius = 196f
private val ringMask = Path().apply { addOval(Rect(Offset.Zero, RingMaskRadius)) }
private val wrapBounds = Rect(Offset.Zero, WrapRadius)
private const val RopeSamples = 48
private val routeStroke = Stroke(width = 64f, cap = StrokeCap.Round, join = StrokeJoin.Round)

private val compassEasing = CubicBezierEasing(0.35f, 0.15f, 0.5f, 1f)
private val spreadColor = lerp(TransitComplementary, TransitMain, 0.40f)

@Composable
fun AnimatedStartupOverlay(readyToReveal: () -> Boolean = { true }) {
    val progress = remember { Animatable(0f) }
    var finished by remember { mutableStateOf(false) }
    val latestReadyToReveal by rememberUpdatedState(readyToReveal)

    LaunchedEffect(Unit) {
        // Compose applies the device's animator duration scale to this timeline.
        progress.animateTo(RevealStart, tween(durationMillis = 869, easing = LinearEasing))
        withTimeoutOrNull(3_000) { snapshotFlow { latestReadyToReveal() }.first { it } }
        progress.animateTo(1f, tween(durationMillis = 231, easing = LinearEasing))
        finished = true
    }

    if (finished) return

    val revealPath = remember { Path() }
    val wrappedPath = remember { Path() }
    val ropePath = remember { Path() }
    val wrappedMeasure = remember { PathMeasure() }
    Canvas(
        Modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent().changes.forEach { it.consume() }
                }
            }
        }
    ) {
        val time = progress.value
        val cornerRadius = hypot(center.x, center.y)

        if (time >= RevealStart) {
            val reveal = FastOutSlowInEasing.transform(phase(time, RevealStart, 1f))
            revealPath.rewind()
            revealPath.addOval(Rect(center, reveal * cornerRadius))
            clipPath(revealPath, clipOp = ClipOp.Difference) {
                drawRect(spreadColor)
            }
        } else {
            drawRect(TransitComplementary)

            val travel = FastOutSlowInEasing.transform(phase(time, LogoHoldEnd, BadgesExit))
            val logoScale = minOf(220.dp.toPx(), size.minDimension * 0.66f) / LogoWidth
            drawCircle(spreadColor, radius = travel * cornerRadius)

            val compassAlpha = 1f - phase(time, CompassStops, RevealStart)
            val angle = -42f * compassEasing.transform(phase(time, LogoHoldEnd, CompassStops))
            val unwrap = FastOutSlowInEasing.transform(phase(travel, 0.42f, 0.75f))
            val release = FastOutSlowInEasing.transform(phase(travel, 0.75f, 1f))
            val wrapping = FastOutSlowInEasing.transform(phase(travel, 0f, 0.42f))
            val innerRouteAlpha = 1f - FastOutSlowInEasing.transform(phase(travel, 0f, 0.18f))

            for (badge in directionBadges) {
                val origin = center + badge.origin * logoScale
                val horizontal = if (badge.direction.x > 0) size.width - origin.x else origin.x
                val vertical = if (badge.direction.y > 0) size.height - origin.y else origin.y
                val distance = minOf(horizontal, vertical) + BadgeRadius * logoScale + 1f
                val head = badge.origin + badge.direction * (distance * travel / logoScale)

                translate(center.x, center.y) {
                    scale(logoScale, pivot = Offset.Zero) {
                        if (travel < 1f) {
                            val tangent = tangentAngle(head)
                            val wrappedDegrees = (badge.anchorAngle + angle - tangent + 360f) % 360f
                            val sweep = -wrappedDegrees * (1f - unwrap)
                            wrappedPath.rewind()
                            if (unwrap < 1f) {
                                wrappedPath.arcTo(wrapBounds, tangent - sweep, sweep, forceMoveTo = true)
                            } else {
                                val contact = pointOnRing(tangent)
                                val tail = contact + (head - contact) * release
                                wrappedPath.moveTo(tail.x, tail.y)
                            }
                            wrappedPath.lineTo(head.x, head.y)
                            wrappedMeasure.setPath(wrappedPath, forceClosed = false)

                            // Morph one stroke from the logo curve into the wrapped rope.
                            ropePath.rewind()
                            for (index in 0..RopeSamples) {
                                val fraction = index.toFloat() / RopeSamples
                                val original = badge.routeSamples[index] +
                                    (head - badge.origin) * fraction
                                val wrapped = wrappedMeasure.getPosition(wrappedMeasure.length * fraction)
                                val point = original + (wrapped - original) * wrapping
                                if (index == 0) ropePath.moveTo(point.x, point.y)
                                else ropePath.lineTo(point.x, point.y)
                            }
                            // Keep the moving rope behind the rim. Only the still logo route
                            // is visible through the compass opening as the pull starts.
                            clipPath(ringMask, clipOp = ClipOp.Difference) {
                                drawPath(ropePath, badge.foreground, style = routeStroke)
                            }
                            if (innerRouteAlpha > 0f) {
                                clipPath(ringMask) {
                                    drawPath(badge.route, badge.foreground, alpha = innerRouteAlpha, style = routeStroke)
                                }
                            }
                        }

                        drawCircle(badge.background, radius = BadgeRadius, center = head)
                        translate(head.x, head.y) {
                            drawPath(badge.triangle, badge.foreground)
                        }
                    }
                }
            }

            translate(center.x, center.y) {
                scale(logoScale, pivot = Offset.Zero) {
                    rotate(angle, pivot = Offset.Zero) {
                        for (layer in startupCompass) {
                            drawPath(layer.path, layer.color, alpha = compassAlpha)
                        }
                    }
                }
            }
        }
    }
}

private fun phase(time: Float, start: Float, end: Float): Float =
    ((time - start) / (end - start)).coerceIn(0f, 1f)

private class DirectionBadge(
    val origin: Offset,
    val direction: Offset,
    val background: Color,
    val foreground: Color,
    val triangle: Path,
    val route: Path
) {
    val anchorAngle = tangentAngle(origin) + 105f
    val routeSamples = PathMeasure().run {
        setPath(route, forceClosed = false)
        List(RopeSamples + 1) { index -> getPosition(length * index / RopeSamples) }
    }
}

private fun tangentAngle(head: Offset): Float {
    val radians = atan2(head.y, head.x) + acos((WrapRadius / head.getDistance()).coerceAtMost(1f))
    return (radians * 180.0 / Math.PI).toFloat()
}

private fun pointOnRing(angle: Float): Offset {
    val radians = angle * Math.PI / 180.0
    return Offset((cos(radians) * WrapRadius).toFloat(), (sin(radians) * WrapRadius).toFloat())
}

// Badge 11 (blue) points down-left. Badge 06 (lime) points up-right.
private val directionBadges = listOf(
    DirectionBadge(
        origin = Offset(-278f, -45f),
        direction = Offset(-1f, 1f),
        background = TransitMain,
        foreground = TransitSelected,
        triangle = badgeTriangle(-6.6f, -31.6f, -23.8f, 21.7f, 31.0f, 10.0f),
        route = Path().apply {
            moveTo(-40f, -39f)
            lineTo(-121f, -127f)
            cubicTo(-141f, -149f, -169f, -148f, -190f, -128f)
            lineTo(-278f, -45f)
        }
    ),
    DirectionBadge(
        origin = Offset(279f, 45f),
        direction = Offset(1f, -1f),
        background = TransitSelected,
        foreground = TransitMain,
        triangle = badgeTriangle(8.2f, 31.3f, 22.8f, -22.9f, -31.5f, -8.4f),
        route = Path().apply {
            moveTo(39f, 38f)
            lineTo(120f, 119f)
            cubicTo(153f, 152f, 190f, 134f, 216f, 108f)
            lineTo(279f, 45f)
        }
    )
)

private fun badgeTriangle(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float) =
    Path().apply {
        moveTo(ax, ay)
        lineTo(bx, by)
        lineTo(cx, cy)
        close()
    }
