package com.oberjein.synth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oberjein.synth.P
import com.oberjein.synth.PRESETS
import com.oberjein.synth.SynthViewModel
import kotlin.math.roundToInt

private val LFO_SHAPES = listOf("Sine", "Tri", "Saw", "Square", "S&H")

@Composable
private fun K(vm: SynthViewModel, id: Int, label: String) =
    Knob(label, vm.values[id], { vm.set(id, it) })

@Composable
private fun T(vm: SynthViewModel, id: Int, label: String) =
    Toggle(label, vm.values[id] > 0.5f, { vm.set(id, if (it) 1f else 0f) })

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen(vm: SynthViewModel) {
    Column(Modifier.fillMaxSize().background(Chassis).safeDrawingPadding()) {
        TopBar(vm)
        FlowRow(
            Modifier.weight(1f).fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 4.dp)
        ) {
            Section("Oscillator 1", Amber) {
                Column { T(vm, P.VCO1_SAW, "Saw"); T(vm, P.VCO1_PULSE, "Pulse") }
                K(vm, P.VCO1_LEVEL, "Level")
            }
            Section("Oscillator 2", Amber) {
                Column { T(vm, P.VCO2_SAW, "Saw"); T(vm, P.VCO2_PULSE, "Pulse") }
                K(vm, P.VCO2_LEVEL, "Level")
                K(vm, P.VCO2_SEMI, "Semitone")
                K(vm, P.VCO2_DETUNE, "Detune")
                T(vm, P.SYNC, "Sync")
            }
            Section("Mixer", Amber) {
                K(vm, P.PWM, "Pulse width")
                K(vm, P.NOISE, "Noise")
            }
            Section("Filter", Ember) {
                K(vm, P.CUTOFF, "Cutoff")
                K(vm, P.RESO, "Resonance")
                K(vm, P.FENV_AMT, "Env amount")
                K(vm, P.KEYTRACK, "Key track")
                T(vm, P.POLES, "24 dB")
            }
            Section("Filter envelope", Teal) {
                K(vm, P.F_A, "Attack"); K(vm, P.F_D, "Decay"); K(vm, P.F_S, "Sustain"); K(vm, P.F_R, "Release")
            }
            Section("Amp envelope", Teal) {
                K(vm, P.A_A, "Attack"); K(vm, P.A_D, "Decay"); K(vm, P.A_S, "Sustain"); K(vm, P.A_R, "Release")
            }
            Section("LFO", Iris) {
                CycleButton(
                    "Shape", LFO_SHAPES, (vm.values[P.LFO_WAVE] * 4f).roundToInt().coerceIn(0, 4)
                ) { vm.set(P.LFO_WAVE, it / 4f) }
                K(vm, P.LFO_RATE, "Rate")
                K(vm, P.LFO_PITCH, "To pitch")
                K(vm, P.LFO_FILTER, "To filter")
                K(vm, P.LFO_PWM, "To width")
            }
            Section("Voices", Cream) {
                K(vm, P.PORTA, "Glide")
                T(vm, P.UNISON, "Unison")
                K(vm, P.UNI_DETUNE, "Spread")
                K(vm, P.VOLUME, "Volume")
            }
        }
        Keyboard(
            firstNote = vm.firstNote,
            whiteKeys = 14,
            onNoteOn = { n, v -> vm.noteOn(n, v) },
            onNoteOff = { n -> vm.noteOff(n) },
            modifier = Modifier.fillMaxWidth().height(120.dp).padding(horizontal = 4.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun TopBar(vm: SynthViewModel) {
    Row(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Walnut, WalnutDark)))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Oberjein", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Cream)
        Spacer(Modifier.width(16.dp))
        BarButton("<") { vm.loadPreset(vm.presetIndex - 1) }
        Text(
            PRESETS[vm.presetIndex].name,
            Modifier.widthIn(min = 100.dp),
            color = Cream, fontSize = 14.sp, textAlign = TextAlign.Center
        )
        BarButton(">") { vm.loadPreset(vm.presetIndex + 1) }
        Spacer(Modifier.weight(1f))
        BarButton("-") { vm.shiftOctave(-1) }
        Text("Octave ${vm.octave}", color = Cream, fontSize = 12.sp)
        BarButton("+") { vm.shiftOctave(1) }
        Spacer(Modifier.width(8.dp))
        BarButton("Panic") { vm.panic() }
    }
}
