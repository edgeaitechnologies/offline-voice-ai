package com.xeles.offlinevoiceai

/**
 * Callback interface for receiving speech recognition events
 * and error notifications from [VoiceAIManager].
 *
 * All new callbacks have default no-op implementations so existing
 * code that only overrides [onSpeechRecognized], [onListeningStateChanged],
 * and [onError] will continue to compile without changes.
 */
interface VoiceAIListener {

    /**
     * Called when a speech segment has been recognized (partial **or** final).
     *
     * This is the original catch-all callback — it fires for every
     * recognition event. If you only need to react to confirmed results,
     * override [onFinalResult] instead.
     *
     * @param text The recognized text string.
     */
    fun onSpeechRecognized(text: String)

    /**
     * Called when the listening state changes.
     *
     * @param isListening `true` when the microphone is actively recording
     *                    and feeding audio to the recognizer; `false` when stopped.
     */
    fun onListeningStateChanged(isListening: Boolean)

    /**
     * Called when an error occurs during STT or TTS operations.
     *
     * @param error The exception describing what went wrong.
     */
    fun onError(error: Throwable)

    // ─── New callbacks (default no-op for backward compatibility) ─────

    /**
     * Called with real-time **partial** recognition text while the user
     * is still speaking. The text may change as the recognizer refines
     * its hypothesis.
     *
     * @param text The current partial hypothesis.
     */
    fun onPartialResult(text: String) {}

    /**
     * Called when the recognizer confirms a **final** result for an
     * utterance. This text will not change further.
     *
     * @param text The confirmed recognition result.
     */
    fun onFinalResult(text: String) {}

    /**
     * Called when silence is first detected after speech.
     *
     * If [SttListeningConfig.autoStopOnSilence] is enabled, the silence
     * timeout countdown starts at this point.
     */
    fun onSilenceDetected() {}

    /**
     * Called when the engine **automatically stops** listening because
     * silence lasted longer than [SttListeningConfig.silenceTimeoutMs].
     *
     * After this callback, the microphone is released and
     * [onListeningStateChanged] is called with `false`.
     */
    fun onAutoStopped() {}
}
