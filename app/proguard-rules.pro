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
-keepattributes Signature

# Persisted JSON field names must remain stable across app updates.
-keepclassmembers,allowoptimization class com.breakyuna.esjzone.network.PersistentCookieJar$StoredCookie {
    <fields>;
}
-keepclassmembers,allowoptimization class com.breakyuna.esjzone.offline.DownloadedNovelManifest {
    <fields>;
}
-keepclassmembers,allowoptimization class com.breakyuna.esjzone.offline.DownloadedChapterRecord {
    <fields>;
}
-keepclassmembers,allowoptimization class com.breakyuna.esjzone.offline.DownloadedChapterContent {
    <fields>;
}
-keepclassmembers,allowoptimization class com.breakyuna.esjzone.offline.DownloadedComponent {
    <fields>;
}

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
