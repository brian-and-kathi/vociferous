package com.brian.dialbuddy.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

enum class CallGlyph {
    Bluetooth,
    Speaker,
    SpeakerOn,
    Mic,
    MicOff,
    Hold,
    Resume,
    Answer,
    Decline,
}

@Composable
fun CallGlyphIcon(glyph: CallGlyph, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (glyph) {
            CallGlyph.Bluetooth -> drawBluetooth(color, stroke)
            CallGlyph.Speaker -> drawSpeaker(color, stroke, waves = false)
            CallGlyph.SpeakerOn -> drawSpeaker(color, stroke, waves = true)
            CallGlyph.Mic -> drawMic(color, stroke, slash = false)
            CallGlyph.MicOff -> drawMic(color, stroke, slash = true)
            CallGlyph.Hold -> drawHold(color)
            CallGlyph.Resume -> drawResume(color)
            CallGlyph.Answer -> drawHandset(color, stroke, rising = true)
            CallGlyph.Decline -> drawHandset(color, stroke, rising = false)
        }
    }
}

private fun DrawScope.drawBluetooth(color: Color, stroke: Stroke) {
    val x = { fraction: Float -> size.width * fraction }
    val y = { fraction: Float -> size.height * fraction }
    drawLine(color, Offset(x(0.50f), y(0.08f)), Offset(x(0.50f), y(0.92f)), stroke.width, stroke.cap)
    drawLine(color, Offset(x(0.50f), y(0.08f)), Offset(x(0.78f), y(0.30f)), stroke.width, stroke.cap)
    drawLine(color, Offset(x(0.78f), y(0.30f)), Offset(x(0.50f), y(0.50f)), stroke.width, stroke.cap)
    drawLine(color, Offset(x(0.50f), y(0.50f)), Offset(x(0.78f), y(0.70f)), stroke.width, stroke.cap)
    drawLine(color, Offset(x(0.78f), y(0.70f)), Offset(x(0.50f), y(0.92f)), stroke.width, stroke.cap)
    drawLine(color, Offset(x(0.22f), y(0.30f)), Offset(x(0.50f), y(0.50f)), stroke.width, stroke.cap)
    drawLine(color, Offset(x(0.50f), y(0.50f)), Offset(x(0.22f), y(0.70f)), stroke.width, stroke.cap)
}

private fun DrawScope.drawSpeaker(color: Color, stroke: Stroke, waves: Boolean) {
    val speaker = Path().apply {
        moveTo(size.width * 0.08f, size.height * 0.38f)
        lineTo(size.width * 0.32f, size.height * 0.38f)
        lineTo(size.width * 0.52f, size.height * 0.18f)
        lineTo(size.width * 0.52f, size.height * 0.82f)
        lineTo(size.width * 0.32f, size.height * 0.62f)
        lineTo(size.width * 0.08f, size.height * 0.62f)
        close()
    }
    drawPath(speaker, color)
    if (waves) {
        drawArc(
            color = color,
            startAngle = -50f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = Offset(size.width * 0.42f, size.height * 0.30f),
            size = Size(size.width * 0.22f, size.height * 0.40f),
            style = stroke,
        )
        drawArc(
            color = color,
            startAngle = -50f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = Offset(size.width * 0.48f, size.height * 0.16f),
            size = Size(size.width * 0.40f, size.height * 0.68f),
            style = stroke,
        )
    }
}

private fun DrawScope.drawMic(color: Color, stroke: Stroke, slash: Boolean) {
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width * 0.36f, size.height * 0.08f),
        size = Size(size.width * 0.28f, size.height * 0.42f),
        cornerRadius = CornerRadius(size.width * 0.14f),
        style = stroke,
    )
    drawArc(
        color = color,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(size.width * 0.22f, size.height * 0.28f),
        size = Size(size.width * 0.56f, size.height * 0.40f),
        style = stroke,
    )
    drawLine(color, Offset(size.width * 0.50f, size.height * 0.68f), Offset(size.width * 0.50f, size.height * 0.82f), stroke.width, stroke.cap)
    drawLine(color, Offset(size.width * 0.32f, size.height * 0.82f), Offset(size.width * 0.68f, size.height * 0.82f), stroke.width, stroke.cap)
    if (slash) {
        drawLine(color, Offset(size.width * 0.22f, size.height * 0.78f), Offset(size.width * 0.78f, size.height * 0.16f), stroke.width, stroke.cap)
    }
}

private fun DrawScope.drawHold(color: Color) {
    val radius = CornerRadius(size.width * 0.06f)
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width * 0.22f, size.height * 0.16f),
        size = Size(size.width * 0.18f, size.height * 0.68f),
        cornerRadius = radius,
    )
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width * 0.60f, size.height * 0.16f),
        size = Size(size.width * 0.18f, size.height * 0.68f),
        cornerRadius = radius,
    )
}

private fun DrawScope.drawResume(color: Color) {
    val triangle = Path().apply {
        moveTo(size.width * 0.30f, size.height * 0.16f)
        lineTo(size.width * 0.30f, size.height * 0.84f)
        lineTo(size.width * 0.78f, size.height * 0.50f)
        close()
    }
    drawPath(triangle, color)
}

private fun DrawScope.drawHandset(color: Color, stroke: Stroke, rising: Boolean) {
    val start = if (rising) Offset(size.width * 0.22f, size.height * 0.70f) else Offset(size.width * 0.22f, size.height * 0.30f)
    val end = if (rising) Offset(size.width * 0.78f, size.height * 0.30f) else Offset(size.width * 0.78f, size.height * 0.70f)
    drawLine(color, start, end, stroke.width * 1.35f, StrokeCap.Round)
    val radius = size.minDimension * 0.16f
    drawCircle(color, radius, start, style = stroke)
    drawCircle(color, radius, end, style = stroke)
}
