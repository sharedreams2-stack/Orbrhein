package com.oberjein.synth

import android.os.Handler
import android.os.Looper
import kotlin.math.max
import kotlin.random.Random

enum class ArpDirection { UP, DOWN, UP_DOWN, DOWN_UP, AS_PLAYED, RANDOM }
enum class ArpScale(val intervals: IntArray) {
    CHROMATIC(intArrayOf(0,1,2,3,4,5,6,7,8,9,10,11)),
    MAJOR(intArrayOf(0,2,4,5,7,9,11)),
    MINOR(intArrayOf(0,2,3,5,7,8,10)),
    DORIAN(intArrayOf(0,2,3,5,7,9,10)),
    PENTATONIC(intArrayOf(0,2,4,7,9)),
    BLUES(intArrayOf(0,3,5,6,7,10)),
    HARMONIC_MINOR(intArrayOf(0,2,3,5,7,8,11))
}

data class ArpStep(
    val enabled: Boolean = true,
    val probability: Float = 1f,
    val ratchets: Int = 1,
    val octave: Int = 0,
    val velocity: Float = 1f,
    val gate: Float = 0.8f,
    val shift: Int = 0
)

data class ArpModRoute(val source: String, val target: Int, val amount: Float, val bipolar: Boolean = false)

data class ArpConfig(
    val enabled: Boolean = false,
    val hold: Boolean = false,
    val bpm: Float = 120f,
    val division: Int = 4,
    val direction: ArpDirection = ArpDirection.UP,
    val octaveRange: Int = 1,
    val scale: ArpScale = ArpScale.CHROMATIC,
    val root: Int = 0,
    val chordSize: Int = 1,
    val strumMs: Int = 0,
    val intervalShift: Int = 0,
    val randomMode: Boolean = false,
    val evolve: Boolean = false,
    val loopLength: Int = 16,
    val humanize: Float = 0f,
    val steps: List<ArpStep> = List(64) { ArpStep() },
    val routes: List<ArpModRoute> = emptyList()
)

/** UI-independent 64-step sequencer. Scheduler uses the main looper; note callbacks remain lock-free JNI calls. */
class Arpeggiator(
    private val onNoteOn: (Int, Float) -> Unit,
    private val onNoteOff: (Int) -> Unit,
    private val onModulation: (Int, Float) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val held = linkedSetOf<Int>()
    private val latched = linkedSetOf<Int>()
    private var config = ArpConfig()
    private var stepIndex = 0
    private var noteIndex = 0
    private var modulatedTargets = emptySet<Int>()
    private var running = false
    private var generation = 0
    private var activeNotes = mutableSetOf<Int>()

    fun configure(value: ArpConfig, autoStart: Boolean = true) {
        config = value.copy(steps = value.steps.take(64).let { currentSteps -> currentSteps + List((64 - currentSteps.size).coerceAtLeast(0)) { ArpStep() } })
        if (!config.enabled && running) stop() else if (config.enabled && !running && autoStart) start()
    }

    fun noteOn(note: Int, velocity: Float) {
        if (config.hold) {
            if (!latched.add(note)) latched.remove(note)
        } else held.add(note)
        if (!config.enabled) onNoteOn(note, velocity)
    }

    fun noteOff(note: Int) {
        held.remove(note)
        if (!config.enabled) onNoteOff(note)
    }

    fun panic() {
        held.clear(); latched.clear()
        activeNotes.toList().forEach(onNoteOff)
        activeNotes.clear()
    }

    fun start() {
        if (running || !config.enabled) return
        running = true
        generation++
        stepIndex = 0
        schedule(generation, 0L)
    }

    fun stop() {
        running = false
        generation++
        handler.removeCallbacksAndMessages(null)
        activeNotes.toList().forEach(onNoteOff)
        activeNotes.clear()
    }

    fun currentStep() = stepIndex
    fun isRunning() = running

    private fun schedule(token: Int, delay: Long) {
        handler.postDelayed({
            if (!running || token != generation) return@postDelayed
            playStep()
            val base = 60000.0 / config.bpm.coerceIn(30f, 300f) * 4.0 / config.division.coerceIn(1, 32)
            val jitter = (Random.nextFloat() - 0.5f) * config.humanize.coerceIn(0f, 1f) * base * 0.18
            schedule(token, max(8L, (base + jitter).toLong()))
        }, delay)
    }

    private fun playStep() {
        modulatedTargets.forEach { onModulation(it, 0f) }
        val rawPool = (if (config.hold) latched else held).toList()
        val pool = if (config.direction == ArpDirection.AS_PLAYED) rawPool else rawPool.sorted()
        val notes = if (pool.isEmpty()) emptyList() else expandNotes(pool)
        if (notes.isNotEmpty()) {
            val step = config.steps[stepIndex.coerceIn(0, 63)]
            if (step.enabled && Random.nextFloat() <= step.probability.coerceIn(0f, 1f)) {
                val newTargets = mutableSetOf<Int>()
                config.routes.forEach { route ->
                    if (route.target in 0 until P.COUNT) {
                        val source = when (route.source.uppercase()) {
                            "VELOCITY" -> step.velocity
                            "PROBABILITY" -> step.probability
                            "RANDOM" -> Random.nextFloat()
                            "PITCH" -> ((notes[noteIndex.mod(notes.size)] % 12) / 11f)
                            else -> stepIndex.toFloat() / (config.loopLength - 1).coerceAtLeast(1)
                        }
                        onModulation(route.target, (if (route.bipolar) source * 2f - 1f else source) * route.amount)
                        newTargets.add(route.target)
                    }
                }
                modulatedTargets = newTargets
                val selected = when(config.direction) {
                    ArpDirection.UP -> notes[noteIndex.mod(notes.size)]
                    ArpDirection.DOWN -> notes[(notes.size - 1 - noteIndex.mod(notes.size))]
                    ArpDirection.RANDOM -> notes.random()
                    ArpDirection.AS_PLAYED -> pool[noteIndex.mod(pool.size)]
                    ArpDirection.UP_DOWN, ArpDirection.DOWN_UP -> {
                        val sequence = if (notes.size < 2) notes else notes + notes.dropLast(1).drop(1).asReversed()
                        if (config.direction == ArpDirection.DOWN_UP) sequence.asReversed()[noteIndex.mod(sequence.size)] else sequence[noteIndex.mod(sequence.size)]
                    }
                }
                val root = selected - selected.mod(12) + config.root
                val pitch = quantize(selected + config.intervalShift + step.shift, root, config.scale)
                val chord = (0 until config.chordSize.coerceIn(1, 8)).map { pitch + config.scale.intervals.getOrElse(it) { 7 } }
                val ratchets = step.ratchets.coerceIn(1, 8)
                val stepMs = 60000.0 / config.bpm.coerceIn(30f, 300f) * 4.0 / config.division.coerceIn(1, 32)
                val duration = max(12L, (stepMs / ratchets * step.gate).toLong())
                chord.forEachIndexed { chordIndex, n ->
                    val shifted = (n + step.octave * 12).coerceIn(0, 127)
                    val startDelay = chordIndex * config.strumMs.toLong()
                    handler.postDelayed({
                        onNoteOn(shifted, (step.velocity).coerceIn(0f, 1f))
                        activeNotes.add(shifted)
                        handler.postDelayed({ onNoteOff(shifted); activeNotes.remove(shifted) }, duration)
                    }, startDelay)
                }
                if (ratchets > 1) {
                    for (r in 1 until ratchets) {
                        val d = (r * max(12.0, stepMs / ratchets)).toLong()
                        handler.postDelayed({ onNoteOn(pitch, step.velocity); activeNotes.add(pitch); handler.postDelayed({ onNoteOff(pitch); activeNotes.remove(pitch) }, duration) }, d)
                    }
                }
                noteIndex++
            }
        }
        stepIndex = (stepIndex + 1) % config.loopLength.coerceIn(1, 64)
        if (config.randomMode || config.evolve) evolvePattern()
    }

    private fun expandNotes(notes: List<Int>): List<Int> {
        val out = mutableListOf<Int>()
        for (oct in 0 until config.octaveRange.coerceIn(1, 4)) notes.forEach { out += it + oct * 12 }
        return out
    }

    private fun quantize(note: Int, root: Int, scale: ArpScale): Int {
        val rel = note - root
        val octave = Math.floorDiv(rel, 12)
        val pitchClass = Math.floorMod(rel, 12)
        val closest = scale.intervals.minByOrNull { kotlin.math.abs(it - pitchClass) } ?: 0
        return root + octave * 12 + closest
    }

    private fun evolvePattern() {
        val step = config.steps[stepIndex.coerceIn(0, 63)]
        val amount = if (config.evolve) 0.18f else 0.45f
        if (Random.nextFloat() < amount) {
            val copy = config.steps.toMutableList()
            copy[stepIndex] = step.copy(
                probability = (step.probability + Random.nextFloat() * 0.35f - 0.12f).coerceIn(0f, 1f),
                ratchets = if (Random.nextFloat() < 0.25f) Random.nextInt(1, 5) else step.ratchets,
                octave = if (Random.nextFloat() < 0.2f) Random.nextInt(-1, 2) else step.octave
            )
            config = config.copy(steps = copy)
        }
    }
}
