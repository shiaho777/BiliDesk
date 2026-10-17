package app.bilidesk

import android.app.Application
import app.bilidesk.core.data.AndroidHost
import app.bilidesk.core.data.AppGraph

class BiliApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidHost.install(this)
        AppGraph.init()
    }
}
