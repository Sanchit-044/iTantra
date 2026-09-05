package `in`.gov.itantra

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ITantraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        `in`.gov.itantra.core.diag.AppLog.logger = `in`.gov.itantra.android.diag.AndroidLogger()
    }
}
