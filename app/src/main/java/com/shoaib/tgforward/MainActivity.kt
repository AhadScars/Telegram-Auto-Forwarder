package com.shoaib.tgforward

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.shoaib.tgforward.service.ForwardService
import com.shoaib.tgforward.ui.AppRoot
import com.shoaib.tgforward.ui.TgTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val engine = (application as TgForwardApp).engine
        if (engine.enabled.value || engine.shortenEnabled.value) {
            ForwardService.start(this)
        }
        setContent {
            TgTheme {
                AppRoot(engine)
            }
        }
    }
}
