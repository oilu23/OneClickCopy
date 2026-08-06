# Keep line numbers for readable crash reports, hide the original file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---- kotlinx.serialization ----
# The plugin generates serializer() methods that are only reached reflectively.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---- Google API client / Drive ----
# Model classes are populated reflectively from JSON.
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.drive.** { *; }
-keepclassmembers class * {
    @com.google.api.client.util.Key <fields>;
}
-dontwarn com.google.api.client.**
-dontwarn com.google.common.**

# The Google HTTP client references optional javax/Joda types that are absent
# on Android; without these the release build fails on missing classes.
-dontwarn javax.naming.**
-dontwarn java.awt.**
-dontwarn org.joda.time.**
-dontwarn org.apache.http.**
-dontwarn android.net.http.AndroidHttpClient

# ---- Room ----
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# ---- App backup payload ----
-keep class com.oneclickcopy.backup.BackupPayload { *; }
-keep class com.oneclickcopy.backup.BackupDocument { *; }
