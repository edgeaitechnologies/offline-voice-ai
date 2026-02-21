package com.xeles.offlinevoiceai

/**
 * Callback interface for tracking TTS (Text-to-Speech) utterance progress.
 *
 * Modeled after Android's `UtteranceProgressListener`, this interface lets
 * consuming apps observe the lifecycle of each [VoiceAIManager.speak] call.
 *
 * All callbacks are dispatched on the **main thread**, so you can safely
 * update UI directly from within any callback method.
 */
interface TtsSpeakingListener {

    /**
     * Called when the TTS engine begins playing audio for the given text.
     *
     * @param utteranceText The text that is being spoken.
     */
    fun onStart(utteranceText: String)

    /**
     * Called when the TTS engine has finished playing audio for the given text.
     *
     * @param utteranceText The text that was spoken.
     */
    fun onDone(utteranceText: String)

    /**
     * Called when an error occurs during speech synthesis or playback.
     *
     * @param utteranceText The text that was being spoken when the error occurred.
     * @param error         The exception describing what went wrong.
     */
    fun onError(utteranceText: String, error: Throwable)
}
