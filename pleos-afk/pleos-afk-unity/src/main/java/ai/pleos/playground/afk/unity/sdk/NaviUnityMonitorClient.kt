package ai.pleos.playground.afk.unity.sdk

import ai.pleos.playground.afk.unity.data.AfkUnityRepository
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

class NaviUnityMonitorClient(
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
            AfkUnityRepository.setNaviConnected(true)
        } catch (e: Exception) {
            Log.w(TAG, "NaviHelper unavailable: ${e.message}", e)
            runCatching { naviHelper?.release() }
            naviHelper = null
            AfkUnityRepository.setNaviConnected(false)
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
        AfkUnityRepository.setNaviConnected(false)
    }

    override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
        AfkUnityRepository.onRouteStarted()
    }

    override fun onRouteCancelled() {
        AfkUnityRepository.onRouteStopped()
    }

    override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) {
        AfkUnityRepository.onTrafficStatusUpdated(
            isRouting = routeStateInfo.isRouting,
            trafficStatus = routeStateInfo.trafficStatus,
        )
    }

    override fun onDrivingInfo(drivingInfo: DrivingInfo) {
        AfkUnityRepository.onDurationUpdated(drivingInfo.destination.duration)
    }

    override fun onTripInfo(tripInfo: TripInfo) {
        AfkUnityRepository.onDurationUpdated(tripInfo.eta)
    }

    override fun onCautionPointInfo(cautionPointList: List<CautionPoint>) {
        AfkUnityRepository.onCautionPointsUpdated(cautionPointList)
    }

    override fun onError(errorCode: NaviErrorCode) {
        Log.w(TAG, "NaviHelper error: $errorCode")
    }

    companion object {
        private const val TAG = "NaviUnityMonitorClient"
    }
}
