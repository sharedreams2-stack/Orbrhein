package com.oberjein.synth

import android.media.midi.MidiDeviceService
import android.media.midi.MidiReceiver

/** Exposes Oberjein as a virtual MIDI device to compatible Android music apps. */
class OberjeinMidiDeviceService : MidiDeviceService() {
    private val input = object : MidiReceiver() {
        override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
            MidiBridge.receive(msg, offset, count)
        }
    }

    override fun onGetInputPortReceivers(): Array<MidiReceiver> = arrayOf(input)

    override fun onCreate() {
        super.onCreate()
        MidiBridge.setOutputs(getOutputPortReceivers())
    }

    override fun onDeviceStatusChanged(status: android.media.midi.MidiDeviceStatus) {
        super.onDeviceStatusChanged(status)
        MidiBridge.setOutputs(getOutputPortReceivers())
    }

    override fun onClose() {
        MidiBridge.setOutputs(emptyArray())
        super.onClose()
    }
}
