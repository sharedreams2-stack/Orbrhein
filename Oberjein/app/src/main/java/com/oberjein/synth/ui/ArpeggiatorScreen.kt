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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oberjein.synth.*

private val arpTargets = listOf(
    P.VCO1_SAW to "OSC1 SAW", P.VCO1_PULSE to "OSC1 PULSE", P.VCO1_LEVEL to "OSC1 LEVEL",
    P.VCO2_SAW to "OSC2 SAW", P.VCO2_PULSE to "OSC2 PULSE", P.VCO2_LEVEL to "OSC2 LEVEL",
    P.VCO2_SEMI to "OSC2 SEMI", P.VCO2_DETUNE to "OSC2 DETUNE", P.SYNC to "SYNC",
    P.PWM to "PULSE WIDTH", P.NOISE to "NOISE", P.CUTOFF to "CUTOFF", P.RESO to "RESONANCE",
    P.POLES to "FILTER POLES", P.FENV_AMT to "FILTER ENV AMT", P.KEYTRACK to "KEYTRACK",
    P.F_A to "FILTER ATTACK", P.F_D to "FILTER DECAY", P.F_S to "FILTER SUSTAIN", P.F_R to "FILTER RELEASE",
    P.A_A to "AMP ATTACK", P.A_D to "AMP DECAY", P.A_S to "AMP SUSTAIN", P.A_R to "AMP RELEASE",
    P.LFO_RATE to "LFO1 RATE", P.LFO_WAVE to "LFO1 WAVE", P.LFO_PITCH to "LFO1 PITCH",
    P.LFO_FILTER to "LFO1 FILTER", P.LFO_PWM to "LFO1 PWM", P.PORTA to "GLIDE",
    P.UNISON to "UNISON", P.UNI_DETUNE to "UNI DETUNE", P.VOLUME to "VOLUME",
    P.SPREAD to "SPREAD", P.XMOD to "CROSS MOD", P.BRIGHT to "BRIGHT",
    P.FILTER_MIX to "FILTER MIX", P.FILTER_BP to "FILTER BP", P.LP24 to "LP24",
    P.FILTER_MULTI to "FILTER SLOPE", P.FENV_INVERT to "ENV INVERT", P.SELF_OSC to "SELF OSC",
    P.LFO2_RATE to "LFO2 RATE", P.LFO2_WAVE to "LFO2 WAVE", P.LFO2_PITCH to "LFO2 PITCH",
    P.LFO2_FILTER to "LFO2 FILTER", P.LFO2_PWM to "LFO2 PWM", P.VOICE_COUNT to "VOICE COUNT",
    P.UNI_VOICES to "UNI VOICES", P.VAM to "VAM", P.PERSISTENT_VOICING to "PERSIST",
    P.LEGATO to "LEGATO", P.OVERSAMPLING to "OVERSAMPLING", P.TRANSPOSE to "TRANSPOSE",
    P.FINE_TUNE to "FINE TUNE"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ArpeggiatorScreen(vm: SynthViewModel) {
    val cfg = vm.arpConfig
    var selected by remember { mutableIntStateOf(0) }
    val step = cfg.steps[selected]
    val directions = ArpDirection.values().toList()
    val scales = ArpScale.values().toList()
    Column(Modifier.fillMaxSize().background(Chassis).verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().background(Panel).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("SUPER ARP", color = ElectricBlue, fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black)
            Spacer(Modifier.weight(1f))
            Text(if (cfg.enabled) "RUNNING" else "STOPPED", color = if (cfg.enabled) ElectricBlue else Cream, fontSize = 9.sp)
            Button(onClick = { vm.toggleArp() }, colors = ButtonDefaults.buttonColors(containerColor = WalnutDark)) { Text(if (cfg.enabled) "STOP" else "START") }
            Button(onClick = { vm.setArpPage(false) }, colors = ButtonDefaults.buttonColors(containerColor = WalnutDark)) { Text("SYNTH") }
        }
        ArpPanel("PERFORMANCE") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("HOLD / LATCH", color = Cream, fontSize = 10.sp); Spacer(Modifier.weight(1f))
                Switch(checked = cfg.hold, onCheckedChange = { vm.updateArp { c -> c.copy(hold = it) } })
            }
            Text("Each key press toggles that note in or out of the latched pool.", color = Color.LightGray, fontSize = 9.sp)
            ArpSlider("TEMPO", cfg.bpm, 30f..300f, { vm.updateArp { c -> c.copy(bpm = it) } }, "%.1f BPM")
            ArpChoice("RATE", listOf("1/1","1/2","1/4","1/8","1/16","1/32"), listOf(1,2,4,8,16,32).indexOf(cfg.division).coerceAtLeast(0)) { v -> vm.updateArp { c -> c.copy(division = listOf(1,2,4,8,16,32)[v]) } }
            ArpChoice("DIRECTION", directions.map { it.name.replace('_',' ') }, directions.indexOf(cfg.direction)) { v -> vm.updateArp { c -> c.copy(direction = directions[v]) } }
            ArpChoice("OCTAVES", (1..4).map { it.toString() }, cfg.octaveRange - 1) { v -> vm.updateArp { c -> c.copy(octaveRange = v + 1) } }
            ArpChoice("SCALE", scales.map { it.name.replace('_',' ') }, scales.indexOf(cfg.scale)) { v -> vm.updateArp { c -> c.copy(scale = scales[v]) } }
            ArpChoice("ROOT", listOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B"), cfg.root.coerceIn(0,11)) { v -> vm.updateArp { c -> c.copy(root = v) } }
            ArpChoice("CHORD", (1..8).map { "$it" }, cfg.chordSize - 1) { v -> vm.updateArp { c -> c.copy(chordSize = v+1) } }
            ArpSlider("STRUM", cfg.strumMs.toFloat(), 0f..120f, { v -> vm.updateArp { c -> c.copy(strumMs = v.toInt()) } }, "%.0f ms")
            ArpChoice("INTERVAL SHIFT", (-12..12).map { "%+d".format(it) }, cfg.intervalShift + 12) { v -> vm.updateArp { c -> c.copy(intervalShift = v-12) } }
            ArpSlider("HUMANIZE", cfg.humanize, 0f..1f, { v -> vm.updateArp { c -> c.copy(humanize = v) } }, "%.0f%%", 100f)
            ArpSlider("LOOP LENGTH", cfg.loopLength.toFloat(), 1f..64f, { v -> vm.updateArp { c -> c.copy(loopLength = v.toInt().coerceIn(1,64)) } }, "%.0f STEPS")
        }
        ArpPanel("64-STEP PATTERN") {
            Text("Select a step to edit probability, ratchets, octave, velocity, gate and semitone shift.", color = Color.LightGray, fontSize = 9.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(64) { i ->
                    val st = cfg.steps[i]
                    Box(Modifier.size(31.dp).background(when { i == selected -> ElectricBlue; !st.enabled -> WalnutDark; i % 4 == 0 -> Panel; else -> Chassis }).clickable { selected = i }.padding(2.dp), contentAlignment = Alignment.Center) {
                        Text("${i+1}", color = if (i == selected) Color.Black else Cream, fontSize = 8.sp)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("STEP ${selected+1}", color = ElectricBlue, fontSize = 11.sp); Spacer(Modifier.weight(1f))
                Switch(checked = step.enabled, onCheckedChange = { v -> editStep(vm, selected) { it.copy(enabled = v) } })
            }
            ArpSlider("PROBABILITY", step.probability, 0f..1f, { v -> editStep(vm, selected) { it.copy(probability = v) } }, "%.0f%%", 100f)
            ArpChoice("RATCHETS", (1..8).map { "$it" }, step.ratchets - 1) { v -> editStep(vm, selected) { it.copy(ratchets = v+1) } }
            ArpChoice("OCTAVE", listOf("-2","-1","0","+1","+2"), step.octave + 2) { v -> editStep(vm, selected) { it.copy(octave = v-2) } }
            ArpSlider("VELOCITY", step.velocity, 0f..1f, { v -> editStep(vm, selected) { it.copy(velocity = v) } }, "%.0f%%", 100f)
            ArpSlider("GATE", step.gate, 0.05f..1f, { v -> editStep(vm, selected) { it.copy(gate = v) } }, "%.0f%%", 100f)
            ArpChoice("STEP SHIFT", (-12..12).map { "%+d".format(it) }, step.shift + 12) { v -> editStep(vm, selected) { it.copy(shift = v-12) } }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { vm.updateArp { c -> c.copy(steps = List(64) { ArpStep() }) } }) { Text("RESET 64") }
                Button(onClick = { vm.updateArp { c -> c.copy(steps = c.steps.map { it.copy(enabled = !it.enabled) }) } }) { Text("INVERT") }
            }
        }
        ArpPanel("GENERATIVE / EVOLVE") {
            ArpSwitch("ADVANCED RANDOM", cfg.randomMode) { v -> vm.updateArp { c -> c.copy(randomMode = v) } }
            ArpSwitch("EVOLVE", cfg.evolve) { v -> vm.updateArp { c -> c.copy(evolve = v) } }
            Text("Random mutates probability, ratchets and octave per loop. Evolve applies slower mutations while preserving pattern identity.", color = Color.LightGray, fontSize = 9.sp)
        }
        ArpPanel("MODULATION MATRIX") {
            Text("Route sequencer sources to any exposed synth parameter.", color = Color.LightGray, fontSize = 9.sp)
            cfg.routes.forEachIndexed { index, route ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = {
                        val sources = listOf("STEP", "VELOCITY", "PROBABILITY", "RANDOM", "PITCH")
                        val nextSource = sources[(sources.indexOf(route.source).coerceAtLeast(0) + 1) % sources.size]
                        vm.updateArp { c -> c.copy(routes = c.routes.toMutableList().also { it[index] = route.copy(source = nextSource) }) }
                    }, colors = ButtonDefaults.buttonColors(containerColor = WalnutDark), modifier = Modifier.weight(0.34f)) { Text(route.source, fontSize = 8.sp) }
                    Text(arpTargets.firstOrNull { it.first == route.target }?.second ?: "PARAM ${route.target}", color = Cream, fontSize = 9.sp, modifier = Modifier.weight(0.40f))
                    Text("${(route.amount*100).toInt()}%", color = Cream, fontSize = 9.sp)
                    Button(onClick = { vm.updateArp { c -> c.copy(routes = c.routes.toMutableList().also { it.removeAt(index) }) } }) { Text("×") }
                }
            }
            var newRouteTarget by remember { mutableIntStateOf(0) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("NEW DESTINATION", color = Cream, fontSize = 9.sp)
                Button(onClick = { newRouteTarget = (newRouteTarget + 1) % arpTargets.size }, colors = ButtonDefaults.buttonColors(containerColor = WalnutDark)) {
                    Text(arpTargets[newRouteTarget].second, fontSize = 8.sp)
                }
                Button(onClick = {
                    val target = arpTargets[newRouteTarget].first
                    if (cfg.routes.none { it.target == target }) vm.updateArp { c -> c.copy(routes = c.routes + ArpModRoute("STEP", target, 0.25f, true)) }
                }) { Text("ADD ROUTE") }
            }
            cfg.routes.forEachIndexed { index, route ->
                ArpSlider("ROUTE ${index+1} AMOUNT", route.amount, -1f..1f, { amount -> vm.updateArp { c -> c.copy(routes = c.routes.toMutableList().also { it[index] = route.copy(amount = amount) }) } }, "%+.2f", 1f)
            }
        }
        ArpPanel("STATE / HISTORY") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.undo() }, enabled = vm.canUndo()) { Text("UNDO") }
                Button(onClick = { vm.redo() }, enabled = vm.canRedo()) { Text("REDO") }
            }
            Text("Complete synth and arpeggiator state backup is available from the main screen.", color = Color.LightGray, fontSize = 9.sp)
        }
    }
}

private fun editStep(vm: SynthViewModel, index: Int, transform: (ArpStep) -> ArpStep) {
    vm.updateArp { c -> c.copy(steps = c.steps.toMutableList().also { it[index] = transform(it[index]) }) }
}

@Composable
private fun ArpPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(Panel).padding(9.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, color = ElectricBlue, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        content()
    }
}

@Composable
private fun ArpChoice(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    var index by remember(label, selected) { mutableIntStateOf(selected.coerceIn(0, options.lastIndex)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Cream, fontSize = 9.sp, modifier = Modifier.width(112.dp))
        Button(onClick = { index = (index + 1).mod(options.size); onSelect(index) }, colors = ButtonDefaults.buttonColors(containerColor = WalnutDark)) { Text(options[index], fontSize = 9.sp) }
    }
}

@Composable
private fun ArpSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValue: (Float) -> Unit, format: String, scale: Float = 1f) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Cream, fontSize = 9.sp, modifier = Modifier.width(112.dp))
        Slider(value = value.coerceIn(range), onValueChange = onValue, valueRange = range, modifier = Modifier.weight(1f))
        Text(String.format(format, value * scale), color = ElectricBlue, fontSize = 9.sp, modifier = Modifier.width(65.dp))
    }
}

@Composable
private fun ArpSwitch(label: String, value: Boolean, onValue: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Cream, fontSize = 10.sp, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onValue)
    }
}
