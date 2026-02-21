# OfflineVoiceAI-Android

A lightweight, **entirely offline**, AI-powered **Speech-to-Text** (STT) and **Text-to-Speech** (TTS) library for Android.

Built on top of [Vosk](https://alphacephei.com/vosk/) (STT) and [Sherpa-ONNX](https://github.com/k2-fsa/sherpa-onnx) (TTS), abstracted behind a clean, **coroutine-friendly Kotlin API**.

---

## ✨ Features

- 🔇 **100% Offline** — No internet required after model download
- 🎤 **Speech-to-Text** — Real-time voice recognition via Vosk
- 🔊 **Text-to-Speech** — Natural speech synthesis via Sherpa-ONNX VITS
- ⏹ **Auto-Stop on Silence** — Configurable silence timeout to automatically stop listening
- 🧩 **Simple API** — Single `VoiceAIManager` facade class
- 📦 **Lightweight** — Models are NOT bundled; you choose your language/size

## 📋 Prerequisites

- **Minimum SDK**: API 24 (Android 7.0)
- **Kotlin**: 2.0+
- **JDK**: 11+

## 🚀 Installation

### Step 1: Add JitPack repository

In your `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### Step 2: Add the dependency

In your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.edgeaitechnologies:offline-voice-ai:1.1.0")
}
```

## 📥 Model Setup

### Download Models

| Engine | Model | Size | Link |
|--------|-------|------|------|
| STT (Vosk) | `vosk-model-small-en-us` | ~40 MB | [Download](https://alphacephei.com/vosk/models) |
| TTS (Sherpa) | `vits-piper-en_US-amy-low` | ~25 MB | [Download](https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models) |

### Place Models in Assets

1. Download and **unzip** the model archives
2. Place the unzipped folders in your app's `src/main/assets/` directory:

```
app/src/main/assets/
├── vosk-model-small-en-us/
│   ├── am/
│   ├── conf/
│   ├── graph/
│   └── ...
└── vits-piper-en_US-amy-low/
    ├── model.onnx
    ├── tokens.txt
    └── espeak-ng-data/
```

> **Note**: Models are extracted to internal storage on first run. Subsequent launches skip extraction automatically.

## 💻 Quick Start

```kotlin
class MainActivity : AppCompatActivity() {

    private lateinit var voiceAI: VoiceAIManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        voiceAI = VoiceAIManager(applicationContext)

        // Initialize (suspend — call from a coroutine)
        lifecycleScope.launch {
            val ready = voiceAI.initialize(
                sttModelAssetName = "vosk-model-small-en-us",
                ttsModelAssetName = "vits-piper-en_US-amy-low"
            )

            if (ready) {
                // Start listening with auto-stop after 2 s silence
                voiceAI.startListening(
                    listener = object : VoiceAIListener {
                        override fun onSpeechRecognized(text: String) {
                            // Fires for every recognition event (partial + final)
                        }
                        override fun onPartialResult(text: String) {
                            Log.d("VoiceAI", "Partial: $text")
                        }
                        override fun onFinalResult(text: String) {
                            Log.d("VoiceAI", "Final: $text")
                        }
                        override fun onSilenceDetected() {
                            Log.d("VoiceAI", "User stopped speaking…")
                        }
                        override fun onAutoStopped() {
                            Log.d("VoiceAI", "Auto-stopped after silence")
                        }
                        override fun onListeningStateChanged(isListening: Boolean) {}
                        override fun onError(error: Throwable) {
                            Log.e("VoiceAI", "Error", error)
                        }
                    },
                    config = SttListeningConfig(
                        silenceTimeoutMs = 2000L,    // Auto-stop after 2 s silence
                        autoStopOnSilence = true      // Set to false for manual stop only
                    )
                )

                // Speak text with progress callbacks
                voiceAI.speak(
                    text = "Hello from OfflineVoiceAI!",
                    listener = object : TtsSpeakingListener {
                        override fun onStart(utteranceText: String) {
                            Log.d("VoiceAI", "Speaking: $utteranceText")
                        }
                        override fun onDone(utteranceText: String) {
                            Log.d("VoiceAI", "Done speaking")
                        }
                        override fun onError(utteranceText: String, error: Throwable) {
                            Log.e("VoiceAI", "TTS error", error)
                        }
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceAI.destroy() // Release native resources
    }
}
```

## 🔐 Permissions

The library declares `RECORD_AUDIO` in its manifest (auto-merges into your app). You must **request runtime permission** before calling `startListening()`:

```kotlin
ActivityCompat.requestPermissions(
    this,
    arrayOf(Manifest.permission.RECORD_AUDIO),
    REQUEST_CODE
)
```

If permission is not granted, `VoiceAIListener.onError()` will receive a `SecurityException`.

## ⏹ STT Auto-Stop on Silence

By default, the STT engine **automatically stops listening** when the user stops speaking. This is controlled via `SttListeningConfig`:

```kotlin
// Default: auto-stop after 2 seconds of silence
voiceAI.startListening(listener)

// Custom timeout: 3 seconds
voiceAI.startListening(listener, SttListeningConfig(silenceTimeoutMs = 3000L))

// Disable auto-stop (manual stop only — original behaviour)
voiceAI.startListening(listener, SttListeningConfig(autoStopOnSilence = false))
```

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `silenceTimeoutMs` | `Long` | `2000` | Milliseconds of silence before auto-stop |
| `autoStopOnSilence` | `Boolean` | `true` | Enable/disable auto-stop |

## 🎤 STT Callbacks

All callbacks are delivered via `VoiceAIListener`. New callbacks have **default no-op implementations**, so existing code continues to work without changes.

| Callback | When it fires |
|----------|---------------|
| `onSpeechRecognized(text)` | Every recognition event (partial + final) — **backward compatible** |
| `onPartialResult(text)` | Real-time partial hypothesis while user is speaking |
| `onFinalResult(text)` | Confirmed final result for an utterance |
| `onSilenceDetected()` | Silence first detected after speech (timeout countdown starts) |
| `onAutoStopped()` | Engine auto-stopped due to silence timeout |
| `onListeningStateChanged(isListening)` | Microphone started/stopped recording |
| `onError(error)` | An error occurred during STT |

## 🔊 TTS Speaking Callbacks

Track the lifecycle of each `speak()` call using `TtsSpeakingListener`:

| Callback | When it fires |
|----------|---------------|
| `onStart(utteranceText)` | Audio playback has begun |
| `onDone(utteranceText)` | Audio playback finished successfully |
| `onError(utteranceText, error)` | Synthesis or playback failed |

All callbacks are delivered on the **main thread**, so you can update UI directly.

The listener is optional — `speak("Hello")` still works as a fire-and-forget call.

## 🏗️ Architecture

```
┌────────────────────────────────────────────┐
│           Your Application                 │
├────────────────────────────────────────────┤
│       VoiceAIManager (Facade)              │
├──────────────────┬─────────────────────────┤
│  SttEngine       │  TtsEngine              │
│  (Vosk Wrapper)  │  (Sherpa-ONNX Wrapper)  │
├──────────────────┼─────────────────────────┤
│  Vosk C++/JNI    │  Sherpa-ONNX C++/JNI    │
└──────────────────┴─────────────────────────┘
```

## 📄 License

This project is open source. See the [LICENSE](LICENSE) file for details.

