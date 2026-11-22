package com.nova.assistant.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.assistant.permissions.NovaPermissionManager
import com.nova.assistant.permissions.PermissionCategoryItem
import com.nova.assistant.permissions.PermissionGrantType
import com.nova.assistant.ui.theme.NovaColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionMatrixSheet(
    categories: List<PermissionCategoryItem>,
    onDismiss: () -> Unit,
    onRequestAllRuntime: () -> Unit,
    onOpenSettingsForCategory: (Int) -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val grantedCount = categories.count { it.isGranted }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = NovaColors.ObsidianBg,
        contentColor = NovaColors.TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SYSTEM PERMISSION MATRIX",
                        color = NovaColors.NeonCyan,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "$grantedCount / ${categories.size} Modules Authorized",
                        color = NovaColors.TextMuted,
                        fontSize = 12.sp
                    )
                }
                Row {
                    OutlinedButton(onClick = onRefresh) {
                        Text("Refresh", fontSize = 12.sp, color = NovaColors.NeonCyan)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onRequestAllRuntime,
                        colors = ButtonDefaults.buttonColors(containerColor = NovaColors.ElectricBlue)
                    ) {
                        Text("Grant Runtime", fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = NovaColors.DeepSpacePanel,
                contentColor = NovaColors.NeonCyan
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("ALL 21 MODULES") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("ADB PRIVILEGED HELPER") }
                )
            }

            Spacer(Modifier.height(10.dp))

            if (selectedTab == 0) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories, key = { it.id }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(NovaColors.GlassCard, RoundedCornerShape(12.dp))
                                .border(
                                    1.dp,
                                    if (item.isGranted) NovaColors.EmeraldActive.copy(alpha = 0.4f)
                                    else NovaColors.BorderGlow,
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (item.isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (item.isGranted) NovaColors.EmeraldActive else NovaColors.AmberWarn
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${item.id}. ${item.title}",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = NovaColors.TextPrimary
                                )
                                Text(
                                    text = item.description,
                                    fontSize = 12.sp,
                                    color = NovaColors.TextMuted
                                )
                            }
                            if (!item.isGranted) {
                                OutlinedButton(
                                    onClick = {
                                        if (item.grantType == PermissionGrantType.RUNTIME) {
                                            onRequestAllRuntime()
                                        } else {
                                            onOpenSettingsForCategory(item.id)
                                        }
                                    }
                                ) {
                                    Text("Enable", fontSize = 11.sp, color = NovaColors.NeonCyan)
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            } else {
                val adbCommands = remember(context.packageName) {
                    NovaPermissionManager.getFullAdbCommandBundle(context.packageName)
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Text(
                            text = "Run these commands via USB Debugging (PC -> ADB) to unlock hidden/restricted Android permissions without root:",
                            color = NovaColors.TextMuted,
                            fontSize = 12.sp
                        )
                    }
                    items(adbCommands) { (label, cmd) ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(NovaColors.GlassCard, RoundedCornerShape(12.dp))
                                .border(1.dp, NovaColors.CyberPurple.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Terminal,
                                        contentDescription = null,
                                        tint = NovaColors.CyberPurple
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = label,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = NovaColors.TextPrimary
                                    )
                                }
                                IconButton(onClick = { copyToClipboard(context, label, cmd) }) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "Copy ADB Command",
                                        tint = NovaColors.NeonCyan
                                    )
                                }
                            }
                            Text(
                                text = cmd,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = NovaColors.NeonCyan
                            )
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "Copied ADB command", Toast.LENGTH_SHORT).show()
}
