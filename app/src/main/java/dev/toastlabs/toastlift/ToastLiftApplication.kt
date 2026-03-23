package dev.toastlabs.toastlift

import android.app.Application
import dev.toastlabs.toastlift.data.AppContainer

class ToastLiftApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
