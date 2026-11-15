package com.oberjein.synth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oberjein.synth.*

private val expTargets = listOf(
    FxP.ACTIVITY to "ACTIVITY", FxP.SHAPE to "SHAPE", FxP.FILTER to "FILTER",
    FxP.MIX to "MIX", FxP.REPEATS to "REPEATS", FxP.SPACE to "SPACE", FxP.LOOP_LEVEL to "LOOP LEVEL"
)

@Composable
fun FxScreen(vm: SynthViewModel) {
    val v = vm.fxValues
    val effect = (v[FxP.EFFECT] * 10f).toInt().coerceIn(0, 10)
    val variation = (v[FxP.VARIATION] * 3f).toInt().coerceIn(0, 3)
    var userSlot by remember { mutableIntStateOf(0) }
    var expSelection by remember { mutableIntStateOf(0) }
    var presetName by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().background(Chassis).verticalScroll(rememberScrollState()).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth().background(Panel).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("OBERJEIN / FX", color = ElectricBlue, fontSize = 17.sp, fontWeight = FontWeight.Black)
                Text("GRANULAR • LOOP • TEXTURE • MULTIDELAY", color = Cream, fontSize = 8.sp)
            }
            Spacer(Modifier.weight(1f))
            Text(if (v[FxP.ENABLE] > 0.5f) "FX ENGAGED" else "FX BYPASSED", color = if (v[FxP.ENABLE] > 0.5f) ElectricBlue else Cream, fontSize = 9.sp)
            Switch(checked = v[FxP.ENABLE] > 0.5f, onCheckedChange = { vm.setFx(FxP.ENABLE, if (it) 1f else 0f) })
            FxButton("SYNTH") { vm.setFxPage(false) }
            FxButton("SUPER ARP") { vm.setArpPage(true) }
        }

        FxPanel("EFFECT SELECTOR • 11 ALGORITHMS / 44 VARIATIONS") {
            FxLine("FAMILY") {
                FxFamily.values().forEach { family ->
                    FxButton(family.title, selected = effect in family.effects) {
                        vm.selectFxEffect(family.effects.first, 0)
                    }
                }
            }
            FxLine("EFFECT") {
                FX_EFFECT_NAMES.forEachIndexed { index, name ->
                    FxButton(name, selected = effect == index) { vm.selectFxEffect(index, variation) }
                }
            }
            FxLine("VARIATION") {
                FX_VARIATIONS.forEachIndexed { index, name ->
                    FxButton(name, selected = variation == index) { vm.selectFxEffect(effect, index) }
                }
            }
            Text("A–D presets use the Activity and Repeats macros to reshape multiple internal parameters for each algorithm.", color = Color.LightGray, fontSize = 9.sp)
        }

        FxPanel("PRIMARY MACROS") {
            FxSlider("TIME / SUBDIV", v[FxP.TIME], 0f..1f, { vm.setFx(FxP.TIME, it) }, "%.0f%%", 100f)
            FxSlider("ACTIVITY", v[FxP.ACTIVITY], 0f..1f, { vm.setFx(FxP.ACTIVITY, it) }, "%.0f%%", 100f)
            FxSlider("REPEATS", v[FxP.REPEATS], 0f..1f, { vm.setFx(FxP.REPEATS, it) }, "%.0f%%", 100f)
            FxSlider("SHAPE", v[FxP.SHAPE], 0f..1f, { vm.setFx(FxP.SHAPE, it) }, "%.0f%%", 100f)
            FxSlider("FILTER", v[FxP.FILTER], 0f..1f, { vm.setFx(FxP.FILTER, it) }, "%.0f%%", 100f)
            FxSlider("MIX", v[FxP.MIX], 0f..1f, { vm.setFx(FxP.MIX, it) }, "%.0f%%", 100f)
            FxSlider("SPACE / REVERB", v[FxP.SPACE], 0f..1f, { vm.setFx(FxP.SPACE, it) }, "%.0f%%", 100f)
            FxSlider("LOOP LEVEL", v[FxP.LOOP_LEVEL], 0f..1f, { vm.setFx(FxP.LOOP_LEVEL, it) }, "%.0f%%", 100f)
        }

        FxPanel("SHIFT / SECONDARY CONTROLS") {
            FxSlider("PITCH MOD DEPTH", v[FxP.PITCH_DEPTH], 0f..1f, { vm.setFx(FxP.PITCH_DEPTH, it) }, "%.0f%%", 100f)
            FxSlider("PITCH MOD RATE", v[FxP.PITCH_RATE], 0f..1f, { vm.setFx(FxP.PITCH_RATE, it) }, "%.0f%%", 100f)
            FxSlider("FILTER RESONANCE", v[FxP.RESONANCE], 0f..1f, { vm.setFx(FxP.RESONANCE, it) }, "%.0f%%", 100f)
            FxSlider("EFFECT MASTER", v[FxP.FX_MASTER], 0f..1f, { vm.setFx(FxP.FX_MASTER, it) }, "%.0f%%", 100f)
            FxSlider("LOOP FADE TIME", v[FxP.LOOP_FADE], 0f..1f, { vm.setFx(FxP.LOOP_FADE, it) }, "%.1f s", 0.03f + v[FxP.LOOP_FADE] * 2f)
            FxChoice("REVERB MODE", listOf("ROOM", "PLATE", "HALL", "SHIMMER"), (v[FxP.REVERB_MODE] * 3f).toInt().coerceIn(0,3)) {
                vm.setFx(FxP.REVERB_MODE, it / 3f)
            }
        }

        FxPanel("TEMPO / PERFORMANCE") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("TEMPO", color = Cream, fontSize = 10.sp)
                Spacer(Modifier.weight(1f))
                Text("%.1f BPM".format(v[FxP.TEMPO]), color = ElectricBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                FxButton("TAP TEMPO") { vm.tapFxTempo() }
            }
            FxSlider("GLOBAL TEMPO", v[FxP.TEMPO], 30f..300f, { vm.setFx(FxP.TEMPO, it) }, "%.1f BPM")
            FxSwitch("TEMPO MODE • FIXED EIGHTH NOTE", v[FxP.TEMPO_MODE] > 0.5f) {
                vm.setFx(FxP.TEMPO_MODE, if (it) 1f else 0f)
            }
            FxSwitch("REVERSE PLAYBACK", v[FxP.REVERSE] > 0.5f) {
                vm.setFx(FxP.REVERSE, if (it) 1f else 0f)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FxButton(if (vm.fxHoldActive) "HOLD: FROZEN" else "HOLD SAMPLER") { vm.toggleFxHold() }
                FxButton("RELEASE HOLD", selected = !vm.fxHoldActive) { if (vm.fxHoldActive) vm.toggleFxHold() }
            }
            Text("Hold Sampler freezes the recent stereo buffer for a sustained texture. The sampler and Phrase Looper run in the native audio callback.", color = Color.LightGray, fontSize = 9.sp)
        }

        FxPanel("PHRASE LOOPER • STEREO / UP TO 60 SECONDS") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FxButton("REC") { vm.fxLooperCommand(1) }
                FxButton("PLAY") { vm.fxLooperCommand(2) }
                FxButton("DUB") { vm.fxLooperCommand(3) }
                FxButton("STOP / FADE") { vm.fxLooperCommand(0) }
                FxButton("ERASE") { vm.fxLooperCommand(4) }
            }
            Text(when (vm.fxLoopState) { 1 -> "RECORDING"; 2 -> "PLAYBACK"; else -> "STOPPED" }, color = ElectricBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            FxSlider("LOOP LEVEL", v[FxP.LOOP_LEVEL], 0f..1f, { vm.setFx(FxP.LOOP_LEVEL, it) }, "%.0f%%", 100f)
            Text("REC captures the post-effect stereo output. DUB records one overdub pass. STOP applies the configured fade; ERASE clears the loop immediately.", color = Color.LightGray, fontSize = 9.sp)
        }

        FxPanel("16 USER PRESETS") {
            FxLine("SLOT") {
                (0..15).forEach { slot ->
                    FxButton((slot + 1).toString().padStart(2, '0'), selected = userSlot == slot) { userSlot = slot }
                }
            }
            Text(vm.fxUserPresets[userSlot].name, color = ElectricBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("NAME", color = Cream, fontSize = 8.sp)
                androidx.compose.material3.OutlinedTextField(
                    value = presetName, onValueChange = { presetName = it.take(20) },
                    modifier = Modifier.weight(1f).height(54.dp), singleLine = true,
                    placeholder = { Text("USER ${(userSlot+1).toString().padStart(2, '0')}", fontSize = 9.sp) }
                )
            }
            FxLine("ACTIONS") {
                FxButton("LOAD") { vm.loadFxUserPreset(userSlot) }
                FxButton("SAVE CURRENT") { vm.saveFxUserPreset(userSlot, presetName.ifBlank { "USER ${(userSlot+1).toString().padStart(2, '0')}" }) }
                FxButton("FACTORY 44") { vm.selectFxEffect(effect, variation) }
            }
            Text("Slots store effect parameters. The active Phrase Looper is a shared performance buffer, not copied into each user slot.", color = Color.LightGray, fontSize = 8.sp)
        }

        FxPanel("GLOBAL CONFIGURATION") {
            FxChoice("OUTPUT ROUTING", listOf("MONO", "STEREO", "WIDE", "MONO SUM"), (v[FxP.STEREO_MODE] * 3f).toInt().coerceIn(0,3)) {
                vm.setFx(FxP.STEREO_MODE, it / 3f)
            }
            FxChoice("BYPASS", listOf("BUFFERED", "TRAILS", "SOFT BYPASS"), (v[FxP.BYPASS_MODE] * 2f).toInt().coerceIn(0,2)) {
                vm.setFx(FxP.BYPASS_MODE, it / 2f)
            }
            FxSwitch("TRAILS ENABLED", v[FxP.TRAILS] > 0.5f) { vm.setFx(FxP.TRAILS, if (it) 1f else 0f) }
            Text("True relay bypass cannot be reproduced by Android software. SOFT BYPASS mutes the wet path; a hardware relay would require external audio hardware.", color = Color.LightGray, fontSize = 9.sp)
        }

        FxPanel("MIDI / EXPRESSION ASSIGN") {
            FxChoice("EXPRESSION TARGET", expTargets.map { it.second }, expSelection) { expSelection = it; vm.selectMidiTarget(P.COUNT + expTargets[it].first) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                FxButton(if (vm.isMidiLearning) "WAITING FOR CC…" else "MIDI LEARN") {
                    vm.selectMidiTarget(P.COUNT + expTargets[expSelection].first)
                    vm.beginMidiLearn()
                }
                FxButton("CLEAR CC") { vm.clearMidiMappings() }
                Text("${vm.midiMappings.size} mapped", color = ElectricBlue, fontSize = 9.sp)
            }
            Text("Connect a MIDI expression pedal/controller and send a CC while Learn is armed. The mapping is included in the full JSON backup.", color = Color.LightGray, fontSize = 9.sp)
        }
    }
}

@Composable
private fun FxPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(Panel).padding(9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = ElectricBlue, fontSize = 11.sp, fontWeight = FontWeight.Black)
        content()
    }
}

@Composable
private fun FxLine(label: String, content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Cream, fontSize = 8.sp, modifier = Modifier.width(62.dp))
        content()
    }
}

@Composable
private fun FxButton(label: String, selected: Boolean = false, onClick: () -> Unit) {
    Button(onClick = onClick, contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (selected) ElectricBlue else WalnutDark, contentColor = if (selected) Color.Black else Cream),
        modifier = Modifier.heightIn(min = 30.dp)) {
        Text(label, fontSize = 8.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FxSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValue: (Float) -> Unit, format: String, scale: Float = 1f) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Cream, fontSize = 9.sp, modifier = Modifier.width(125.dp))
        Slider(value = value.coerceIn(range), onValueChange = onValue, valueRange = range, modifier = Modifier.weight(1f))
        Text(String.format(java.util.Locale.US, format, value * scale), color = ElectricBlue, fontSize = 9.sp, modifier = Modifier.width(65.dp))
    }
}

@Composable
private fun FxSwitch(label: String, value: Boolean, onValue: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Cream, fontSize = 9.sp, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onValue)
    }
}

@Composable
private fun FxChoice(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Cream, fontSize = 9.sp, modifier = Modifier.width(125.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEachIndexed { index, option -> FxButton(option, selected == index) { onSelect(index) } }
        }
    }
}
