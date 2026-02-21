package com.xeles.offlinevoiceai

/**
 * Configuration for STT (Speech-to-Text) listening behaviour.
 *
 * @param silenceTimeoutMs  Duration of silence (in milliseconds) after which the
 *                          engine automatically stops listening. Default is **2 000 ms**.
 * @param autoStopOnSilence Whether the engine should auto-stop when no speech is
 *                          detected for [silenceTimeoutMs]. Set to `false` to keep
 *                          the microphone open indefinitely (manual stop only).
 */
data class SttListeningConfig(
    val silenceTimeoutMs: Long = 2000L,
    val autoStopOnSilence: Boolean = true
)
