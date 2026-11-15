package ai.pleos.playground.afk.unity

import ai.pleos.playground.afk.unity.data.AfkUnityRepository
import ai.pleos.playground.afk.unity.service.AfkUnityMonitoringService
import android.content.Context

/**
 * Entry point invoked from Unity C# via AndroidJavaClass("ai.pleos.playground.afk.unity.PleosAfkUnityBridge").
 */
object PleosAfkUnityBridge {

    @JvmStatic
    fun startMonitoring(context: Context) {
        AfkUnityRepository.init(context)
        AfkUnityMonitoringService.start(context)
    }

    @JvmStatic
    fun stopMonitoring(context: Context) {
        AfkUnityMonitoringService.stop(context)
    }

    @JvmStatic
    fun registerUnityCallback(gameObjectName: String, methodName: String) {
        AfkUnityRepository.registerUnityCallback(gameObjectName, methodName)
    }

    @JvmStatic
    fun unregisterUnityCallback() {
        AfkUnityRepository.unregisterUnityCallback()
    }

    @JvmStatic
    fun getMetricsJson(context: Context): String =
        AfkUnityRepository.getMetricsJson(context)

    @JvmStatic
    fun consumePendingDeltaJson(context: Context): String =
        AfkUnityRepository.consumePendingDeltaJson(context)

    @JvmStatic
    fun resetMetrics(context: Context) {
        AfkUnityRepository.reset(context)
    }

    @JvmStatic
    fun simulateCongestion(extraSeconds: Long) {
        AfkUnityRepository.simulateCongestion(extraSeconds)
    }

    @JvmStatic
    fun simulateTrafficAccident() {
        AfkUnityRepository.simulateTrafficAccident()
    }

    @JvmStatic
    fun simulateAirbagDeployed() {
        AfkUnityRepository.simulateAirbagDeployed()
    }
}
