package com.oberjein.synth

import android.os.Bundle
import android.view.WindowManager
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
    private val vm: SynthViewModel by viewModels()
    private var midi: MidiInput? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent { OberjeinTheme { MainScreen(vm) } }
    }

    override fun onStart() {
        super.onStart()
        vm.pushAll()
        SynthEngine.nativeStart()
        midi = MidiInput(this, vm).also { it.start() }
    }

    override fun onStop() {
        super.onStop()
        SynthEngine.nativeAllNotesOff()
        SynthEngine.nativeStop()
        midi?.stop()
        midi = null
    }
}
