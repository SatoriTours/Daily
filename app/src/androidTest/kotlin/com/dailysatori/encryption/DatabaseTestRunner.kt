package com.dailysatori.encryption

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/** Tests explicitly exercise bootstrap, including pre-DI failures and genuinely old APK data. */
class DatabaseTestRunner : AndroidJUnitRunner() {
    override fun newApplication(loader: ClassLoader, name: String, context: Context): Application =
        super.newApplication(loader, Application::class.java.name, context)
}
