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
        `in`.gov.itantra.service.ConnectionService.start(this)
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
        
        val missingPermissions = permissionsToRequest.filter {
            androidx.core.content.ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            `in`.gov.itantra.service.ConnectionService.start(this)
        } else {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }

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
