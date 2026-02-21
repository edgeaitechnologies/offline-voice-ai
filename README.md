# OfflineVoiceAI-Android

A lightweight, **entirely offline**, AI-powered **Speech-to-Text** (STT) and **Text-to-Speech** (TTS) library for Android.

Built on top of [Vosk](https://alphacephei.com/vosk/) (STT) and [Sherpa-ONNX](https://github.com/k2-fsa/sherpa-onnx) (TTS), abstracted behind a clean, **coroutine-friendly Kotlin API**.

---

## ✨ Features

- 🔇 **100% Offline** — No internet required after model download
- 🎤 **Speech-to-Text** — Real-time voice recognition via Vosk
- 🔊 **Text-to-Speech** — Natural speech synthesis via Sherpa-ONNX VITS
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
    implementation("com.github.Xavier984:OfflineVoiceAI-Android:1.0.0")
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
                // Start listening (requires RECORD_AUDIO permission)
                voiceAI.startListening(object : VoiceAIListener {
                    override fun onSpeechRecognized(text: String) {
                        Log.d("VoiceAI", "Heard: $text")
                    }
                    override fun onListeningStateChanged(isListening: Boolean) {}
                    override fun onError(error: Throwable) {
                        Log.e("VoiceAI", "Error", error)
                    }
                })

                // Speak text
                voiceAI.speak("Hello from OfflineVoiceAI!")
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
