package ai.pleos.playground.afk.unity.model

import org.json.JSONArray
import org.json.JSONObject

data class AfkUnityMetrics(
    val isServiceRunning: Boolean = false,
    val naviConnected: Boolean = false,
    val vehicleConnected: Boolean = false,
    val isRouting: Boolean = false,
    val totalCongestionDistMeters: Double = 0.0,
    val totalCongestionTimeSec: Long = 0L,
    val totalCongestionAvgSpeedKmh: Double = 0.0,
    val heavySegmentCount: Int = 0,
    val slowSegmentCount: Int = 0,
    val etaDelaySec: Int = 0,
    val activeTrafficEventCount: Int = 0,
    val accidentEventCount: Int = 0,
    val roadWorkOrDamageCount: Int = 0,
    val disasterOrOtherCount: Int = 0,
    val cautionPointCount: Int = 0,
    val airbagDeployed: Boolean = false,
    val airbagDeployedAreas: Set<String> = emptySet(),
    val activeTelltaleCount: Int = 0,
    val congestionPoints: Int = 0,
    val incidentPoints: Int = 0,
    val accidentPoints: Int = 0,
    val totalUnpleasantPoints: Int = 0,
    val lastUpdatedEpochMs: Long = 0L,
) {
    fun toJsonString(): String {
        val json = JSONObject()
        json.put("isServiceRunning", isServiceRunning)
        json.put("naviConnected", naviConnected)
        json.put("vehicleConnected", vehicleConnected)
        json.put("isRouting", isRouting)
        json.put("totalCongestionDistMeters", totalCongestionDistMeters)
        json.put("totalCongestionTimeSec", totalCongestionTimeSec)
        json.put("totalCongestionAvgSpeedKmh", totalCongestionAvgSpeedKmh)
        json.put("heavySegmentCount", heavySegmentCount)
        json.put("slowSegmentCount", slowSegmentCount)
        json.put("etaDelaySec", etaDelaySec)
        json.put("activeTrafficEventCount", activeTrafficEventCount)
        json.put("accidentEventCount", accidentEventCount)
        json.put("roadWorkOrDamageCount", roadWorkOrDamageCount)
        json.put("disasterOrOtherCount", disasterOrOtherCount)
        json.put("cautionPointCount", cautionPointCount)
        json.put("airbagDeployed", airbagDeployed)
        json.put("airbagDeployedAreas", JSONArray(airbagDeployedAreas.toList()))
        json.put("activeTelltaleCount", activeTelltaleCount)
        json.put("congestionPoints", congestionPoints)
        json.put("incidentPoints", incidentPoints)
        json.put("accidentPoints", accidentPoints)
        json.put("totalUnpleasantPoints", totalUnpleasantPoints)
        json.put("lastUpdatedEpochMs", lastUpdatedEpochMs)
        return json.toString()
    }
}

data class AfkPendingDelta(
    val congestionTimeDeltaSec: Long = 0L,
    val etaDelayDeltaSec: Int = 0,
    val accidentEventDelta: Int = 0,
    val roadWorkOrDamageDelta: Int = 0,
    val cautionPointDelta: Int = 0,
    val airbagDeployedInPeriod: Boolean = false,
    val congestionPointsDelta: Int = 0,
    val incidentPointsDelta: Int = 0,
    val accidentPointsDelta: Int = 0,
    val totalUnpleasantPointsDelta: Int = 0,
) {
    fun toJsonString(): String {
        val json = JSONObject()
        json.put("congestionTimeDeltaSec", congestionTimeDeltaSec)
        json.put("etaDelayDeltaSec", etaDelayDeltaSec)
        json.put("accidentEventDelta", accidentEventDelta)
        json.put("roadWorkOrDamageDelta", roadWorkOrDamageDelta)
        json.put("cautionPointDelta", cautionPointDelta)
        json.put("airbagDeployedInPeriod", airbagDeployedInPeriod)
        json.put("congestionPointsDelta", congestionPointsDelta)
        json.put("incidentPointsDelta", incidentPointsDelta)
        json.put("accidentPointsDelta", accidentPointsDelta)
        json.put("totalUnpleasantPointsDelta", totalUnpleasantPointsDelta)
        return json.toString()
    }
}
