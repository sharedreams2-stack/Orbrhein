package com.oberjein.synth

import android.os.Bundle
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.oberjein.synth.ui.MainScreen
import com.oberjein.synth.ui.OberjeinTheme

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val vm: SynthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        getSharedPreferences("oberjein_state", MODE_PRIVATE).getString("current", null)?.let { saved -> runCatching { vm.restoreStateJson(saved) } }
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent { OberjeinTheme { MainScreen(vm) } }
    }

    override fun onStart() {
        super.onStart()
        MidiBridge.attach(vm)
        ContextCompat.startForegroundService(this, Intent(this, OberjeinAudioService::class.java).setAction(OberjeinAudioService.ACTION_START))
    }

    override fun onStop() {
        super.onStop()
        getSharedPreferences("oberjein_state", MODE_PRIVATE).edit().putString("current", vm.exportStateJson()).apply()
    }
}
