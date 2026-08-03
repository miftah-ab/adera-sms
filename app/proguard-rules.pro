# Adera SMS — ProGuard Rules

# ---------------------------------------------------------------------------
# Room entities
# Keep all entity classes intact — Room generates SQL column names from field
# names, and obfuscating them would silently break all DB queries.
# ---------------------------------------------------------------------------
-keep class com.adera.sms.data.entity.** { *; }
-keep class com.adera.sms.data.Converters { *; }

# ---------------------------------------------------------------------------
# Room DAOs (generated implementations referenced by AppDatabase)
# ---------------------------------------------------------------------------
-keep interface com.adera.sms.data.dao.** { *; }

# ---------------------------------------------------------------------------
# WorkManager workers
# WorkManager uses reflection to instantiate workers by class name.
# If you rename or move SmsSenderWorker, update this rule too.
# ---------------------------------------------------------------------------
-keep class com.adera.sms.service.SmsSenderWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ---------------------------------------------------------------------------
# Kotlin Coroutines
# ---------------------------------------------------------------------------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ---------------------------------------------------------------------------
# Enums — kept because Room TypeConverters call CallStatus.valueOf(String)
# via reflection; obfuscating enum names breaks this.
# ---------------------------------------------------------------------------
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---------------------------------------------------------------------------
# Kotlin Metadata (needed for Kotlin reflection used by Room/Compose)
# ---------------------------------------------------------------------------
-keep class kotlin.Metadata { *; }

# ---------------------------------------------------------------------------
# AndroidX / Jetpack Compose
# ---------------------------------------------------------------------------
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
