# R8 rules for the release build.
#
# Native methods are kept by the default proguard-android-optimize.txt.
# What it can't see are the Java members the C engine looks up by name
# through JNI (see src/main/jni/droidzebra-jni.c), so keep those here.

# ZebraEngine.Callback(int, JSONObject) is called from droidzebra-msg.c.
-keepclassmembers class com.shurik.droidzebra.ZebraEngine {
    org.json.JSONObject Callback(int, org.json.JSONObject);
}

# EngineError is thrown from native code via FindClass/ThrowNew.
-keep class com.shurik.droidzebra.EngineError {
    <init>(...);
}
