package com.nova.assistant.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.assistant.model.AiProvider
import com.nova.assistant.model.VoiceEngineType
import com.nova.assistant.security.ApiKeyVault
import com.nova.assistant.ui.theme.NovaColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnifiedSettingsDialog(
    vault: ApiKeyVault,
    onDismiss: () -> Unit,
    onSavedAndTestVoice: (AiProvider, VoiceEngineType) -> Unit
) {
    val context = LocalContext.current
    val memoryPrefs = remember {
        context.getSharedPreferences("nova_long_term_brain", android.content.Context.MODE_PRIVATE)
    }

    var selectedTab by remember { mutableIntStateOf(0) }

    // Tab 0: Brain APIs
    var provider by remember { mutableStateOf(vault.getPreferredProvider()) }
    var geminiKey by remember { mutableStateOf(vault.getGeminiKey()) }
    var groqKey by remember { mutableStateOf(vault.getGroqKey()) }
    var openRouterKey by remember { mutableStateOf(vault.getOpenRouterKey()) }
    var openRouterModel by remember { mutableStateOf(vault.getOpenRouterModel()) }
    var customAiUrl by remember { mutableStateOf(vault.getCustomAiUrl()) }
    var customAiKey by remember { mutableStateOf(vault.getCustomAiKey()) }
    var customAiModel by remember { mutableStateOf(vault.getCustomAiModel()) }

    // Tab 1: Voice APIs
    var voiceEngine by remember { mutableStateOf(vault.getVoiceEngineType()) }
    var elevenLabsKey by remember { mutableStateOf(vault.getElevenLabsKey()) }
    var voiceId by remember { mutableStateOf(vault.getVoiceId()) }
    var customVoiceUrl by remember { mutableStateOf(vault.getCustomVoiceUrl()) }

    // Tab 2: Personalization & Memory
    var userName by remember { mutableStateOf(vault.getUserName()) }
    var personaPrompt by remember { mutableStateOf(vault.getPersonaPrompt()) }
    var longTermFacts by remember { mutableStateOf(memoryPrefs.getString("user_facts", "").orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NovaColors.ObsidianBg,
        title = {
            Text(
                "N.O.V.A. // API & BRAIN SETTINGS",
                color = NovaColors.NeonCyan,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = NovaColors.DeepSpacePanel,
                    contentColor = NovaColors.NeonCyan
                ) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("BRAIN APIs", fontSize = 11.sp) })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("VOICE APIs", fontSize = 11.sp) })
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("PERSONAL", fontSize = 11.sp) })
                }

                Spacer(Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 370.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (selectedTab == 0) {
                        Text(
                            "Primary Brain Provider (Auto-switches to others if quota/limit ends):",
                            color = NovaColors.TextMuted,
                            fontSize = 11.sp
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AiProvider.entries.forEach { item ->
                                FilterChip(
                                    selected = provider == item,
                                    onClick = { provider = item },
                                    label = { Text(item.displayName, fontSize = 11.sp) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = geminiKey,
                            onValueChange = { geminiKey = it },
                            label = { Text("1. Google Gemini API Key") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = groqKey,
                            onValueChange = { groqKey = it },
                            label = { Text("2. Groq Cloud API Key") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = openRouterKey,
                            onValueChange = { openRouterKey = it },
                            label = { Text("3. OpenRouter API Key (Multi-Model)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = openRouterModel,
                            onValueChange = { openRouterModel = it },
                            label = { Text("OpenRouter Model ID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = customAiUrl,
                            onValueChange = { customAiUrl = it },
                            label = { Text("4. Custom AI Chat Completions URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = customAiKey,
                            onValueChange = { customAiKey = it },
                            label = { Text("Custom AI Bearer Key (Optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = customAiModel,
                            onValueChange = { customAiModel = it },
                            label = { Text("Custom AI Model Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else if (selectedTab == 1) {
                        Text(
                            "Select Voice Engine (Change anytime if API quota ends):",
                            color = NovaColors.TextMuted,
                            fontSize = 11.sp
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            VoiceEngineType.entries.forEach { eng ->
                                FilterChip(
                                    selected = voiceEngine == eng,
                                    onClick = { voiceEngine = eng },
                                    label = { Text(eng.displayName, fontSize = 11.sp) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = elevenLabsKey,
                            onValueChange = { elevenLabsKey = it },
                            label = { Text("ElevenLabs Studio API Key") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = voiceId,
                            onValueChange = { voiceId = it },
                            label = { Text("Voice ID (ElevenLabs ID or Celeste-PlayAI)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = customVoiceUrl,
                            onValueChange = { customVoiceUrl = it },
                            label = { Text("Custom TTS POST URL (returns MP3)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        OutlinedTextField(
                            value = userName,
                            onValueChange = { userName = it },
                            label = { Text("Your Name (NOVA calls you this)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = personaPrompt,
                            onValueChange = { personaPrompt = it },
                            label = { Text("NOVA's Personality & Thinking Style") },
                            minLines = 3,
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = longTermFacts,
                            onValueChange = { longTermFacts = it },
                            label = { Text("Saved Long-Term Memory (Editable)") },
                            minLines = 3,
                            maxLines = 5,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedButton(
                            onClick = {
                                longTermFacts = ""
                                memoryPrefs.edit().remove("user_facts").apply()
                            }
                        ) {
                            Text("Clear Memory", fontSize = 11.sp, color = NovaColors.CrimsonError)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    vault.saveAllSettings(
                        provider = provider,
                        geminiKey = geminiKey.trim(),
                        groqKey = groqKey.trim(),
                        openRouterKey = openRouterKey.trim(),
                        openRouterModel = openRouterModel.trim(),
                        customAiUrl = customAiUrl.trim(),
                        customAiKey = customAiKey.trim(),
                        customAiModel = customAiModel.trim(),
                        voiceEngine = voiceEngine,
                        elevenLabsKey = elevenLabsKey.trim(),
                        voiceId = voiceId.trim(),
                        customVoiceUrl = customVoiceUrl.trim(),
                        userName = userName.trim(),
                        personaPrompt = personaPrompt.trim()
                    )
                    memoryPrefs.edit().putString("user_facts", longTermFacts.trim()).apply()
                    onSavedAndTestVoice(provider, voiceEngine)
                },
                colors = ButtonDefaults.buttonColors(containerColor = NovaColors.NeonCyan)
            ) {
                Text("Save All Settings", color = NovaColors.ObsidianBg)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = NovaColors.TextMuted)
            }
        }
    )
}
