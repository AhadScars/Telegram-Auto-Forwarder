package com.shoaib.tgforward

import android.app.Application
import com.shoaib.tgforward.engine.AmazonClient
import com.shoaib.tgforward.engine.ForwardEngine

class TgForwardApp : Application() {
    lateinit var engine: ForwardEngine
        private set

    override fun onCreate() {
        super.onCreate()
        AmazonClient.init(this)
        engine = ForwardEngine(this)
        engine.bootstrap()
    }
}
