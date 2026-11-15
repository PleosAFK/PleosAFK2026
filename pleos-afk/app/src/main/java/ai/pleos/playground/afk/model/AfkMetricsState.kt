package ai.pleos.playground.afk.model

enum class SdkConnectionState {
    IDLE,
    CONNECTED,
    UNAVAILABLE,
}

data class CongestionMetrics(
    val totalCongestionDistMeters: Double = 0.0,
    val totalCongestionTimeSec: Long = 0L,
    val totalCongestionAvgSpeedKmh: Double = 0.0,
    val heavySegmentCount: Int = 0,
    val slowSegmentCount: Int = 0,
    val etaDelaySec: Int = 0,
    val isRouting: Boolean = false,
)

data class IncidentMetrics(
    val activeTrafficEventCount: Int = 0,
    val accidentEventCount: Int = 0,
    val roadWorkOrDamageCount: Int = 0,
    val disasterOrOtherCount: Int = 0,
    val cautionPointCount: Int = 0,
    val activeEventsSummary: List<String> = emptyList(),
)

data class VehicleSafetyMetrics(
    val airbagDeployed: Boolean = false,
    val airbagDeployedAreas: Set<String> = emptySet(),
    val supportedAirbagAreas: List<String> = emptyList(),
    val activeTelltaleCount: Int = 0,
)

data class UnpleasantScore(
    val congestionPoints: Int = 0,
    val incidentPoints: Int = 0,
    val accidentPoints: Int = 0,
) {
    val totalPoints: Int
        get() = congestionPoints + incidentPoints + accidentPoints
}

data class AfkMetricsState(
    val isServiceRunning: Boolean = false,
    val naviState: SdkConnectionState = SdkConnectionState.IDLE,
    val naviMessage: String = "Not started",
    val vehicleState: SdkConnectionState = SdkConnectionState.IDLE,
    val vehicleMessage: String = "Not started",
    val congestion: CongestionMetrics = CongestionMetrics(),
    val incidents: IncidentMetrics = IncidentMetrics(),
    val safety: VehicleSafetyMetrics = VehicleSafetyMetrics(),
    val score: UnpleasantScore = UnpleasantScore(),
    val eventLogs: List<String> = emptyList(),
)
