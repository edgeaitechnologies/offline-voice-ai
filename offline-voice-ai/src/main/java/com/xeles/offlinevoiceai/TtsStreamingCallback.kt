package com.xeles.offlinevoiceai

/**
 * Callback interface for streaming TTS (Text-to-Speech) operations.
 *
 * Used with [VoiceAIManager.speakStreamed] and the session-based streaming
 * API ([VoiceAIManager.beginStreaming] / [VoiceAIManager.streamText] /
 * [VoiceAIManager.endStreaming]).
 *
 * All callbacks are dispatched on the **main thread**, so you can safely
 * update UI directly from within any callback method.
 */
interface TtsStreamingCallback {

    /**
     * Called when the streaming playback pipeline has been opened and
     * audio output is about to begin.
     */
    fun onStreamingStarted()

    /**
     * Called each time a sentence has been synthesized and played through
     * the speaker.
     *
     * In session-based streaming, this fires once per detected sentence.
     * In [VoiceAIManager.speakStreamed], this fires once for the full text.
     *
     * @param sentence The sentence that was just spoken.
     */
    fun onSentenceSynthesized(sentence: String)

    /**
     * Called when all queued text has been fully synthesized and played.
     */
    fun onStreamingComplete()

    /**
     * Called when an error occurs during streaming synthesis or playback.
     *
     * @param error The exception describing what went wrong.
     */
    fun onStreamingError(error: Throwable)
}
