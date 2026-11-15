package ai.pleos.playground.afk.unity.service

import ai.pleos.playground.afk.unity.data.AfkUnityRepository
import ai.pleos.playground.afk.unity.sdk.NaviUnityMonitorClient
import ai.pleos.playground.afk.unity.sdk.VehicleUnityMonitorClient
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class AfkUnityMonitoringService : Service() {

    private lateinit var naviClient: NaviUnityMonitorClient
    private lateinit var vehicleClient: VehicleUnityMonitorClient

    override fun onCreate() {
        super.onCreate()
        AfkUnityRepository.init(this)
        createNotificationChannel()
        naviClient = NaviUnityMonitorClient(this)
        vehicleClient = VehicleUnityMonitorClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        AfkUnityRepository.setServiceRunning(true)
        naviClient.start()
        vehicleClient.start()

        return START_STICKY
    }

    override fun onDestroy() {
        naviClient.stop()
        vehicleClient.stop()
        AfkUnityRepository.setServiceRunning(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Pleos AFK Background Monitor",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Collects Pleos navigation and vehicle safety events in the background"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Pleos AFK Monitor Active")
            .setContentText("Collecting road & vehicle metrics for game")
            .setOngoing(true)
            .build()

    companion object {
        private const val CHANNEL_ID = "pleos_afk_unity_monitor"
        private const val NOTIFICATION_ID = 2001
        private const val ACTION_STOP = "ai.pleos.playground.afk.unity.action.STOP"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, AfkUnityMonitoringService::class.java))
        }

        fun stop(context: Context) {
            val intent = Intent(context, AfkUnityMonitoringService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
