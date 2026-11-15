package com.oberjein.synth

/** Thin JNI bridge to the C++ synth (cpp/native-lib.cpp). All calls are real-time safe. */
object SynthEngine {
    init { System.loadLibrary("oberjein") }

    external fun nativeStart(): Boolean
    external fun nativeStop()
    external fun nativeSetParam(id: Int, value: Float)
    external fun nativeNoteOn(note: Int, velocity: Float)
    external fun nativeNoteOff(note: Int)
    external fun nativeAllNotesOff()
    external fun nativePitchBend(value: Float)
}
