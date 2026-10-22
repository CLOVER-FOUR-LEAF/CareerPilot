# kotlinx.serialization 生成的 serializer 通过反射/静态字段引用，需保留
-keepclassmembers class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.careerpilot.app.data.model.**$$serializer { *; }
-keepclassmembers class com.careerpilot.app.data.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.careerpilot.app.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
