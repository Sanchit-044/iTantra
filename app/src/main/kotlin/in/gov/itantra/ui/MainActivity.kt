package `in`.gov.itantra.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import `in`.gov.itantra.core.theme.ThemeStore
import javax.inject.Inject

import android.Manifest
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var themeStore: ThemeStore

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Could handle denied permissions here, but we'll assume granted for hackathon prototype
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val permissionsToRequest = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())

        setContent {
            val mode by themeStore.mode.collectAsState()
            ITantraTheme(mode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ITantraApp()
                }
            }
        }
    }
}
