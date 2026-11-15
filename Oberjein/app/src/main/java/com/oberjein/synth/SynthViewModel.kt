package com.oberjein.synth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import kotlin.math.roundToInt

class SynthViewModel : ViewModel() {
    val values = mutableStateListOf<Float>().apply { addAll(DEFAULTS.toList()) }
    var presetIndex by mutableIntStateOf(0); private set
    var octave by mutableIntStateOf(3); private set
    var midiTarget by mutableIntStateOf(P.CUTOFF); private set
    var isMidiLearning by mutableStateOf(false); private set
    val midiMappings = mutableStateMapOf<Int, Int>()

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

    fun selectMidiTarget(id: Int) { midiTarget = id }

    fun beginMidiLearn() { isMidiLearning = true }

    fun clearMidiMappings() { midiMappings.clear(); isMidiLearning = false }

    fun onMidiCC(controller: Int, value: Int) {
        if (isMidiLearning) {
            midiMappings[controller] = midiTarget
            isMidiLearning = false
            return
        }
        val target = midiMappings[controller]
        if (target != null) {
            val normalized = value.coerceIn(0, 127) / 127f
            set(target, if (target == P.OVERSAMPLING) (normalized * 2f).roundToInt() / 2f else normalized)
        } else if (controller == 1) {
            set(P.LFO_PITCH, value.coerceIn(0, 127) / 127f)
        }
        if (controller == 123) panic()
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
