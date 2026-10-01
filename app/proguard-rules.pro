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
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep per-app locale API surface used by LocaleHelper.
# android:autoStoreLocales="true" in the manifest handles persistence on API 33+;
# AppCompat's own mechanism covers older versions. We only need to preserve the
# public API entry points so R8 doesn't inline them away.
-keep class androidx.appcompat.app.AppCompatDelegate {
    public static *** setApplicationLocales(...);
    public static *** getApplicationLocales();
}
-keep class androidx.core.os.LocaleListCompat { *; }

# Keep LocaleHelper and Prefs so language code survives obfuscation
-keep class sukun.minimalist.app.launcher.com.helper.LocaleHelper { *; }
-keep class sukun.minimalist.app.launcher.com.data.Prefs { *; }

# Google Sign-In via Credential Manager (release / R8)
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class androidx.credentials.** { *; }
-dontwarn androidx.credentials.**
-keep class com.google.android.gms.auth.api.signin.** { *; }
-keep class sukun.minimalist.app.launcher.com.helper.GoogleSignInHostActivity { *; }
-keep class sukun.minimalist.app.launcher.com.helper.GoogleAuthHelper { *; }

# Room instantiates its generated *_Impl database via getDeclaredConstructor().
# R8 full mode (default from AGP 8) does not implicitly keep the default
# constructor of a class that a keep rule matches, and Room 2.6.1 — pulled in
# transitively by WorkManager — only ships "-keep class * extends RoomDatabase".
# Without the constructor, androidx.startup fails to create WorkDatabase_Impl
# and the process dies before MainActivity is shown.
-keep class * extends androidx.room.RoomDatabase {
    <init>(...);
}
-keep @androidx.room.Database class * { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class androidx.work.impl.WorkDatabase { *; }
-keep class androidx.work.impl.WorkDatabase_Impl {
    <init>(...);
}
-keep class androidx.work.impl.** { *; }
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# Workers are only referenced through reified type parameters, which R8 can
# fold into plain name strings. Keep the classes themselves so WorkManager can
# still instantiate them by name.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# AndroidX Startup (WorkManagerInitializer)
-keep class androidx.startup.** { *; }