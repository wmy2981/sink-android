# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

# kotlinx.serialization models and generated serializers
-keepclassmembers class cool.sink.mobile.data.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class cool.sink.mobile.data.**$$serializer { *; }
-keepclasseswithmembers class cool.sink.mobile.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
