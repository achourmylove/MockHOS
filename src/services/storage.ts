import { AppPreferencesData, AppRole } from '../types';

const STORAGE_KEY_PREFIX = 'headunit_gps_';

const DEFAULT_PREFS: AppPreferencesData = {
  role: 'NOT_SET',
  autoStartBoot: true,
  intervalSec: 5,
  includeTelemetry: true,
  batterySaverEnabled: true,
  floatingOverlayEnabled: false,
};

export const AppPreferences = {
  getRole(): AppRole {
    const val = localStorage.getItem(`${STORAGE_KEY_PREFIX}role`);
    if (val === 'SENDER' || val === 'RECEIVER') return val;
    return 'NOT_SET';
  },

  setRole(role: AppRole): void {
    localStorage.setItem(`${STORAGE_KEY_PREFIX}role`, role);
  },

  isAutoStartEnabled(): boolean {
    const val = localStorage.getItem(`${STORAGE_KEY_PREFIX}auto_start`);
    return val === null ? DEFAULT_PREFS.autoStartBoot : val === 'true';
  },

  setAutoStartEnabled(enabled: boolean): void {
    localStorage.setItem(`${STORAGE_KEY_PREFIX}auto_start`, String(enabled));
  },

  getIntervalSec(): number {
    const val = localStorage.getItem(`${STORAGE_KEY_PREFIX}interval_sec`);
    const num = val ? parseInt(val, 10) : NaN;
    return isNaN(num) ? DEFAULT_PREFS.intervalSec : num;
  },

  setIntervalSec(sec: number): void {
    localStorage.setItem(`${STORAGE_KEY_PREFIX}interval_sec`, String(sec));
  },

  isTelemetryEnabled(): boolean {
    const val = localStorage.getItem(`${STORAGE_KEY_PREFIX}include_telemetry`);
    return val === null ? DEFAULT_PREFS.includeTelemetry : val === 'true';
  },

  setTelemetryEnabled(enabled: boolean): void {
    localStorage.setItem(`${STORAGE_KEY_PREFIX}include_telemetry`, String(enabled));
  },

  isBatterySaverEnabled(): boolean {
    const val = localStorage.getItem(`${STORAGE_KEY_PREFIX}battery_saver`);
    return val === null ? DEFAULT_PREFS.batterySaverEnabled : val === 'true';
  },

  setBatterySaverEnabled(enabled: boolean): void {
    localStorage.setItem(`${STORAGE_KEY_PREFIX}battery_saver`, String(enabled));
  },

  isFloatingOverlayEnabled(): boolean {
    const val = localStorage.getItem(`${STORAGE_KEY_PREFIX}floating_overlay`);
    return val === null ? DEFAULT_PREFS.floatingOverlayEnabled : val === 'true';
  },

  setFloatingOverlayEnabled(enabled: boolean): void {
    localStorage.setItem(`${STORAGE_KEY_PREFIX}floating_overlay`, String(enabled));
  },

  getAll(): AppPreferencesData {
    return {
      role: this.getRole(),
      autoStartBoot: this.isAutoStartEnabled(),
      intervalSec: this.getIntervalSec(),
      includeTelemetry: this.isTelemetryEnabled(),
      batterySaverEnabled: this.isBatterySaverEnabled(),
      floatingOverlayEnabled: this.isFloatingOverlayEnabled(),
    };
  },
};
