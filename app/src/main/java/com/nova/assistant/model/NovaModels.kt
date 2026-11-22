package com.nova.assistant.model

enum class NovaOrbState(val label: String) {
    IDLE("STANDBY"),
    LISTENING_WAKE_WORD("WAKE_ACTIVE"),
    LISTENING_COMMAND("LISTENING"),
    THINKING("NEURAL_PROC"),
    SPEAKING("VOCALIZING")
}

enum class VoiceEmotion(val tag: String) {
    NEUTRAL("neutral"),
    HAPPY("happy"),
    EXCITED("excited"),
    CALM("calm"),
    EMPATHETIC("empathetic"),
    SERIOUS("serious"),
    PLAYFUL("playful");

    companion object {
        fun fromString(raw: String): VoiceEmotion {
            return entries.firstOrNull { it.tag.equals(raw.trim(), ignoreCase = true) } ?: NEUTRAL
        }
    }
}

enum class VoiceEngineType(val displayName: String) {
    GROQ_NEURAL("Groq Neural Voice (Free)"),
    ELEVENLABS("ElevenLabs Studio Voice"),
    CUSTOM_HTTP("Custom Voice URL"),
    ANDROID_TTS("Device Offline TTS")
}

enum class AiProvider(val displayName: String) {
    GEMINI("Google Gemini"),
    GROQ("Groq Cloud"),
    OPENROUTER("OpenRouter (Multi-AI)"),
    CUSTOM_OPENAI("Custom AI URL")
}

data class NovaAction(
    val type: String,
    val params: Map<String, String> = emptyMap()
)

data class NovaAiResponse(
    val reply: String,
    val emotion: VoiceEmotion = VoiceEmotion.NEUTRAL,
    val actions: List<NovaAction> = emptyList(),
    val isComplete: Boolean = true,
    val remainingSteps: String = "",
    val nextWaitMs: Long = 1800L,
    val keepListening: Boolean = false
)

data class CommandLogEntry(
    val id: Long,
    val isUser: Boolean,
    val text: String,
    val actionSummary: String = "",
    val timestamp: String
)
