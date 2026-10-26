package com.upisegregator.app

import android.app.Application

/**
 * Main Application class for UPI Segregator.
 * Serves as the application-level context and dependency initialization point.
 */
class UpiSegregatorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Future milestones will initialize Room database and repositories here.
    }
}
