package com.example.whatsappchatconverter

import android.app.Application
import android.util.Log
import com.jraska.console.timber.ConsoleTree
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Locale

class WhatsappConverterApp : Application() {
    override fun onCreate() {
        super.onCreate()

        val dateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())

        val consoleTree = ConsoleTree.Builder()
            .minPriority(Log.VERBOSE)
            .timeFormat(dateFormat)
            .build()

        Timber.plant(consoleTree)
    }
}