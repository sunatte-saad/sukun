package app.sukun.helper

import app.sukun.data.AppModel

interface AppFilterHelper {
    fun onAppFiltered(items:List<AppModel>)
}