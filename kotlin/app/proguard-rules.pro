# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Ktor (under rpc-ktordriver) logs through SLF4J, which looks for a binding it
# can live without: with none it falls back to a no-op logger. R8 flags the
# optional binding class as missing and fails the release build without this.
-dontwarn org.slf4j.impl.StaticLoggerBinder

# MediaPipe (the pose landmarker) builds its graph config from protobuf-lite
# messages, which protobuf reads back by field name through reflection, and its
# native code calls into its Java classes by name over JNI. Renamed, the release
# build fails at start: "Field platform_ for ... not found".
-keep class com.google.mediapipe.** { *; }
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
# MediaPipe logs through Flogger, which finds its caller by walking the stack
# for its own class names and loads its backend by name: renamed, the
# landmarker's first class load fails with "no caller found on the stack".
-keep class com.google.common.flogger.** { *; }
# Kept whole, MediaPipe's profiler and graph-template methods name protos that
# tasks-vision does not ship; the pose landmarker calls neither.
-dontwarn com.google.mediapipe.proto.CalculatorProfileProto$CalculatorProfile
-dontwarn com.google.mediapipe.proto.GraphTemplateProto$CalculatorGraphTemplate
