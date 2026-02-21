package com.xeles.offlinevoiceai.engine

import android.content.res.AssetManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Wrapper around the Sherpa-ONNX offline TTS engine.
 *
 * Converts text to audio using a VITS model and plays it through [AudioTrack].
 */
internal class TtsEngine {

    companion object {
        private const val TAG = "TtsEngine"
    }

    private var tts: OfflineTts? = null
    private var audioTrack: AudioTrack? = null
    private var speakingJob: Job? = null
    private var sampleRate: Int = 22050
    private val scope = CoroutineScope(Dispatchers.IO)

    val isSpeaking: Boolean
        get() = speakingJob?.isActive == true

    /**
     * Initialize the Sherpa-ONNX TTS engine.
     *
     * @param assetManager The app's AssetManager (required by Sherpa-ONNX native layer).
     * @param modelDir     Absolute path to the directory containing the VITS model files
     *                     (model.onnx, tokens.txt, and optionally espeak-ng-data/, lexicon.txt).
     * @return `true` if initialization succeeded.
     */
    fun initialize(assetManager: AssetManager, modelDir: String): Boolean {
        return try {
            val vitsConfig = OfflineTtsVitsModelConfig()
            vitsConfig.model = "$modelDir/en_US-amy-low.onnx"
            vitsConfig.tokens = "$modelDir/tokens.txt"
            vitsConfig.dataDir = "$modelDir/espeak-ng-data"
            vitsConfig.lexicon = ""

            val modelConfig = OfflineTtsModelConfig()
            modelConfig.vits = vitsConfig

            val ttsConfig = OfflineTtsConfig()
            ttsConfig.model = modelConfig

            tts = OfflineTts(assetManager, ttsConfig)
            sampleRate = tts!!.sampleRate()

            Log.d(TAG, "Sherpa-ONNX TTS initialized. Sample rate: $sampleRate")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Sherpa-ONNX TTS", e)
            false
        }
    }

    /**
     * Convert the given text to speech and play it through the device speaker.
     *
     * This method runs asynchronously. Call [stopSpeaking] to interrupt playback.
     *
     * @param text      The text string to synthesize.
     * @param speed     Speech speed multiplier (default 1.0).
     * @param speakerId Speaker ID for multi-speaker models (default 0).
     */
    fun speak(text: String, speed: Float = 1.0f, speakerId: Int = 0) {
        if (tts == null) {
            Log.e(TAG, "TTS engine not initialized. Call initialize() first.")
            return
        }

        // Stop any current playback
        stopSpeaking()

        speakingJob = scope.launch {
            try {
                // Generate audio samples
                val audio = tts!!.generate(
                    text = text,
                    sid = speakerId,
                    speed = speed,
                )

                if (!isActive) return@launch

                val samples = audio.samples

                // Create AudioTrack for playback
                val bufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_FLOAT
                ).coerceAtLeast(samples.size * 4)

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack = track

                track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                track.play()

                // Wait for playback to finish
                val durationMs = (samples.size.toLong() * 1000L) / sampleRate
                kotlinx.coroutines.delay(durationMs + 200) // small buffer

                track.stop()
                track.release()
                audioTrack = null

                Log.d(TAG, "TTS playback complete for text: '${text.take(50)}...'")
            } catch (e: Exception) {
                Log.e(TAG, "Error during TTS playback", e)
            }
        }
    }

    /**
     * Stop any active TTS playback.
     */
    fun stopSpeaking() {
        speakingJob?.cancel()
        speakingJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
    }

    /**
     * Release all native resources.
     */
    fun release() {
        stopSpeaking()
        tts = null
    }
}
