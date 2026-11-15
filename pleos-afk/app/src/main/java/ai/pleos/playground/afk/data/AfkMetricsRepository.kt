package ai.pleos.playground.afk.data

import ai.pleos.playground.afk.model.AfkMetricsState
import ai.pleos.playground.afk.model.CongestionMetrics
import ai.pleos.playground.afk.model.IncidentMetrics
import ai.pleos.playground.afk.model.SdkConnectionState
import ai.pleos.playground.afk.model.UnpleasantScore
import ai.pleos.playground.afk.model.VehicleSafetyMetrics
import ai.pleos.playground.navi.constants.TrafficCongestionType
import ai.pleos.playground.navi.constants.TrafficEventType
import ai.pleos.playground.navi.data.CautionPoint
import ai.pleos.playground.navi.data.TrafficStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Central state holder that collects raw Navi/Vehicle signals and quantifies
 * unpleasant driving events (congestion, road incidents, airbag deployment)
 * for downstream game/leveling systems.
 */
object AfkMetricsRepository {

    private const val MAX_LOG_ITEMS = 40

    private val _state = MutableStateFlow(AfkMetricsState())
    val state: StateFlow<AfkMetricsState> = _state.asStateFlow()

    private var baselineDurationSec: Int? = null

    fun setServiceRunning(running: Boolean) {
        _state.update { current ->
            current.copy(
                isServiceRunning = running,
                eventLogs = prependLog(
                    current.eventLogs,
                    if (running) "Monitoring service started" else "Monitoring service stopped"
                )
            )
        }
    }

    fun updateNaviConnection(state: SdkConnectionState, message: String) {
        _state.update { current ->
            current.copy(
                naviState = state,
                naviMessage = message,
                eventLogs = prependLog(current.eventLogs, "[Navi] $message")
            )
        }
    }

    fun updateVehicleConnection(state: SdkConnectionState, message: String) {
        _state.update { current ->
            current.copy(
                vehicleState = state,
                vehicleMessage = message,
                eventLogs = prependLog(current.eventLogs, "[Vehicle] $message")
            )
        }
    }

    fun onRouteStarted() {
        baselineDurationSec = null
        _state.update { current ->
            current.copy(
                congestion = current.congestion.copy(isRouting = true, etaDelaySec = 0),
                eventLogs = prependLog(current.eventLogs, "[Navi] Route started (ETA baseline reset)")
            )
        }
    }

    fun onRouteStopped(reason: String) {
        baselineDurationSec = null
        _state.update { current ->
            current.copy(
                congestion = current.congestion.copy(isRouting = false),
                eventLogs = prependLog(current.eventLogs, "[Navi] $reason")
            )
        }
    }

    fun onDurationUpdated(remainingDurationSec: Int) {
        val baseline = baselineDurationSec
        if (baseline == null) {
            baselineDurationSec = remainingDurationSec
            return
        }
        val delaySec = (remainingDurationSec - baseline).coerceAtLeast(0)
        _state.update { current ->
            val updatedCongestion = current.congestion.copy(etaDelaySec = delaySec)
            current.copy(
                congestion = updatedCongestion,
                score = recalculateScore(updatedCongestion, current.incidents, current.safety)
            )
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

        val eventSummaries = events.take(5).map { event ->
            "${event.eventType.name}: ${event.description.ifBlank { "No description" }}"
        }

        _state.update { current ->
            val updatedCongestion = current.congestion.copy(
                isRouting = isRouting,
                totalCongestionDistMeters = summary?.totalCongestionDist ?: 0.0,
                totalCongestionTimeSec = summary?.totalCongestionTime ?: 0L,
                totalCongestionAvgSpeedKmh = summary?.totalCongestionAvrSpeed ?: 0.0,
                heavySegmentCount = heavyCount,
                slowSegmentCount = slowCount,
            )
            val updatedIncidents = current.incidents.copy(
                activeTrafficEventCount = events.size,
                accidentEventCount = accidentCount,
                roadWorkOrDamageCount = roadWorkOrDamageCount,
                disasterOrOtherCount = disasterOrOtherCount,
                activeEventsSummary = eventSummaries,
            )
            val logLine = "[Navi] Traffic: ${updatedCongestion.totalCongestionTimeSec}s congested, " +
                "${events.size} events (accidents=$accidentCount)"
            current.copy(
                congestion = updatedCongestion,
                incidents = updatedIncidents,
                score = recalculateScore(updatedCongestion, updatedIncidents, current.safety),
                eventLogs = prependLog(current.eventLogs, logLine)
            )
        }
    }

    fun onCautionPointsUpdated(cautionPoints: List<CautionPoint>) {
        _state.update { current ->
            val updatedIncidents = current.incidents.copy(cautionPointCount = cautionPoints.size)
            val logLine = if (cautionPoints.isEmpty()) {
                "[Navi] Caution points cleared"
            } else {
                "[Navi] Caution points: ${cautionPoints.joinToString { "${it.type.name}(${it.remainDistance}m)" }}"
            }
            current.copy(
                incidents = updatedIncidents,
                score = recalculateScore(current.congestion, updatedIncidents, current.safety),
                eventLogs = prependLog(current.eventLogs, logLine)
            )
        }
    }

    fun onSupportedAirbagAreas(areas: List<String>) {
        _state.update { current ->
            current.copy(
                safety = current.safety.copy(supportedAirbagAreas = areas),
                eventLogs = prependLog(current.eventLogs, "[Vehicle] Airbag areas: ${areas.joinToString()}")
            )
        }
    }

    fun onAirbagDeployUpdated(area: String, deployed: Boolean) {
        _state.update { current ->
            val updatedAreas = if (deployed) {
                current.safety.airbagDeployedAreas + area
            } else {
                current.safety.airbagDeployedAreas - area
            }
            val updatedSafety = current.safety.copy(
                airbagDeployed = updatedAreas.isNotEmpty(),
                airbagDeployedAreas = updatedAreas,
            )
            val logLine = "[Vehicle] Airbag ($area) deployed=$deployed"
            current.copy(
                safety = updatedSafety,
                score = recalculateScore(current.congestion, current.incidents, updatedSafety),
                eventLogs = prependLog(current.eventLogs, logLine)
            )
        }
    }

    fun onTelltalesUpdated(activeCount: Int) {
        _state.update { current ->
            current.copy(
                safety = current.safety.copy(activeTelltaleCount = activeCount),
                eventLogs = prependLog(current.eventLogs, "[Vehicle] Active telltales: $activeCount")
            )
        }
    }

    fun appendLog(message: String) {
        _state.update { current ->
            current.copy(eventLogs = prependLog(current.eventLogs, message))
        }
    }

    // --- Simulation helpers for testing without live Pleos services ---

    fun simulateCongestion(extraSeconds: Long = 300L) {
        _state.update { current ->
            val updatedCongestion = current.congestion.copy(
                isRouting = true,
                totalCongestionTimeSec = current.congestion.totalCongestionTimeSec + extraSeconds,
                totalCongestionDistMeters = current.congestion.totalCongestionDistMeters + 1200.0,
                totalCongestionAvgSpeedKmh = 12.0,
                heavySegmentCount = current.congestion.heavySegmentCount + 1,
                etaDelaySec = current.congestion.etaDelaySec + extraSeconds.toInt(),
            )
            current.copy(
                congestion = updatedCongestion,
                score = recalculateScore(updatedCongestion, current.incidents, current.safety),
                eventLogs = prependLog(current.eventLogs, "[SIM] Added +${extraSeconds}s heavy congestion")
            )
        }
    }

    fun simulateTrafficAccident() {
        _state.update { current ->
            val updatedIncidents = current.incidents.copy(
                activeTrafficEventCount = current.incidents.activeTrafficEventCount + 1,
                accidentEventCount = current.incidents.accidentEventCount + 1,
                activeEventsSummary = (listOf("ACCIDENT: Simulated multi-car incident ahead") +
                    current.incidents.activeEventsSummary).take(5),
            )
            current.copy(
                incidents = updatedIncidents,
                score = recalculateScore(current.congestion, updatedIncidents, current.safety),
                eventLogs = prependLog(current.eventLogs, "[SIM] Simulated forward traffic ACCIDENT")
            )
        }
    }

    fun simulateAirbagDeployed() {
        onAirbagDeployUpdated(area = "SIM_ROW_1_LEFT", deployed = true)
    }

    fun resetMetrics() {
        baselineDurationSec = null
        _state.update { current ->
            current.copy(
                congestion = CongestionMetrics(),
                incidents = IncidentMetrics(),
                safety = VehicleSafetyMetrics(supportedAirbagAreas = current.safety.supportedAirbagAreas),
                score = UnpleasantScore(),
                eventLogs = prependLog(current.eventLogs, "Metrics reset")
            )
        }
    }

    private fun recalculateScore(
        congestion: CongestionMetrics,
        incidents: IncidentMetrics,
        safety: VehicleSafetyMetrics,
    ): UnpleasantScore {
        val congestionMinutes = (congestion.totalCongestionTimeSec / 60L).toInt()
        val delayMinutes = congestion.etaDelaySec / 60
        val congestionPoints = (congestionMinutes * 2) + delayMinutes + (congestion.heavySegmentCount * 3)

        val incidentPoints = (incidents.accidentEventCount * 15) +
            (incidents.roadWorkOrDamageCount * 5) +
            (incidents.disasterOrOtherCount * 10) +
            (incidents.cautionPointCount * 2)

        val accidentPoints = if (safety.airbagDeployed) 100 else 0

        return UnpleasantScore(
            congestionPoints = congestionPoints,
            incidentPoints = incidentPoints,
            accidentPoints = accidentPoints,
        )
    }

    private fun prependLog(existing: List<String>, message: String): List<String> {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        return (listOf("$timestamp $message") + existing).take(MAX_LOG_ITEMS)
    }
}
