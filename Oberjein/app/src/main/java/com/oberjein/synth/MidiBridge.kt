package com.oberjein.synth

import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.Looper

/** MIDI 1.0 virtual-device bridge for inter-app routing. */
object MidiBridge {
    @Volatile private var viewModel: SynthViewModel? = null
    @Volatile private var outputs: Array<MidiReceiver> = emptyArray()
    private val main = Handler(Looper.getMainLooper())

    fun attach(vm: SynthViewModel) { viewModel = vm }
    fun currentViewModel(): SynthViewModel? = viewModel
    fun stopPlayback() { viewModel?.let { it.pauseArpeggiator(); it.panic() } }
    fun detach(vm: SynthViewModel) { if (viewModel === vm) viewModel = null }
    fun setOutputs(receivers: Array<MidiReceiver>) { outputs = receivers }

    fun send(bytes: ByteArray) {
        val now = System.nanoTime()
        outputs.forEach { receiver -> runCatching { receiver.send(bytes, 0, bytes.size, now) } }
    }

    fun receive(bytes: ByteArray, offset: Int, count: Int) {
        val data = bytes.copyOfRange(offset, offset + count)
        main.post {
            val vm = viewModel ?: return@post
            var i = 0
            while (i < data.size) {
                val status = data[i].toInt() and 0xFF
                if (status < 0x80) { i++; continue }
                val kind = status and 0xF0
                val length = when (kind) { 0xC0, 0xD0 -> 2; 0xF0 -> 1; else -> 3 }
                if (i + length > data.size) break
                val d1 = if (length > 1) data[i+1].toInt() and 0x7F else 0
                val d2 = if (length > 2) data[i+2].toInt() and 0x7F else 0
                when (kind) {
                    0x90 -> if (d2 > 0) vm.midiNoteOn(d1, d2 / 127f) else vm.midiNoteOff(d1)
                    0x80 -> vm.midiNoteOff(d1)
                    0xB0 -> vm.onMidiCC(d1, d2)
                    0xE0 -> vm.pitchBend((((d2 shl 7) or d1) - 8192) / 8192f)
                }
                i += length
            }
        }
    }

    fun noteOn(note: Int, velocity: Float) {
        val vel = (velocity.coerceIn(0f,1f) * 127f).toInt()
        send(byteArrayOf(0x90.toByte(), (note and 0x7F).toByte(), vel.toByte()))
    }
    fun noteOff(note: Int) = send(byteArrayOf(0x80.toByte(), (note and 0x7F).toByte(), 0))
}
