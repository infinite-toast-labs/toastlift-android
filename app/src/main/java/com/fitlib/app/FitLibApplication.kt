package com.fitlib.app

import android.app.Application
import com.fitlib.app.data.AppContainer

class FitLibApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
