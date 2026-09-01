package in.gov.itantra

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ITantraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize any necessary components here
    }
}
