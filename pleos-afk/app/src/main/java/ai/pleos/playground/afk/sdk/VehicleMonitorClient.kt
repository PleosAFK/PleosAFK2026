package ai.pleos.playground.afk.sdk

import ai.pleos.playground.afk.data.AfkMetricsRepository
import ai.pleos.playground.afk.model.SdkConnectionState
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.Safety
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.telltale.Telltales
import ai.pleos.playground.vehicle.constant.telltale.TelltaleState
import ai.pleos.playground.vehicle.listener.SeatAirbagDeployedListener
import ai.pleos.playground.vehicle.listener.TelltaleInfoListener
import android.content.Context
import android.util.Log

/**
 * Wraps Pleos [Vehicle] SDK to monitor safety signals such as seat airbag deployment
 * and dashboard telltales.
 */
class VehicleMonitorClient(
    context: Context,
) {
    private val vehicle = Vehicle(context.applicationContext)
    private val safety: Safety = vehicle.getSafety()

    private var isInitialized = false
    private val registeredAirbagListeners = mutableMapOf<SeatArea, SeatAirbagDeployedListener>()

    private val telltaleListener = object : TelltaleInfoListener {
        override fun onTelltaleInfoUpdated(telltales: Telltales?) {
            val activeCount = telltales?.telltales?.count { it.state != TelltaleState.OFF } ?: 0
            AfkMetricsRepository.onTelltalesUpdated(activeCount)
        }

        override fun onFailed(e: Exception) {
            AfkMetricsRepository.appendLog("[Vehicle] Telltale failed: ${e.message}")
        }
    }

    fun start() {
        if (isInitialized) return
        try {
            vehicle.initialize()
            isInitialized = true
            AfkMetricsRepository.updateVehicleConnection(
                SdkConnectionState.CONNECTED,
                "Vehicle SDK initialized"
            )
            subscribeSafetySignals()
        } catch (e: Exception) {
            Log.w(TAG, "Vehicle SDK unavailable: ${e.message}", e)
            isInitialized = false
            AfkMetricsRepository.updateVehicleConnection(
                SdkConnectionState.UNAVAILABLE,
                "Unavailable: ${e.message ?: e::class.java.simpleName}"
            )
        }
    }

    private fun subscribeSafetySignals() {
        runCatching {
            safety.checkAirbagDeployedCapabilities(
                onComplete = { _, areas ->
                    AfkMetricsRepository.onSupportedAirbagAreas(areas.map { it.displayName() })
                    areas.forEach { area ->
                        val areaName = area.displayName()
                        val listener = object : SeatAirbagDeployedListener {
                            override fun onSeatAirbagDeployUpdated(deployed: Boolean?) {
                                AfkMetricsRepository.onAirbagDeployUpdated(
                                    area = areaName,
                                    deployed = deployed == true
                                )
                            }

                            override fun onFailed(e: Exception) {
                                AfkMetricsRepository.appendLog("[Vehicle] Airbag($areaName) failed: ${e.message}")
                            }
                        }
                        registeredAirbagListeners[area] = listener
                        safety.registerSeatAirbagDeployed(area, listener)
                    }
                },
                onFailed = { e ->
                    AfkMetricsRepository.appendLog("[Vehicle] Airbag capability check failed: ${e.message}")
                }
            )
        }.onFailure { e ->
            AfkMetricsRepository.appendLog("[Vehicle] Airbag registration error: ${e.message}")
        }

        runCatching {
            safety.registerTelltales(telltaleListener)
        }.onFailure { e ->
            AfkMetricsRepository.appendLog("[Vehicle] Telltale registration error: ${e.message}")
        }
    }

    fun stop() {
        if (!isInitialized) return
        runCatching {
            registeredAirbagListeners.forEach { (area, listener) ->
                safety.unregisterSeatAirbagDeployed(area, listener)
            }
            registeredAirbagListeners.clear()
            safety.unregisterTelltales(telltaleListener)
            vehicle.release()
        }.onFailure { e ->
            Log.w(TAG, "Vehicle SDK release failed: ${e.message}", e)
        }
        isInitialized = false
        AfkMetricsRepository.updateVehicleConnection(SdkConnectionState.IDLE, "Released")
    }

    private fun SeatArea.displayName(): String =
        values.joinToString("+") { it.name }.ifBlank { "UNKNOWN" }

    companion object {
        private const val TAG = "VehicleMonitorClient"
    }
}
