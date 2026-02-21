package com.xeles.offlinevoiceai

import android.content.Context
import android.util.Log
import com.xeles.offlinevoiceai.engine.SttEngine
import com.xeles.offlinevoiceai.engine.TtsEngine
import com.xeles.offlinevoiceai.util.AssetExtractor

/**
 * **VoiceAIManager** — the single entry point for the OfflineVoiceAI library.
 *
 * Provides a clean, coroutine-friendly API for offline Speech-to-Text (STT)
 * and Text-to-Speech (TTS) on Android.
 *
 * ## Quick Start
 * ```kotlin
 * val manager = VoiceAIManager(applicationContext)
 *
 * // In a coroutine:
 * val ready = manager.initialize(
 *     sttModelAssetName = "vosk-model-small-en-us",
 *     ttsModelAssetName = "vits-piper-en_US-amy-low"
 * )
 *
 * if (ready) {
 *     manager.startListening(object : VoiceAIListener {
 *         override fun onSpeechRecognized(text: String) { /* ... */ }
 *         override fun onListeningStateChanged(isListening: Boolean) { /* ... */ }
 *         override fun onError(error: Throwable) { /* ... */ }
 *     })
 * }
 * ```
 *
 * **Important**: Call [destroy] in your Activity/Fragment's `onDestroy()` to
 * release native C++ pointers and prevent memory leaks.
 *
 * @param context Application context (will be retained for asset extraction only).
 */
class VoiceAIManager(private val context: Context) {

    companion object {
        private const val TAG = "VoiceAIManager"
    }

    private val sttEngine = SttEngine()
    private val ttsEngine = TtsEngine()
    private var isInitialized = false

    /**
     * Initialize both STT and TTS engines by extracting model assets
     * from the app's `assets/` folder and loading them.
     *
     * This is a **suspend** function — call it from a coroutine
     * (e.g., `lifecycleScope.launch`).
     *
     * @param sttModelAssetName Name of the Vosk model folder inside `assets/`
     *                          (e.g., `"vosk-model-small-en-us"`).
     * @param ttsModelAssetName Name of the Sherpa TTS model folder inside `assets/`
     *                          (e.g., `"vits-piper-en_US-amy-low"`).
     * @return `true` if both engines initialized successfully.
     */
    suspend fun initialize(
        sttModelAssetName: String,
        ttsModelAssetName: String
    ): Boolean {
        Log.d(TAG, "Initializing VoiceAI — extracting models...")

        // Extract STT model
        val sttPath = AssetExtractor.extract(context, sttModelAssetName)
        if (sttPath == null) {
            Log.e(TAG, "Failed to extract STT model: $sttModelAssetName")
            return false
        }

        // Extract TTS model
        val ttsPath = AssetExtractor.extract(context, ttsModelAssetName)
        if (ttsPath == null) {
            Log.e(TAG, "Failed to extract TTS model: $ttsModelAssetName")
            return false
        }

        // Initialize engines
        val sttOk = sttEngine.initialize(sttPath)
        val ttsOk = ttsEngine.initialize(ttsPath)

        isInitialized = sttOk && ttsOk
        Log.d(TAG, "Initialization complete — STT: $sttOk, TTS: $ttsOk")
        return isInitialized
    }

    // ─── STT Controls ────────────────────────────────────────────────

    /**
     * Start listening for speech through the device microphone.
     *
     * The consuming app **must** have already obtained
     * `Manifest.permission.RECORD_AUDIO` before calling this method.
     * If the permission is missing, [VoiceAIListener.onError] will be
     * called with a [SecurityException].
     *
     * @param listener Callback to receive recognized text and state changes.
     * @param config   Optional [SttListeningConfig] to control silence timeout
     *                 and auto-stop behaviour. Defaults to 2 s silence auto-stop.
     * @throws IllegalStateException if [initialize] has not been called.
     */
    fun startListening(
        listener: VoiceAIListener,
        config: SttListeningConfig = SttListeningConfig()
    ) {
        requireInitialized()
        sttEngine.startListening(listener, config)
    }

    /**
     * Stop listening. Safe to call even if not currently listening.
     */
    fun stopListening() {
        sttEngine.stopListening()
    }

    // ─── TTS Controls ────────────────────────────────────────────────

    /**
     * Synthesize speech from the given text and play it through the speaker.
     *
     * @param text      The text to speak.
     * @param speed     Speech speed multiplier (default `1.0`).
     * @param speakerId Speaker ID for multi-speaker models (default `0`).
     * @param listener  Optional [TtsSpeakingListener] to receive utterance
     *                  progress callbacks (`onStart`, `onDone`, `onError`).
     *                  Callbacks are delivered on the **main thread**.
     */
    fun speak(
        text: String,
        speed: Float = 1.0f,
        speakerId: Int = 0,
        listener: TtsSpeakingListener? = null
    ) {
        requireInitialized()
        ttsEngine.speak(text, speed, speakerId, listener)
    }

    /**
     * Stop any active TTS playback.
     */
    fun stopSpeaking() {
        ttsEngine.stopSpeaking()
    }

    // ─── Lifecycle ───────────────────────────────────────────────────

    /**
     * Release all native resources held by both engines.
     *
     * **Must** be called in `onDestroy()` to prevent memory leaks.
     * After this call, the manager cannot be reused — create a new instance.
     */
    fun destroy() {
        Log.d(TAG, "Destroying VoiceAIManager")
        sttEngine.release()
        ttsEngine.release()
        isInitialized = false
    }

    // ─── Internal ────────────────────────────────────────────────────

    private fun requireInitialized() {
        check(isInitialized) {
            "VoiceAIManager is not initialized. Call initialize() first."
        }
    }
}
