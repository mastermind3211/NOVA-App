package com.nova.assistant.ai

import android.content.Context
import com.nova.assistant.model.AiProvider
import com.nova.assistant.model.NovaAction
import com.nova.assistant.model.NovaAiResponse
import com.nova.assistant.model.VoiceEmotion
import com.nova.assistant.security.ApiKeyVault
import com.nova.assistant.service.NovaAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class NovaAiBrain(private val context: Context, private val vault: ApiKeyVault) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val memoryPrefs = context.getSharedPreferences("nova_long_term_brain", Context.MODE_PRIVATE)
    private val shortTermChat = ArrayDeque<String>()

    fun getLongTermFacts(): String =
        memoryPrefs.getString("user_facts", "").orEmpty()

    fun setLongTermFacts(facts: String) {
        memoryPrefs.edit().putString("user_facts", facts.trim()).apply()
    }

    private fun saveNewFact(newFact: String) {
        if (newFact.isBlank() || newFact.equals("none", ignoreCase = true)) return
        val existing = getLongTermFacts()
        if (!existing.contains(newFact, ignoreCase = true)) {
            val updated = (if (existing.isBlank()) newFact.trim() else "$existing | ${newFact.trim()}").takeLast(1400)
            memoryPrefs.edit().putString("user_facts", updated).apply()
        }
    }

    private fun buildSystemPrompt(): String {
        val userName = vault.getUserName()
        val customPersona = vault.getPersonaPrompt()
        return """
You are NOVA, an unstoppable, self-thinking, autonomous Android Mobile Agent belonging personally to $userName.
PERSONALITY DIRECTIVE: $customPersona
Understand English, Hindi (Devanagari), and Hinglish (Romanized Hindi + English).

ALWAYS respond ONLY with a single valid JSON object (no markdown fences, no extra text):
{
  "thought": "1. Analyze every sub-task in USER_FULL_MULTI_TASK_GOAL. 2. Check COMPLETED_STEPS_SO_FAR and LIVE_SCREEN_ELEMENTS (#1, #2...). 3. Decide exact action(s) to execute right now.",
  "remaining_steps": "List any sub-tasks still NOT finished after this step, or 'none' ONLY when 100% of user's goal is completely finished and verified",
  "memory_to_save": "Any new personal fact/preference learned about $userName, or 'none'",
  "emotion": "neutral | happy | excited | calm | empathetic | serious | playful",
  "reply": "Short natural spoken response in the user's language (English, Hindi, or Hinglish)",
  "actions": [
    { "type": "ACTION_NAME", "params": { "key": "value" } }
  ],
  "is_complete": false,
  "keep_listening": false,
  "next_wait_ms": 2000
}

STRICT MULTI-STEP & COMPLETION RULES (NEVER STOP HALFWAY):
1. BREAK DOWN EVERY COMMAND: If user gives 2 or 3 tasks (e.g., "Instagram kholo, search pe jao, aur trends by rahul search karo" OR "Flashlight on karo aur YouTube par song play karo"), execute step-by-step and keep "remaining_steps" populated and "is_complete": false until ALL parts are finished!
2. USE ELEMENT NUMBERS (#1, #2, #3...): Every visible UI element in LIVE_SCREEN_ELEMENTS has an index like `#4 [BTN] Search @(950,140)`. Prefer `CLICK_INDEX: {"index": "4"}` (or `CLICK_TEXT` / `TAP_COORDINATES`) to reliably tap icons, search bars, videos, and send buttons!
3. VERIFY AFTER UI ACTIONS: Whenever you run `OPEN_APP`, `CLICK_INDEX`, `CLICK_TEXT`, `TYPE_TEXT`, or `ACCESSIBILITY_SCROLL`, set `"is_complete": false` so you can inspect the updated screen on the next iteration and complete the next step!
4. YOUTUBE PLAYBACK: Use `PLAY_YOUTUBE: {"query": "..."}` first. On the next verification iteration, check `LIVE_SCREEN_ELEMENTS` — if search results are listed and the video hasn't opened yet, click the first video using `CLICK_INDEX`!
5. PURE CONVERSATION: Only when no phone action is needed at all, use `SPEAK_ONLY`, `"remaining_steps": "none"`, `"is_complete": true`, `"keep_listening": true`.

Supported ACTION_NAME values & params:
- CLICK_INDEX: {"index": "element number like 4 from #4 in LIVE_SCREEN_ELEMENTS"}
- CLICK_TEXT: {"text": "visible button, label, or id:xyz from screen"}
- TYPE_TEXT: {"text": "text to type", "submit": "true" | "false"}
- TAP_COORDINATES: {"x": "540", "y": "960"}
- LONG_PRESS_COORDINATES: {"x": "540", "y": "960"}
- OPEN_APP: {"app_name": "any installed app name"}
- PLAY_YOUTUBE: {"query": "song or video name"}
- PLAY_SPOTIFY: {"query": "song or artist name"}
- ACCESSIBILITY_SCROLL: {"direction": "down" | "up"}
- ACCESSIBILITY_SWIPE: {"direction": "left" | "right"}
- ACCESSIBILITY_BACK: {}
- ACCESSIBILITY_HOME: {}
- ACCESSIBILITY_RECENTS: {}
- OPEN_NOTIFICATIONS_PANEL: {}
- OPEN_QUICK_SETTINGS: {}
- ACCESSIBILITY_READ_SCREEN: {}
- TOGGLE_FLASHLIGHT: {"state": "on" | "off"}
- OPEN_CAMERA: {"mode": "photo" | "selfie"}
- MAKE_CALL: {"target": "phone_number_or_contact_name"}
- SEND_SMS: {"target": "phone_number_or_contact_name", "message": "text"}
- READ_SMS: {}
- SEND_WHATSAPP: {"target": "contact_name_or_number", "message": "text"}
- READ_NOTIFICATIONS: {}
- SEARCH_WEB: {"query": "search terms"}
- OPEN_URL: {"url": "website url"}
- NAVIGATE_MAPS: {"destination": "place name"}
- SET_ALARM: {"hour": "0-23", "minute": "0-59", "label": "alarm name"}
- SET_TIMER: {"seconds": "60", "label": "timer name"}
- CREATE_CALENDAR_EVENT: {"title": "event title", "minutes_from_now": "60"}
- SET_BRIGHTNESS: {"level": "0-100"}
- SET_VOLUME: {"level": "0-100", "stream": "media|ring"}
- TOGGLE_DND: {"state": "on" | "off"}
- TOGGLE_WIFI: {"state": "on" | "off"}
- TOGGLE_BLUETOOTH: {"state": "on" | "off"}
- OPEN_BLUETOOTH_SETTINGS: {}
- COPY_TO_CLIPBOARD: {"text": "text to copy"}
- OPEN_SETTINGS_PAGE: {"page": "wifi|bluetooth|display|sound|battery|location|apps|main"}
- LOCK_SCREEN: {}
- MEDIA_CONTROL: {"command": "play_pause|next|previous"}
- GET_BATTERY_STATUS: {}
- WAIT_SECONDS: {"seconds": "2"}
- SPEAK_ONLY: {}
        """.trimIndent()
    }

    suspend fun processCommand(userQuery: String, stepContext: String = ""): NovaAiResponse = withContext(Dispatchers.IO) {
        val preferred = vault.getPreferredProvider()
        val geminiKey = vault.getGeminiKey().trim()
        val groqKey = vault.getGroqKey().trim()
        val openRouterKey = vault.getOpenRouterKey().trim()
        val customAiUrl = vault.getCustomAiUrl().trim()

        if (geminiKey.isBlank() && groqKey.isBlank() && openRouterKey.isBlank() && customAiUrl.isBlank()) {
            return@withContext NovaAiResponse(
                reply = "Settings mein jaakar Gemini, Groq, ya OpenRouter ki API key daal do, phir main tumse baat kar paunga!",
                emotion = VoiceEmotion.HAPPY,
                actions = emptyList(),
                isComplete = true
            )
        }

        val currentTime = SimpleDateFormat("EEEE, dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
        val screenSnapshot = NovaAccessibilityService.instance?.dumpInteractiveScreen().orEmpty()
        val storedFacts = getLongTermFacts()

        val enrichedPrompt = buildString {
            append("CURRENT_TIME: ").append(currentTime).append("\n")
            if (storedFacts.isNotBlank()) {
                append("LONG_TERM_MEMORY_ABOUT_USER: ").append(storedFacts).append("\n\n")
            }
            if (shortTermChat.isNotEmpty()) {
                append("RECENT_CONVERSATION_FLOW:\n").append(shortTermChat.joinToString("\n")).append("\n\n")
            }
            append("USER_FULL_MULTI_TASK_GOAL: ").append(userQuery)
            if (stepContext.isNotBlank()) {
                append("\nCOMPLETED_STEPS_SO_FAR:\n").append(stepContext)
            }
            if (screenSnapshot.isNotBlank()) {
                append("\nLIVE_SCREEN_ELEMENTS:\n").append(screenSnapshot)
            }
        }

        val allProviders = listOf(AiProvider.GEMINI, AiProvider.GROQ, AiProvider.OPENROUTER, AiProvider.CUSTOM_OPENAI)
        val order = listOf(preferred) + allProviders.filter { it != preferred }
        val errorDetails = mutableListOf<String>()

        for (provider in order) {
            val rawResult = when (provider) {
                AiProvider.GEMINI -> if (geminiKey.isNotBlank()) callGemini(geminiKey, enrichedPrompt, errorDetails) else null
                AiProvider.GROQ -> if (groqKey.isNotBlank()) callGroq(groqKey, enrichedPrompt, errorDetails) else null
                AiProvider.OPENROUTER -> if (openRouterKey.isNotBlank()) callOpenAiCompatible(
                    providerLabel = "OpenRouter",
                    endpointUrl = "https://openrouter.ai/api/v1/chat/completions",
                    apiKey = openRouterKey,
                    model = vault.getOpenRouterModel().trim().ifBlank { "meta-llama/llama-3.3-70b-instruct:free" },
                    query = enrichedPrompt,
                    errors = errorDetails
                ) else null
                AiProvider.CUSTOM_OPENAI -> if (customAiUrl.isNotBlank()) callOpenAiCompatible(
                    providerLabel = "CustomAI",
                    endpointUrl = customAiUrl,
                    apiKey = vault.getCustomAiKey().trim(),
                    model = vault.getCustomAiModel().trim().ifBlank { "gpt-4o-mini" },
                    query = enrichedPrompt,
                    errors = errorDetails
                ) else null
            }
            if (rawResult != null) {
                val parsed = parseAiJson(rawResult)
                if (parsed.isComplete) {
                    if (shortTermChat.size >= 12) shortTermChat.removeFirst()
                    shortTermChat.addLast("User: $userQuery | NOVA (${parsed.emotion.tag}): ${parsed.reply}")
                }
                return@withContext parsed
            }
        }

        val reason = errorDetails.joinToString(" | ").ifBlank { "No internet connection" }
        NovaAiResponse(
            reply = "API limit ya network issue aa gaya hai. Settings mein doosri API key select kar lo. ($reason)",
            emotion = VoiceEmotion.EMPATHETIC,
            actions = emptyList(),
            isComplete = true
        )
    }

    private fun discoverGeminiModels(apiKey: String): List<String> {
        val defaults = listOf(
            "gemini-3.8-flash",
            "gemini-3.5-flash",
            "gemini-3-flash-preview",
            "gemini-2.5-flash"
        )
        return runCatching {
            val req = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use defaults
                val arr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("models") ?: return@use defaults
                val live = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    val m = arr.getJSONObject(i)
                    val methods = m.optJSONArray("supportedGenerationMethods")?.toString().orEmpty()
                    val name = m.optString("name").removePrefix("models/")
                    if (methods.contains("generateContent") && name.contains("flash", ignoreCase = true)) {
                        live.add(name)
                    }
                }
                (defaults + live).distinct()
            }
        }.getOrDefault(defaults)
    }

    private fun callGemini(apiKey: String, query: String, errors: MutableList<String>): String? {
        val models = discoverGeminiModels(apiKey)
        val payload = JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", buildSystemPrompt())))
            })
            put("contents", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", query)))
            }))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.55)
                put("responseMimeType", "application/json")
            })
        }

        for (model in models) {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val res = runCatching {
                client.newCall(request).execute().use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        val msg = runCatching {
                            JSONObject(bodyStr).getJSONObject("error").getString("message")
                        }.getOrDefault(bodyStr.take(80))
                        errors.add("Gemini($model) ${resp.code}: $msg")
                        return@use null
                    }
                    val root = JSONObject(bodyStr)
                    root.getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                }
            }.onFailure {
                errors.add("Gemini NetErr: ${it.localizedMessage}")
            }.getOrNull()

            if (res != null) return res
        }
        return null
    }

    private fun discoverGroqModels(apiKey: String): List<String> {
        val defaults = listOf(
            "llama-3.1-8b-instant",
            "meta-llama/llama-4-scout-17b-16e-instruct",
            "qwen/qwen3-32b"
        )
        return runCatching {
            val req = Request.Builder()
                .url("https://api.groq.com/openai/v1/models")
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use defaults
                val arr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("data") ?: return@use defaults
                val live = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    val id = arr.getJSONObject(i).optString("id")
                    if (id.isNotBlank() && !id.contains("whisper", true) && !id.contains("guard", true) && !id.contains("tts", true)) {
                        live.add(id)
                    }
                }
                (defaults + live).distinct()
            }
        }.getOrDefault(defaults)
    }

    private fun callGroq(apiKey: String, query: String, errors: MutableList<String>): String? {
        val groqModels = discoverGroqModels(apiKey)
        for (model in groqModels) {
            val res = callOpenAiCompatible(
                providerLabel = "Groq($model)",
                endpointUrl = "https://api.groq.com/openai/v1/chat/completions",
                apiKey = apiKey,
                model = model,
                query = query,
                errors = errors
            )
            if (res != null) return res
        }
        return null
    }

    private fun callOpenAiCompatible(
        providerLabel: String,
        endpointUrl: String,
        apiKey: String,
        model: String,
        query: String,
        errors: MutableList<String>
    ): String? {
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", buildSystemPrompt()))
            .put(JSONObject().put("role", "user").put("content", query))

        val payload = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("temperature", 0.55)
        }

        val reqBuilder = Request.Builder()
            .url(endpointUrl)
            .post(payload.toString().toRequestBody(jsonMediaType))

        if (apiKey.isNotBlank()) {
            reqBuilder.header("Authorization", "Bearer $apiKey")
        }

        return runCatching {
            client.newCall(reqBuilder.build()).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val msg = runCatching {
                        JSONObject(bodyStr).getJSONObject("error").getString("message")
                    }.getOrDefault(bodyStr.take(80))
                    errors.add("$providerLabel ${resp.code}: $msg")
                    return@use null
                }
                val root = JSONObject(bodyStr)
                root.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            }
        }.onFailure {
            errors.add("$providerLabel NetErr: ${it.localizedMessage}")
        }.getOrNull()
    }

    private fun parseAiJson(raw: String): NovaAiResponse {
        val cleaned = raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        return runCatching {
            val start = cleaned.indexOf('{')
            val end = cleaned.lastIndexOf('}')
            val jsonStr = if (start >= 0 && end > start) cleaned.substring(start, end + 1) else cleaned
            val obj = JSONObject(jsonStr)
            saveNewFact(obj.optString("memory_to_save", "none"))
            val reply = obj.optString("reply", "Done.")
            val emotion = VoiceEmotion.fromString(obj.optString("emotion", "neutral"))
            val remainingSteps = obj.optString("remaining_steps", "none").trim()
            val hasMoreWork = remainingSteps.isNotBlank() &&
                !remainingSteps.equals("none", ignoreCase = true) &&
                !remainingSteps.equals("null", ignoreCase = true)
            val isComplete = if (hasMoreWork) false else obj.optBoolean("is_complete", true)
            val keepListening = obj.optBoolean("keep_listening", false)
            val nextWaitMs = obj.optLong("next_wait_ms", 1800L).coerceIn(800L, 5000L)
            val actionsArr = obj.optJSONArray("actions") ?: JSONArray()
            val actions = buildList {
                for (i in 0 until actionsArr.length()) {
                    val actObj = actionsArr.getJSONObject(i)
                    val type = actObj.optString("type", "SPEAK_ONLY")
                    val paramsObj = actObj.optJSONObject("params") ?: JSONObject()
                    val paramsMap = mutableMapOf<String, String>()
                    paramsObj.keys().forEach { key ->
                        paramsMap[key] = paramsObj.optString(key, "")
                    }
                    add(NovaAction(type = type, params = paramsMap))
                }
            }
            NovaAiResponse(
                reply = reply,
                emotion = emotion,
                actions = actions,
                isComplete = isComplete,
                remainingSteps = remainingSteps,
                nextWaitMs = nextWaitMs,
                keepListening = keepListening
            )
        }.getOrElse {
            NovaAiResponse(reply = cleaned.take(240), actions = emptyList(), isComplete = true)
        }
    }
}
