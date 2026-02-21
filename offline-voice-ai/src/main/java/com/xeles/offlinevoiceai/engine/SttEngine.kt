package com.xeles.offlinevoiceai.engine

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.xeles.offlinevoiceai.VoiceAIListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

/**
 * Wrapper around the Vosk speech recognition engine.
 *
 * Manages [AudioRecord] for capturing PCM audio (16-bit, 16 kHz, mono)
 * and feeds it to the Vosk [Recognizer]. Recognized text is delivered
 * through [VoiceAIListener] callbacks.
 */
internal class SttEngine {

    companion object {
        private const val TAG = "SttEngine"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    val isListening: Boolean
        get() = recordingJob?.isActive == true

    /**
     * Initialize the Vosk model from the given absolute path.
     *
     * @param modelPath Absolute path to the unzipped Vosk model directory.
     * @return `true` if initialization succeeded.
     */
    fun initialize(modelPath: String): Boolean {
        return try {
            model = Model(modelPath)
            recognizer = Recognizer(model, SAMPLE_RATE.toFloat())
            Log.d(TAG, "Vosk model loaded from: $modelPath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Vosk model", e)
            false
        }
    }

    /**
     * Start recording audio and feeding it to the Vosk recognizer.
     *
     * The caller must ensure [Manifest.permission.RECORD_AUDIO] has been granted;
     * otherwise a [SecurityException] is thrown.
     *
     * @param listener Callback to receive recognized text and state changes.
     * @throws SecurityException if RECORD_AUDIO permission is not granted.
     * @throws IllegalStateException if the engine has not been initialized.
     */
    fun startListening(listener: VoiceAIListener) {
        if (model == null || recognizer == null) {
            listener.onError(IllegalStateException("STT engine not initialized. Call initialize() first."))
            return
        }

        if (isListening) {
            Log.w(TAG, "Already listening — ignoring duplicate startListening call")
            return
        }

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
            .coerceAtLeast(4096)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL,
                ENCODING,
                bufferSize
            )
        } catch (e: SecurityException) {
            listener.onError(SecurityException("RECORD_AUDIO permission not granted."))
            return
        }

        audioRecord?.startRecording()
        listener.onListeningStateChanged(true)

        recordingJob = scope.launch {
            val buffer = ShortArray(bufferSize / 2)
            try {
                while (isActive) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: break
                    if (read > 0) {
                        // Convert ShortArray to ByteArray for Vosk
                        val bytes = ByteArray(read * 2)
                        for (i in 0 until read) {
                            bytes[i * 2] = (buffer[i].toInt() and 0xFF).toByte()
                            bytes[i * 2 + 1] = (buffer[i].toInt() shr 8 and 0xFF).toByte()
                        }

                        val rec = recognizer!!
                        if (rec.acceptWaveForm(bytes, bytes.size)) {
                            // Final result for this utterance
                            val result = parseVoskResult(rec.result)
                            if (result.isNotBlank()) {
                                listener.onSpeechRecognized(result)
                            }
                        } else {
                            // Partial result
                            val partial = parseVoskPartial(rec.partialResult)
                            if (partial.isNotBlank()) {
                                listener.onSpeechRecognized(partial)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during audio recording", e)
                listener.onError(e)
            } finally {
                stopAudioRecord()
                listener.onListeningStateChanged(false)
            }
        }
    }

    /**
     * Stop listening and release the audio recording resources.
     */
    fun stopListening() {
        recordingJob?.cancel()
        recordingJob = null
        stopAudioRecord()
    }

    /**
     * Release all native resources. After this call, the engine cannot be reused.
     */
    fun release() {
        stopListening()
        try {
            recognizer?.close()
        } catch (_: Exception) {}
        try {
            model?.close()
        } catch (_: Exception) {}
        recognizer = null
        model = null
    }

    private fun stopAudioRecord() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
    }

    /**
     * Parse the JSON result from Vosk's `getResult()`.
     * Example: `{"text": "hello world"}`
     */
    private fun parseVoskResult(json: String): String {
        return try {
            JSONObject(json).optString("text", "")
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Parse the JSON partial result from Vosk's `getPartialResult()`.
     * Example: `{"partial": "hello"}`
     */
    private fun parseVoskPartial(json: String): String {
        return try {
            JSONObject(json).optString("partial", "")
        } catch (_: Exception) {
            ""
        }
    }
}
