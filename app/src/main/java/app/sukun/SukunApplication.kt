package app.sukun

import android.app.Application
import android.content.Context
import app.sukun.helper.LocaleHelper

class SukunApplication : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(base))
    }

    override fun onCreate() {
        super.onCreate()
        LocaleHelper.syncAppLocale(this)
    }
}
