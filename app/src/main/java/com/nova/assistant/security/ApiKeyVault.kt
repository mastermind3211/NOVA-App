package com.nova.assistant.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.nova.assistant.model.AiProvider
import com.nova.assistant.model.VoiceEngineType

class ApiKeyVault(context: Context) {

    private val prefs: SharedPreferences = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "nova_secure_vault",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrElse {
        context.getSharedPreferences("nova_fallback_vault", Context.MODE_PRIVATE)
    }

    fun saveAllSettings(
        provider: AiProvider,
        geminiKey: String,
        groqKey: String,
        openRouterKey: String,
        openRouterModel: String,
        customAiUrl: String,
        customAiKey: String,
        customAiModel: String,
        voiceEngine: VoiceEngineType,
        elevenLabsKey: String,
        voiceId: String,
        customVoiceUrl: String,
        userName: String,
        personaPrompt: String
    ) {
        prefs.edit()
            .putString(KEY_PROVIDER, provider.name)
            .putString(KEY_GEMINI, geminiKey)
            .putString(KEY_GROQ, groqKey)
            .putString(KEY_OPENROUTER, openRouterKey)
            .putString(KEY_OPENROUTER_MODEL, openRouterModel.ifBlank { "meta-llama/llama-3.3-70b-instruct:free" })
            .putString(KEY_CUSTOM_AI_URL, customAiUrl)
            .putString(KEY_CUSTOM_AI_KEY, customAiKey)
            .putString(KEY_CUSTOM_AI_MODEL, customAiModel)
            .putString(KEY_VOICE_ENGINE, voiceEngine.name)
            .putString(KEY_ELEVENLABS, elevenLabsKey)
            .putString(KEY_VOICE_ID, voiceId)
            .putString(KEY_CUSTOM_VOICE_URL, customVoiceUrl)
            .putString(KEY_USER_NAME, userName.ifBlank { "Rahul" })
            .putString(KEY_PERSONA, personaPrompt)
            .apply()
    }

    fun getPreferredProvider(): AiProvider {
        val raw = prefs.getString(KEY_PROVIDER, AiProvider.GEMINI.name)
        return runCatching { AiProvider.valueOf(raw ?: AiProvider.GEMINI.name) }
            .getOrDefault(AiProvider.GEMINI)
    }

    fun getGeminiKey(): String = prefs.getString(KEY_GEMINI, "").orEmpty()
    fun getGroqKey(): String = prefs.getString(KEY_GROQ, "").orEmpty()
    fun getOpenRouterKey(): String = prefs.getString(KEY_OPENROUTER, "").orEmpty()
    fun getOpenRouterModel(): String = prefs.getString(KEY_OPENROUTER_MODEL, "meta-llama/llama-3.3-70b-instruct:free").orEmpty()
    fun getCustomAiUrl(): String = prefs.getString(KEY_CUSTOM_AI_URL, "").orEmpty()
    fun getCustomAiKey(): String = prefs.getString(KEY_CUSTOM_AI_KEY, "").orEmpty()
    fun getCustomAiModel(): String = prefs.getString(KEY_CUSTOM_AI_MODEL, "gpt-4o-mini").orEmpty()

    fun getVoiceEngineType(): VoiceEngineType {
        val defaultEngine = if (getGroqKey().isNotBlank()) VoiceEngineType.GROQ_NEURAL else VoiceEngineType.ANDROID_TTS
        val raw = prefs.getString(KEY_VOICE_ENGINE, defaultEngine.name)
        return runCatching { VoiceEngineType.valueOf(raw ?: defaultEngine.name) }
            .getOrDefault(defaultEngine)
    }

    fun getElevenLabsKey(): String = prefs.getString(KEY_ELEVENLABS, "").orEmpty()
    fun getVoiceId(): String = prefs.getString(KEY_VOICE_ID, "21m00Tcm4TlvDq8ikWAM").orEmpty()
    fun getCustomVoiceUrl(): String = prefs.getString(KEY_CUSTOM_VOICE_URL, "").orEmpty()

    fun getUserName(): String = prefs.getString(KEY_USER_NAME, "Rahul").orEmpty()
    fun getPersonaPrompt(): String = prefs.getString(
        KEY_PERSONA,
        "You are my loyal, witty, emotionally intelligent personal AI friend and autonomous phone assistant. Talk naturally in Hinglish/Hindi like a real human companion."
    ).orEmpty()

    companion object {
        private const val KEY_PROVIDER = "preferred_provider"
        private const val KEY_GEMINI = "gemini_api_key"
        private const val KEY_GROQ = "groq_api_key"
        private const val KEY_OPENROUTER = "openrouter_api_key"
        private const val KEY_OPENROUTER_MODEL = "openrouter_model"
        private const val KEY_CUSTOM_AI_URL = "custom_ai_url"
        private const val KEY_CUSTOM_AI_KEY = "custom_ai_key"
        private const val KEY_CUSTOM_AI_MODEL = "custom_ai_model"
        private const val KEY_VOICE_ENGINE = "voice_engine_type"
        private const val KEY_ELEVENLABS = "elevenlabs_api_key"
        private const val KEY_VOICE_ID = "custom_voice_id"
        private const val KEY_CUSTOM_VOICE_URL = "custom_voice_http_url"
        private const val KEY_USER_NAME = "user_display_name"
        private const val KEY_PERSONA = "custom_persona_prompt"
    }
}
