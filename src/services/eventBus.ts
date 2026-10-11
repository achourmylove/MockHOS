import { DiagnosticsLog, ServiceHealthState, ServiceState, TransmissionLog } from '../types';

type Listener = (state: ServiceState) => void;

class ServiceEventBusManager {
  private state: ServiceState = {
    isRunning: false,
    isBtConnected: false,
    healthState: 'IDLE',
    connectedDeviceName: '',
    connectedDeviceMac: '',
    lastLat: null,
    lastLon: null,
    lastAltitude: null,
    lastSpeedKmh: null,
    lastAccuracyM: null,
    lastHeadingDeg: null,
    lastPayload: null,
    lastTransmissionTime: 0,
    totalTransmitted: 0,
    statusMessage: 'Service Idle',
    activeIntervalSec: 5,
    isBatterySaverActive: false,
    logs: [],
    diagnosticLogs: [],
  };

  private listeners = new Set<Listener>();

  getState(): ServiceState {
    return this.state;
  }

  subscribe(listener: Listener): () => void {
    this.listeners.add(listener);
    listener(this.state);
    return () => {
      this.listeners.delete(listener);
    };
  }

  private notify() {
    for (const listener of this.listeners) {
      listener(this.state);
    }
  }

  updateRunning(isRunning: boolean) {
    let health: ServiceHealthState = 'IDLE';
    if (isRunning) {
      health = this.state.isBtConnected ? 'HEALTHY' : 'DEGRADED_BT';
    }

    this.state = {
      ...this.state,
      isRunning,
      healthState: health,
      statusMessage: isRunning ? 'Service running' : 'Service stopped',
    };
    this.addDiagnostic('SERVICE', isRunning ? 'Service promoted to foreground worker' : 'Service stopped');
    this.notify();
  }

  updateBluetooth(isConnected: boolean, deviceName = '', deviceMac = '') {
    let health: ServiceHealthState = 'IDLE';
    if (this.state.isRunning) {
      health = isConnected ? 'HEALTHY' : 'DEGRADED_BT';
    }

    this.state = {
      ...this.state,
      isBtConnected: isConnected,
      connectedDeviceName: deviceName,
      connectedDeviceMac: deviceMac,
      healthState: health,
      statusMessage: isConnected ? `Connected to ${deviceName}` : 'Bluetooth waiting for peer',
    };
    this.addDiagnostic(
      'BLUETOOTH',
      isConnected ? `Connected to ${deviceName}` : 'Bluetooth peer disconnected/waiting',
      !isConnected
    );
    this.notify();
  }

  updateHealth(health: ServiceHealthState, statusMsg?: string) {
    this.state = {
      ...this.state,
      healthState: health,
      statusMessage: statusMsg ?? this.state.statusMessage,
    };
    if (statusMsg) {
      this.addDiagnostic('HEALTH' as any, statusMsg, health !== 'HEALTHY');
    }
    this.notify();
  }

  updatePowerState(activeIntervalSec: number, isBatterySaverActive: boolean) {
    this.state = {
      ...this.state,
      activeIntervalSec,
      isBatterySaverActive,
    };
    this.notify();
  }

  setStatusMessage(msg: string) {
    this.state = {
      ...this.state,
      statusMessage: msg,
    };
    this.notify();
  }

  recordTransmission(params: {
    lat: number;
    lon: number;
    altitude?: number | null;
    speedKmh?: number | null;
    accuracyM?: number | null;
    headingDeg?: number | null;
    payload: string;
    delivered: boolean;
    targetMac: string;
    note?: string | null;
  }) {
    const newLog: TransmissionLog = {
      id: Date.now() + Math.random(),
      timestamp: Date.now(),
      lat: params.lat,
      lon: params.lon,
      altitude: params.altitude,
      speed: params.speedKmh,
      accuracy: params.accuracyM,
      jsonPayload: params.payload,
      delivered: params.delivered,
      targetMac: params.targetMac,
      note: params.note,
    };

    const updatedLogs = [...this.state.logs, newLog].slice(-60);

    this.state = {
      ...this.state,
      lastLat: params.lat,
      lastLon: params.lon,
      lastAltitude: params.altitude ?? this.state.lastAltitude,
      lastSpeedKmh: params.speedKmh ?? this.state.lastSpeedKmh,
      lastAccuracyM: params.accuracyM ?? this.state.lastAccuracyM,
      lastHeadingDeg: params.headingDeg ?? this.state.lastHeadingDeg,
      lastPayload: params.payload,
      lastTransmissionTime: Date.now(),
      totalTransmitted: this.state.totalTransmitted + (params.delivered ? 1 : 0),
      logs: updatedLogs,
      statusMessage: params.delivered
        ? `Location sent (${params.lat.toFixed(5)}, ${params.lon.toFixed(5)})`
        : 'Acquired GPS, peer disconnected',
    };
    this.notify();
  }

  addDiagnostic(
    category: 'SERVICE' | 'BLUETOOTH' | 'LOCATION' | 'POWER' | 'BOOT' | 'RECEIVER',
    message: string,
    isWarning = false
  ) {
    const entry: DiagnosticsLog = {
      id: Date.now() + Math.random(),
      timestamp: Date.now(),
      category,
      message,
      isWarningOrError: isWarning,
    };
    const updated = [...this.state.diagnosticLogs, entry].slice(-100);
    this.state = {
      ...this.state,
      diagnosticLogs: updated,
    };
    this.notify();
  }

  clearLogs() {
    this.state = {
      ...this.state,
      logs: [],
      totalTransmitted: 0,
    };
    this.notify();
  }

  clearDiagnostics() {
    this.state = {
      ...this.state,
      diagnosticLogs: [],
    };
    this.notify();
  }
}

export const ServiceEventBus = new ServiceEventBusManager();
