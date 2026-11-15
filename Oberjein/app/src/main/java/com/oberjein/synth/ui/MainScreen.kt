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
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import kotlin.math.pow
import kotlin.math.log10
import kotlin.math.max
import java.util.Locale

private val LFO_SHAPES = listOf("SINE", "TRI", "SAW", "SQUARE", "S&H")
private val VOICE_OPTIONS = (1..16).map { it.toString().padStart(2, '0') }
private val LEGATO_OPTIONS = listOf("OFF", "KEEP F", "KEEP A", "KEEP ALL")
private val VAM_OPTIONS = listOf("LAST", "LOW")
private val MIDI_TARGET_IDS = listOf(P.CUTOFF, P.RESO, P.FINE_TUNE, P.TRANSPOSE, P.OVERSAMPLING, P.LFO_RATE, P.LFO_PITCH, P.LFO_FILTER, P.PWM, P.VOLUME)
private val MIDI_TARGET_OPTIONS = listOf("CUTOFF", "RESONANCE", "FINE TUNE", "TRANSPOSE", "OVERSAMPLE", "LFO RATE", "LFO PITCH", "LFO FILTER", "PW", "VOLUME")

@Composable
private fun K(vm: SynthViewModel, id: Int, label: String) {
    val value = vm.values[id]
    val readout = when (id) {
        P.CUTOFF -> String.format(Locale.US, "%.0f Hz", 20.0 * 2.0.pow(value.toDouble() * 9.81))
        P.RESO -> String.format(Locale.US, "Q %.1f", 1.0 / max(0.025, 2.0 - value.toDouble() * 1.92))
        P.F_A, P.F_D, P.F_R, P.A_A, P.A_D, P.A_R -> {
            val seconds = 0.001 * 10.0.pow(value.toDouble() * 4.0)
            if (seconds < 1.0) String.format(Locale.US, "%.0f ms", seconds * 1000.0)
            else String.format(Locale.US, "%.1f s", seconds)
        }
        P.VOLUME -> String.format(Locale.US, "%.1f dB", 20.0 * log10(max(0.0001, value.toDouble())))
        P.VCO2_SEMI -> String.format(Locale.US, "%+d st", ((value - 0.5f) * 24f).roundToInt())
        P.VCO2_DETUNE -> String.format(Locale.US, "%.1f ct", value * 100f)
        P.SPREAD -> String.format(Locale.US, "%.1f ct", value * 8f)
        P.UNI_DETUNE -> String.format(Locale.US, "%.1f ct", value * 30f)
        P.LFO_RATE, P.LFO2_RATE -> String.format(Locale.US, "%.2f Hz", 0.1 * 300.0.pow(value.toDouble()))
        P.PORTA -> if (value < 0.01f) "OFF" else String.format(Locale.US, "%.0f ms", 10.0 * 300.0.pow(value.toDouble()))
        P.TRANSPOSE -> String.format(Locale.US, "%+d OCT", ((value - 0.5f) * 8f).roundToInt())
        P.FINE_TUNE -> String.format(Locale.US, "%+.1f ct", (value - 0.5f) * 200f)
        else -> String.format(Locale.US, "%.0f%%", value * 100f)
    }
    Knob(label, value, { vm.set(id, it) }, readout = readout)
}

@Composable
private fun T(vm: SynthViewModel, id: Int, label: String) {
    val enabled = !(id == P.FILTER_BP && vm.values[P.LP24] > 0.5f)
    Toggle(label, vm.values[id] > 0.5f, { vm.set(id, if (it) 1f else 0f) }, enabled = enabled)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen(vm: SynthViewModel) {
    val context = LocalContext.current
    val exportState = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(vm.exportStateJson()) } }
    }
    val importState = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { vm.restoreStateJson(it.readText()) } }
    }
    if (vm.showArp) { ArpeggiatorScreen(vm); return }
    Column(Modifier.fillMaxSize().background(Chassis).padding(horizontal = 8.dp, vertical = 6.dp)) {
        TopBar(vm, onExport = { exportState.launch("Oberjein-backup.json") }, onImport = { importState.launch(arrayOf("application/json", "text/*")) })
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
                        CycleButton("VOICES", VOICE_OPTIONS, (vm.values[P.VOICE_COUNT] * 15f).roundToInt().coerceIn(0, 15)) {
                            vm.set(P.VOICE_COUNT, it / 15f)
                        }
                        CycleButton("UNI VOICES", VOICE_OPTIONS, (vm.values[P.UNI_VOICES] * 15f).roundToInt().coerceIn(0, 15)) {
                            vm.set(P.UNI_VOICES, it / 15f)
                        }
                        CycleButton("VAM", VAM_OPTIONS, if (vm.values[P.VAM] > 0.5f) 1 else 0) {
                            vm.set(P.VAM, it.toFloat())
                        }
                        T(vm, P.PERSISTENT_VOICING, "PERSIST")
                        CycleButton("LEGATO", LEGATO_OPTIONS, (vm.values[P.LEGATO] * 3f).roundToInt().coerceIn(0, 3)) {
                            vm.set(P.LEGATO, it / 3f)
                        }
                        K(vm, P.PORTA, "GLIDE")
                        CycleButton("OVERSAMPLE", listOf("1X", "2X", "4X"),
                            (vm.values[P.OVERSAMPLING] * 2f).roundToInt().coerceIn(0, 2)) {
                            vm.set(P.OVERSAMPLING, it / 2f)
                        }
                        K(vm, P.TRANSPOSE, "TRANSPOSE")
                        K(vm, P.FINE_TUNE, "FINE TUNE")
                        CycleButton("MIDI TARGET", MIDI_TARGET_OPTIONS,
                            MIDI_TARGET_IDS.indexOf(vm.midiTarget).coerceAtLeast(0)) {
                            vm.selectMidiTarget(MIDI_TARGET_IDS[it])
                        }
                        BarButton(if (vm.isMidiLearning) "WAIT MIDI" else "MIDI LEARN") {
                            vm.beginMidiLearn()
                        }
                        BarButton("CLEAR CC") { vm.clearMidiMappings() }
                        Text(if (vm.isMidiLearning) "MOVE A CONTROLLER" else "CC ${vm.midiMappings.size} MAPPED",
                            color = ElectricBlue, fontSize = 7.sp, fontWeight = FontWeight.Bold)
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
                Section("DUAL MODULATION", Modifier.weight(0.45f)) {
                    Text("LFO 1", color = ElectricBlue, fontSize = 7.sp, fontWeight = FontWeight.Black)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        K(vm, P.LFO_RATE, "RATE")
                        K(vm, P.LFO_PITCH, "PITCH")
                        K(vm, P.LFO_PWM, "PW")
                        CycleButton("WAVE", LFO_SHAPES, (vm.values[P.LFO_WAVE] * 4f).roundToInt().coerceIn(0, 4)) {
                            vm.set(P.LFO_WAVE, it / 4f)
                        }
                        K(vm, P.LFO_FILTER, "FILTER")
                    }
                    Text("LFO 2", color = ElectricBlue, fontSize = 7.sp, fontWeight = FontWeight.Black)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        K(vm, P.LFO2_RATE, "RATE")
                        K(vm, P.LFO2_PITCH, "PITCH")
                        K(vm, P.LFO2_PWM, "PW")
                        CycleButton("WAVE", LFO_SHAPES, (vm.values[P.LFO2_WAVE] * 4f).roundToInt().coerceIn(0, 4)) {
                            vm.set(P.LFO2_WAVE, it / 4f)
                        }
                        K(vm, P.LFO2_FILTER, "FILTER")
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
private fun TopBar(vm: SynthViewModel, onExport: () -> Unit, onImport: () -> Unit) {
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
        BarButton("UNDO") { vm.undo() }
        BarButton("REDO") { vm.redo() }
        BarButton("EXPORT") { onExport() }
        BarButton("IMPORT") { onImport() }
        BarButton("SUPER ARP") { vm.setArpPage(true) }
        BarButton("PANIC") { vm.panic() }
    }
}
