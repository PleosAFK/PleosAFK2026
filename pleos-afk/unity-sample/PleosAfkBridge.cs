using System;
using UnityEngine;
#if UNITY_ANDROID && !UNITY_EDITOR
using UnityEngine.Android;
#endif

namespace Pleos.Afk.Unity
{
    [Serializable]
    public class PleosAfkMetrics
    {
        public bool isServiceRunning;
        public bool naviConnected;
        public bool vehicleConnected;
        public bool isRouting;
        public double totalCongestionDistMeters;
        public long totalCongestionTimeSec;
        public double totalCongestionAvgSpeedKmh;
        public int heavySegmentCount;
        public int slowSegmentCount;
        public int etaDelaySec;
        public int activeTrafficEventCount;
        public int accidentEventCount;
        public int roadWorkOrDamageCount;
        public int disasterOrOtherCount;
        public int cautionPointCount;
        public bool airbagDeployed;
        public string[] airbagDeployedAreas;
        public int activeTelltaleCount;
        public int congestionPoints;
        public int incidentPoints;
        public int accidentPoints;
        public int totalUnpleasantPoints;
        public long lastUpdatedEpochMs;
    }

    [Serializable]
    public class PleosAfkDelta
    {
        public long congestionTimeDeltaSec;
        public int etaDelayDeltaSec;
        public int accidentEventDelta;
        public int roadWorkOrDamageDelta;
        public int cautionPointDelta;
        public bool airbagDeployedInPeriod;
        public int congestionPointsDelta;
        public int incidentPointsDelta;
        public int accidentPointsDelta;
        public int totalUnpleasantPointsDelta;
    }

    public class PleosAfkBridge : MonoBehaviour
    {
        private const string BridgeClassName = "ai.pleos.playground.afk.unity.PleosAfkUnityBridge";

        [SerializeField] private bool enableDebugLog = true;

        public event Action<PleosAfkMetrics> OnMetricsUpdated;
        public event Action<PleosAfkDelta> OnDeltaConsumed;
        public PleosAfkMetrics LatestMetrics { get; private set; } = new PleosAfkMetrics();

        private void Start()
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            if (!Permission.HasUserAuthorizedPermission("android.permission.POST_NOTIFICATIONS"))
            {
                Permission.RequestUserPermission("android.permission.POST_NOTIFICATIONS");
            }
            RegisterUnityCallback();
            StartMonitoring();
#endif
        }

        private void OnApplicationPause(bool pauseStatus)
        {
            // When returning from Navigation/background to Unity, pull the latest metrics and consume delta.
            if (!pauseStatus)
            {
                RefreshMetrics();
                ConsumePendingDelta();
            }
        }

        private void OnDestroy()
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            using (var bridge = new AndroidJavaClass(BridgeClassName))
            {
                bridge.CallStatic("unregisterUnityCallback");
            }
#endif
        }

        public void StartMonitoring()
        {
            Log("StartMonitoring called");
#if UNITY_ANDROID && !UNITY_EDITOR
            using (var bridge = new AndroidJavaClass(BridgeClassName))
            using (var activity = GetCurrentActivity())
            {
                bridge.CallStatic("startMonitoring", activity);
            }
#endif
        }

        public void StopMonitoring()
        {
            Log("StopMonitoring called");
#if UNITY_ANDROID && !UNITY_EDITOR
            using (var bridge = new AndroidJavaClass(BridgeClassName))
            using (var activity = GetCurrentActivity())
            {
                bridge.CallStatic("stopMonitoring", activity);
            }
#endif
        }

        public void RefreshMetrics()
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            using (var bridge = new AndroidJavaClass(BridgeClassName))
            using (var activity = GetCurrentActivity())
            {
                string json = bridge.CallStatic<string>("getMetricsJson", activity);
                ApplyMetricsJson(json);
            }
#endif
        }

        public PleosAfkDelta ConsumePendingDelta()
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            using (var bridge = new AndroidJavaClass(BridgeClassName))
            using (var activity = GetCurrentActivity())
            {
                string json = bridge.CallStatic<string>("consumePendingDeltaJson", activity);
                if (string.IsNullOrEmpty(json)) return new PleosAfkDelta();
                var delta = JsonUtility.FromJson<PleosAfkDelta>(json);
                Log(
                    $"Delta consumed: totalPts=+{delta.totalUnpleasantPointsDelta}, " +
                    $"congestion=+{delta.congestionTimeDeltaSec}s, etaDelay=+{delta.etaDelayDeltaSec}s, " +
                    $"accidents=+{delta.accidentEventDelta}, airbag={delta.airbagDeployedInPeriod}"
                );
                OnDeltaConsumed?.Invoke(delta);
                return delta;
            }
#else
            return new PleosAfkDelta();
#endif
        }

        public void ResetMetrics()
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            using (var bridge = new AndroidJavaClass(BridgeClassName))
            using (var activity = GetCurrentActivity())
            {
                bridge.CallStatic("resetMetrics", activity);
            }
            RefreshMetrics();
#endif
        }

        // Called automatically from Android via UnityPlayer.UnitySendMessage when foregrounded.
        public void OnNativeMetricsJson(string json)
        {
            ApplyMetricsJson(json);
        }

        private void ApplyMetricsJson(string json)
        {
            if (string.IsNullOrEmpty(json)) return;
            LatestMetrics = JsonUtility.FromJson<PleosAfkMetrics>(json);
            Log(
                $"Metrics updated: service={LatestMetrics.isServiceRunning}, " +
                $"navi={LatestMetrics.naviConnected}, vehicle={LatestMetrics.vehicleConnected}, " +
                $"congestion={LatestMetrics.totalCongestionTimeSec}s, events={LatestMetrics.activeTrafficEventCount}, " +
                $"airbag={LatestMetrics.airbagDeployed}, score={LatestMetrics.totalUnpleasantPoints}"
            );
            OnMetricsUpdated?.Invoke(LatestMetrics);
        }

        private void Log(string message)
        {
            if (enableDebugLog)
            {
                Debug.Log($"[PleosAFK] {message}");
            }
        }

        private void RegisterUnityCallback()
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            using (var bridge = new AndroidJavaClass(BridgeClassName))
            {
                bridge.CallStatic("registerUnityCallback", gameObject.name, nameof(OnNativeMetricsJson));
            }
#endif
        }

#if UNITY_ANDROID && !UNITY_EDITOR
        private static AndroidJavaObject GetCurrentActivity()
        {
            using (var unityPlayer = new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
            {
                return unityPlayer.GetStatic<AndroidJavaObject>("currentActivity");
            }
        }
#endif
    }
}
