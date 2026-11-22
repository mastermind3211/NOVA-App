package com.nova.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.assistant.model.AiProvider
import com.nova.assistant.model.CommandLogEntry
import com.nova.assistant.model.NovaOrbState
import com.nova.assistant.model.VoiceEngineType
import com.nova.assistant.permissions.PermissionCategoryItem
import com.nova.assistant.security.ApiKeyVault
import com.nova.assistant.ui.components.NovaHolographicOrb
import com.nova.assistant.ui.components.PermissionMatrixSheet
import com.nova.assistant.ui.components.UnifiedSettingsDialog
import com.nova.assistant.ui.theme.NovaColors

@Composable
fun MainDashboardScreen(
    vault: ApiKeyVault,
    orbState: NovaOrbState,
    audioLevel: Float,
    isServiceRunning: Boolean,
    isOverlayActive: Boolean,
    activeProvider: AiProvider,
    batteryPercent: Int,
    logs: List<CommandLogEntry>,
    permissionCategories: List<PermissionCategoryItem>,
    onToggleService: () -> Unit,
    onToggleOverlay: () -> Unit,
    onOrbTap: () -> Unit,
    onSendTextCommand: (String) -> Unit,
    onSettingsUpdated: (AiProvider, VoiceEngineType) -> Unit,
    onRequestAllRuntimePermissions: () -> Unit,
    onOpenSettingsForCategory: (Int) -> Unit,
    onRefreshPermissions: () -> Unit
) {
    var inputCommand by remember { mutableStateOf("") }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showPermissionSheet by remember { mutableStateOf(false) }

    val grantedCount = permissionCategories.count { it.isGranted }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NovaColors.ObsidianBg,
                        Color(0xFF0A1124),
                        NovaColors.ObsidianBg
                    )
                )
            )
            .padding(top = 36.dp, bottom = 16.dp, start = 16.dp, end = 16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NovaColors.GlassCard, RoundedCornerShape(14.dp))
                    .border(1.dp, NovaColors.BorderGlow, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "N.O.V.A. // CORE v4.0",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = NovaColors.NeonCyan
                    )
                    Text(
                        text = "${activeProvider.displayName} • BAT $batteryPercent% • PERMS $grantedCount/${permissionCategories.size}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = NovaColors.TextMuted
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "All APIs & Settings", tint = NovaColors.NeonCyan)
                    }
                    IconButton(onClick = { showPermissionSheet = true }) {
                        Icon(Icons.Default.Security, contentDescription = "Permissions", tint = NovaColors.CyberPurple)
                    }
                    IconButton(onClick = onToggleOverlay) {
                        Icon(
                            Icons.Default.Layers,
                            contentDescription = "Floating HUD",
                            tint = if (isOverlayActive) NovaColors.EmeraldActive else NovaColors.TextMuted
                        )
                    }
                    IconButton(onClick = onToggleService) {
                        Icon(
                            Icons.Default.PowerSettingsNew,
                            contentDescription = "Service Power",
                            tint = if (isServiceRunning) NovaColors.EmeraldActive else NovaColors.CrimsonError
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                NovaHolographicOrb(
                    state = orbState,
                    audioLevel = audioLevel,
                    size = 215.dp,
                    onClick = onOrbTap
                )
            }

            Spacer(Modifier.height(8.dp))

            val quickCommands = listOf(
                "Flashlight on karo, brightness 90% karo aur YouTube par song play karo",
                "WhatsApp kholo aur recent chats dekho",
                "Battery check karo aur volume 80% karo",
                "Aur batao aaj kya naya karein?",
                "Subah 7 baje ka alarm laga do"
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(quickCommands) { cmd ->
                    AssistChip(
                        onClick = { onSendTextCommand(cmd) },
                        label = { Text(cmd, fontSize = 11.sp, color = NovaColors.TextPrimary) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = NovaColors.GlassCard
                        )
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = "NEURAL TELEMETRY LOG",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = NovaColors.NeonCyan,
                letterSpacing = 1.5.sp
            )
            Spacer(Modifier.height(6.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(NovaColors.DeepSpacePanel, RoundedCornerShape(14.dp))
                    .border(1.dp, NovaColors.BorderGlow, RoundedCornerShape(14.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                reverseLayout = true
            ) {
                items(logs.reversed(), key = { it.id }) { entry ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (entry.isUser) NovaColors.ElectricBlue.copy(alpha = 0.16f)
                                else NovaColors.CyberPurple.copy(alpha = 0.14f),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "${if (entry.isUser) "USER" else "NOVA"} • ${entry.timestamp}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = if (entry.isUser) NovaColors.NeonCyan else NovaColors.CyberPurple
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = entry.text,
                            fontSize = 13.sp,
                            color = NovaColors.TextPrimary
                        )
                        if (entry.actionSummary.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "⚡ ${entry.actionSummary}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = NovaColors.EmeraldActive
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputCommand,
                    onValueChange = { inputCommand = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            "Speak 'Hello Nova' or chat freely...",
                            fontSize = 12.sp,
                            color = NovaColors.TextMuted
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NovaColors.NeonCyan,
                        unfocusedBorderColor = NovaColors.BorderGlow,
                        focusedTextColor = NovaColors.TextPrimary,
                        unfocusedTextColor = NovaColors.TextPrimary
                    )
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = onOrbTap,
                    modifier = Modifier
                        .size(48.dp)
                        .background(NovaColors.ElectricBlue.copy(alpha = 0.25f), CircleShape)
                        .border(1.dp, NovaColors.NeonCyan, CircleShape)
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "Listen", tint = NovaColors.NeonCyan)
                }
                Spacer(Modifier.width(6.dp))
                IconButton(
                    onClick = {
                        if (inputCommand.isNotBlank()) {
                            onSendTextCommand(inputCommand.trim())
                            inputCommand = ""
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(NovaColors.NeonCyan, CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = NovaColors.ObsidianBg)
                }
            }
        }
    }

    if (showSettingsDialog) {
        UnifiedSettingsDialog(
            vault = vault,
            onDismiss = { showSettingsDialog = false },
            onSavedAndTestVoice = { newProv, newVoice ->
                onSettingsUpdated(newProv, newVoice)
                showSettingsDialog = false
            }
        )
    }

    if (showPermissionSheet) {
        PermissionMatrixSheet(
            categories = permissionCategories,
            onDismiss = { showPermissionSheet = false },
            onRequestAllRuntime = onRequestAllRuntimePermissions,
            onOpenSettingsForCategory = onOpenSettingsForCategory,
            onRefresh = onRefreshPermissions
        )
    }
}
