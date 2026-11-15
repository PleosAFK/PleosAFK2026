package ai.pleos.playground.afk.sdk

import ai.pleos.playground.afk.data.AfkMetricsRepository
import ai.pleos.playground.afk.model.SdkConnectionState
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.CautionPoint
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log

/**
 * Wraps Pleos [NaviHelper] to collect congestion, ETA delay, traffic incidents,
 * and caution point events in the background.
 */
class NaviMonitorClient(
    private val context: Context,
) : NaviHelperEventListener {

    private var naviHelper: NaviHelper? = null

    fun start() {
        if (naviHelper != null) return
        try {
            val helper = NaviHelper(context.applicationContext)
            helper.initialize()
            helper.addListener(this)
            naviHelper = helper
            AfkMetricsRepository.updateNaviConnection(
                SdkConnectionState.CONNECTED,
                "NaviHelper initialized"
            )
        } catch (e: Exception) {
            Log.w(TAG, "NaviHelper unavailable: ${e.message}", e)
            runCatching { naviHelper?.release() }
            naviHelper = null
            AfkMetricsRepository.updateNaviConnection(
                SdkConnectionState.UNAVAILABLE,
                "Unavailable: ${e.message ?: e::class.java.simpleName}"
            )
        }
    }

    fun stop() {
        val helper = naviHelper ?: return
        runCatching {
            helper.removeListener(this)
            helper.release()
        }.onFailure { e ->
            Log.w(TAG, "NaviHelper release failed: ${e.message}", e)
        }
        naviHelper = null
        AfkMetricsRepository.updateNaviConnection(SdkConnectionState.IDLE, "Released")
    }

    override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
        AfkMetricsRepository.onRouteStarted()
    }

    override fun onRouteCancelled() {
        AfkMetricsRepository.onRouteStopped("Route cancelled")
    }

    override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) {
        AfkMetricsRepository.onTrafficStatusUpdated(
            isRouting = routeStateInfo.isRouting,
            trafficStatus = routeStateInfo.trafficStatus,
        )
    }

    override fun onDrivingInfo(drivingInfo: DrivingInfo) {
        AfkMetricsRepository.onDurationUpdated(drivingInfo.destination.duration)
    }

    override fun onTripInfo(tripInfo: TripInfo) {
        AfkMetricsRepository.onDurationUpdated(tripInfo.eta)
    }

    override fun onCautionPointInfo(cautionPointList: List<CautionPoint>) {
        AfkMetricsRepository.onCautionPointsUpdated(cautionPointList)
    }

    override fun onError(errorCode: NaviErrorCode) {
        AfkMetricsRepository.appendLog("[Navi] Error: $errorCode")
    }

    companion object {
        private const val TAG = "NaviMonitorClient"
    }
}
