package app.sukun.helper

import android.content.Context
import android.content.res.Configuration
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
        HINDI("hi", "Hindi", "हिन्दी"),
        PORTUGUESE("pt", "Portuguese", "Português"),
        ITALIAN("it", "Italian", "Italiano"),
        JAPANESE("ja", "Japanese", "日本語"),
        TURKISH("tr", "Turkish", "Türkçe"),
        RUSSIAN("ru", "Russian", "Русский"),
        KOREAN("ko", "Korean", "한국어"),
        INDONESIAN("id", "Indonesian", "Bahasa Indonesia"),
    }

    fun setLocale(context: Context, languageCode: String) {
        val locale = if (languageCode.isEmpty()) {
            Locale.getDefault()
        } else {
            Locale(languageCode)
        }

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        context.resources.updateConfiguration(config, context.resources.displayMetrics)

        // Save preference
        Prefs(context).appLanguage = languageCode
    }

    fun getSelectedLanguage(context: Context): Language {
        val languageCode = Prefs(context).appLanguage
        return Language.values().find { it.code == languageCode } ?: Language.SYSTEM
    }

    fun getAvailableLanguages(): List<Language> {
        return Language.values().toList()
    }

    fun getLocaleForLanguage(language: Language): Locale {
        return if (language.code.isEmpty()) {
            Locale.getDefault()
        } else {
            Locale(language.code)
        }
    }
}
