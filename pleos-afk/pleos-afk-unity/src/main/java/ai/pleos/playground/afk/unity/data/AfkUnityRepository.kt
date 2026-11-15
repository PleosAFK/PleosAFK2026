package ai.pleos.playground.afk.unity.data

import ai.pleos.playground.afk.unity.model.AfkPendingDelta
import ai.pleos.playground.afk.unity.model.AfkUnityMetrics
import ai.pleos.playground.navi.constants.TrafficCongestionType
import ai.pleos.playground.navi.constants.TrafficEventType
import ai.pleos.playground.navi.data.CautionPoint
import ai.pleos.playground.navi.data.TrafficStatus
import android.content.Context
import android.util.Log

/**
 * Thread-safe repository for Pleos AFK metrics.
 * Persists the latest JSON snapshot in SharedPreferences so Unity can retrieve
 * metrics accumulated while Unity was paused in the background.
 */
object AfkUnityRepository {

    private const val TAG = "AfkUnityRepository"
    private const val PREFS_NAME = "pleos_afk_unity_prefs"
    private const val KEY_METRICS_JSON = "latest_metrics_json"
    private const val KEY_PENDING_DELTA_JSON = "pending_delta_json"

    private val lock = Any()
    private var appContext: Context? = null
    private var metrics = AfkUnityMetrics()
    private var pendingDelta = AfkPendingDelta()
    private var baselineDurationSec: Int? = null

    private var unityGameObject: String? = null
    private var unityMethodName: String? = null

    fun init(context: Context) {
        synchronized(lock) {
            if (appContext == null) {
                appContext = context.applicationContext
            }
        }
    }

    fun registerUnityCallback(gameObject: String, methodName: String) {
        synchronized(lock) {
            unityGameObject = gameObject
            unityMethodName = methodName
        }
    }

    fun unregisterUnityCallback() {
        synchronized(lock) {
            unityGameObject = null
            unityMethodName = null
        }
    }

    fun getMetricsJson(context: Context? = null): String {
        synchronized(lock) {
            if (context != null && appContext == null) {
                appContext = context.applicationContext
            }
            if (metrics.lastUpdatedEpochMs == 0L) {
                val saved = appContext
                    ?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    ?.getString(KEY_METRICS_JSON, null)
                if (!saved.isNullOrBlank()) {
                    return saved
                }
            }
            return metrics.toJsonString()
        }
    }

    /**
     * Atomically returns newly accumulated unpleasant metrics since the last consume call,
     * then resets the pending delta counters to zero.
     */
    fun consumePendingDeltaJson(context: Context? = null): String {
        synchronized(lock) {
            if (context != null && appContext == null) {
                appContext = context.applicationContext
            }
            val resultJson = if (pendingDelta == AfkPendingDelta()) {
                appContext
                    ?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    ?.getString(KEY_PENDING_DELTA_JSON, null)
                    ?.takeIf { it.isNotBlank() }
                    ?: pendingDelta.toJsonString()
            } else {
                pendingDelta.toJsonString()
            }
            pendingDelta = AfkPendingDelta()
            appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ?.edit()
                ?.remove(KEY_PENDING_DELTA_JSON)
                ?.apply()
            return resultJson
        }
    }

    fun setServiceRunning(running: Boolean) {
        update { it.copy(isServiceRunning = running) }
    }

    fun setNaviConnected(connected: Boolean) {
        update { it.copy(naviConnected = connected) }
    }

    fun setVehicleConnected(connected: Boolean) {
        update { it.copy(vehicleConnected = connected) }
    }

    fun onRouteStarted() {
        synchronized(lock) {
            baselineDurationSec = null
        }
        update { it.copy(isRouting = true, etaDelaySec = 0) }
    }

    fun onRouteStopped() {
        synchronized(lock) {
            baselineDurationSec = null
        }
        update { it.copy(isRouting = false) }
    }

    fun onDurationUpdated(remainingDurationSec: Int) {
        val delaySec = synchronized(lock) {
            val baseline = baselineDurationSec
            if (baseline == null) {
                baselineDurationSec = remainingDurationSec
                return
            }
            (remainingDurationSec - baseline).coerceAtLeast(0)
        }
        update { current ->
            recalculate(current.copy(etaDelaySec = delaySec))
        }
    }

    fun onTrafficStatusUpdated(isRouting: Boolean, trafficStatus: TrafficStatus?) {
        val summary = trafficStatus?.summary
        val segments = trafficStatus?.congestionItemList.orEmpty()
        val events = trafficStatus?.trafficEventList.orEmpty()

        val heavyCount = segments.count { it.trafficCongestionType == TrafficCongestionType.HEAVY }
        val slowCount = segments.count { it.trafficCongestionType == TrafficCongestionType.SLOW }
        val accidentCount = events.count { it.eventType == TrafficEventType.ACCIDENT }
        val roadWorkOrDamageCount = events.count {
            it.eventType == TrafficEventType.ROAD_DAMAGE || it.eventType == TrafficEventType.REPAIRING
        }
        val disasterOrOtherCount = events.size - accidentCount - roadWorkOrDamageCount

        update { current ->
            recalculate(
                current.copy(
                    isRouting = isRouting,
                    totalCongestionDistMeters = summary?.totalCongestionDist ?: 0.0,
                    totalCongestionTimeSec = summary?.totalCongestionTime ?: 0L,
                    totalCongestionAvgSpeedKmh = summary?.totalCongestionAvrSpeed ?: 0.0,
                    heavySegmentCount = heavyCount,
                    slowSegmentCount = slowCount,
                    activeTrafficEventCount = events.size,
                    accidentEventCount = accidentCount,
                    roadWorkOrDamageCount = roadWorkOrDamageCount,
                    disasterOrOtherCount = disasterOrOtherCount,
                )
            )
        }
    }

    fun onCautionPointsUpdated(cautionPoints: List<CautionPoint>) {
        update { current ->
            recalculate(current.copy(cautionPointCount = cautionPoints.size))
        }
    }

    fun onAirbagDeployUpdated(area: String, deployed: Boolean) {
        update { current ->
            val updatedAreas = if (deployed) {
                current.airbagDeployedAreas + area
            } else {
                current.airbagDeployedAreas - area
            }
            recalculate(
                current.copy(
                    airbagDeployed = updatedAreas.isNotEmpty(),
                    airbagDeployedAreas = updatedAreas,
                )
            )
        }
    }

    fun onTelltalesUpdated(activeCount: Int) {
        update { it.copy(activeTelltaleCount = activeCount) }
    }

    fun simulateCongestion(extraSeconds: Long = 300L) {
        update { current ->
            recalculate(
                current.copy(
                    isRouting = true,
                    totalCongestionTimeSec = current.totalCongestionTimeSec + extraSeconds,
                    totalCongestionDistMeters = current.totalCongestionDistMeters + 1200.0,
                    totalCongestionAvgSpeedKmh = 12.0,
                    heavySegmentCount = current.heavySegmentCount + 1,
                    etaDelaySec = current.etaDelaySec + extraSeconds.toInt(),
                )
            )
        }
    }

    fun simulateTrafficAccident() {
        update { current ->
            recalculate(
                current.copy(
                    activeTrafficEventCount = current.activeTrafficEventCount + 1,
                    accidentEventCount = current.accidentEventCount + 1,
                )
            )
        }
    }

    fun simulateAirbagDeployed() {
        onAirbagDeployUpdated(area = "SIM_ROW_1_LEFT", deployed = true)
    }

    fun reset(context: Context? = null) {
        synchronized(lock) {
            if (context != null && appContext == null) {
                appContext = context.applicationContext
            }
            baselineDurationSec = null
            pendingDelta = AfkPendingDelta()
            appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ?.edit()
                ?.remove(KEY_PENDING_DELTA_JSON)
                ?.apply()
        }
        update { current ->
            AfkUnityMetrics(
                isServiceRunning = current.isServiceRunning,
                naviConnected = current.naviConnected,
                vehicleConnected = current.vehicleConnected,
            )
        }
    }

    private fun update(transform: (AfkUnityMetrics) -> AfkUnityMetrics) {
        val jsonPayload: String
        val targetGameObject: String?
        val targetMethod: String?

        synchronized(lock) {
            val prev = metrics
            val next = transform(prev).copy(lastUpdatedEpochMs = System.currentTimeMillis())
            metrics = next

            val deltaCongestionTime = (next.totalCongestionTimeSec - prev.totalCongestionTimeSec).coerceAtLeast(0L)
            val deltaEtaDelay = (next.etaDelaySec - prev.etaDelaySec).coerceAtLeast(0)
            val deltaAccidentEvent = (next.accidentEventCount - prev.accidentEventCount).coerceAtLeast(0)
            val deltaRoadWork = (next.roadWorkOrDamageCount - prev.roadWorkOrDamageCount).coerceAtLeast(0)
            val deltaCaution = (next.cautionPointCount - prev.cautionPointCount).coerceAtLeast(0)
            val deltaAirbag = next.airbagDeployed && !prev.airbagDeployed
            val deltaCongestionPts = (next.congestionPoints - prev.congestionPoints).coerceAtLeast(0)
            val deltaIncidentPts = (next.incidentPoints - prev.incidentPoints).coerceAtLeast(0)
            val deltaAccidentPts = (next.accidentPoints - prev.accidentPoints).coerceAtLeast(0)
            val deltaTotalPts = deltaCongestionPts + deltaIncidentPts + deltaAccidentPts

            if (
                deltaCongestionTime > 0L || deltaEtaDelay > 0 || deltaAccidentEvent > 0 ||
                deltaRoadWork > 0 || deltaCaution > 0 || deltaAirbag || deltaTotalPts > 0
            ) {
                pendingDelta = pendingDelta.copy(
                    congestionTimeDeltaSec = pendingDelta.congestionTimeDeltaSec + deltaCongestionTime,
                    etaDelayDeltaSec = pendingDelta.etaDelayDeltaSec + deltaEtaDelay,
                    accidentEventDelta = pendingDelta.accidentEventDelta + deltaAccidentEvent,
                    roadWorkOrDamageDelta = pendingDelta.roadWorkOrDamageDelta + deltaRoadWork,
                    cautionPointDelta = pendingDelta.cautionPointDelta + deltaCaution,
                    airbagDeployedInPeriod = pendingDelta.airbagDeployedInPeriod || deltaAirbag,
                    congestionPointsDelta = pendingDelta.congestionPointsDelta + deltaCongestionPts,
                    incidentPointsDelta = pendingDelta.incidentPointsDelta + deltaIncidentPts,
                    accidentPointsDelta = pendingDelta.accidentPointsDelta + deltaAccidentPts,
                    totalUnpleasantPointsDelta = pendingDelta.totalUnpleasantPointsDelta + deltaTotalPts,
                )
            }

            jsonPayload = next.toJsonString()
            appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ?.edit()
                ?.putString(KEY_METRICS_JSON, jsonPayload)
                ?.putString(KEY_PENDING_DELTA_JSON, pendingDelta.toJsonString())
                ?.apply()
            targetGameObject = unityGameObject
            targetMethod = unityMethodName
        }

        if (!targetGameObject.isNullOrBlank() && !targetMethod.isNullOrBlank()) {
            notifyUnity(targetGameObject, targetMethod, jsonPayload)
        }
    }

    private fun recalculate(m: AfkUnityMetrics): AfkUnityMetrics {
        val congestionMinutes = (m.totalCongestionTimeSec / 60L).toInt()
        val delayMinutes = m.etaDelaySec / 60
        val congestionPoints = (congestionMinutes * 2) + delayMinutes + (m.heavySegmentCount * 3)

        val incidentPoints = (m.accidentEventCount * 15) +
            (m.roadWorkOrDamageCount * 5) +
            (m.disasterOrOtherCount * 10) +
            (m.cautionPointCount * 2)

        val accidentPoints = if (m.airbagDeployed) 100 else 0

        return m.copy(
            congestionPoints = congestionPoints,
            incidentPoints = incidentPoints,
            accidentPoints = accidentPoints,
            totalUnpleasantPoints = congestionPoints + incidentPoints + accidentPoints,
        )
    }

    private fun notifyUnity(gameObject: String, methodName: String, payload: String) {
        runCatching {
            val unityPlayerClass = Class.forName("com.unity3d.player.UnityPlayer")
            val sendMethod = unityPlayerClass.getMethod(
                "UnitySendMessage",
                String::class.java,
                String::class.java,
                String::class.java
            )
            sendMethod.invoke(null, gameObject, methodName, payload)
        }.onFailure { e ->
            Log.d(TAG, "UnitySendMessage skipped: ${e.message}")
        }
    }
}
