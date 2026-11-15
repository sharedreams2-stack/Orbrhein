package com.oberjein.synth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oberjein.synth.P
import com.oberjein.synth.PRESETS
import com.oberjein.synth.SynthViewModel
import kotlin.math.roundToInt

private val LFO_SHAPES = listOf("SINE", "TRI", "SAW", "SQUARE", "S&H")

@Composable
private fun K(vm: SynthViewModel, id: Int, label: String) {
    Knob(label, vm.values[id], { vm.set(id, it) })
}

@Composable
private fun T(vm: SynthViewModel, id: Int, label: String) {
    val enabled = !(id == P.FILTER_BP && vm.values[P.LP24] > 0.5f)
    Toggle(label, vm.values[id] > 0.5f, { vm.set(id, if (it) 1f else 0f) }, enabled = enabled)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen(vm: SynthViewModel) {
    Column(Modifier.fillMaxSize().background(Chassis).padding(horizontal = 8.dp, vertical = 6.dp)) {
        TopBar(vm)
        Row(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.weight(0.19f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BrandPanel(Modifier.weight(0.36f))
                Section("MASTER", Modifier.weight(0.28f)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { K(vm, P.VOLUME, "VOLUME") }
                }
                Section("GLOBAL", Modifier.weight(0.36f)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        K(vm, P.SPREAD, "SPREAD")
                        K(vm, P.UNI_DETUNE, "UNI DETUNE")
                        T(vm, P.UNISON, "UNISON")
                        K(vm, P.PORTA, "GLIDE")
                    }
                }
            }
            Column(Modifier.weight(0.27f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Section("OSCILLATORS", Modifier.weight(0.66f)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            T(vm, P.VCO1_SAW, "SAW")
                            T(vm, P.VCO1_PULSE, "PULSE")
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            T(vm, P.VCO2_SAW, "SAW")
                            T(vm, P.VCO2_PULSE, "PULSE")
                        }
                        K(vm, P.PWM, "PW")
                        K(vm, P.VCO2_SEMI, "SEMITONE")
                        K(vm, P.VCO2_DETUNE, "DETUNE")
                        K(vm, P.XMOD, "XMOD")
                        K(vm, P.BRIGHT, "BRIGHT")
                        T(vm, P.SYNC, "SYNC")
                    }
                }
                Section("MIXER", Modifier.weight(0.34f)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        K(vm, P.VCO1_LEVEL, "OSC 1")
                        K(vm, P.VCO2_LEVEL, "OSC 2")
                        K(vm, P.NOISE, "NOISE")
                    }
                }
            }
            Column(Modifier.weight(0.22f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Section("FILTER", Modifier.weight(0.55f)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        K(vm, P.CUTOFF, "CUTOFF")
                        K(vm, P.RESO, "RESONANCE")
                        K(vm, P.FENV_AMT, "ENV AMT")
                        K(vm, P.KEYTRACK, "KEY")
                        K(vm, P.FILTER_MIX, "FILTER MIX")
                        K(vm, P.FILTER_MULTI, "SLOPE")
                        T(vm, P.LP24, "LP24")
                        T(vm, P.FILTER_BP, "BP")
                        T(vm, P.FENV_INVERT, "ENV INV")
                        K(vm, P.SELF_OSC, "SELF OSC")
                    }
                }
                Section("MODULATION", Modifier.weight(0.45f)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        K(vm, P.LFO_RATE, "LFO RATE")
                        K(vm, P.LFO_PITCH, "FREQ AMT")
                        K(vm, P.LFO_PWM, "PW AMT")
                        CycleButton("LFO SHAPE", LFO_SHAPES, (vm.values[P.LFO_WAVE] * 4f).roundToInt().coerceIn(0, 4)) {
                            vm.set(P.LFO_WAVE, it / 4f)
                        }
                        K(vm, P.LFO_FILTER, "FILTER")
                    }
                }
            }
            Column(Modifier.weight(0.32f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Section("FILTER ENVELOPE", Modifier.weight(0.5f)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        K(vm, P.F_A, "ATTACK"); K(vm, P.F_D, "DECAY"); K(vm, P.F_S, "SUSTAIN"); K(vm, P.F_R, "RELEASE")
                    }
                }
                Section("AMPLIFIER ENVELOPE", Modifier.weight(0.5f)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        K(vm, P.A_A, "ATTACK"); K(vm, P.A_D, "DECAY"); K(vm, P.A_S, "SUSTAIN"); K(vm, P.A_R, "RELEASE")
                    }
                }
            }
        }
        Keyboard(
            firstNote = vm.firstNote, whiteKeys = 14,
            onNoteOn = { n, v -> vm.noteOn(n, v) },
            onNoteOff = { n -> vm.noteOff(n) },
            modifier = Modifier.fillMaxWidth().height(88.dp).background(Panel).padding(horizontal = 3.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun BrandPanel(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(ColorWood, WalnutDark))).padding(8.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("OB-Xd", color = Cream, fontSize = 34.sp, fontWeight = FontWeight.Black, letterSpacing = (-2).sp)
        Text("VIRTUAL ANALOG SYNTHESIZER", color = Cream, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        Text("OBERJEIN", color = ElectricBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
    }
}

@Composable
private fun TopBar(vm: SynthViewModel) {
    Row(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("OBERJEIN", color = Cream, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
        Spacer(Modifier.weight(1f))
        BarButton("◀") { vm.loadPreset(vm.presetIndex - 1) }
        Text(PRESETS[vm.presetIndex].name.uppercase(), Modifier.padding(horizontal = 8.dp),
            color = ElectricBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace)
        BarButton("▶") { vm.loadPreset(vm.presetIndex + 1) }
        Spacer(Modifier.weight(1f))
        BarButton("−") { vm.shiftOctave(-1) }
        Text("OCT " + vm.octave, color = Cream, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        BarButton("+") { vm.shiftOctave(1) }
        BarButton("PANIC") { vm.panic() }
    }
}
