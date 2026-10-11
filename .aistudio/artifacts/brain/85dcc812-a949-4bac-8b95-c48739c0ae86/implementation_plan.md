# Bluetooth Adapter Change Receiver & Floating Overlay Bubble

## User Review & Critical Decisions

> [!IMPORTANT]
> The following decisions were confirmed by the user:
> - **Floating Button Presentation**: Both a **Floating Window overlay over other apps / app drawer (with draggable touch support)** using `SYSTEM_ALERT_WINDOW` AND a **Persistent notification action button** to maximize the app anytime.
> - **Bluetooth State Change Behavior**: Immediately and automatically trigger reconnection logic in `LocationBroadcastService` whenever `BluetoothAdapter.ACTION_STATE_CHANGED` (turning ON) or `BluetoothDevice.ACTION_ACL_CONNECTED` occurs.

---

### 1. Overview & Core Concept

This feature enhances automotive continuity and accessibility by:
1. **Bluetooth State & Device Connectivity BroadcastReceiver (`BluetoothStateReceiver`)**:
   - Listens to system broadcasts for `BluetoothAdapter.ACTION_STATE_CHANGED` (state transitions from OFF -> TURNING_ON -> ON).
   - Listens to `BluetoothDevice.ACTION_ACL_CONNECTED` and `ACTION_ACL_DISCONNECTED`.
   - When the user toggles Bluetooth ON in Android Quick Settings or when entering their vehicle, the receiver immediately signals `LocationBroadcastService` to execute instant auto-reconnect without waiting for polling timeouts.
2. **Floating Overlay Bubble (`FloatingOverlayService`)**:
   - Uses Android WindowManager `TYPE_APPLICATION_OVERLAY` (`SYSTEM_ALERT_WINDOW`) to display a sleek, draggable floating circular automotive icon over the home screen, app drawer, and other navigation apps (like Google Maps or Waze).
   - Tapping the floating overlay immediately brings `MainActivity` to the foreground (maximizing it).
   - Provides an in-app toggle in Settings to grant the "Display over other apps" permission and toggle the floating bubble on/off.
   - Also includes a dedicated "Maximize App" quick action in the persistent notification.

---

### 2. User Experience & Visual Design

- **Floating Bubble Widget**:
  - Compact 56dp circular badge styled with automotive telemetry accents (slate dark background, cyan glowing GPS icon, green status dot when connected).
  - Smooth touch drag gesture to position the bubble anywhere on screen edges.
  - Tap expands / launches `MainActivity` with `FLAG_ACTIVITY_NEW_TASK` and `FLAG_ACTIVITY_REORDER_TO_FRONT`.
- **System Notification Actions**:
  - Adds a dedicated "Maximize" / "Open Dashboard" button directly on the ongoing foreground service notification.
- **Settings Screen Integration**:
  - Adds a "Floating Overlay Button" switch with guidance to grant the Android overlay permission (`Settings.ACTION_MANAGE_OVERLAY_PERMISSION`).

---

### 3. Key Product Decisions & Trade-Offs

- **Overlay Permission Handling**:
  - Standard Android security requires user authorization for `SYSTEM_ALERT_WINDOW`. If permission is not yet granted, the app provides a smooth one-tap intent launcher directly into Android's "Display over other apps" settings.
- **Broadcast Receiver Registration**:
  - Registered both in `AndroidManifest.xml` (for static system wakeups) and dynamically inside `LocationBroadcastService` so that Bluetooth adapter toggles take effect instantly with zero latency.

---

### 4. Technical Architecture & Component Flow

```
┌────────────────────────────────────────────────────────┐
│               Android System Events                    │
│   • BluetoothAdapter.ACTION_STATE_CHANGED (STATE_ON)   │
│   • BluetoothDevice.ACTION_ACL_CONNECTED               │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│          BluetoothStateReceiver.kt                     │
│   • Receives adapter ON / ACL_CONNECTED                │
│   • Signals LocationBroadcastService via Intent        │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│          LocationBroadcastService.kt                   │
│   • Resets backoff & triggers autoConnectAnyPaired()   │
│   • Immediately resumes location stream                │
└───────────────────────────┘

┌────────────────────────────────────────────────────────┐
│           FloatingOverlayService.kt                    │
│   • WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY│
│   • Draggable TouchListener                            │
│   • OnClick -> Intent(MainActivity) [Maximize]         │
└────────────────────────────────────────────────────────┘
```
