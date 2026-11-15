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
    var arpStep by mutableIntStateOf(0); private set
    private data class Snapshot(val values: List<Float>, val arp: ArpConfig)
    private val undoStack = ArrayDeque<Snapshot>()
    private val redoStack = ArrayDeque<Snapshot>()
    private val arpeggiator = Arpeggiator({ n, v -> SynthEngine.nativeNoteOn(n, v) }, { n -> SynthEngine.nativeNoteOff(n) })

    val firstNote: Int get() = 12 * (octave + 1)

    private fun snapshot() = Snapshot(values.toList(), arpConfig)
    private fun remember() { undoStack.addLast(snapshot()); while (undoStack.size > 100) undoStack.removeFirst(); redoStack.clear() }
    fun canUndo() = undoStack.isNotEmpty()
    fun canRedo() = redoStack.isNotEmpty()
    private fun applySnapshot(s: Snapshot) {
        s.values.forEachIndexed { id, v -> values[id] = v }
        SynthEngine.nativeAllNotesOff(); pushAll()
        arpConfig = s.arp; arpeggiator.configure(arpConfig); if (arpConfig.enabled) arpeggiator.start()
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
    fun setArpPage(show: Boolean) { showArp = show }


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
        // Reserve All Notes Off so MIDI Learn can never steal the panic command.
        if (controller == 123) { panic(); return }
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
    }

    fun shiftOctave(delta: Int) {
        octave = (octave + delta).coerceIn(1, 6)
        SynthEngine.nativeAllNotesOff()
    }

    // Safe to call from any thread (MIDI input uses these).
    fun noteOn(note: Int, velocity: Float) = arpeggiator.noteOn(note, velocity)
    fun noteOff(note: Int) = arpeggiator.noteOff(note)
    fun pitchBend(value: Float) = SynthEngine.nativePitchBend(value)
    fun panic() { arpeggiator.panic(); SynthEngine.nativeAllNotesOff() }

    fun exportStateJson(): String {
        val root = JSONObject()
        root.put("format", "oberjein-state-v1")
        root.put("parameters", JSONArray(values.toList()))
        root.put("presetIndex", presetIndex)
        root.put("octave", octave)
        root.put("midiMappings", JSONObject().also { obj -> midiMappings.forEach { (cc, id) -> obj.put(cc.toString(), id) } })
        val a = JSONObject()
        a.put("enabled", arpConfig.enabled); a.put("hold", arpConfig.hold); a.put("bpm", arpConfig.bpm)
        a.put("division", arpConfig.division); a.put("direction", arpConfig.direction.name)
        a.put("octaveRange", arpConfig.octaveRange); a.put("scale", arpConfig.scale.name); a.put("root", arpConfig.root)
        a.put("chordSize", arpConfig.chordSize); a.put("strumMs", arpConfig.strumMs); a.put("intervalShift", arpConfig.intervalShift)
        a.put("randomMode", arpConfig.randomMode); a.put("evolve", arpConfig.evolve); a.put("loopLength", arpConfig.loopLength); a.put("humanize", arpConfig.humanize)
        a.put("steps", JSONArray().also { arr -> arpConfig.steps.forEach { st -> arr.put(JSONObject().put("enabled",st.enabled).put("probability",st.probability).put("ratchets",st.ratchets).put("octave",st.octave).put("velocity",st.velocity).put("gate",st.gate).put("shift",st.shift)) } })
        a.put("routes", JSONArray().also { arr -> arpConfig.routes.forEach { route -> arr.put(JSONObject().put("source",route.source).put("target",route.target).put("amount",route.amount).put("bipolar",route.bipolar)) } })
        root.put("arpeggiator", a)
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
        SynthEngine.nativeAllNotesOff(); pushAll(); arpeggiator.configure(arpConfig); if (arpConfig.enabled) arpeggiator.start()
    }

}
