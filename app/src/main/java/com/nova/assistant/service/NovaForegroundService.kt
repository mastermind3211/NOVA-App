package com.nova.assistant.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.nova.assistant.MainActivity
import com.nova.assistant.R
import com.nova.assistant.ai.NovaAiBrain
import com.nova.assistant.executor.DeviceActionExecutor
import com.nova.assistant.model.CommandLogEntry
import com.nova.assistant.model.NovaOrbState
import com.nova.assistant.model.VoiceEmotion
import com.nova.assistant.security.ApiKeyVault
import com.nova.assistant.ui.overlay.FloatingHudOverlay
import com.nova.assistant.voice.NovaVoiceEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NovaRuntimeBus {
    private val _orbState = MutableStateFlow(NovaOrbState.IDLE)
    val orbState: StateFlow<NovaOrbState> = _orbState.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _isOverlayActive = MutableStateFlow(false)
    val isOverlayActive: StateFlow<Boolean> = _isOverlayActive.asStateFlow()

    private val _lastReply = MutableStateFlow("Awaiting command...")
    val lastReply: StateFlow<String> = _lastReply.asStateFlow()

    private val _logs = MutableStateFlow<List<CommandLogEntry>>(emptyList())
    val logs: StateFlow<List<CommandLogEntry>> = _logs.asStateFlow()

    fun setOrbState(state: NovaOrbState) { _orbState.value = state }
    fun setAudioLevel(level: Float) { _audioLevel.value = level }
    fun setServiceRunning(running: Boolean) { _isServiceRunning.value = running }
    fun setOverlayActive(active: Boolean) { _isOverlayActive.value = active }
    fun setLastReply(reply: String) { _lastReply.value = reply }

    fun appendLog(isUser: Boolean, text: String, actionSummary: String = "") {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val entry = CommandLogEntry(
            id = System.nanoTime(),
            isUser = isUser,
            text = text,
            actionSummary = actionSummary,
            timestamp = ts
        )
        _logs.value = (_logs.value + entry).takeLast(50)
    }
}

class NovaForegroundService : LifecycleService() {

    companion object {
        const val CHANNEL_ID = "nova_core_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_LISTEN_NOW = "com.nova.assistant.ACTION_LISTEN_NOW"
        const val ACTION_PROCESS_TEXT = "com.nova.assistant.ACTION_PROCESS_TEXT"
        const val ACTION_SHOW_OVERLAY = "com.nova.assistant.ACTION_SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "com.nova.assistant.ACTION_HIDE_OVERLAY"
        const val ACTION_STOP_SERVICE = "com.nova.assistant.ACTION_STOP_SERVICE"
        const val ACTION_TEST_VOICE = "com.nova.assistant.ACTION_TEST_VOICE"
        const val EXTRA_TEXT_COMMAND = "extra_text_command"
    }

    private lateinit var vault: ApiKeyVault
    private lateinit var aiBrain: NovaAiBrain
    private lateinit var executor: DeviceActionExecutor
    private lateinit var voiceEngine: NovaVoiceEngine
    private lateinit var overlay: FloatingHudOverlay

    override fun onCreate() {
        super.onCreate()
        vault = ApiKeyVault(applicationContext)
        aiBrain = NovaAiBrain(applicationContext, vault)
        executor = DeviceActionExecutor(applicationContext)

        voiceEngine = NovaVoiceEngine(
            context = applicationContext,
            vault = vault,
            onStateChanged = { NovaRuntimeBus.setOrbState(it) },
            onAudioLevel = { NovaRuntimeBus.setAudioLevel(it) },
            onWakeWordDetected = {
                NovaRuntimeBus.appendLog(false, "Wake word 'Hello Nova' detected.")
            },
            onCommandRecognized = { command ->
                processUserCommand(command, speakReply = true)
            },
            onEngineError = { err ->
                NovaRuntimeBus.appendLog(false, "Voice Engine: $err")
            }
        )

        overlay = FloatingHudOverlay(
            context = applicationContext,
            onTriggerListen = { voiceEngine.startDirectCommandListening() },
            onCloseOverlay = { overlay.hide() }
        )

        createNotificationChannel()
        val notification = buildNotification("Listening for 'Hello Nova'")
        val hasMic = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasMic) {
            NovaRuntimeBus.appendLog(false, "Microphone permission missing. Stopping core service.")
            stopSelf()
            return
        }

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure {
            NovaRuntimeBus.appendLog(false, "Foreground start blocked: ${it.localizedMessage}")
            stopSelf()
            return
        }

        NovaRuntimeBus.setServiceRunning(true)
        voiceEngine.startWakeWordListening()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (!NovaRuntimeBus.isServiceRunning.value) {
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_LISTEN_NOW -> voiceEngine.startDirectCommandListening()
            ACTION_PROCESS_TEXT -> {
                val cmd = intent.getStringExtra(EXTRA_TEXT_COMMAND).orEmpty()
                if (cmd.isNotBlank()) processUserCommand(cmd, speakReply = true)
            }
            ACTION_SHOW_OVERLAY -> overlay.show()
            ACTION_HIDE_OVERLAY -> overlay.hide()
            ACTION_TEST_VOICE -> voiceEngine.speak("Hello! Main Nova hoon, aur meri nayi custom voice ab active hai.", VoiceEmotion.EXCITED)
            ACTION_STOP_SERVICE -> {
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun processUserCommand(command: String, speakReply: Boolean) {
        NovaRuntimeBus.appendLog(true, command)
        NovaRuntimeBus.setOrbState(NovaOrbState.THINKING)

        lifecycleScope.launch {
            var step = 0
            val stepHistory = StringBuilder()
            var finalReply = "Done."
            var finalEmotion = VoiceEmotion.NEUTRAL
            var finalKeepListening = false
            val executedTypes = mutableListOf<String>()
            var repeatCount = 0
            var previousSignature = ""

            while (step < 18) {
                val aiResponse = aiBrain.processCommand(command, stepHistory.toString())
                finalEmotion = aiResponse.emotion
                finalKeepListening = aiResponse.keepListening
                val executedSummary = executor.executeActions(aiResponse.actions)
                val stepActionsStr = aiResponse.actions.joinToString { "${it.type}(${it.params})" }
                executedTypes.addAll(aiResponse.actions.map { it.type })

                if (!aiResponse.isComplete && aiResponse.actions.isNotEmpty()) {
                    NovaRuntimeBus.appendLog(
                        isUser = false,
                        text = "Agent Step ${step + 1}: ${aiResponse.reply}",
                        actionSummary = stepActionsStr
                    )
                }

                val isHardPermissionError = listOf("permission required", "not granted", "service is not enabled")
                    .any { executedSummary.lowercase().contains(it) }

                finalReply = when {
                    isHardPermissionError -> executedSummary
                    executedSummary.isNotBlank() && aiResponse.reply.isBlank() -> executedSummary
                    executedSummary.isNotBlank() && aiResponse.actions.any {
                        it.type in listOf("READ_SMS", "READ_NOTIFICATIONS", "ACCESSIBILITY_READ_SCREEN", "GET_BATTERY_STATUS")
                    } -> executedSummary
                    else -> aiResponse.reply
                }

                if (stepActionsStr == previousSignature) {
                    repeatCount++
                } else {
                    repeatCount = 0
                }
                previousSignature = stepActionsStr

                if (aiResponse.isComplete || aiResponse.actions.isEmpty() || isHardPermissionError || repeatCount >= 3) {
                    break
                }

                stepHistory.append("Step ${step + 1}: ")
                    .append(stepActionsStr)
                    .append(" -> Result: $executedSummary | Pending: ${aiResponse.remainingSteps}\n")
                step++
                kotlinx.coroutines.delay(aiResponse.nextWaitMs)
            }

            NovaRuntimeBus.setLastReply(finalReply)
            NovaRuntimeBus.appendLog(
                isUser = false,
                text = finalReply,
                actionSummary = executedTypes.joinToString(" -> ")
            )

            if (speakReply) {
                voiceEngine.speak(finalReply, finalEmotion, autoFollowUpListen = finalKeepListening)
            } else {
                voiceEngine.startWakeWordListening()
            }
        }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "NOVA Neural Voice Core",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps 'Hello Nova' wake-word detection active"
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(statusText: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val listenIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, NovaForegroundService::class.java).setAction(ACTION_LISTEN_NOW),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NOVA Core Online")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_nova_launcher_foreground)
            .setContentIntent(openAppIntent)
            .addAction(R.drawable.ic_nova_launcher_foreground, "Speak Now", listenIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        if (::overlay.isInitialized) overlay.hide()
        if (::voiceEngine.isInitialized) voiceEngine.destroy()
        NovaRuntimeBus.setServiceRunning(false)
        NovaRuntimeBus.setOrbState(NovaOrbState.IDLE)
        super.onDestroy()
    }
}
