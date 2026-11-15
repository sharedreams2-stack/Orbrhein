package com.oberjein.synth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

class SynthViewModel : ViewModel() {
    val values = mutableStateListOf<Float>().apply { addAll(DEFAULTS.toList()) }
    var presetIndex by mutableIntStateOf(0); private set
    var octave by mutableIntStateOf(3); private set
    var midiTarget by mutableIntStateOf(P.CUTOFF); private set
    var isMidiLearning by mutableStateOf(false); private set
    val midiMappings = mutableStateMapOf<Int, Int>()
    var arpConfig by mutableStateOf(ArpConfig()); private set
    var showArp by mutableStateOf(false)
    var showFx by mutableStateOf(false)
    val fxValues = mutableStateListOf<Float>().apply { addAll(FX_DEFAULTS) }
    val fxUserPresets = mutableStateListOf<FxUserPreset>().apply { repeat(16) { add(FxUserPreset("USER ${(it + 1).toString().padStart(2, '0')}", FX_DEFAULTS)) } }
    private val tapTimes = ArrayDeque<Long>()
    var fxHoldActive by mutableStateOf(false); private set
    var fxLoopState by mutableIntStateOf(0); private set
    var arpStep by mutableIntStateOf(0); private set
    private data class Snapshot(val values: List<Float>, val arp: ArpConfig, val mappings: Map<Int, Int>, val preset: Int, val octave: Int, val fx: List<Float>, val fxUsers: List<FxUserPreset>)
    private val undoStack = ArrayDeque<Snapshot>()
    private val redoStack = ArrayDeque<Snapshot>()
    private val arpeggiator = Arpeggiator(
        { n, v -> SynthEngine.nativeNoteOn(n, v); MidiBridge.noteOn(n, v) },
        { n -> SynthEngine.nativeNoteOff(n); MidiBridge.noteOff(n) },
        { id, offset -> if (id in values.indices) SynthEngine.nativeSetParam(id, (values[id] + offset).coerceIn(0f, 1f)) },
        { steps -> arpConfig = arpConfig.copy(steps = steps) }
    )

    val firstNote: Int get() = 12 * (octave + 1)

    private fun snapshot() = Snapshot(values.toList(), arpConfig, midiMappings.toMap(), presetIndex, octave, fxValues.toList(), fxUserPresets.toList())
    private fun remember() { undoStack.addLast(snapshot()); while (undoStack.size > 100) undoStack.removeFirst(); redoStack.clear() }
    fun canUndo() = undoStack.isNotEmpty()
    fun canRedo() = redoStack.isNotEmpty()
    private fun applySnapshot(s: Snapshot) {
        s.values.forEachIndexed { id, v -> values[id] = v }
        presetIndex = s.preset; octave = s.octave
        midiMappings.clear(); midiMappings.putAll(s.mappings)
        s.fx.forEachIndexed { id, value -> fxValues[id] = value; SynthEngine.nativeSetFxParam(id, value) }
        s.fxUsers.forEachIndexed { index, preset -> fxUserPresets[index] = preset }
        SynthEngine.nativeAllNotesOff(); pushAll()
        arpConfig = s.arp; arpeggiator.configure(arpConfig)
    }
    fun undo() { if (undoStack.isNotEmpty()) { redoStack.addLast(snapshot()); applySnapshot(undoStack.removeLast()) } }
    fun redo() { if (redoStack.isNotEmpty()) { undoStack.addLast(snapshot()); applySnapshot(redoStack.removeLast()) } }

    fun set(id: Int, value: Float) {
        if (id !in values.indices) return
        val v = value.coerceIn(0f, 1f)
        if (kotlin.math.abs(values[id] - v) < 0.0001f) return
        remember()
        values[id] = v
        SynthEngine.nativeSetParam(id, v)
    }

    fun updateArp(transform: (ArpConfig) -> ArpConfig) {
        val next = transform(arpConfig)
        if (next == arpConfig) return
        remember(); arpConfig = next
        arpeggiator.configure(next)
        if (next.enabled) arpeggiator.start()
    }
    fun toggleArp() { updateArp { it.copy(enabled = !it.enabled) } }
    fun arpNoteOn(note: Int, velocity: Float) = arpeggiator.noteOn(note, velocity)
    fun arpNoteOff(note: Int) = arpeggiator.noteOff(note)
    fun setArpPage(show: Boolean) { showArp = show; if (show) showFx = false }
    fun setFxPage(show: Boolean) { showFx = show; if (show) showArp = false }

    fun setFx(id: Int, value: Float) {
        if (id !in fxValues.indices) return
        val v = if (id == FxP.TEMPO) value.coerceIn(30f, 300f) else value.coerceIn(0f, 1f)
        if (kotlin.math.abs(fxValues[id] - v) < 0.0001f) return
        remember(); fxValues[id] = v; SynthEngine.nativeSetFxParam(id, v)
    }

    fun selectFxEffect(effect: Int, variation: Int) {
        val index = (effect.coerceIn(0, 10) * 4 + variation.coerceIn(0, 3)).coerceIn(0, FX_FACTORY_PRESETS.lastIndex)
        val preset = FX_FACTORY_PRESETS[index]
        remember()
        preset.values.forEachIndexed { id, value -> fxValues[id] = value; SynthEngine.nativeSetFxParam(id, value) }
    }

    fun tapFxTempo(now: Long = android.os.SystemClock.elapsedRealtime()) {
        if (tapTimes.isNotEmpty() && now - tapTimes.last() > 2500L) tapTimes.clear()
        if (tapTimes.isNotEmpty()) {
            val delta = now - tapTimes.last()
            if (delta in 250L..2000L) {
                tapTimes.addLast(now); while (tapTimes.size > 5) tapTimes.removeFirst()
                val intervals = tapTimes.zipWithNext { a, b -> b - a }
                val bpm = (60000.0 / intervals.average()).toFloat().coerceIn(30f, 240f)
                setFx(FxP.TEMPO, bpm); SynthEngine.nativeFxTapTempo(bpm)
            } else { tapTimes.clear(); tapTimes.addLast(now) }
        } else tapTimes.addLast(now)
    }

    fun toggleFxHold() { fxHoldActive = !fxHoldActive; SynthEngine.nativeFxHold(fxHoldActive) }
    fun fxLooperCommand(command: Int) {
        SynthEngine.nativeFxLooperCommand(command)
        fxLoopState = when (command) { 1 -> 1; 2,3 -> 2; else -> 0 }
    }
    fun saveFxUserPreset(slot: Int, name: String? = null) {
        if (slot !in 0..15) return
        remember()
        fxUserPresets[slot] = FxUserPreset(name?.take(20)?.ifBlank { "USER ${slot + 1}" } ?: "USER ${(slot + 1).toString().padStart(2, '0')}", fxValues.toList())
    }
    fun loadFxUserPreset(slot: Int) {
        if (slot !in 0..15) return
        remember(); fxUserPresets[slot].values.forEachIndexed { id, value -> fxValues[id] = value; SynthEngine.nativeSetFxParam(id, value) }
    }


    fun pushAll() = values.forEachIndexed { id, v -> SynthEngine.nativeSetParam(id, v) }

    fun loadPreset(index: Int) {
        remember()
        val i = Math.floorMod(index, PRESETS.size)
        presetIndex = i
        PRESETS[i].values.forEachIndexed { id, v -> values[id] = v }
        SynthEngine.nativeAllNotesOff()
        pushAll()
    }

    fun selectMidiTarget(id: Int) { midiTarget = id }

    fun beginMidiLearn() { isMidiLearning = true }

    fun clearMidiMappings() { if (midiMappings.isNotEmpty()) remember(); midiMappings.clear(); isMidiLearning = false }

    fun onMidiCC(controller: Int, value: Int) {
        // Reserve All Notes Off so MIDI Learn can never steal the panic command.
        if (controller == 123) { panic(); return }
        if (isMidiLearning) {
            remember()
            midiMappings[controller] = midiTarget
            isMidiLearning = false
            return
        }
        val target = midiMappings[controller]
        if (target != null) {
            val normalized = value.coerceIn(0, 127) / 127f
            if (target >= P.COUNT) setFx(target - P.COUNT, normalized)
            else set(target, if (target == P.OVERSAMPLING) (normalized * 2f).roundToInt() / 2f else normalized)
        } else if (controller == 1) {
            set(P.LFO_PITCH, value.coerceIn(0, 127) / 127f)
        }
    }

    fun shiftOctave(delta: Int) {
        remember()
        octave = (octave + delta).coerceIn(1, 6)
        SynthEngine.nativeAllNotesOff()
    }

    // Safe to call from any thread (MIDI input uses these).
    fun noteOn(note: Int, velocity: Float) = arpeggiator.noteOn(note, velocity)
    fun noteOff(note: Int) = arpeggiator.noteOff(note)
    fun midiNoteOn(note: Int, velocity: Float) { if (arpConfig.enabled) arpeggiator.noteOn(note, velocity) else SynthEngine.nativeNoteOn(note, velocity) }
    fun midiNoteOff(note: Int) { if (arpConfig.enabled) arpeggiator.noteOff(note) else SynthEngine.nativeNoteOff(note) }
    fun resumeArpeggiator() { if (arpConfig.enabled) arpeggiator.start() }
    fun pauseArpeggiator() { arpeggiator.stop() }
    fun pitchBend(value: Float) = SynthEngine.nativePitchBend(value)
    fun panic() { arpeggiator.panic(); SynthEngine.nativeAllNotesOff(); MidiBridge.send(byteArrayOf(0xB0.toByte(), 123, 0)) }

    fun exportStateJson(): String {
        val savedArp = arpeggiator.currentConfig()
        val root = JSONObject()
        root.put("format", "oberjein-state-v1")
        root.put("parameters", JSONArray(values.toList()))
        root.put("presetIndex", presetIndex)
        root.put("octave", octave)
        root.put("midiMappings", JSONObject().also { obj -> midiMappings.forEach { (cc, id) -> obj.put(cc.toString(), id) } })
        val a = JSONObject()
        a.put("enabled", savedArp.enabled); a.put("hold", savedArp.hold); a.put("bpm", savedArp.bpm)
        a.put("division", savedArp.division); a.put("direction", savedArp.direction.name)
        a.put("octaveRange", savedArp.octaveRange); a.put("scale", savedArp.scale.name); a.put("root", savedArp.root)
        a.put("chordSize", savedArp.chordSize); a.put("strumMs", savedArp.strumMs); a.put("intervalShift", savedArp.intervalShift)
        a.put("randomMode", savedArp.randomMode); a.put("evolve", savedArp.evolve); a.put("loopLength", savedArp.loopLength); a.put("humanize", savedArp.humanize)
        a.put("steps", JSONArray().also { arr -> savedArp.steps.forEach { st -> arr.put(JSONObject().put("enabled",st.enabled).put("probability",st.probability).put("ratchets",st.ratchets).put("octave",st.octave).put("velocity",st.velocity).put("gate",st.gate).put("shift",st.shift)) } })
        a.put("routes", JSONArray().also { arr -> savedArp.routes.forEach { route -> arr.put(JSONObject().put("source",route.source).put("target",route.target).put("amount",route.amount).put("bipolar",route.bipolar)) } })
        root.put("arpeggiator", a)
        root.put("fxParameters", JSONArray(fxValues.toList()))
        root.put("fxUserPresets", JSONArray().also { arr -> fxUserPresets.forEach { preset -> arr.put(JSONObject().put("name", preset.name).put("values", JSONArray(preset.values))) } })
        return root.toString(2)
    }

    fun restoreStateJson(json: String) {
        val root = JSONObject(json)
        val params = root.getJSONArray("parameters")
        if (params.length() != P.COUNT) throw IllegalArgumentException("Incompatible parameter count")
        remember()
        for (i in 0 until params.length()) values[i] = params.getDouble(i).toFloat().coerceIn(0f,1f)
        presetIndex = root.optInt("presetIndex", 0).coerceIn(0, PRESETS.lastIndex)
        octave = root.optInt("octave", 3).coerceIn(1,6)
        midiMappings.clear()
        val maps = root.optJSONObject("midiMappings") ?: JSONObject()
        maps.keys().forEach { key -> midiMappings[key.toIntOrNull() ?: -1] = maps.optInt(key) }
        val a = root.optJSONObject("arpeggiator") ?: JSONObject()
        val stepArray = a.optJSONArray("steps") ?: JSONArray()
        val steps = List(64) { i ->
            val st = stepArray.optJSONObject(i) ?: JSONObject()
            ArpStep(st.optBoolean("enabled",true),st.optDouble("probability",1.0).toFloat(),st.optInt("ratchets",1),st.optInt("octave",0),st.optDouble("velocity",1.0).toFloat(),st.optDouble("gate",0.8).toFloat(),st.optInt("shift",0))
        }
        val routeArray = a.optJSONArray("routes") ?: JSONArray()
        val routes = (0 until routeArray.length()).mapNotNull { i -> routeArray.optJSONObject(i)?.let { ArpModRoute(it.optString("source"),it.optInt("target"),it.optDouble("amount").toFloat(),it.optBoolean("bipolar")) } }
        arpConfig = ArpConfig(a.optBoolean("enabled",false),a.optBoolean("hold",false),a.optDouble("bpm",120.0).toFloat(),a.optInt("division",4),runCatching { ArpDirection.valueOf(a.optString("direction","UP")) }.getOrDefault(ArpDirection.UP),a.optInt("octaveRange",1),runCatching { ArpScale.valueOf(a.optString("scale","CHROMATIC")) }.getOrDefault(ArpScale.CHROMATIC),a.optInt("root",0),a.optInt("chordSize",1),a.optInt("strumMs",0),a.optInt("intervalShift",0),a.optBoolean("randomMode",false),a.optBoolean("evolve",false),a.optInt("loopLength",16),a.optDouble("humanize",0.0).toFloat(),steps,routes)
        root.optJSONArray("fxParameters")?.let { array ->
            if (array.length() == FxP.COUNT) for (i in 0 until array.length()) { fxValues[i] = array.getDouble(i).toFloat(); SynthEngine.nativeSetFxParam(i, fxValues[i]) }
        }
        root.optJSONArray("fxUserPresets")?.let { array ->
            for (i in 0 until minOf(16, array.length())) {
                val item = array.optJSONObject(i) ?: continue
                val vals = item.optJSONArray("values") ?: continue
                if (vals.length() == FxP.COUNT) fxUserPresets[i] = FxUserPreset(item.optString("name", "USER ${i+1}"), List(FxP.COUNT) { vals.getDouble(it).toFloat() })
            }
        }
        SynthEngine.nativeAllNotesOff(); pushAll(); arpeggiator.configure(arpConfig, autoStart = false)
    }

}
