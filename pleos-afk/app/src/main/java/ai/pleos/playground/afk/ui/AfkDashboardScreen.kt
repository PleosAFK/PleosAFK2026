package ai.pleos.playground.afk.ui

import ai.pleos.playground.afk.model.AfkMetricsState
import ai.pleos.playground.afk.model.SdkConnectionState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AfkDashboardScreen(
    state: AfkMetricsState,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onSimulateCongestion: () -> Unit,
    onSimulateAccident: () -> Unit,
    onSimulateAirbag: () -> Unit,
    onReset: () -> Unit,
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Pleos AFK — Unpleasant Event Monitor",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            // Service Control Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (state.isServiceRunning) "Background Service: RUNNING" else "Background Service: STOPPED",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text("NaviHelper: ${state.naviState} (${state.naviMessage})")
                    Text("Vehicle SDK: ${state.vehicleState} (${state.vehicleMessage})")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onStartService, enabled = !state.isServiceRunning) {
                            Text("Start Background Monitor")
                        }
                        OutlinedButton(onClick = onStopService, enabled = state.isServiceRunning) {
                            Text("Stop")
                        }
                    }
                }
            }

            // Quantified Unpleasant Score Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Quantified Unpleasant Score: ${state.score.totalPoints} pts",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Congestion: ${state.score.congestionPoints} | " +
                            "Incidents: ${state.score.incidentPoints} | " +
                            "Airbag/Accident: ${state.score.accidentPoints}"
                    )
                }
            }

            // Raw Metrics Cards
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("1. Congestion & Time Loss (NaviHelper)", fontWeight = FontWeight.SemiBold)
                    Text("Routing active: ${state.congestion.isRouting}")
                    Text("Total congestion time: ${state.congestion.totalCongestionTimeSec} sec")
                    Text("Total congestion dist: ${state.congestion.totalCongestionDistMeters.toInt()} m")
                    Text("Avg speed in congestion: ${state.congestion.totalCongestionAvgSpeedKmh} km/h")
                    Text("Heavy / Slow segments: ${state.congestion.heavySegmentCount} / ${state.congestion.slowSegmentCount}")
                    Text("ETA delay vs start: +${state.congestion.etaDelaySec} sec")
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("2. Road Incidents & Caution Points (NaviHelper)", fontWeight = FontWeight.SemiBold)
                    Text("Active traffic events: ${state.incidents.activeTrafficEventCount}")
                    Text(
                        "Accidents: ${state.incidents.accidentEventCount} | " +
                            "Roadwork/Damage: ${state.incidents.roadWorkOrDamageCount} | " +
                            "Other: ${state.incidents.disasterOrOtherCount}"
                    )
                    Text("Caution points ahead: ${state.incidents.cautionPointCount}")
                    state.incidents.activeEventsSummary.forEach { summary ->
                        Text("• $summary", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("3. Vehicle Safety (Vehicle SDK)", fontWeight = FontWeight.SemiBold)
                    Text("Airbag deployed: ${state.safety.airbagDeployed}")
                    if (state.safety.airbagDeployedAreas.isNotEmpty()) {
                        Text("Deployed areas: ${state.safety.airbagDeployedAreas.joinToString()}")
                    }
                    Text(
                        "Supported airbag areas: " +
                            if (state.safety.supportedAirbagAreas.isEmpty()) "(none / unavailable)"
                            else state.safety.supportedAirbagAreas.joinToString()
                    )
                    Text("Active telltales: ${state.safety.activeTelltaleCount}")
                }
            }

            // Simulation Controls
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Simulation Controls (Local Verification)", fontWeight = FontWeight.SemiBold)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = onSimulateCongestion) { Text("+5m Congestion") }
                        OutlinedButton(onClick = onSimulateAccident) { Text("+1 Road Accident") }
                        OutlinedButton(onClick = onSimulateAirbag) { Text("Airbag Deployed") }
                        OutlinedButton(onClick = onReset) { Text("Reset") }
                    }
                }
            }

            // Event Log
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Recent Event Logs", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    if (state.eventLogs.isEmpty()) {
                        Text("No events yet.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        state.eventLogs.forEach { line ->
                            Text(line, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
