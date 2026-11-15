package com.oberjein.synth

object FxP {
    const val ENABLE = 0
    const val EFFECT = 1
    const val VARIATION = 2
    const val TIME = 3
    const val ACTIVITY = 4
    const val REPEATS = 5
    const val SHAPE = 6
    const val FILTER = 7
    const val MIX = 8
    const val SPACE = 9
    const val LOOP_LEVEL = 10
    const val PITCH_DEPTH = 11
    const val PITCH_RATE = 12
    const val RESONANCE = 13
    const val REVERB_MODE = 14
    const val BYPASS_MODE = 15
    const val TRAILS = 16
    const val REVERSE = 17
    const val STEREO_MODE = 18
    const val TEMPO = 19
    const val TEMPO_MODE = 20
    const val FX_MASTER = 21
    const val LOOP_FADE = 22
    const val COUNT = 23
}

enum class FxFamily(val title: String, val effects: IntRange) {
    MICRO_LOOP("MICRO LOOP", 0..2),
    GRANULES("GRANULES", 3..4),
    GLITCH("GLITCH", 5..8),
    MULTIDELAY("MULTIDELAY", 9..10)
}

val FX_EFFECT_NAMES = listOf("MOSAIC", "SEQ", "GLIDE", "HAZE", "TUNNEL", "GLITCH", "BLOCKS", "INTERRUPT", "ARP", "PATTERN", "WARP")
val FX_VARIATIONS = listOf("A", "B", "C", "D")

data class FxUserPreset(val name: String, val values: List<Float>)

val FX_DEFAULTS = listOf(
    0f, 0f, 0f, 0.35f, 0.4f, 0.35f, 0.5f, 0.7f, 0.35f, 0.25f, 0.7f,
    0.15f, 0.35f, 0.2f, 0f, 0f, 0f, 0f, 0f, 120f, 0f, 0.8f, 0.4f
)

private fun fxPreset(name: String, effect: Int, activity: Float, repeats: Float, shape: Float, time: Float) =
    FxUserPreset(name, FX_DEFAULTS.toMutableList().also {
        it[FxP.ENABLE] = 1f; it[FxP.EFFECT] = effect / 10f
        it[FxP.ACTIVITY] = activity; it[FxP.REPEATS] = repeats
        it[FxP.SHAPE] = shape; it[FxP.TIME] = time
    })

val FX_FACTORY_PRESETS = listOf(
    fxPreset("Mosaic Wide",0,0.65f,0.55f,0.6f,0.35f),
    fxPreset("Mosaic Octaves",0,0.8f,0.7f,0.4f,0.55f),
    fxPreset("Mosaic Pulse",0,0.45f,0.85f,0.75f,0.25f),
    fxPreset("Mosaic Drift",0,0.7f,0.6f,0.9f,0.6f),
    fxPreset("Seq Filter",1,0.6f,0.45f,0.75f,0.2f),
    fxPreset("Seq Bounce",1,0.8f,0.7f,0.5f,0.45f),
    fxPreset("Seq Layers",1,0.9f,0.6f,0.8f,0.55f),
    fxPreset("Seq Crush",1,0.95f,0.9f,0.3f,0.3f),
    fxPreset("Glide Half",2,0.45f,0.55f,0.8f,0.4f),
    fxPreset("Glide Dive",2,0.65f,0.7f,0.4f,0.65f),
    fxPreset("Glide Rise",2,0.55f,0.5f,0.7f,0.25f),
    fxPreset("Glide Reverse",2,0.85f,0.85f,0.6f,0.7f),
    fxPreset("Haze Short",3,0.45f,0.4f,0.8f,0.3f),
    fxPreset("Haze Cloud",3,0.9f,0.75f,0.9f,0.75f),
    fxPreset("Haze Octave",3,0.65f,0.7f,0.5f,0.5f),
    fxPreset("Haze Sub",3,0.75f,0.8f,0.4f,0.6f),
    fxPreset("Tunnel Stretch",4,0.5f,0.75f,0.6f,0.7f),
    fxPreset("Tunnel Sub",4,0.6f,0.8f,0.35f,0.85f),
    fxPreset("Tunnel Resonant",4,0.7f,0.9f,0.9f,0.65f),
    fxPreset("Tunnel Trigger",4,0.9f,0.65f,0.5f,0.45f),
    fxPreset("Glitch Scatter",5,0.75f,0.55f,0.8f,0.35f),
    fxPreset("Glitch Repeat",5,0.9f,0.8f,0.5f,0.2f),
    fxPreset("Glitch Freeze",5,0.55f,0.9f,0.9f,0.7f),
    fxPreset("Glitch Random",5,1f,0.85f,0.4f,0.45f),
    fxPreset("Blocks Tight",6,0.65f,0.5f,0.7f,0.15f),
    fxPreset("Blocks Wide",6,0.75f,0.65f,0.5f,0.45f),
    fxPreset("Blocks Reverse",6,0.85f,0.7f,0.9f,0.5f),
    fxPreset("Blocks Crush",6,0.95f,0.85f,0.3f,0.25f),
    fxPreset("Interrupt Gate",7,0.75f,0.6f,0.45f,0.25f),
    fxPreset("Interrupt Stutter",7,0.95f,0.75f,0.8f,0.2f),
    fxPreset("Interrupt Sparse",7,0.45f,0.55f,0.7f,0.55f),
    fxPreset("Interrupt Chaos",7,1f,0.95f,0.35f,0.4f),
    fxPreset("Arp Steps",8,0.65f,0.6f,0.7f,0.25f),
    fxPreset("Arp Octaves",8,0.8f,0.75f,0.55f,0.45f),
    fxPreset("Arp Glass",8,0.55f,0.8f,0.95f,0.7f),
    fxPreset("Arp Motion",8,0.95f,0.9f,0.45f,0.3f),
    fxPreset("Pattern Echo",9,0.6f,0.75f,0.55f,0.4f),
    fxPreset("Pattern Sparse",9,0.35f,0.65f,0.8f,0.55f),
    fxPreset("Pattern Dense",9,0.9f,0.85f,0.45f,0.25f),
    fxPreset("Pattern Long",9,0.65f,0.9f,0.8f,0.75f),
    fxPreset("Warp Tape",10,0.45f,0.7f,0.7f,0.45f),
    fxPreset("Warp Pitch",10,0.8f,0.8f,0.9f,0.7f),
    fxPreset("Warp Reverse",10,0.65f,0.85f,0.5f,0.55f),
    fxPreset("Warp Infinite",10,1f,0.95f,0.6f,0.85f)
)
