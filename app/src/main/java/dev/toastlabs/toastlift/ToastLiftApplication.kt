package dev.toastlabs.toastlift

import android.app.Application
import com.appreveal.AppReveal
import dev.toastlabs.toastlift.data.AppContainer
import dev.toastlabs.toastlift.data.DataEnvironment
import dev.toastlabs.toastlift.debug.ToastLiftAppRevealBindings

class ToastLiftApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Staging deliberately keeps this debug-only server so its production
        // feature configuration can be captured and reviewed. Release uses the
        // no-op AppReveal dependency and never enters this branch.
        if (BuildConfig.DEBUG) {
            AppReveal.start(this)
            ToastLiftAppRevealBindings.install(this)
        }
    }

    internal fun replaceContainerForDebug(dataEnvironment: DataEnvironment) {
        container.toastLiftDatabase.close()
        container = AppContainer(this, dataEnvironment)
    }

    internal fun restoreRealContainerAfterDebug() {
        container.toastLiftDatabase.close()
        container = AppContainer(this)
    }
}
