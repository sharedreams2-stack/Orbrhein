package com.oberjein.synth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oberjein.synth.*
import java.util.Locale

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

private val arpDirectionLabels = listOf("UP", "DOWN", "UP/DN", "DN/UP", "AS PLAYED", "RANDOM")
private val arpScaleLabels = listOf("CHROMA", "MAJOR", "MINOR", "DORIAN", "PENTA", "BLUES", "HARM MIN")
private val arpScaleValues = ArpScale.values().toList()
private val arpDivisionValues = listOf(1, 2, 4, 8, 16, 32)
private val arpDivisionLabels = listOf("1/1", "1/2", "1/4", "1/8", "1/16", "1/32")
private val arpSourceNames = listOf("STEP", "VELOCITY", "PROBABILITY", "RANDOM", "PITCH")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ArpeggiatorScreen(vm: SynthViewModel) {
    val cfg = vm.arpConfig
    var selected by remember { mutableIntStateOf(0) }
    var newRouteSource by remember { mutableIntStateOf(0) }
    var newRouteTarget by remember { mutableIntStateOf(0) }
    val step = cfg.steps[selected]
    val directions = ArpDirection.values().toList()

    Column(
        Modifier.fillMaxSize().background(Chassis).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().background(Panel).padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("SUPER ARP", color = Cream, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Text("64-STEP PERFORMANCE SEQUENCER", color = ElectricBlue, fontSize = 7.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            Text(if (cfg.enabled) "RUNNING" else "STOPPED", color = if (cfg.enabled) ElectricBlue else Dim, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(5.dp))
            BarButton(if (cfg.enabled) "STOP" else "START") { vm.toggleArp() }
            BarButton(if (cfg.hold) "HOLD ON" else "HOLD OFF") { vm.updateArp { it.copy(hold = !it.hold) } }
            BarButton("UNDO") { vm.undo() }
            BarButton("REDO") { vm.redo() }
            BarButton("SYNTH") { vm.setArpPage(false) }
            BarButton("FX") { vm.setFxPage(true) }
        }

        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // COLUMN 1 — CLOCK, NOTE POOL AND MUSICAL QUANTIZATION
            Column(Modifier.weight(0.22f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Section("CLOCK / DIRECTION") {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Knob("TEMPO", ((cfg.bpm - 30f) / 270f).coerceIn(0f, 1f),
                            { v -> vm.updateArp { it.copy(bpm = 30f + v * 270f) } },
                            diameter = 34.dp, readout = String.format(Locale.US, "%.1f BPM", cfg.bpm))
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            ArpCompactChoice("RATE", arpDivisionLabels, arpDivisionValues.indexOf(cfg.division).coerceAtLeast(0)) {
                                vm.updateArp { c -> c.copy(division = arpDivisionValues[it]) }
                            }
                            ArpCompactChoice("DIRECTION", arpDirectionLabels, directions.indexOf(cfg.direction).coerceAtLeast(0)) {
                                vm.updateArp { c -> c.copy(direction = directions[it]) }
                            }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        ArpCompactChoice("OCTAVES", (1..4).map { "$it" }, cfg.octaveRange - 1) {
                            vm.updateArp { c -> c.copy(octaveRange = it + 1) }
                        }
                        ArpCompactChoice("LOOP", listOf("01", "08", "16", "32", "64"),
                            listOf(1,8,16,32,64).indexOf(cfg.loopLength).let { if (it < 0) 4 else it }) {
                            vm.updateArp { c -> c.copy(loopLength = listOf(1,8,16,32,64)[it]) }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Toggle("HOLD", cfg.hold, { v -> vm.updateArp { it.copy(hold = v) } })
                        Text("Latch notes: press again to release", color = Dim, fontSize = 7.sp, modifier = Modifier.weight(1f))
                    }
                }

                Section("SCALE / KEY") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ArpCompactChoice("SCALE", arpScaleLabels, arpScaleValues.indexOf(cfg.scale).coerceAtLeast(0), width = 82.dp) {
                            vm.updateArp { c -> c.copy(scale = arpScaleValues[it]) }
                        }
                        ArpCompactChoice("ROOT", listOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B"), cfg.root.coerceIn(0,11)) {
                            vm.updateArp { c -> c.copy(root = it) }
                        }
                    }
                    Text("Notes are quantized to the selected scale before chord/interval processing.", color = Dim, fontSize = 7.sp)
                }

                Section("CHORD / INTERVAL") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        ArpCompactChoice("CHORD", (1..8).map { "$it" }, cfg.chordSize - 1) {
                            vm.updateArp { c -> c.copy(chordSize = it + 1) }
                        }
                        ArpCompactChoice("SHIFT", (-12..12).map { "%+d".format(it) }, cfg.intervalShift + 12, width = 68.dp) {
                            vm.updateArp { c -> c.copy(intervalShift = it - 12) }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Knob("STRUM", (cfg.strumMs / 120f).coerceIn(0f,1f),
                            { v -> vm.updateArp { it.copy(strumMs = (v * 120f).toInt()) } },
                            diameter = 32.dp, readout = "${cfg.strumMs} ms")
                        Knob("HUMAN", cfg.humanize, { v -> vm.updateArp { it.copy(humanize = v) } },
                            diameter = 32.dp, readout = "${(cfg.humanize * 100).toInt()}%")
                    }
                }
            }

            // COLUMN 2 — 64 STEP GRID AND PER-STEP EDITOR
            Column(Modifier.weight(0.34f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Section("64-STEP PATTERN") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("PATTERN GRID", color = Dim, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text("ACTIVE ${cfg.steps.count { it.enabled }} / 64", color = ElectricBlue, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(8) { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                repeat(8) { col ->
                                    val i = row * 8 + col
                                    val st = cfg.steps[i]
                                    val cellColor = when {
                                        i == selected -> ElectricBlue
                                        !st.enabled -> Chassis
                                        i % 8 == 0 -> Color(0xFF64502D)
                                        else -> WalnutDark
                                    }
                                    Box(
                                        Modifier.weight(1f).height(23.dp).clip(RoundedCornerShape(2.dp))
                                            .background(cellColor).clickable { selected = i },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("%02d".format(i + 1),
                                            color = if (i == selected) Color.Black else if (st.enabled) Cream else Dim,
                                            fontSize = 7.sp,
                                            fontWeight = if (i == selected || i % 4 == 0) FontWeight.Black else FontWeight.Normal)
                                    }
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        BarButton("ALL ON") { vm.updateArp { c -> c.copy(steps = c.steps.map { it.copy(enabled = true) }) } }
                        BarButton("ALL OFF") { vm.updateArp { c -> c.copy(steps = c.steps.map { it.copy(enabled = false) }) } }
                        BarButton("RESET") { vm.updateArp { c -> c.copy(steps = List(64) { ArpStep() }) } }
                        BarButton("INVERT") { vm.updateArp { c -> c.copy(steps = c.steps.map { it.copy(enabled = !it.enabled) }) } }
                    }
                }

                Section("STEP EDIT / ${selected + 1}") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("STEP ENABLE", color = Cream, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Toggle("ON", step.enabled) { enabled -> editArpStep(vm, selected) { it.copy(enabled = enabled) } }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Knob("PROB", step.probability, { v -> editArpStep(vm, selected) { it.copy(probability = v) } },
                            diameter = 32.dp, readout = "${(step.probability * 100).toInt()}%")
                        Knob("VELOCITY", step.velocity, { v -> editArpStep(vm, selected) { it.copy(velocity = v) } },
                            diameter = 32.dp, readout = "${(step.velocity * 100).toInt()}%")
                        Knob("GATE", (step.gate - 0.05f) / 0.95f, { v -> editArpStep(vm, selected) { it.copy(gate = 0.05f + v * 0.95f) } },
                            diameter = 32.dp, readout = "${(step.gate * 100).toInt()}%")
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        ArpCompactChoice("RATCHET", (1..8).map { "$it" }, step.ratchets - 1) {
                            editArpStep(vm, selected) { c -> c.copy(ratchets = it + 1) }
                        }
                        ArpCompactChoice("OCTAVE", listOf("-2","-1","0","+1","+2"), step.octave + 2) {
                            editArpStep(vm, selected) { c -> c.copy(octave = it - 2) }
                        }
                        ArpCompactChoice("SHIFT", (-12..12).map { "%+d".format(it) }, step.shift + 12, width = 68.dp) {
                            editArpStep(vm, selected) { c -> c.copy(shift = it - 12) }
                        }
                    }
                }
            }

            // COLUMN 3 — GENERATION AND PERFORMANCE
            Column(Modifier.weight(0.21f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Section("GENERATIVE") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Toggle("RANDOM", cfg.randomMode) { v -> vm.updateArp { it.copy(randomMode = v) } }
                        Column(Modifier.weight(1f)) {
                            Text("ADVANCED RANDOM", color = Cream, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            Text("Per-loop probability / octave / ratchet mutation", color = Dim, fontSize = 7.sp)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Toggle("EVOLVE", cfg.evolve) { v -> vm.updateArp { it.copy(evolve = v) } }
                        Column(Modifier.weight(1f)) {
                            Text("EVOLVE", color = Cream, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            Text("Slow mutation, preserving pattern identity", color = Dim, fontSize = 7.sp)
                        }
                    }
                }
                Section("PERFORMANCE") {
                    Text("HOLD toggles notes in the latch pool. Disable HOLD for normal key press/release behavior.", color = Dim, fontSize = 7.sp)
                    Text("Ratchets, probability, gate and velocity are edited per step in the pattern editor.", color = Dim, fontSize = 7.sp)
                }
                Section("HISTORY") {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        BarButton("UNDO") { vm.undo() }
                        BarButton("REDO") { vm.redo() }
                    }
                    Text("Full synth + arp backup is available from the main screen.", color = Dim, fontSize = 7.sp)
                }
            }

            // COLUMN 4 — MODULATION MATRIX
            Column(Modifier.weight(0.23f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Section("MODULATION MATRIX") {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        ArpCompactChoice("SOURCE", arpSourceNames, newRouteSource, width = 75.dp) { newRouteSource = it }
                        ArpCompactChoice("DESTINATION", arpTargets.map { it.second }, newRouteTarget, width = 92.dp) { newRouteTarget = it }
                    }
                    BarButton("ADD ROUTE") {
                        val target = arpTargets[newRouteTarget].first
                        if (cfg.routes.none { it.target == target }) {
                            vm.updateArp { c -> c.copy(routes = c.routes + ArpModRoute(arpSourceNames[newRouteSource], target, 0.25f, true)) }
                        }
                    }
                    Text("${cfg.routes.size} ROUTES", color = ElectricBlue, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                    if (cfg.routes.isEmpty()) {
                        Text("No routes. Add a source and destination above.", color = Dim, fontSize = 7.sp)
                    }
                    cfg.routes.forEachIndexed { index, route ->
                        Column(Modifier.fillMaxWidth().background(Chassis).padding(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ArpCompactChoice("SRC", arpSourceNames, arpSourceNames.indexOf(route.source).coerceAtLeast(0), width = 70.dp) { v ->
                                    vm.updateArp { c -> c.copy(routes = c.routes.toMutableList().also { it[index] = route.copy(source = arpSourceNames[v]) }) }
                                }
                                Box(Modifier.weight(1f).padding(top = 8.dp).clickable {
                                    val oldIndex = arpTargets.indexOfFirst { it.first == route.target }.coerceAtLeast(0)
                                    val nextTarget = arpTargets[(oldIndex + 1) % arpTargets.size].first
                                    vm.updateArp { c -> c.copy(routes = c.routes.toMutableList().also { it[index] = route.copy(target = nextTarget) }) }
                                }) {
                                    Text(arpTargets.firstOrNull { it.first == route.target }?.second ?: "PARAM ${route.target}",
                                        color = Cream, fontSize = 7.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                BarButton(if (route.bipolar) "BI" else "UNI") {
                                    vm.updateArp { c -> c.copy(routes = c.routes.toMutableList().also { it[index] = route.copy(bipolar = !it[index].bipolar) }) }
                                }
                                BarButton("X") { vm.updateArp { c -> c.copy(routes = c.routes.toMutableList().also { it.removeAt(index) }) } }
                            }
                            Knob("AMOUNT", (route.amount + 1f) * 0.5f,
                                { v -> vm.updateArp { c -> c.copy(routes = c.routes.toMutableList().also { it[index] = route.copy(amount = v * 2f - 1f) }) } },
                                diameter = 28.dp, readout = String.format(Locale.US, "%+.2f", route.amount))
                        }
                    }
                }
            }
        }
    }
}

private fun editArpStep(vm: SynthViewModel, index: Int, transform: (ArpStep) -> ArpStep) {
    vm.updateArp { c -> c.copy(steps = c.steps.toMutableList().also { it[index] = transform(it[index]) }) }
}

@Composable
private fun ArpCompactChoice(
    label: String,
    options: List<String>,
    selected: Int,
    width: Dp = 58.dp,
    onSelect: (Int) -> Unit
) {
    val index = selected.coerceIn(0, options.lastIndex)
    Column(Modifier.width(width), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = Dim, fontSize = 6.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Box(
            Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(2.dp))
                .background(WalnutDark).clickable { onSelect((index + 1) % options.size) }
                .padding(horizontal = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(options[index], color = ElectricBlue, fontSize = 7.sp, fontWeight = FontWeight.Black,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
