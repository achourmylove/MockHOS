package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.RoseError
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val serviceState by ServiceEventBus.state.collectAsState()

    var currentRole by remember { mutableStateOf(AppPreferences.getRole(context)) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var sendIntervalSec by remember { mutableIntStateOf(AppPreferences.getIntervalSec(context)) }
    var includeTelemetry by remember { mutableStateOf(AppPreferences.isTelemetryEnabled(context)) }
    var autoStartBoot by remember { mutableStateOf(AppPreferences.isAutoStartEnabled(context)) }
    var batterySaverEnabled by remember { mutableStateOf(AppPreferences.isBatterySaverEnabled(context)) }
    var floatingOverlayEnabled by remember { mutableStateOf(AppPreferences.isFloatingOverlayEnabled(context)) }
    var hasOverlayPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) android.provider.Settings.canDrawOverlays(context) else true
        )
    }
    var backupStatus by remember { mutableStateOf<String?>("Backing up app to Downloads...") }

    // Permissions list
    val permissionsToRequest = remember {
        mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            }
        }.toTypedArray()
    }

    var hasRequiredPermissions by remember {
        mutableStateOf(
            permissionsToRequest.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasRequiredPermissions = results.values.all { it }
        if (hasRequiredPermissions && currentRole != AppRole.NOT_SET && !serviceState.isRunning) {
            // Auto run service once permissions are granted
            startConfiguredService(context, currentRole)
        }
    }

    // Auto backup on startup + Auto run service if role configured
    LaunchedEffect(currentRole, hasRequiredPermissions) {
        // Auto backup once
        scope.launch {
            val res = AppBackupManager.performAutoBackup(context)
            if (res.isSuccess) {
                backupStatus = "Saved to ${res.getOrNull()}"
            } else {
                backupStatus = "Backup: ${res.exceptionOrNull()?.message}"
            }
        }

        // Auto start service without waiting for user interaction if role is decided
        if (currentRole != AppRole.NOT_SET && hasRequiredPermissions && !serviceState.isRunning) {
            startConfiguredService(context, currentRole)
        }
    }

    // First time setup screen if role is NOT_SET
    if (currentRole == AppRole.NOT_SET) {
        FirstTimeSetupScreen(
            onRoleSelected = { chosenRole ->
                AppPreferences.setRole(context, chosenRole)
                currentRole = chosenRole
                if (!hasRequiredPermissions) {
                    permissionLauncher.launch(permissionsToRequest)
                } else {
                    startConfiguredService(context, chosenRole)
                }
            }
        )
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (serviceState.isRunning) EmeraldSuccess else Color.Gray)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (currentRole == AppRole.SENDER) "GPS Transmitter (Sender)" else "Head-Unit (Receiver)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (serviceState.isRunning) {
                                    if (serviceState.isBtConnected) "Connected & Exchanging" else "Service Active (Auto-Connecting)"
                                } else {
                                    "Service Idle"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                val res = AppBackupManager.performAutoBackup(context)
                                if (res.isSuccess) {
                                    val p = res.getOrNull()
                                    backupStatus = "Saved to $p"
                                    Toast.makeText(context, "App backup saved to $p", Toast.LENGTH_SHORT).show()
                                } else {
                                    val err = res.exceptionOrNull()?.message ?: "Error"
                                    backupStatus = "Backup failed: $err"
                                    Toast.makeText(context, "Backup failed: $err", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.testTag("manual_backup_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Backup app APK to Downloads"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Auto backup banner
            if (backupStatus != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Auto-Backup: $backupStatus",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Missing permissions alert
            if (!hasRequiredPermissions) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Permissions Required",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Grant location & Bluetooth to let background auto-exchange run.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Button(
                            onClick = { permissionLauncher.launch(permissionsToRequest) },
                            modifier = Modifier.testTag("grant_permissions_btn")
                        ) {
                            Text("Grant")
                        }
                    }
                }
            }

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(if (currentRole == AppRole.SENDER) "Transmitter" else "Receiver") },
                    icon = { Icon(Icons.Default.DirectionsCar, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Coordinates") },
                    icon = { Icon(Icons.Default.LocationOn, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Settings") },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                when (selectedTab) {
                    0 -> AutoDashboardTab(
                        currentRole = currentRole,
                        serviceState = serviceState,
                        onRestartService = { startConfiguredService(context, currentRole) },
                        onStopService = { stopConfiguredService(context) }
                    )
                    1 -> TelemetryTab(serviceState = serviceState)
                    2 -> SettingsTab(
                        currentRole = currentRole,
                        onRoleChange = { newRole ->
                            AppPreferences.setRole(context, newRole)
                            currentRole = newRole
                            // Restart service with new role automatically
                            stopConfiguredService(context)
                            startConfiguredService(context, newRole)
                        },
                        autoStartBoot = autoStartBoot,
                        onAutoStartBootChange = {
                            autoStartBoot = it
                            AppPreferences.setAutoStartEnabled(context, it)
                        },
                        sendIntervalSec = sendIntervalSec,
                        onSendIntervalChange = {
                            sendIntervalSec = it
                            AppPreferences.setIntervalSec(context, it)
                        },
                        includeTelemetry = includeTelemetry,
                        onIncludeTelemetryChange = {
                            includeTelemetry = it
                            AppPreferences.setTelemetryEnabled(context, it)
                        },
                        batterySaverEnabled = batterySaverEnabled,
                        onBatterySaverEnabledChange = {
                            batterySaverEnabled = it
                            AppPreferences.setBatterySaverEnabled(context, it)
                        },
                        onRequestBatteryOptimizationExemption = {
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    val intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    context.startActivity(intent)
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not open battery settings directly", Toast.LENGTH_SHORT).show()
                            }
                        },
                        floatingOverlayEnabled = floatingOverlayEnabled,
                        onFloatingOverlayEnabledChange = { enabled ->
                            floatingOverlayEnabled = enabled
                            AppPreferences.setFloatingOverlayEnabled(context, enabled)
                            if (enabled) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(context)) {
                                    Toast.makeText(context, "Please grant 'Display over other apps' permission", Toast.LENGTH_LONG).show()
                                    val intent = Intent(
                                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        android.net.Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                } else {
                                    val overlayIntent = Intent(context, FloatingOverlayService::class.java).apply {
                                        action = FloatingOverlayService.ACTION_START
                                    }
                                    context.startService(overlayIntent)
                                }
                            } else {
                                val overlayIntent = Intent(context, FloatingOverlayService::class.java).apply {
                                    action = FloatingOverlayService.ACTION_STOP
                                }
                                context.startService(overlayIntent)
                            }
                        },
                        hasOverlayPermission = hasOverlayPermission,
                        onRequestOverlayPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val intent = Intent(
                                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    android.net.Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * First-Time Setup Wizard: Sender or Receiver
 */
@Composable
fun FirstTimeSetupScreen(onRoleSelected: (AppRole) -> Unit) {
    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.DirectionsCar,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "HeadUnit GPS Setup",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Select what this device will do. Both devices connect and exchange GPS automatically without MAC or verification prompts.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onRoleSelected(AppRole.SENDER) }
                    .testTag("select_sender_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.GpsFixed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "GPS Sender (Source Phone)",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Runs on your handheld phone with GPS. Periodically acquires live coordinates and streams JSON to the car automatically.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onRoleSelected(AppRole.RECEIVER) }
                    .testTag("select_receiver_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BluetoothConnected, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "GPS Receiver (Car Head-Unit)",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Runs on the car's Android head-unit. Listens on Bluetooth SPP and accepts incoming GPS streams automatically.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "You can change this role anytime in Settings.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AutoDashboardTab(
    currentRole: AppRole,
    serviceState: ServiceEventBus.ServiceState,
    onRestartService: () -> Unit,
    onStopService: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (serviceState.isRunning) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (currentRole == AppRole.SENDER) "ROLE: SENDER (GPS SOURCE)" else "ROLE: RECEIVER (HEAD-UNIT)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (serviceState.isRunning) "Running Automatically" else "Service Stopped",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Icon(
                            imageVector = if (serviceState.isBtConnected) {
                                Icons.Default.BluetoothConnected
                            } else if (serviceState.isRunning) {
                                Icons.Default.Autorenew
                            } else {
                                Icons.Default.GpsOff
                            },
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = if (serviceState.isBtConnected) EmeraldSuccess else MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = serviceState.statusMessage,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (serviceState.isRunning) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = if (serviceState.isBatterySaverActive) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (serviceState.isBatterySaverActive) "⚡ Battery Saver Active (${serviceState.activeIntervalSec}s interval)" else "⚡ Active Interval: ${serviceState.activeIntervalSec}s",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (serviceState.isBatterySaverActive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (serviceState.isRunning) {
                            Button(
                                onClick = onStopService,
                                colors = ButtonDefaults.buttonColors(containerColor = RoseError),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("stop_service_btn")
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pause Service")
                            }
                        } else {
                            Button(
                                onClick = onRestartService,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("resume_service_btn")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Resume Service")
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldSuccess)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Zero-Verification Auto Exchange",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• No MAC addresses or prompts required.\n• Once phone and car are paired, they connect automatically.\n• The foreground service resumes immediately after phone boot / restart.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsTab(
    currentRole: AppRole,
    onRoleChange: (AppRole) -> Unit,
    autoStartBoot: Boolean,
    onAutoStartBootChange: (Boolean) -> Unit,
    sendIntervalSec: Int,
    onSendIntervalChange: (Int) -> Unit,
    includeTelemetry: Boolean,
    onIncludeTelemetryChange: (Boolean) -> Unit,
    batterySaverEnabled: Boolean,
    onBatterySaverEnabledChange: (Boolean) -> Unit,
    onRequestBatteryOptimizationExemption: () -> Unit,
    floatingOverlayEnabled: Boolean,
    onFloatingOverlayEnabledChange: (Boolean) -> Unit,
    hasOverlayPermission: Boolean,
    onRequestOverlayPermission: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "App Role (Current: ${currentRole.name})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Switch between broadcasting GPS coordinates or receiving them on the car head-unit:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onRoleChange(AppRole.SENDER) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("role_sender_btn"),
                            colors = if (currentRole == AppRole.SENDER) {
                                ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            }
                        ) {
                            Text("Sender (Phone)")
                        }

                        OutlinedButton(
                            onClick = { onRoleChange(AppRole.RECEIVER) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("role_receiver_btn"),
                            colors = if (currentRole == AppRole.RECEIVER) {
                                ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            }
                        ) {
                            Text("Receiver (Car)")
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Auto-Start & Boot Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Start on Phone Reboot", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Resumes GPS foreground streaming automatically after device restart (ACTION_BOOT_COMPLETED).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoStartBoot,
                            onCheckedChange = onAutoStartBootChange,
                            modifier = Modifier.testTag("boot_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Broadcast Interval: ${sendIntervalSec}s", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(2, 5, 10, 15, 30).forEach { sec ->
                            FilterChip(
                                selected = sendIntervalSec == sec,
                                onClick = { onSendIntervalChange(sec) },
                                label = { Text("${sec}s") },
                                modifier = Modifier.testTag("interval_${sec}s_chip")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Include Extra Telemetry", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Attach altitude, speed, and accuracy to JSON stream.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = includeTelemetry,
                            onCheckedChange = onIncludeTelemetryChange,
                            modifier = Modifier.testTag("telemetry_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Battery Saver Mode", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Automatically increases GPS polling interval (to 30s+) during low-power states (Power Save Mode or battery ≤ 20%) to prevent draining the phone.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = batterySaverEnabled,
                            onCheckedChange = onBatterySaverEnabledChange,
                            modifier = Modifier.testTag("battery_saver_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = onRequestBatteryOptimizationExemption,
                        modifier = Modifier.fillMaxWidth().testTag("battery_opt_btn")
                    ) {
                        Icon(Icons.Default.BatteryChargingFull, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Configure Battery Optimization Exemption")
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Floating Button over App Drawer", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Displays a draggable floating button on screen to quickly maximize the app at any time.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = floatingOverlayEnabled,
                            onCheckedChange = onFloatingOverlayEnabledChange,
                            modifier = Modifier.testTag("floating_overlay_switch")
                        )
                    }

                    if (!hasOverlayPermission) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onRequestOverlayPermission,
                            modifier = Modifier.fillMaxWidth().testTag("grant_overlay_btn")
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Grant 'Display over other apps' Permission")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TelemetryTab(serviceState: ServiceEventBus.ServiceState) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Real-Time GPS Telemetry",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("LATITUDE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = serviceState.lastLat?.let { String.format(Locale.US, "%.6f°", it) } ?: "--",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("LONGITUDE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = serviceState.lastLon?.let { String.format(Locale.US, "%.6f°", it) } ?: "--",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Speed: ${serviceState.lastSpeedKmh?.let { String.format(Locale.US, "%.1f km/h", it) } ?: "--"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.GpsFixed, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Accuracy: ${serviceState.lastAccuracyM?.let { String.format(Locale.US, "±%.1fm", it) } ?: "--"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    if (serviceState.lastPayload != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Last Transmitted / Received JSON:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = serviceState.lastPayload ?: "",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedButton(
                                    onClick = {
                                        serviceState.lastPayload?.let {
                                            clipboardManager.setText(AnnotatedString(it))
                                            Toast.makeText(context, "Copied JSON to clipboard", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.testTag("copy_payload_btn")
                                ) {
                                    Text("Copy")
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Exchange Log (${serviceState.logs.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (serviceState.logs.isNotEmpty()) {
                    Text(
                        text = "Clear",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { ServiceEventBus.clearLogs() }
                            .padding(4.dp)
                    )
                }
            }
        }

        if (serviceState.logs.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No transmissions logged yet",
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Coordinates exchange starts automatically once devices are in Bluetooth range.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(serviceState.logs.reversed()) { log ->
                val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (log.delivered) EmeraldSuccess else RoseError)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%.5f, %.5f", log.lat, log.lon),
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = timeFormat.format(Date(log.timestamp)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = log.jsonPayload,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (log.note != null) {
                                Text(
                                    text = log.note,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (log.delivered) EmeraldSuccess else RoseError
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun startConfiguredService(context: Context, role: AppRole) {
    val intent = Intent(context, LocationBroadcastService::class.java).apply {
        action = LocationBroadcastService.ACTION_START
        putExtra(LocationBroadcastService.EXTRA_ROLE, role.name)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

private fun stopConfiguredService(context: Context) {
    val intent = Intent(context, LocationBroadcastService::class.java).apply {
        action = LocationBroadcastService.ACTION_STOP
    }
    context.startService(intent)
}
