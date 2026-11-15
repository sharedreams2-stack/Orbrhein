package com.oberjein.synth

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

/** Keeps the synth engine and MIDI input alive while the Activity is backgrounded or locked. */
class OberjeinAudioService : Service() {
    private var midiInput: MidiInput? = null
    private var midiViewModel: SynthViewModel? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var engineStarted = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "Oberjein:AudioPlayback"
        ).apply { setReferenceCounted(false); acquire() }
        engineStarted = runCatching { SynthEngine.nativeStart() }.getOrDefault(false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            MidiBridge.stopPlayback()
            stopPlaybackAndSelf()
            return START_NOT_STICKY
        }
        val activeVm = MidiBridge.currentViewModel()
        if (activeVm != null) {
            activeVm.pushAll()
            activeVm.pushFxAll()
            activeVm.resumeArpeggiator()
        }
        if (activeVm != null && midiViewModel !== activeVm) {
            midiInput?.stop()
            midiInput = MidiInput(this, activeVm).also { it.start() }
            midiViewModel = activeVm
        }
        return START_STICKY
    }

    private fun stopPlaybackAndSelf() {
        midiInput?.stop()
        midiInput = null
        midiViewModel = null
        runCatching { SynthEngine.nativeAllNotesOff(); SynthEngine.nativeStop() }
        engineStarted = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        midiInput?.stop()
        midiInput = null
        midiViewModel = null
        if (engineStarted) runCatching { SynthEngine.nativeAllNotesOff(); SynthEngine.nativeStop() }
        engineStarted = false
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Oberjein audio", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Controles de reprodução do sintetizador"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        val stopIntent = PendingIntent.getService(this, 1, Intent(this, OberjeinAudioService::class.java).setAction(ACTION_STOP), flags)
        val openIntent = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), flags)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Oberjein")
            .setContentText("Sintetizador ativo em segundo plano")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_pause, "Parar áudio", stopIntent)
            .build()
    }

    companion object {
        const val ACTION_START = "com.oberjein.synth.action.START_AUDIO"
        const val ACTION_STOP = "com.oberjein.synth.action.STOP_AUDIO"
        private const val CHANNEL_ID = "oberjein_audio_playback"
        private const val NOTIFICATION_ID = 4101
    }
}
