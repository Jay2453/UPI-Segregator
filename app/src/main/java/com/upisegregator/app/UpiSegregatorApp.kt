package com.upisegregator.app

import android.app.Application
import com.upisegregator.app.data.AppContainer
import com.upisegregator.app.data.AppContainerImpl

/**
 * Main Application class for UPI Segregator.
 * Serves as the application-level context and dependency initialization point.
 */
class UpiSegregatorApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainerImpl(this)
    }
}
