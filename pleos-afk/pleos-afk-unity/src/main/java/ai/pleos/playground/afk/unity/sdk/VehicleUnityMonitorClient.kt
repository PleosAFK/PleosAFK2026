package ai.pleos.playground.afk.unity.sdk

import ai.pleos.playground.afk.unity.data.AfkUnityRepository
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.Safety
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.telltale.Telltales
import ai.pleos.playground.vehicle.constant.telltale.TelltaleState
import ai.pleos.playground.vehicle.listener.SeatAirbagDeployedListener
import ai.pleos.playground.vehicle.listener.TelltaleInfoListener
import android.content.Context
import android.util.Log

class VehicleUnityMonitorClient(
    context: Context,
) {
    private val vehicle = Vehicle(context.applicationContext)
    private val safety: Safety = vehicle.getSafety()

    private var isInitialized = false
    private val registeredAirbagListeners = mutableMapOf<SeatArea, SeatAirbagDeployedListener>()

    private val telltaleListener = object : TelltaleInfoListener {
        override fun onTelltaleInfoUpdated(telltales: Telltales?) {
            val activeCount = telltales?.telltales?.count { it.state != TelltaleState.OFF } ?: 0
            AfkUnityRepository.onTelltalesUpdated(activeCount)
        }

        override fun onFailed(e: Exception) {
            Log.w(TAG, "Telltale failed: ${e.message}")
        }
    }

    fun start() {
        if (isInitialized) return
        try {
            vehicle.initialize()
            isInitialized = true
            AfkUnityRepository.setVehicleConnected(true)
            subscribeSafetySignals()
        } catch (e: Exception) {
            Log.w(TAG, "Vehicle SDK unavailable: ${e.message}", e)
            isInitialized = false
            AfkUnityRepository.setVehicleConnected(false)
        }
    }

    private fun subscribeSafetySignals() {
        runCatching {
            safety.checkAirbagDeployedCapabilities(
                onComplete = { _, areas ->
                    areas.forEach { area ->
                        val areaName = area.values.joinToString("+") { it.name }.ifBlank { "UNKNOWN" }
                        val listener = object : SeatAirbagDeployedListener {
                            override fun onSeatAirbagDeployUpdated(deployed: Boolean?) {
                                AfkUnityRepository.onAirbagDeployUpdated(areaName, deployed == true)
                            }

                            override fun onFailed(e: Exception) {
                                Log.w(TAG, "Airbag($areaName) failed: ${e.message}")
                            }
                        }
                        registeredAirbagListeners[area] = listener
                        safety.registerSeatAirbagDeployed(area, listener)
                    }
                },
                onFailed = { e ->
                    Log.w(TAG, "Airbag capability check failed: ${e.message}")
                }
            )
        }.onFailure { e ->
            Log.w(TAG, "Airbag registration error: ${e.message}")
        }

        runCatching {
            safety.registerTelltales(telltaleListener)
        }.onFailure { e ->
            Log.w(TAG, "Telltale registration error: ${e.message}")
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
            Log.w(TAG, "Vehicle release failed: ${e.message}")
        }
        isInitialized = false
        AfkUnityRepository.setVehicleConnected(false)
    }

    companion object {
        private const val TAG = "VehicleUnityMonitor"
    }
}
