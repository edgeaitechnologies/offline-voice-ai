# Consumer ProGuard rules for offline-voice-ai library.
# These rules are automatically applied to the consuming app.

# Keep Vosk native methods
-keep class org.vosk.** { *; }

# Keep Sherpa-ONNX native methods
-keep class com.k2fsa.sherpa.onnx.** { *; }
