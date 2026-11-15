package com.oberjein.synth

import android.content.Context
import android.content.pm.PackageManager
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.Looper

/** USB / Bluetooth MIDI keyboards: notes, pitch bend, mod wheel (CC1 -> LFO pitch), CC123 panic. */
class MidiInput(private val context: Context, private val vm: SynthViewModel) {
    private val main = Handler(Looper.getMainLooper())
    private var manager: MidiManager? = null
    private val devices = mutableListOf<MidiDevice>()
    private val ports = mutableListOf<MidiOutputPort>()

    private val deviceCallback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(device: MidiDeviceInfo) { open(device) }
    }

    private val receiver = object : MidiReceiver() {
        override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
            parse(msg, offset, count)
        }
    }

    @Suppress("DEPRECATION")
    fun start() {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_MIDI)) return
        val m = context.getSystemService(Context.MIDI_SERVICE) as? MidiManager ?: return
        manager = m
        m.registerDeviceCallback(deviceCallback, main)
        m.devices.forEach { open(it) }
    }

    fun stop() {
        manager?.unregisterDeviceCallback(deviceCallback)
        manager = null
        ports.forEach { runCatching { it.disconnect(receiver); it.close() } }
        devices.forEach { runCatching { it.close() } }
        ports.clear()
        devices.clear()
    }

    private fun open(info: MidiDeviceInfo) {
        if (info.outputPortCount == 0) return
        manager?.openDevice(info, { device ->
            if (device != null) {
                val port = device.openOutputPort(0)
                if (port != null) {
                    port.connect(receiver)
                    ports += port
                    devices += device
                } else {
                    device.close()
                }
            }
        }, main)
    }

    private fun parse(msg: ByteArray, offset: Int, count: Int) {
        var i = offset
        val end = offset + count
        while (i < end) {
            val status = msg[i].toInt() and 0xFF
            if (status < 0x80) { i++; continue }
            val kind = status and 0xF0
            val len = when (kind) { 0xC0, 0xD0 -> 2; 0xF0 -> 1; else -> 3 }
            if (i + len > end) break
            val d1 = if (len > 1) msg[i + 1].toInt() and 0x7F else 0
            val d2 = if (len > 2) msg[i + 2].toInt() and 0x7F else 0
            when (kind) {
                0x90 -> main.post { if (d2 > 0) vm.midiNoteOn(d1, d2 / 127f) else vm.midiNoteOff(d1) }
                0x80 -> main.post { vm.midiNoteOff(d1) }
                0xE0 -> main.post { vm.pitchBend((((d2 shl 7) or d1) - 8192) / 8192f) }
                0xB0 -> main.post { vm.onMidiCC(d1, d2) }
            }
            i += len
        }
    }
}
