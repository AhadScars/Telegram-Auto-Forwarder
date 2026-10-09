package com.shoaib.tgforward.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences("tg_forward", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("enabled", false) || prefs.getBoolean("short_on", false)
        val apiId = prefs.getInt("api_id", 0)
        if (enabled && apiId != 0) {
            ForwardService.start(context, "Phone start hua. Auto post wapas on hai.")
        }
    }
}
