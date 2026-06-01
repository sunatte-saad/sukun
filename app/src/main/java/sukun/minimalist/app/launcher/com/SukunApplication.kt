package sukun.minimalist.app.launcher.com

import android.app.Application
import android.content.Context
import sukun.minimalist.app.launcher.com.helper.LocaleHelper

class SukunApplication : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(base))
    }

    override fun onCreate() {
        super.onCreate()
        LocaleHelper.syncAppLocale(this)
    }
}
