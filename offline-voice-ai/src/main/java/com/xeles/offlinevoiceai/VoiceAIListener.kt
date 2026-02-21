package com.xeles.offlinevoiceai

/**
 * Callback interface for receiving speech recognition events
 * and error notifications from [VoiceAIManager].
 */
interface VoiceAIListener {

    /**
     * Called when a speech segment has been recognized.
     *
     * For partial results during active listening, this may be called
     * multiple times with progressively more complete text.
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
}
