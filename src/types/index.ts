export type AppRole = 'NOT_SET' | 'SENDER' | 'RECEIVER';

export type ServiceHealthState =
  | 'IDLE'
  | 'HEALTHY'
  | 'DEGRADED_BT'
  | 'DEGRADED_LOCATION'
  | 'POWER_THROTTLED'
  | 'PERMISSION_REVOKED';

export interface TransmissionLog {
  id: number;
  timestamp: number;
  lat: number;
  lon: number;
  altitude?: number | null;
  speed?: number | null;
  accuracy?: number | null;
  jsonPayload: string;
  delivered: boolean;
  targetMac: string;
  note?: string | null;
}

export interface DiagnosticsLog {
  id: number;
  timestamp: number;
  category: 'SERVICE' | 'BLUETOOTH' | 'LOCATION' | 'POWER' | 'BOOT' | 'RECEIVER';
  message: string;
  isWarningOrError: boolean;
}

export interface ServiceState {
  isRunning: boolean;
  isBtConnected: boolean;
  healthState: ServiceHealthState;
  connectedDeviceName: string;
  connectedDeviceMac: string;
  lastLat: number | null;
  lastLon: number | null;
  lastAltitude: number | null;
  lastSpeedKmh: number | null;
  lastAccuracyM: number | null;
  lastHeadingDeg: number | null;
  lastPayload: string | null;
  lastTransmissionTime: number;
  totalTransmitted: number;
  statusMessage: string;
  activeIntervalSec: number;
  isBatterySaverActive: boolean;
  logs: TransmissionLog[];
  diagnosticLogs: DiagnosticsLog[];
}

export interface PowerStatus {
  isIgnoringBatteryOptimizations: boolean;
  isPowerSaveMode: boolean;
  isDeviceIdleMode: boolean;
  isBatterySaverSettingEnabled: boolean;
  effectiveIntervalSec: number;
  batteryLevelPct: number;
  isCharging: boolean;
}

export interface AppPreferencesData {
  role: AppRole;
  autoStartBoot: boolean;
  intervalSec: number;
  includeTelemetry: boolean;
  batterySaverEnabled: boolean;
  floatingOverlayEnabled: boolean;
}
