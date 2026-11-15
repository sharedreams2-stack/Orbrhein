package com.oberjein.synth

/** Parameter ids. Keep this order identical to `enum Param` in cpp/synth.h. */
object P {
    const val VCO1_SAW = 0
    const val VCO1_PULSE = 1
    const val VCO1_LEVEL = 2
    const val VCO2_SAW = 3
    const val VCO2_PULSE = 4
    const val VCO2_LEVEL = 5
    const val VCO2_SEMI = 6
    const val VCO2_DETUNE = 7
    const val SYNC = 8
    const val PWM = 9
    const val NOISE = 10
    const val CUTOFF = 11
    const val RESO = 12
    const val POLES = 13
    const val FENV_AMT = 14
    const val KEYTRACK = 15
    const val F_A = 16
    const val F_D = 17
    const val F_S = 18
    const val F_R = 19
    const val A_A = 20
    const val A_D = 21
    const val A_S = 22
    const val A_R = 23
    const val LFO_RATE = 24
    const val LFO_WAVE = 25
    const val LFO_PITCH = 26
    const val LFO_FILTER = 27
    const val LFO_PWM = 28
    const val PORTA = 29
    const val UNISON = 30
    const val UNI_DETUNE = 31
    const val VOLUME = 32
    const val SPREAD = 33
    const val XMOD = 34
    const val BRIGHT = 35
    const val FILTER_MIX = 36
    const val FILTER_BP = 37
    const val LP24 = 38
    const val FILTER_MULTI = 39
    const val FENV_INVERT = 40
    const val SELF_OSC = 41
    const val LFO2_RATE = 42
    const val LFO2_WAVE = 43
    const val LFO2_PITCH = 44
    const val LFO2_FILTER = 45
    const val LFO2_PWM = 46
    const val VOICE_COUNT = 47
    const val UNI_VOICES = 48
    const val VAM = 49
    const val PERSISTENT_VOICING = 50
    const val LEGATO = 51
    const val OVERSAMPLING = 52
    const val TRANSPOSE = 53
    const val FINE_TUNE = 54
    const val COUNT = 55
}

/** All values are normalized 0..1 (switches are 0 or 1). The DSP maps them to real units. */
val DEFAULTS: FloatArray = FloatArray(P.COUNT).also {
    it[P.VCO1_SAW] = 1f; it[P.VCO1_LEVEL] = 0.8f
    it[P.VCO2_SAW] = 1f; it[P.VCO2_LEVEL] = 0.8f; it[P.VCO2_SEMI] = 0.5f; it[P.VCO2_DETUNE] = 0.05f
    it[P.PWM] = 0.3f
    it[P.CUTOFF] = 0.65f; it[P.RESO] = 0.15f; it[P.POLES] = 1f; it[P.FENV_AMT] = 0.3f; it[P.KEYTRACK] = 0.5f
    it[P.F_A] = 0.05f; it[P.F_D] = 0.45f; it[P.F_S] = 0.4f; it[P.F_R] = 0.35f
    it[P.A_A] = 0.02f; it[P.A_D] = 0.5f; it[P.A_S] = 0.8f; it[P.A_R] = 0.35f
    it[P.LFO_RATE] = 0.4f
    it[P.UNI_DETUNE] = 0.3f; it[P.VOLUME] = 0.7f
    it[P.SPREAD] = 0.35f; it[P.XMOD] = 0f; it[P.BRIGHT] = 0f
    it[P.FILTER_MIX] = 0f; it[P.FILTER_MULTI] = 0f
    it[P.LFO2_RATE] = 0.25f
    it[P.VOICE_COUNT] = 0.47f
    it[P.UNI_VOICES] = 0.47f
    it[P.OVERSAMPLING] = 0f
    it[P.TRANSPOSE] = 0.5f
    it[P.FINE_TUNE] = 0.5f
}

class Preset(val name: String, val values: FloatArray)

private fun preset(name: String, vararg changes: Pair<Int, Float>): Preset {
    val v = DEFAULTS.copyOf()
    changes.forEach { (id, x) -> v[id] = x }
    return Preset(name, v)
}

val PRESETS: List<Preset> = listOf(
    preset("Init"),
    preset("Brass",
        P.VCO2_DETUNE to 0.03f, P.CUTOFF to 0.35f, P.RESO to 0.1f, P.FENV_AMT to 0.55f,
        P.F_A to 0.35f, P.F_D to 0.5f, P.F_S to 0.5f, P.F_R to 0.35f,
        P.A_A to 0.3f, P.A_S to 0.85f, P.A_R to 0.35f),
    preset("Poly pad",
        P.VCO1_PULSE to 1f, P.PWM to 0.5f, P.LFO_PWM to 0.5f, P.LFO_RATE to 0.2f,
        P.CUTOFF to 0.5f, P.FENV_AMT to 0.2f, P.F_A to 0.75f, P.F_S to 0.7f,
        P.A_A to 0.75f, P.A_S to 0.9f, P.A_R to 0.8f),
    preset("Fat bass",
        P.VCO1_PULSE to 1f, P.VCO2_DETUNE to 0.02f, P.CUTOFF to 0.3f, P.RESO to 0.3f, P.FENV_AMT to 0.6f,
        P.F_A to 0.02f, P.F_D to 0.4f, P.F_S to 0.1f, P.A_A to 0.02f, P.A_S to 0.9f, P.A_R to 0.2f),
    preset("Sync lead",
        P.SYNC to 1f, P.VCO2_SEMI to 0.8f, P.LFO_PITCH to 0.15f, P.LFO_RATE to 0.45f,
        P.PORTA to 0.35f, P.UNISON to 1f, P.UNI_DETUNE to 0.25f, P.CUTOFF to 0.7f, P.FENV_AMT to 0.4f),
    preset("Strings",
        P.VCO2_DETUNE to 0.06f, P.POLES to 0f, P.CUTOFF to 0.55f, P.FENV_AMT to 0.1f,
        P.A_A to 0.65f, P.A_S to 0.9f, P.A_R to 0.7f, P.LFO_PWM to 0f),
    preset("Pluck",
        P.VCO1_SAW to 0f, P.VCO1_PULSE to 1f, P.PWM to 0.4f, P.CUTOFF to 0.3f, P.RESO to 0.35f, P.FENV_AMT to 0.7f,
        P.F_D to 0.35f, P.F_S to 0f, P.A_D to 0.4f, P.A_S to 0f, P.A_R to 0.3f),
    preset("Sweep fx",
        P.NOISE to 0.5f, P.RESO to 0.7f, P.LFO_FILTER to 0.6f, P.LFO_RATE to 0.15f, P.CUTOFF to 0.5f,
        P.A_A to 0.8f, P.A_S to 1f, P.A_R to 0.8f),
)
