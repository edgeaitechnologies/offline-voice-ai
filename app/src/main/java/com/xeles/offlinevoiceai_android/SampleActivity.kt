package com.xeles.offlinevoiceai_android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.xeles.offlinevoiceai.SttListeningConfig
import com.xeles.offlinevoiceai.TtsSpeakingListener
import com.xeles.offlinevoiceai.TtsStreamingCallback
import com.xeles.offlinevoiceai.VoiceAIListener
import com.xeles.offlinevoiceai.VoiceAIManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Sample Activity demonstrating the OfflineVoiceAI library.
 *
 * Before running, place the following model folders in `app/src/main/assets/`:
 * - A Vosk model folder (e.g., `vosk-model-small-en-us`)
 * - A Sherpa VITS TTS model folder (e.g., `vits-piper-en_US-amy-low`)
 */
class SampleActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_RECORD_AUDIO = 100

        // ── Change these to match YOUR model folder names in assets/ ──
        private const val STT_MODEL_NAME = "vosk-model-small-en-us-0.15"
        private const val TTS_MODEL_NAME = "vits-piper-en_US-amy-low"

        /**
         * Simulated LLM response — a medium-large text that will be
         * fed token-by-token to [VoiceAIManager.streamText] to mimic
         * how a real language model streams its output.
         */
        private const val SIMULATED_LLM_TEXT =
            "Artificial intelligence is transforming the world around us. " +
            "From healthcare to education, its impact is profound. " +
            "Machine learning algorithms can now detect diseases earlier than ever before! " +
            "Self-driving cars are becoming a reality on our roads. " +
            "Natural language processing enables computers to understand human speech. " +
            "The future of AI holds endless possibilities for humanity."
    }

    private lateinit var voiceAI: VoiceAIManager

    private lateinit var btnListen: Button
    private lateinit var btnSpeak: Button
    private lateinit var btnSpeakStreamed: Button
    private lateinit var btnStreamSimulate: Button
    private lateinit var btnStopSpeaking: Button
    private lateinit var txtResult: TextView
    private lateinit var editSpeak: EditText
    private lateinit var txtStatus: TextView
    private lateinit var scrollView: ScrollView

    private var isListening = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sample)

        btnListen = findViewById(R.id.btn_listen)
        btnSpeak = findViewById(R.id.btn_speak)
        btnSpeakStreamed = findViewById(R.id.btn_speak_streamed)
        btnStreamSimulate = findViewById(R.id.btn_stream_simulate)
        btnStopSpeaking = findViewById(R.id.btn_stop_speaking)
        txtResult = findViewById(R.id.txt_result)
        editSpeak = findViewById(R.id.edit_speak)
        txtStatus = findViewById(R.id.txt_status)
        scrollView = findViewById(R.id.scroll_result)

        voiceAI = VoiceAIManager(applicationContext)

        // Disable buttons until initialization completes
        setTtsButtonsEnabled(false)
        btnListen.isEnabled = false
        txtStatus.text = "Initializing models…"

        // Initialize in a background coroutine
        CoroutineScope(Dispatchers.Main).launch {
            val success = voiceAI.initialize(STT_MODEL_NAME, TTS_MODEL_NAME)
            if (success) {
                txtStatus.text = "✅ Ready"
                btnListen.isEnabled = true
                setTtsButtonsEnabled(true)
            } else {
                txtStatus.text = "❌ Initialization failed — check model assets"
            }
        }

        // ── STT Button ───────────────────────────────────────────────
        btnListen.setOnClickListener {
            if (!isListening) {
                if (hasMicPermission()) {
                    startListening()
                } else {
                    requestMicPermission()
                }
            } else {
                stopListening()
            }
        }

        // ── TTS: Classic Speak ───────────────────────────────────────
        btnSpeak.setOnClickListener {
            val text = editSpeak.text.toString().trim()
            if (text.isNotEmpty()) {
                voiceAI.speak(text, listener = object : TtsSpeakingListener {
                    override fun onStart(utteranceText: String) {
                        txtStatus.text = "🔊 Speaking (classic)…"
                    }

                    override fun onDone(utteranceText: String) {
                        txtStatus.text = "✅ Done speaking (classic)"
                    }

                    override fun onError(utteranceText: String, error: Throwable) {
                        txtStatus.text = "⚠️ TTS Error: ${error.message}"
                    }
                })
            } else {
                Toast.makeText(this, "Enter text to speak", Toast.LENGTH_SHORT).show()
            }
        }

        // ── TTS: Speak Streamed (full text, chunked audio) ───────────
        btnSpeakStreamed.setOnClickListener {
            val text = editSpeak.text.toString().trim()
            if (text.isNotEmpty()) {
                voiceAI.speakStreamed(text, callback = object : TtsStreamingCallback {
                    override fun onStreamingStarted() {
                        txtStatus.text = "⚡ Streaming audio…"
                    }

                    override fun onSentenceSynthesized(sentence: String) {
                        txtStatus.text = "⚡ Played: ${sentence.take(40)}…"
                    }

                    override fun onStreamingComplete() {
                        txtStatus.text = "✅ Done (streamed)"
                    }

                    override fun onStreamingError(error: Throwable) {
                        txtStatus.text = "⚠️ Stream error: ${error.message}"
                    }
                })
            } else {
                Toast.makeText(this, "Enter text to speak", Toast.LENGTH_SHORT).show()
            }
        }

        // ── TTS: Simulate LLM Streaming ──────────────────────────────
        btnStreamSimulate.setOnClickListener {
            simulateLlmStream()
        }

        // ── Stop Speaking ────────────────────────────────────────────
        btnStopSpeaking.setOnClickListener {
            voiceAI.stopSpeaking()
            txtStatus.text = "⏹ Stopped"
        }
    }

    // ─── Simulate LLM Token-by-Token Streaming ──────────────────────

    private fun simulateLlmStream() {
        txtStatus.text = "🤖 Starting LLM simulation…"
        txtResult.text = ""

        // Split text into small tokens (simulating LLM output)
        val tokens = SIMULATED_LLM_TEXT.split(" ").map { "$it " }

        voiceAI.beginStreaming(callback = object : TtsStreamingCallback {
            override fun onStreamingStarted() {
                txtStatus.text = "🤖 Streaming session opened"
            }

            override fun onSentenceSynthesized(sentence: String) {
                runOnUiThread {
                    txtResult.append("✅ $sentence\n")
                    scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                    txtStatus.text = "🔊 Sentence played"
                }
            }

            override fun onStreamingComplete() {
                runOnUiThread {
                    txtResult.append("\n🏁 All sentences done!\n")
                    txtStatus.text = "✅ LLM stream complete"
                    scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                }
            }

            override fun onStreamingError(error: Throwable) {
                runOnUiThread {
                    txtStatus.text = "⚠️ Stream error: ${error.message}"
                }
            }
        })

        // Feed tokens with a delay to simulate LLM typing speed
        CoroutineScope(Dispatchers.IO).launch {
            for (token in tokens) {
                delay(80) // ~80ms per token ≈ fast LLM speed
                voiceAI.streamText(token)

                runOnUiThread {
                    // Show the token being fed in the status
                    txtStatus.text = "🤖 Feeding: …${token.trim()}"
                }
            }
            delay(100)
            voiceAI.endStreaming()
        }
    }

    // ─── STT Listener ────────────────────────────────────────────────

    private val voiceListener = object : VoiceAIListener {
        override fun onSpeechRecognized(text: String) {
            // Fires for EVERY recognition event (partial + final).
            // Use onPartialResult / onFinalResult for finer control.
        }

        override fun onPartialResult(text: String) {
            runOnUiThread {
                // Show live partial hypothesis (overwrite previous line)
                val lines = txtResult.text.toString().trimEnd().lines().toMutableList()
                if (lines.isNotEmpty()) lines.removeLastOrNull()
                lines.add("💬 $text")
                txtResult.text = lines.joinToString("\n") + "\n"
                scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
            }
        }

        override fun onFinalResult(text: String) {
            runOnUiThread {
                // Replace the partial line with the confirmed result
                val lines = txtResult.text.toString().trimEnd().lines().toMutableList()
                if (lines.isNotEmpty()) lines.removeLastOrNull()
                lines.add("✅ $text")
                txtResult.text = lines.joinToString("\n") + "\n"
                scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
            }
        }

        override fun onSilenceDetected() {
            runOnUiThread {
                txtStatus.text = "🤫 Silence detected — waiting…"
            }
        }

        override fun onAutoStopped() {
            runOnUiThread {
                txtStatus.text = "⏹ Auto-stopped (silence timeout)"
            }
        }

        override fun onListeningStateChanged(isListening: Boolean) {
            runOnUiThread {
                this@SampleActivity.isListening = isListening
                btnListen.text = if (isListening) "⏹ Stop Listening" else "🎤 Start Listening"
                if (isListening) txtStatus.text = "🎤 Listening…"
            }
        }

        override fun onError(error: Throwable) {
            runOnUiThread {
                txtStatus.text = "⚠️ Error: ${error.message}"
            }
        }
    }

    private fun startListening() {
        txtResult.text = ""
        // Auto-stop after 2 seconds of silence (default config)
        voiceAI.startListening(voiceListener, SttListeningConfig(silenceTimeoutMs = 2000L))
    }

    private fun stopListening() {
        voiceAI.stopListening()
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    private fun setTtsButtonsEnabled(enabled: Boolean) {
        btnSpeak.isEnabled = enabled
        btnSpeakStreamed.isEnabled = enabled
        btnStreamSimulate.isEnabled = enabled
        btnStopSpeaking.isEnabled = enabled
    }

    // ─── Permissions ─────────────────────────────────────────────────

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestMicPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQUEST_RECORD_AUDIO
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startListening()
            } else {
                Toast.makeText(this, "Microphone permission is required for STT", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ─── Lifecycle ───────────────────────────────────────────────────

    override fun onDestroy() {
        super.onDestroy()
        voiceAI.destroy()
    }
}
