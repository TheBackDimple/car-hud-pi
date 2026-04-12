package com.example.carhud

import android.app.Application
import com.example.carhud.service.BleObdProvider
import com.example.carhud.service.NavigationVoiceController

class CarHudApplication : Application() {

    val bleObdProvider: BleObdProvider by lazy { BleObdProvider(this) }

    override fun onCreate() {
        super.onCreate()
        NavigationVoiceController.init(this)
    }
}
