package com.mutissx.soundframe.app

import android.app.Application
import com.mutissx.soundframe.app.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class SoundframeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@SoundframeApplication)
            modules(appModules)
        }
    }
}
