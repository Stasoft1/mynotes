# Release-сборка: сжатие и обфускация кода (R8). Правила держат то, что находят по имени или из нативного кода.
-keepattributes SourceFile,LineNumberTable,Signature,*Annotation*,InnerClasses,EnclosingMethod

# SQLCipher: нативная библиотека обращается к классам Java по имени.
-keep class net.zetetic.database.** { *; }
-keep class net.sqlcipher.** { *; }

# Карта osmdroid.
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Прочее, чего нет на Android.
-dontwarn javax.annotation.**
-dontwarn org.slf4j.**
-dontwarn org.apache.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
