package com.oberjein.synth.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.graphics.Brush
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

@Composable
fun Knob(label: String, value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier, diameter: Dp = 42.dp) {
    val current by rememberUpdatedState(value)
    val callback by rememberUpdatedState(onChange)
    Column(modifier.width(diameter + 12.dp).padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(diameter).pointerInput(Unit) {
            detectVerticalDragGestures { change, dragAmount ->
                change.consume()
                callback((current - dragAmount / 240f).coerceIn(0f, 1f))
            }
        }) {
            val radius = size.minDimension * 0.43f
            drawCircle(Brush.radialGradient(listOf(Color(0xFF73777D), Color(0xFF30343A), Color(0xFF111316)),
                center = Offset(size.width * 0.35f, size.height * 0.28f), radius = size.minDimension * 0.78f), radius, center)
            drawCircle(Color(0xFF08090B), radius, center, style = Stroke(1.2.dp.toPx()))
            drawCircle(Color(0xFF777C82), radius * 0.82f, center, style = Stroke(0.8.dp.toPx()))
            val topLeft = Offset(2.dp.toPx(), 2.dp.toPx())
            val arcSize = Size(size.width - 4.dp.toPx(), size.height - 4.dp.toPx())
            drawArc(Color(0xFF45494F), 135f, 270f, false, topLeft, arcSize, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            drawArc(ElectricBlue, 135f, 270f * value, false, topLeft, arcSize, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            val angle = Math.toRadians((135f + 270f * value).toDouble())
            val dir = Offset(cos(angle).toFloat(), sin(angle).toFloat())
            drawLine(Color(0xFFF4F0E8), center + dir * (radius * 0.20f), center + dir * (radius * 0.78f),
                strokeWidth = 2.2.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(Color(0xFFBFC4CA), 1.5.dp.toPx(), center + dir * (radius * 0.78f))
        }
        Text(label, fontSize = 8.sp, color = Cream, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun Toggle(label: String, on: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 2.dp, vertical = 3.dp).width(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(3.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF3A3D42), Color(0xFF17191D))))
            .clickable { onChange(!on) }, contentAlignment = Alignment.Center) {
            Text(if (on) "●" else "○", color = if (on) LedRed else Dim, fontSize = 11.sp)
        }
        Text(label, fontSize = 7.sp, color = Cream, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun CycleButton(label: String, options: List<String>, index: Int, onChange: (Int) -> Unit) {
    Column(Modifier.width(54.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(3.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF45494E), Color(0xFF17191C))))
            .clickable { onChange((index + 1) % options.size) }, contentAlignment = Alignment.Center) {
            Text(options[index], color = ElectricBlue, fontSize = 9.sp, fontWeight = FontWeight.Black)
        }
        Text(label, fontSize = 7.sp, color = Cream, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun BarButton(label: String, onClick: () -> Unit) {
    Box(Modifier.padding(horizontal = 2.dp).clip(RoundedCornerShape(3.dp))
        .background(Color(0xFF292C30)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Cream)
    }
}

@Composable
fun Section(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    CompositionLocalProvider(LocalTint provides ElectricBlue) {
        Column(modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp)).background(Panel).padding(horizontal = 7.dp, vertical = 5.dp)) {
            Text(title, fontSize = 9.sp, fontWeight = FontWeight.Black, color = Cream, letterSpacing = 0.6.sp)
            Spacer(Modifier.height(3.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(ElectricBlue))
            Spacer(Modifier.height(5.dp))
            content()
        }
    }
}
