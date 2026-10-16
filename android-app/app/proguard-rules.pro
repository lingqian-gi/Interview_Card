# 保持 kotlinx.serialization 相关
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.caj.interviewcards.** {
    *** Companion;
}
-keepclasseswithmembers class com.caj.interviewcards.** {
    kotlinx.serialization.KSerializer serializer(...);
}
