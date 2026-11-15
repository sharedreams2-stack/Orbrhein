package com.oberjein.synth.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput

private val WHITE = intArrayOf(0, 2, 4, 5, 7, 9, 11)
private val BLACK_AFTER = intArrayOf(0, 1, 3, 4, 5)  // white-key degrees followed by a black key

/** Returns (midi note, velocity) under the finger, or null. Velocity grows toward the bottom of the key. */
private fun hitTest(p: Offset, w: Float, h: Float, first: Int, whiteKeys: Int): Pair<Int, Float>? {
    if (p.x < 0f || p.x > w || p.y < 0f || p.y > h) return null
    val kw = w / whiteKeys
    val vel = (0.35f + 0.65f * (p.y / h)).coerceIn(0.2f, 1f)
    if (p.y < h * 0.6f) {
        val bw = kw * 0.6f
        for (i in 0 until whiteKeys - 1) {
            val deg = i % 7
            if (deg in BLACK_AFTER) {
                val cx = (i + 1) * kw
                if (p.x in (cx - bw / 2)..(cx + bw / 2)) {
                    return Pair(first + (i / 7) * 12 + WHITE[deg] + 1, vel)
                }
            }
        }
    }
    val idx = (p.x / kw).toInt().coerceIn(0, whiteKeys - 1)
    return Pair(first + (idx / 7) * 12 + WHITE[idx % 7], vel)
}

/** Multi-touch keyboard; sliding a finger across keys plays them in turn. */
@Composable
fun Keyboard(
    firstNote: Int,
    whiteKeys: Int,
    onNoteOn: (Int, Float) -> Unit,
    onNoteOff: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pressed = remember { mutableStateListOf<Int>() }
    val noteOn by rememberUpdatedState(onNoteOn)
    val noteOff by rememberUpdatedState(onNoteOff)

    Canvas(
        modifier.pointerInput(firstNote, whiteKeys) {
            val active = HashMap<PointerId, Int>()
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    for (ch in event.changes) {
                        if (ch.pressed) {
                            val hit = hitTest(ch.position, size.width.toFloat(), size.height.toFloat(), firstNote, whiteKeys)
                            val prev = active[ch.id]
                            if (hit?.first != prev) {
                                if (prev != null) {
                                    active.remove(ch.id); pressed.remove(prev); noteOff(prev)
                                }
                                if (hit != null) {
                                    active[ch.id] = hit.first; pressed.add(hit.first); noteOn(hit.first, hit.second)
                                }
                            }
                            ch.consume()
                        } else {
                            val prev = active.remove(ch.id)
                            if (prev != null) { pressed.remove(prev); noteOff(prev) }
                        }
                    }
                }
            }
        }
    ) {
        val kw = size.width / whiteKeys
        for (i in 0 until whiteKeys) {
            val note = firstNote + (i / 7) * 12 + WHITE[i % 7]
            drawRect(
                if (pressed.contains(note)) Amber else Ivory,
                Offset(i * kw + 1f, 0f), Size(kw - 2f, size.height)
            )
        }
        val bw = kw * 0.6f
        for (i in 0 until whiteKeys - 1) {
            val deg = i % 7
            if (deg in BLACK_AFTER) {
                val note = firstNote + (i / 7) * 12 + WHITE[deg] + 1
                drawRect(
                    if (pressed.contains(note)) Ember else Ebony,
                    Offset((i + 1) * kw - bw / 2, 0f), Size(bw, size.height * 0.6f)
                )
            }
        }
    }
}
