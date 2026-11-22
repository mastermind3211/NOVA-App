package com.nova.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nova.assistant.permissions.NovaPermissionManager
import com.nova.assistant.security.ApiKeyVault
import com.nova.assistant.service.NovaForegroundService
import com.nova.assistant.service.NovaRuntimeBus
import com.nova.assistant.ui.screens.MainDashboardScreen
import com.nova.assistant.ui.theme.NovaTheme

class MainActivity : ComponentActivity() {

    private lateinit var vault: ApiKeyVault

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vault = ApiKeyVault(applicationContext)

        setContent {
            NovaTheme {
                val lifecycleOwner = LocalLifecycleOwner.current
                val orbState by NovaRuntimeBus.orbState.collectAsState()
                val audioLevel by NovaRuntimeBus.audioLevel.collectAsState()
                val isServiceRunning by NovaRuntimeBus.isServiceRunning.collectAsState()
                val isOverlayActive by NovaRuntimeBus.isOverlayActive.collectAsState()
                val logs by NovaRuntimeBus.logs.collectAsState()

                var activeProvider by remember { mutableStateOf(vault.getPreferredProvider()) }
                var permissionCategories by remember {
                    mutableStateOf(NovaPermissionManager.evaluateAllCategories(this))
                }
                var batteryPct by remember { mutableIntStateOf(readBatteryPercent()) }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    permissionCategories = NovaPermissionManager.evaluateAllCategories(this)
                    if (hasMicPermission() && !isServiceRunning) {
                        startNovaServiceSafely()
                    }
                }

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            permissionCategories = NovaPermissionManager.evaluateAllCategories(this@MainActivity)
                            batteryPct = readBatteryPercent()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                LaunchedEffect(Unit) {
                    batteryPct = readBatteryPercent()
                    val missing = NovaPermissionManager.getMissingRuntimePermissions(this@MainActivity)
                    if (missing.isEmpty() && !isServiceRunning) {
                        startNovaServiceSafely()
                    }
                }

                MainDashboardScreen(
                    vault = vault,
                    orbState = orbState,
                    audioLevel = audioLevel,
                    isServiceRunning = isServiceRunning,
                    isOverlayActive = isOverlayActive,
                    activeProvider = activeProvider,
                    batteryPercent = batteryPct,
                    logs = logs,
                    permissionCategories = permissionCategories,
                    onToggleService = {
                        if (isServiceRunning) {
                            startService(
                                Intent(this, NovaForegroundService::class.java)
                                    .setAction(NovaForegroundService.ACTION_STOP_SERVICE)
                            )
                        } else {
                            if (!hasMicPermission()) {
                                val missing = NovaPermissionManager.getMissingRuntimePermissions(this)
                                permissionLauncher.launch(missing)
                            } else {
                                startNovaServiceSafely()
                            }
                        }
                    },
                    onToggleOverlay = {
                        if (!Settings.canDrawOverlays(this)) {
                            NovaPermissionManager.openSettingsForCategory(this, 15)
                        } else {
                            if (!isServiceRunning) {
                                if (!hasMicPermission()) {
                                    permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                                    return@MainDashboardScreen
                                }
                                startNovaServiceSafely()
                            }
                            val action = if (isOverlayActive) {
                                NovaForegroundService.ACTION_HIDE_OVERLAY
                            } else {
                                NovaForegroundService.ACTION_SHOW_OVERLAY
                            }
                            ContextCompat.startForegroundService(
                                this,
                                Intent(this, NovaForegroundService::class.java).setAction(action)
                            )
                        }
                    },
                    onOrbTap = {
                        if (!hasMicPermission()) {
                            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                        } else if (!isServiceRunning) {
                            startNovaServiceSafely()
                        } else {
                            startService(
                                Intent(this, NovaForegroundService::class.java)
                                    .setAction(NovaForegroundService.ACTION_LISTEN_NOW)
                            )
                        }
                    },
                    onSendTextCommand = { cmd ->
                        if (!isServiceRunning) {
                            if (!hasMicPermission()) {
                                permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                                return@MainDashboardScreen
                            }
                            startNovaServiceSafely()
                        }
                        ContextCompat.startForegroundService(
                            this,
                            Intent(this, NovaForegroundService::class.java).apply {
                                action = NovaForegroundService.ACTION_PROCESS_TEXT
                                putExtra(NovaForegroundService.EXTRA_TEXT_COMMAND, cmd)
                            }
                        )
                    },
                    onSettingsUpdated = { newProvider, newVoice ->
                        activeProvider = newProvider
                        NovaRuntimeBus.appendLog(false, "Settings saved: Brain=${newProvider.displayName}, Voice=${newVoice.displayName}")
                        if (isServiceRunning) {
                            startService(
                                Intent(this, NovaForegroundService::class.java)
                                    .setAction(NovaForegroundService.ACTION_TEST_VOICE)
                            )
                        }
                    },
                    onRequestAllRuntimePermissions = {
                        val missing = NovaPermissionManager.getMissingRuntimePermissions(this)
                        if (missing.isNotEmpty()) {
                            permissionLauncher.launch(missing)
                        }
                    },
                    onOpenSettingsForCategory = { categoryId ->
                        NovaPermissionManager.openSettingsForCategory(this, categoryId)
                    },
                    onRefreshPermissions = {
                        permissionCategories = NovaPermissionManager.evaluateAllCategories(this)
                        batteryPct = readBatteryPercent()
                    }
                )
            }
        }
    }

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startNovaServiceSafely() {
        if (!hasMicPermission()) return
        val intent = Intent(this, NovaForegroundService::class.java)
        runCatching {
            ContextCompat.startForegroundService(this, intent)
        }.onFailure {
            NovaRuntimeBus.appendLog(false, "Service start error: ${it.localizedMessage}")
        }
    }

    private fun readBatteryPercent(): Int {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
    }
}
