# Proguard rules for iTantra

# Keep models and entities
-keep class in.gov.itantra.** { *; }

# Keep ONNX Runtime
-keep class ai.onnxruntime.** { *; }

# Keep AndroidX Navigation & Compose
-keepattributes *Annotation*,InnerClasses,Signature,EnclosingMethod
