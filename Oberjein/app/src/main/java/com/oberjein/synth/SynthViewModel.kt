package com.oberjein.synth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class SynthViewModel : ViewModel() {
    val values = mutableStateListOf<Float>().apply { addAll(DEFAULTS.toList()) }
    var presetIndex by mutableIntStateOf(0); private set
    var octave by mutableIntStateOf(3); private set

    val firstNote: Int get() = 12 * (octave + 1)

    fun set(id: Int, value: Float) {
        values[id] = value
        SynthEngine.nativeSetParam(id, value)
    }

    fun pushAll() = values.forEachIndexed { id, v -> SynthEngine.nativeSetParam(id, v) }

    fun loadPreset(index: Int) {
        val i = Math.floorMod(index, PRESETS.size)
        presetIndex = i
        PRESETS[i].values.forEachIndexed { id, v -> values[id] = v }
        SynthEngine.nativeAllNotesOff()
        pushAll()
    }

    fun shiftOctave(delta: Int) {
        octave = (octave + delta).coerceIn(1, 6)
        SynthEngine.nativeAllNotesOff()
    }

    // Safe to call from any thread (MIDI input uses these).
    fun noteOn(note: Int, velocity: Float) = SynthEngine.nativeNoteOn(note, velocity)
    fun noteOff(note: Int) = SynthEngine.nativeNoteOff(note)
    fun pitchBend(value: Float) = SynthEngine.nativePitchBend(value)
    fun panic() = SynthEngine.nativeAllNotesOff()
}
