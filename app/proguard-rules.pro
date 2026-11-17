# WorkManager instantiates workers by class name.
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
# ML Kit / Play services ship their own consumer rules.
-dontwarn org.jetbrains.annotations.**

# PdfBox-Android: optional JPEG2000 codec is not bundled.
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
