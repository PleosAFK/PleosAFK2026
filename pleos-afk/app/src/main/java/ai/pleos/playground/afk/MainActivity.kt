package ai.pleos.playground.afk

import ai.pleos.playground.afk.data.AfkMetricsRepository
import ai.pleos.playground.afk.service.AfkMonitoringService
import ai.pleos.playground.afk.ui.AfkDashboardScreen
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        AfkMonitoringService.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                val state by AfkMetricsRepository.state.collectAsStateWithLifecycle()
                AfkDashboardScreen(
                    state = state,
                    onStartService = ::startMonitoringWithPermissionCheck,
                    onStopService = { AfkMonitoringService.stop(this) },
                    onSimulateCongestion = { AfkMetricsRepository.simulateCongestion() },
                    onSimulateAccident = { AfkMetricsRepository.simulateTrafficAccident() },
                    onSimulateAirbag = { AfkMetricsRepository.simulateAirbagDeployed() },
                    onReset = { AfkMetricsRepository.resetMetrics() },
                )
            }
        }
    }

    private fun startMonitoringWithPermissionCheck() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            AfkMonitoringService.start(this)
        } else {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
