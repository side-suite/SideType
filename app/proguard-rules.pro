# To enable ProGuard in your project, edit project.properties
# to define the proguard.config property as described in that file.
#
# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in ${sdk.dir}/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the ProGuard
# include property in project.properties.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Vosk offline speech recognition (SID-53).
# Vosk binds to libvosk.so through JNA, which resolves classes, fields and methods reflectively
# from native code. R8 cannot see those uses, so without these keeps it renames them (58 JNA
# classes were mangled when this was first hit) and the native load fails with UnsatisfiedLinkError,
# surfacing to the user as "Could not load the voice model" (ERROR_MODEL_LOAD_FAILED).
#
# NOTE: compileFullReleaseJavaWithJavac does NOT run R8, so removing these looks fine at compile
# time and only breaks on a real device. Verify with assembleFullRelease + an on-device run.
# See docs/adr/0001-on-device-asr-engine.md.
-keep class org.vosk.** { *; }
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }
-keepclassmembers class * implements com.sun.jna.Structure { *; }
# JNA references desktop-only APIs that do not exist on Android; harmless here.
-dontwarn java.awt.**
-dontwarn com.sun.jna.**
-dontwarn org.vosk.**
