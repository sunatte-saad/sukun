package sukun.minimalist.app.launcher.com.helper

import sukun.minimalist.app.launcher.com.data.AppModel

interface AppFilterHelper {
    fun onAppFiltered(items:List<AppModel>)
}