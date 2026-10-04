# App-specific R8 rules. Library consumer rules are supplied transitively by
# AndroidX, Coil, Haze, Telephoto, and Lottie where those libraries need them.
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

# Gson reads generic List<T> fields from persisted download manifests and
# chapter files. Keep signatures used to recover those element types.
-keepattributes Signature,*Annotation*

# Persisted JSON field names must remain stable across app updates.
-keep class com.breakyuna.esjzone.network.PersistentCookieJar$StoredCookie { *; }
-keep class com.breakyuna.esjzone.offline.DownloadedNovelManifest { *; }
-keep class com.breakyuna.esjzone.offline.DownloadedChapterRecord { *; }
-keep class com.breakyuna.esjzone.offline.DownloadedChapterContent { *; }
-keep class com.breakyuna.esjzone.offline.DownloadedComponent { *; }
-keepclassmembers,allowoptimization class com.breakyuna.esjzone.network.features.HomeDataSnapshot {
    <fields>;
}
-keep,allowoptimization class com.breakyuna.esjzone.network.features.HomeDataSnapshot
-keepclassmembers,allowoptimization class com.breakyuna.esjzone.network.features.WeeklyUpdateDaySnapshot {
    <fields>;
}
-keep,allowoptimization class com.breakyuna.esjzone.network.features.WeeklyUpdateDaySnapshot
-keepclassmembers,allowoptimization class com.breakyuna.esjzone.network.features.HistorySnapshot {
    <fields>;
}
-keep,allowoptimization class com.breakyuna.esjzone.network.features.HistorySnapshot
-keepclassmembers,allowoptimization class com.breakyuna.esjzone.network.features.HistoryNovelSnapshot {
    <fields>;
}
-keep,allowoptimization class com.breakyuna.esjzone.network.features.HistoryNovelSnapshot
-keepclassmembers,allowoptimization class com.breakyuna.esjzone.novellibrary.novel.CoveredNovelImpl {
    <fields>;
}
-keep,allowoptimization class com.breakyuna.esjzone.novellibrary.novel.CoveredNovelImpl
-keepclassmembers,allowoptimization class com.breakyuna.esjzone.novellibrary.data.WeeklyPopularNovel {
    <fields>;
}
-keep,allowoptimization class com.breakyuna.esjzone.novellibrary.data.WeeklyPopularNovel

# Navigation 3 persists NavKey values through kotlinx.serialization. Keep
# route names, companions, and generated serializers for restore after process
# death or an app update.
-keep,allowoptimization class com.breakyuna.esjzone.ui.navigation.AppNavKey {
    *;
}
-keep,allowoptimization class com.breakyuna.esjzone.ui.navigation.AppNavKey$* {
    *;
}
-keep,allowoptimization class com.breakyuna.esjzone.ui.navigation.LegacyRoute {
    *;
}
-keep,allowoptimization class com.breakyuna.esjzone.ui.navigation.LegacyRoute$* {
    *;
}
-keep,allowoptimization class com.breakyuna.esjzone.ui.navigation.ReaderRoute {
    *;
}
-keep,allowoptimization class com.breakyuna.esjzone.ui.navigation.ReaderRoute$* {
    *;
}

# Room derives this generated implementation class name at runtime from the
# @Database type.
-keep,allowoptimization class com.breakyuna.esjzone.database.GeneralDatabase_Impl {
    *;
}

# WorkManager persists the worker class name and reflectively invokes this
# constructor when restoring queued work.
-keep,allowoptimization class com.breakyuna.esjzone.offline.NovelDownloadWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Coil 3, Haze, Telephoto, and Lottie use direct APIs here and supply their
# own consumer rules where needed; broad library keeps are not warranted.

# Versioned chapter snapshots and precise reading locations have stable wire names.
-keep class com.breakyuna.esjzone.data.reader.ChapterBody { *; }
-keep class com.breakyuna.esjzone.data.reader.StoredBlock { *; }
-keep class com.breakyuna.esjzone.data.reader.StoredText { *; }
-keep class com.breakyuna.esjzone.data.reader.StoredTextStyle { *; }
-keep class com.breakyuna.esjzone.network.StructuredChapterCache$Snapshot { *; }
-keep class com.breakyuna.esjzone.novellibrary.novel.Chapter { *; }
-keep class com.breakyuna.esjzone.domain.reader.ReaderAnchor { *; }
-keep class com.breakyuna.esjzone.database.entity.LocalReadingActivity { *; }
