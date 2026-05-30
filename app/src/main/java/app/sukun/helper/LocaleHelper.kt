package app.sukun.helper

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import app.sukun.data.Prefs
import java.util.Locale

object LocaleHelper {

    enum class Language(val code: String, val displayName: String, val nativeName: String) {
        SYSTEM("", "System Default", ""),
        ENGLISH("en", "English", "English"),
        SPANISH("es", "Spanish", "Español"),
        FRENCH("fr", "French", "Français"),
        GERMAN("de", "German", "Deutsch"),
        ARABIC("ar", "Arabic", "العربية"),
        CHINESE("zh", "Chinese", "中文"),
        PORTUGUESE("pt", "Portuguese", "Português"),
        ITALIAN("it", "Italian", "Italiano"),
        JAPANESE("ja", "Japanese", "日本語"),
        TURKISH("tr", "Turkish", "Türkçe"),
        RUSSIAN("ru", "Russian", "Русский"),
        KOREAN("ko", "Korean", "한국어"),
        INDONESIAN("id", "Indonesian", "Bahasa Indonesia");

        fun listLabel(): String = when {
            code.isEmpty() -> displayName
            nativeName.isNotBlank() -> nativeName
            else -> displayName
        }

        fun listSubtitle(): String? = when {
            code.isEmpty() -> null
            nativeName.isNotBlank() && nativeName != displayName -> displayName
            else -> null
        }
    }

    fun applyAppLocale(context: Context, languageCode: String) {
        Prefs(context).appLanguage = languageCode
        AppCompatDelegate.setApplicationLocales(toLocaleList(languageCode))
    }

    fun syncAppLocale(context: Context) {
        AppCompatDelegate.setApplicationLocales(toLocaleList(Prefs(context).appLanguage))
    }

    fun wrapContext(base: Context): Context {
        val languageCode = Prefs(base).appLanguage
        if (languageCode.isEmpty()) return base

        val locale = localeForCode(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        }
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    fun getSelectedLanguage(context: Context): Language {
        val languageCode = Prefs(context).appLanguage
        return Language.values().find { it.code == languageCode } ?: Language.SYSTEM
    }

    fun getAvailableLanguages(): List<Language> = Language.values().toList()

    fun getLocaleForLanguage(language: Language): Locale = localeForCode(language.code)

    private fun localeForCode(languageCode: String): Locale {
        if (languageCode.isEmpty()) return Locale.getDefault()
        return Locale.forLanguageTag(languageCode)
    }

    private fun toLocaleList(languageCode: String): LocaleListCompat {
        return if (languageCode.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageCode)
        }
    }
}
