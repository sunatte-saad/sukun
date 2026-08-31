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
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

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