package com.oberjein.synth.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/** Rotary knob, 0..1. Drag up/down to change. */
@Composable
fun Knob(
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 46.dp,
) {
    val tint = LocalTint.current
    val current by rememberUpdatedState(value)
    val callback by rememberUpdatedState(onChange)
    Column(
        modifier.width(diameter + 16.dp).padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(
            Modifier.size(diameter).pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    callback((current - dragAmount / 260f).coerceIn(0f, 1f))
                }
            }
        ) {
            val stroke = 4.dp.toPx()
            val topLeft = Offset(stroke, stroke)
            val arc = Size(size.width - 2 * stroke, size.height - 2 * stroke)
            val style = Stroke(stroke, cap = StrokeCap.Round)
            drawArc(Track, 135f, 270f, false, topLeft, arc, style = style)
            if (current > 0.005f) drawArc(tint, 135f, 270f * current, false, topLeft, arc, style = style)
            val a = Math.toRadians((135f + 270f * current).toDouble())
            val r = size.minDimension / 2f - stroke
            val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
            drawLine(
                Cream, center + dir * (r * 0.3f), center + dir * (r * 0.9f),
                strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round
            )
        }
        Text(label, fontSize = 10.sp, color = Dim, maxLines = 1)
    }
}

@Composable
fun Toggle(label: String, on: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val tint = LocalTint.current
    Box(
        modifier.padding(2.dp).clip(RoundedCornerShape(4.dp))
            .background(if (on) tint else Track)
            .clickable { onChange(!on) }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (on) Chassis else Dim)
    }
}

/** Tap to step through a short list of options. */
@Composable
fun CycleButton(label: String, options: List<String>, index: Int, onChange: (Int) -> Unit) {
    val tint = LocalTint.current
    Column(Modifier.width(62.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.clip(RoundedCornerShape(4.dp)).background(Track)
                .clickable { onChange((index + 1) % options.size) }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(options[index], fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tint)
        }
        Text(label, fontSize = 10.sp, color = Dim, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
fun BarButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.padding(horizontal = 3.dp).clip(RoundedCornerShape(4.dp))
            .background(Color(0x33000000))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Cream)
    }
}

/** A panel section with a colored legend stripe; children pick up the stripe color. */
@Composable
fun Section(title: String, tint: Color, content: @Composable RowScope.() -> Unit) {
    CompositionLocalProvider(LocalTint provides tint) {
        Column(Modifier.padding(4.dp).clip(RoundedCornerShape(6.dp)).background(Panel)) {
            Box(Modifier.fillMaxWidth().height(3.dp).background(tint))
            Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = tint)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically, content = content)
            }
        }
    }
}
