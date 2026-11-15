package ai.pleos.playground.afk.service

import ai.pleos.playground.afk.MainActivity
import ai.pleos.playground.afk.data.AfkMetricsRepository
import ai.pleos.playground.afk.sdk.NaviMonitorClient
import ai.pleos.playground.afk.sdk.VehicleMonitorClient
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Foreground service that keeps NaviHelper and Vehicle SDK listeners alive
 * while navigation or other Pleos apps occupy the foreground.
 */
class AfkMonitoringService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var naviClient: NaviMonitorClient
    private lateinit var vehicleClient: VehicleMonitorClient

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        naviClient = NaviMonitorClient(this)
        vehicleClient = VehicleMonitorClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification("Monitoring road & vehicle events..."))
        AfkMetricsRepository.setServiceRunning(true)

        naviClient.start()
        vehicleClient.start()

        AfkMetricsRepository.state
            .onEach { metrics ->
                val summary = "Score ${metrics.score.totalPoints} | " +
                    "Congestion ${metrics.congestion.totalCongestionTimeSec}s | " +
                    "Incidents ${metrics.incidents.activeTrafficEventCount}"
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(NOTIFICATION_ID, buildNotification(summary))
            }
            .launchIn(serviceScope)

        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        naviClient.stop()
        vehicleClient.stop()
        AfkMetricsRepository.setServiceRunning(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Pleos AFK Monitoring",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Background monitoring for traffic congestion, incidents, and vehicle safety"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(contentText: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Pleos AFK Monitor Active")
            .setContentText(contentText)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "pleos_afk_monitor"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "ai.pleos.playground.afk.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, AfkMonitoringService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, AfkMonitoringService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
