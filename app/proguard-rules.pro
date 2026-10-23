# Proguard rules for KtxSrtAuto
-keepattributes *Annotation*
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.darktype.ktxsrt.WebAppInterface { *; }
