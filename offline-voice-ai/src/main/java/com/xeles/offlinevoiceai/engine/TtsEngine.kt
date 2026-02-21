package com.xeles.offlinevoiceai.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.xeles.offlinevoiceai.TtsSpeakingListener
import com.xeles.offlinevoiceai.TtsStreamingCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    // ── Streaming session state ─────────────────────────────────────
    private var sentenceChannel: Channel<String>? = null
    private val textBuffer = StringBuilder()
    private var streamingCallback: TtsStreamingCallback? = null
    private var streamingSpeed: Float = 1.0f
    private var streamingSpeakerId: Int = 0

    /** Sentence-ending characters used to split streamed text. */
    private val sentenceDelimiters = charArrayOf('.', '!', '?', '\n')

    /**
     * Named callback class for [OfflineTts.generateWithCallback].
     *
     * D8 (the Android DEX compiler) desugars all lambdas and anonymous
     * functions into `ExternalSyntheticLambda` classes whose `invoke()`
     * method is invisible to JNI reflection.  sherpa-onnx's native code
     * calls `invoke([F)Ljava/lang/Integer;` via JNI, so we **must** use
     * a named class that D8 will not touch.
     */
    private class AudioChunkCallback(
        private val track: AudioTrack,
        private val activeCheck: () -> Boolean
    ) : (FloatArray) -> Int {
        override fun invoke(samples: FloatArray): Int {
            if (!activeCheck()) return 1
            track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            return 0
        }
    }

    val isSpeaking: Boolean
        get() = speakingJob?.isActive == true

    /**
     * Initialize the Sherpa-ONNX TTS engine.
     *
     * @param modelDir Absolute path to the directory containing the VITS model files
     *                 (model.onnx, tokens.txt, and optionally espeak-ng-data/, lexicon.txt).
     * @return `true` if initialization succeeded.
     */
    fun initialize(modelDir: String): Boolean {
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
            ttsConfig.maxNumSentences = 200

            tts = OfflineTts(null, ttsConfig)
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
     * @param listener  Optional callback to receive utterance progress events.
     */
    fun speak(text: String, speed: Float = 1.0f, speakerId: Int = 0, listener: TtsSpeakingListener? = null) {
        if (tts == null) {
            Log.e(TAG, "TTS engine not initialized. Call initialize() first.")
            listener?.let {
                CoroutineScope(Dispatchers.Main).launch {
                    it.onError(text, IllegalStateException("TTS engine not initialized. Call initialize() first."))
                }
            }
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

                // Notify listener that playback has started
                listener?.let { l ->
                    withContext(Dispatchers.Main) { l.onStart(text) }
                }

                // Wait for playback to finish
                val durationMs = (samples.size.toLong() * 1000L) / sampleRate
                kotlinx.coroutines.delay(durationMs + 200) // small buffer

                track.stop()
                track.release()
                audioTrack = null

                Log.d(TAG, "TTS playback complete for text: '${text.take(50)}...'")

                // Notify listener that playback is done
                listener?.let { l ->
                    withContext(Dispatchers.Main) { l.onDone(text) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during TTS playback", e)
                listener?.let { l ->
                    withContext(Dispatchers.Main) { l.onError(text, e) }
                }
            }
        }
    }

    // ─── Streamed speak (full text, chunked audio) ────────────────

    /**
     * Synthesize [text] and play audio **as chunks are generated**,
     * rather than waiting for the full synthesis to complete.
     *
     * The text is split into sentences internally. Each sentence is
     * synthesized individually using `generateWithCallback`, so the
     * first sentence starts playing almost immediately while subsequent
     * sentences are still being generated.
     *
     * Uses [AudioTrack.MODE_STREAM] for gapless playback across sentences.
     *
     * @param text      The text string to synthesize.
     * @param speed     Speech speed multiplier (default 1.0).
     * @param speakerId Speaker ID for multi-speaker models (default 0).
     * @param callback  Optional [TtsStreamingCallback] to receive progress events.
     */
    fun speakStreamed(
        text: String,
        speed: Float = 1.0f,
        speakerId: Int = 0,
        callback: TtsStreamingCallback? = null
    ) {
        if (tts == null) {
            Log.e(TAG, "TTS engine not initialized. Call initialize() first.")
            callback?.let {
                CoroutineScope(Dispatchers.Main).launch {
                    it.onStreamingError(IllegalStateException("TTS engine not initialized. Call initialize() first."))
                }
            }
            return
        }

        stopSpeaking()

        speakingJob = scope.launch {
            try {
                // Create a single AudioTrack in STREAM mode, reused across
                // all sentences for gapless playback.
                val minBuf = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_FLOAT
                )

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
                    .setBufferSizeInBytes(minBuf * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack = track
                track.play()

                withContext(Dispatchers.Main) { callback?.onStreamingStarted() }

                // Split text into sentences so each one is synthesized and
                // played independently — this gives low time-to-first-audio.
                val sentences = splitIntoSentences(text)
                val chunkCallback = AudioChunkCallback(track) { isActive }

                for (sentence in sentences) {
                    if (!isActive) break

                    tts!!.generateWithCallback(
                        text = sentence,
                        sid = speakerId,
                        speed = speed,
                        callback = chunkCallback
                    )

                    withContext(Dispatchers.Main) {
                        callback?.onSentenceSynthesized(sentence)
                    }
                }

                // Wait for the AudioTrack buffer to drain
                if (isActive) {
                    kotlinx.coroutines.delay(200)
                }

                track.stop()
                track.release()
                audioTrack = null

                Log.d(TAG, "Streamed TTS playback complete for: '${text.take(50)}...'")

                withContext(Dispatchers.Main) { callback?.onStreamingComplete() }
            } catch (e: Exception) {
                Log.e(TAG, "Error during streamed TTS playback", e)
                withContext(Dispatchers.Main) { callback?.onStreamingError(e) }
            }
        }
    }

    /**
     * Split text into sentences using delimiter characters.
     * Returns a list of non-empty trimmed sentences.
     */
    private fun splitIntoSentences(text: String): List<String> {
        val sentences = mutableListOf<String>()
        val buffer = StringBuilder()

        for (ch in text) {
            buffer.append(ch)
            if (ch in sentenceDelimiters) {
                val sentence = buffer.toString().trim()
                if (sentence.isNotEmpty()) {
                    sentences.add(sentence)
                }
                buffer.clear()
            }
        }

        // Flush any remaining text that didn't end with a delimiter
        val remaining = buffer.toString().trim()
        if (remaining.isNotEmpty()) {
            sentences.add(remaining)
        }

        return sentences
    }

    // ─── Session-based streaming (incremental text) ──────────────

    /**
     * Open a streaming session for feeding text incrementally.
     *
     * After calling this, use [streamText] to feed text chunks
     * (e.g. tokens from an LLM) and [endStreaming] to signal completion.
     *
     * Internally, text is buffered and sentences are detected by
     * delimiter characters (`.` `!` `?` `\n`). Each complete sentence
     * is synthesized and played sequentially.
     *
     * @param speed     Speech speed multiplier (default 1.0).
     * @param speakerId Speaker ID for multi-speaker models (default 0).
     * @param callback  Optional [TtsStreamingCallback] to receive progress events.
     */
    fun beginStreaming(
        speed: Float = 1.0f,
        speakerId: Int = 0,
        callback: TtsStreamingCallback? = null
    ) {
        if (tts == null) {
            Log.e(TAG, "TTS engine not initialized. Call initialize() first.")
            callback?.let {
                CoroutineScope(Dispatchers.Main).launch {
                    it.onStreamingError(IllegalStateException("TTS engine not initialized. Call initialize() first."))
                }
            }
            return
        }

        stopSpeaking()

        streamingCallback = callback
        streamingSpeed = speed
        streamingSpeakerId = speakerId
        textBuffer.clear()

        // Unbounded channel — sentences are produced by feedText, consumed by the job
        val channel = Channel<String>(Channel.UNLIMITED)
        sentenceChannel = channel

        speakingJob = scope.launch {
            try {
                withContext(Dispatchers.Main) { callback?.onStreamingStarted() }

                for (sentence in channel) {
                    if (!isActive) break
                    synthesizeAndPlay(sentence, speed, speakerId, callback)
                }

                if (isActive) {
                    Log.d(TAG, "Streaming session complete")
                    withContext(Dispatchers.Main) { callback?.onStreamingComplete() }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in streaming session", e)
                withContext(Dispatchers.Main) { callback?.onStreamingError(e) }
            }
        }
    }

    /**
     * Feed a chunk of text into the active streaming session.
     *
     * Text is buffered internally. When a sentence-ending delimiter is
     * detected, the complete sentence is queued for synthesis and playback.
     *
     * @param chunk A text fragment (may be a single token or multiple words).
     * @throws IllegalStateException if no streaming session is active.
     */
    fun streamText(chunk: String) {
        val channel = sentenceChannel
            ?: throw IllegalStateException("No active streaming session. Call beginStreaming() first.")

        textBuffer.append(chunk)

        // Extract complete sentences from the buffer
        while (true) {
            val idx = textBuffer.indexOfAny(sentenceDelimiters)
            if (idx == -1) break

            val sentence = textBuffer.substring(0, idx + 1).trim()
            textBuffer.delete(0, idx + 1)

            if (sentence.isNotEmpty()) {
                channel.trySend(sentence)
            }
        }
    }

    /**
     * Signal that no more text will be fed to this streaming session.
     *
     * Any remaining buffered text is flushed and synthesized.
     * [TtsStreamingCallback.onStreamingComplete] will fire after the
     * last sentence finishes playing.
     */
    fun endStreaming() {
        val channel = sentenceChannel ?: return

        // Flush remaining buffered text
        val remaining = textBuffer.toString().trim()
        textBuffer.clear()
        if (remaining.isNotEmpty()) {
            channel.trySend(remaining)
        }

        channel.close() // signals the consumer loop to finish
        sentenceChannel = null
    }

    /**
     * Synthesize a single sentence and play it through the speaker
     * using [AudioTrack.MODE_STREAM] and `generateWithCallback`.
     */
    private suspend fun synthesizeAndPlay(
        sentence: String,
        speed: Float,
        speakerId: Int,
        callback: TtsStreamingCallback?
    ) {
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )

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
            .setBufferSizeInBytes(minBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack = track
        track.play()

        // Named class callback — see AudioChunkCallback docs above.
        tts!!.generateWithCallback(
            text = sentence,
            sid = speakerId,
            speed = speed,
            callback = AudioChunkCallback(track) { speakingJob?.isActive == true }
        )

        // Small delay to let the last chunk finish playing
        kotlinx.coroutines.delay(200)

        track.stop()
        track.release()
        audioTrack = null

        Log.d(TAG, "Sentence played: '${sentence.take(50)}'")
        withContext(Dispatchers.Main) { callback?.onSentenceSynthesized(sentence) }
    }

    // ─── Controls ────────────────────────────────────────────────────

    /**
     * Stop any active TTS playback (works for all modes).
     */
    fun stopSpeaking() {
        speakingJob?.cancel()
        speakingJob = null
        sentenceChannel?.close()
        sentenceChannel = null
        textBuffer.clear()
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
