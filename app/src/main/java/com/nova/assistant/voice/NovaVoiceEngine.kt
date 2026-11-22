package com.nova.assistant.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.nova.assistant.model.NovaOrbState
import com.nova.assistant.model.VoiceEmotion
import com.nova.assistant.model.VoiceEngineType
import com.nova.assistant.security.ApiKeyVault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

class NovaVoiceEngine(
    private val context: Context,
    private val vault: ApiKeyVault,
    private val onStateChanged: (NovaOrbState) -> Unit,
    private val onAudioLevel: (Float) -> Unit,
    private val onWakeWordDetected: () -> Unit,
    private val onCommandRecognized: (String) -> Unit,
    private val onEngineError: (String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private var speechRecognizer: SpeechRecognizer? = null
    private var mediaPlayer: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var isDestroyed = false
    private var isWakeWordLoopActive = false
    private var followUpListenAfterSpeech = false
    private var isListeningNow = false
    private var currentMode: NovaOrbState = NovaOrbState.IDLE

    private var bestHindiVoice: Voice? = null
    private var bestEnglishVoice: Voice? = null
    private var finalUtteranceId: String = ""

    private val wakeVariants = listOf(
        "hello nova", "halo nova", "hey nova", "helo nova",
        "hello noah", "halo noah", "hi nova", "ok nova",
        "nova", "नोवा", "हेलो नोवा", "हे नोवा"
    )

    private var focusRequest: AudioFocusRequest? = null

    init {
        initOfflineTts()
        mainHandler.post { ensureRecognizerInitialized() }
    }

    private fun initOfflineTts() {
        tts = TextToSpeech(context, { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
                selectMostRealisticOfflineVoices()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        mainHandler.post { onStateChanged(NovaOrbState.SPEAKING) }
                    }

                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == finalUtteranceId) {
                            onSpeechFinished()
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        onSpeechFinished()
                    }
                })
            }
        }, "com.google.android.tts")
    }

    private fun onSpeechFinished() {
        mainHandler.post {
            abandonAudioFocus()
            if (isDestroyed) return@post
            if (followUpListenAfterSpeech) {
                followUpListenAfterSpeech = false
                startListeningInternal(NovaOrbState.LISTENING_COMMAND)
            } else if (isWakeWordLoopActive) {
                startWakeWordListening()
            } else {
                onStateChanged(NovaOrbState.IDLE)
            }
        }
    }

    private fun ensureRecognizerInitialized() {
        if (isDestroyed) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onEngineError("Speech recognition service unavailable on device.")
            return
        }
        if (speechRecognizer != null) return

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListeningNow = true
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {
                    val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                    onAudioLevel(normalized)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    isListeningNow = false
                    onAudioLevel(0f)
                }

                override fun onError(error: Int) {
                    isListeningNow = false
                    onAudioLevel(0f)
                    if (isDestroyed) return

                    if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT) {
                        runCatching { speechRecognizer?.destroy() }
                        speechRecognizer = null
                        mainHandler.postDelayed({
                            if (!isDestroyed) {
                                ensureRecognizerInitialized()
                                if (currentMode == NovaOrbState.LISTENING_COMMAND) {
                                    startListeningInternal(NovaOrbState.LISTENING_COMMAND)
                                } else if (isWakeWordLoopActive) {
                                    startListeningInternal(NovaOrbState.LISTENING_WAKE_WORD)
                                }
                            }
                        }, 650L)
                        return
                    }

                    if (currentMode == NovaOrbState.LISTENING_WAKE_WORD && isWakeWordLoopActive) {
                        mainHandler.postDelayed({
                            if (isWakeWordLoopActive && !isDestroyed) {
                                startListeningInternal(NovaOrbState.LISTENING_WAKE_WORD)
                            }
                        }, 250L)
                    } else if (currentMode == NovaOrbState.LISTENING_COMMAND) {
                        if (isWakeWordLoopActive) {
                            mainHandler.postDelayed({ startWakeWordListening() }, 300L)
                        } else {
                            onStateChanged(NovaOrbState.IDLE)
                        }
                    }
                }

                override fun onResults(results: Bundle?) {
                    isListeningNow = false
                    onAudioLevel(0f)
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    val best = matches.maxByOrNull { it.length }.orEmpty().trim()
                    handleRecognitionResult(best, matches)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    if (currentMode == NovaOrbState.LISTENING_WAKE_WORD) {
                        val partials = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                        val hit = partials.firstOrNull { containsWakeWord(it) }
                        if (hit != null && stripWakeWord(hit).isEmpty()) {
                            runCatching { speechRecognizer?.cancel() }
                            isListeningNow = false
                            playActivationFeedback()
                            onWakeWordDetected()
                            mainHandler.postDelayed({
                                startListeningInternal(NovaOrbState.LISTENING_COMMAND)
                            }, 180L)
                        }
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun selectMostRealisticOfflineVoices() {
        val engine = tts ?: return
        val voices = runCatching { engine.voices }.getOrNull().orEmpty()
        if (voices.isEmpty()) return

        fun scoreVoice(v: Voice, targetLang: String): Int {
            if (!v.locale.language.equals(targetLang, ignoreCase = true)) return -1
            var score = v.quality
            val name = v.name.lowercase(Locale.ROOT)
            if (v.locale.country.equals("IN", ignoreCase = true)) score += 130
            if (name.contains("neural") || name.contains("wavenet") || name.contains("studio") || name.contains("natural")) score += 220
            if (name.contains("multi") || name.contains("hd") || name.contains("hq")) score += 110
            if (!v.isNetworkConnectionRequired) score += 25
            return score
        }

        bestHindiVoice = voices
            .filter { it.locale.language.equals("hi", ignoreCase = true) }
            .maxByOrNull { scoreVoice(it, "hi") }

        bestEnglishVoice = voices
            .filter { it.locale.language.equals("en", ignoreCase = true) }
            .maxByOrNull { scoreVoice(it, "en") }

        bestEnglishVoice?.let { engine.voice = it } ?: run {
            engine.language = Locale("en", "IN")
        }
    }

    fun startWakeWordListening() {
        if (isDestroyed) return
        isWakeWordLoopActive = true
        mainHandler.post {
            startListeningInternal(NovaOrbState.LISTENING_WAKE_WORD)
        }
    }

    fun startDirectCommandListening() {
        if (isDestroyed) return
        mainHandler.post {
            stopCurrentAudioPlayback()
            if (isListeningNow) {
                runCatching { speechRecognizer?.cancel() }
                isListeningNow = false
            }
            playActivationFeedback()
            mainHandler.postDelayed({
                startListeningInternal(NovaOrbState.LISTENING_COMMAND)
            }, 150L)
        }
    }

    fun stopAllListening() {
        isWakeWordLoopActive = false
        isListeningNow = false
        mainHandler.post {
            stopCurrentAudioPlayback()
            runCatching { speechRecognizer?.cancel() }
            currentMode = NovaOrbState.IDLE
            onStateChanged(NovaOrbState.IDLE)
            onAudioLevel(0f)
        }
    }

    private fun stopCurrentAudioPlayback() {
        runCatching {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        }
        mediaPlayer = null
        runCatching { tts?.stop() }
    }

    fun speak(text: String, emotion: VoiceEmotion = VoiceEmotion.NEUTRAL, autoFollowUpListen: Boolean = false) {
        followUpListenAfterSpeech = autoFollowUpListen
        if (text.isBlank()) {
            onSpeechFinished()
            return
        }
        val cleanText = formatForNaturalSpeech(text)
        val engineType = vault.getVoiceEngineType()

        mainHandler.post {
            runCatching { speechRecognizer?.cancel() }
            isListeningNow = false
            stopCurrentAudioPlayback()
            requestAudioFocus()
            onStateChanged(NovaOrbState.SPEAKING)
        }

        if (engineType == VoiceEngineType.ANDROID_TTS) {
            speakWithOfflineTts(cleanText, emotion)
            return
        }

        scope.launch {
            val audioBytes: ByteArray? = when (engineType) {
                VoiceEngineType.ELEVENLABS -> fetchElevenLabsAudio(cleanText, emotion)
                VoiceEngineType.GROQ_NEURAL -> fetchGroqNeuralAudio(cleanText)
                VoiceEngineType.CUSTOM_HTTP -> fetchCustomHttpAudio(cleanText, emotion)
                VoiceEngineType.ANDROID_TTS -> null
            }

            if (audioBytes != null && audioBytes.isNotEmpty()) {
                playAudioBytes(audioBytes, cleanText, emotion)
            } else {
                // Fallback seamlessly to offline TTS if cloud voice key missing or offline
                speakWithOfflineTts(cleanText, emotion)
            }
        }
    }

    private fun fetchElevenLabsAudio(text: String, emotion: VoiceEmotion): ByteArray? {
        val apiKey = vault.getElevenLabsKey().trim()
        if (apiKey.isBlank()) return null
        val voiceId = vault.getVoiceId().trim().ifBlank { "21m00Tcm4TlvDq8ikWAM" }

        val stability = when (emotion) {
            VoiceEmotion.EXCITED, VoiceEmotion.HAPPY, VoiceEmotion.PLAYFUL -> 0.35
            VoiceEmotion.EMPATHETIC, VoiceEmotion.CALM -> 0.55
            else -> 0.45
        }
        val styleBoost = when (emotion) {
            VoiceEmotion.EXCITED, VoiceEmotion.PLAYFUL -> 0.65
            VoiceEmotion.HAPPY, VoiceEmotion.EMPATHETIC -> 0.45
            else -> 0.25
        }

        val payload = JSONObject().apply {
            put("text", text)
            put("model_id", "eleven_multilingual_v2")
            put("voice_settings", JSONObject().apply {
                put("stability", stability)
                put("similarity_boost", 0.80)
                put("style", styleBoost)
                put("use_speaker_boost", true)
            })
        }

        val req = Request.Builder()
            .url("https://api.elevenlabs.io/v1/text-to-speech/$voiceId")
            .header("xi-api-key", apiKey)
            .header("Accept", "audio/mpeg")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        return runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w("NovaVoiceEngine", "ElevenLabs HTTP ${resp.code}")
                    null
                } else {
                    resp.body?.bytes()
                }
            }
        }.getOrNull()
    }

    private fun fetchGroqNeuralAudio(text: String): ByteArray? {
        val groqKey = vault.getGroqKey().trim()
        if (groqKey.isBlank()) return null

        val selectedVoice = vault.getVoiceId().trim().takeIf {
            it in listOf("Fritz-PlayAI", "Celeste-PlayAI", "Cheyenne-PlayAI", "Mason-PlayAI", "Quinn-PlayAI", "Thunder-PlayAI")
        } ?: "Celeste-PlayAI"

        val payload = JSONObject().apply {
            put("model", "playai-tts")
            put("input", text)
            put("voice", selectedVoice)
            put("response_format", "mp3")
        }

        val req = Request.Builder()
            .url("https://api.groq.com/openai/v1/audio/speech")
            .header("Authorization", "Bearer $groqKey")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        return runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w("NovaVoiceEngine", "Groq TTS HTTP ${resp.code}")
                    null
                } else {
                    resp.body?.bytes()
                }
            }
        }.getOrNull()
    }

    private fun fetchCustomHttpAudio(text: String, emotion: VoiceEmotion): ByteArray? {
        val endpoint = vault.getCustomVoiceUrl().trim()
        if (endpoint.isBlank()) return null

        val payload = JSONObject().apply {
            put("text", text)
            put("emotion", emotion.tag)
            put("voice_id", vault.getVoiceId().trim())
        }

        val req = Request.Builder()
            .url(endpoint)
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        return runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.bytes()
            }
        }.getOrNull()
    }

    private suspend fun playAudioBytes(bytes: ByteArray, fallbackText: String, emotion: VoiceEmotion) = withContext(Dispatchers.Main) {
        if (isDestroyed) return@withContext
        runCatching {
            val tempFile = File.createTempFile("nova_voice_", ".mp3", context.cacheDir)
            tempFile.writeBytes(bytes)

            stopCurrentAudioPlayback()
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(tempFile.absolutePath)
                setOnCompletionListener { mp ->
                    runCatching { mp.release() }
                    mediaPlayer = null
                    runCatching { tempFile.delete() }
                    onSpeechFinished()
                }
                setOnErrorListener { mp, _, _ ->
                    runCatching { mp.release() }
                    mediaPlayer = null
                    runCatching { tempFile.delete() }
                    speakWithOfflineTts(fallbackText, emotion)
                    true
                }
                prepare()
                start()
            }
            mediaPlayer = player
            onStateChanged(NovaOrbState.SPEAKING)
        }.onFailure {
            speakWithOfflineTts(fallbackText, emotion)
        }
    }

    private fun speakWithOfflineTts(cleanText: String, emotion: VoiceEmotion) {
        if (!isTtsReady) {
            onSpeechFinished()
            return
        }
        mainHandler.post {
            val containsDevanagari = cleanText.any { it in '\u0900'..'\u097F' }
            val hasHinglishMarkers = listOf(
                "karo", "kar diya", "hai", "hain", "hoon", "ho gaya", "aapka", "baje", "gaana", "chal", "khol", "bilkul", "abhi"
            ).any { cleanText.lowercase(Locale.ROOT).contains(it) }

            if (containsDevanagari || hasHinglishMarkers) {
                bestHindiVoice?.let { tts?.voice = it } ?: run { tts?.language = Locale("hi", "IN") }
            } else {
                bestEnglishVoice?.let { tts?.voice = it } ?: run { tts?.language = Locale("en", "IN") }
            }

            val (pitch, rate) = when (emotion) {
                VoiceEmotion.HAPPY -> 1.05f to 1.05f
                VoiceEmotion.EXCITED -> 1.10f to 1.10f
                VoiceEmotion.CALM -> 0.90f to 0.94f
                VoiceEmotion.EMPATHETIC -> 0.92f to 0.92f
                VoiceEmotion.SERIOUS -> 0.86f to 0.97f
                VoiceEmotion.PLAYFUL -> 1.07f to 1.07f
                VoiceEmotion.NEUTRAL -> 0.96f to 1.02f
            }
            tts?.setPitch(pitch)
            tts?.setSpeechRate(rate)

            val id = "nova_utt_${System.currentTimeMillis()}"
            finalUtteranceId = id
            tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, id)
        }
    }

    private fun formatForNaturalSpeech(raw: String): String {
        return raw
            .replace("N.O.V.A.", "Nova")
            .replace("NOVA", "Nova")
            .replace("AI", "A.I.")
            .replace("Wi-Fi", "WiFi")
            .replace("•", ",")
            .replace("⚡", "")
            .replace("...", ",")
            .trim()
    }

    private fun startListeningInternal(mode: NovaOrbState) {
        if (isDestroyed) return
        ensureRecognizerInitialized()
        val recognizer = speechRecognizer ?: return

        if (isListeningNow) {
            runCatching { recognizer.cancel() }
            isListeningNow = false
        }
        currentMode = mode
        onStateChanged(mode)

        val completeSilenceMs = if (mode == NovaOrbState.LISTENING_WAKE_WORD) 1500L else 5000L
        val possibleSilenceMs = if (mode == NovaOrbState.LISTENING_WAKE_WORD) 1300L else 4500L
        val minLengthMs = if (mode == NovaOrbState.LISTENING_WAKE_WORD) 1500L else 6000L
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, completeSilenceMs)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, possibleSilenceMs)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, minLengthMs)
        }
        runCatching { recognizer.startListening(intent) }
            .onFailure { Log.e("NovaVoiceEngine", "Failed to start listening", it) }
    }

    private fun handleRecognitionResult(best: String, allMatches: List<String>) {
        if (currentMode == NovaOrbState.LISTENING_WAKE_WORD) {
            val matched = allMatches.firstOrNull { containsWakeWord(it) }
            if (matched != null) {
                playActivationFeedback()
                onWakeWordDetected()
                val trailingCommand = stripWakeWord(matched)
                if (trailingCommand.length >= 2) {
                    onCommandRecognized(trailingCommand)
                } else {
                    startListeningInternal(NovaOrbState.LISTENING_COMMAND)
                }
            } else if (isWakeWordLoopActive) {
                mainHandler.postDelayed({ startWakeWordListening() }, 180L)
            }
        } else if (currentMode == NovaOrbState.LISTENING_COMMAND) {
            if (best.isNotBlank()) {
                onCommandRecognized(best)
            } else if (isWakeWordLoopActive) {
                startWakeWordListening()
            } else {
                onStateChanged(NovaOrbState.IDLE)
            }
        }
    }

    private fun containsWakeWord(input: String): Boolean {
        val lower = input.lowercase(Locale.ROOT)
        return wakeVariants.any { lower.contains(it) }
    }

    private fun stripWakeWord(input: String): String {
        var result = input.lowercase(Locale.ROOT)
        wakeVariants.forEach { variant ->
            result = result.replace(variant, "")
        }
        return result.trim()
    }

    private fun playActivationFeedback() {
        runCatching {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 140)
            mainHandler.postDelayed({ runCatching { tg.release() } }, 200L)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                v.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
    }

    private fun requestAudioFocus() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attrs)
            .build()
        focusRequest = req
        audioManager.requestAudioFocus(req)
    }

    private fun abandonAudioFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
    }

    fun destroy() {
        isDestroyed = true
        isWakeWordLoopActive = false
        isListeningNow = false
        scope.cancel()
        mainHandler.post {
            stopCurrentAudioPlayback()
            runCatching { speechRecognizer?.destroy() }
            speechRecognizer = null
            runCatching {
                tts?.stop()
                tts?.shutdown()
            }
        }
    }
}
