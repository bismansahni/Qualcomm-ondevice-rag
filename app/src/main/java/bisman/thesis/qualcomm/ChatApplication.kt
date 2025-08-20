package bisman.thesis.qualcomm

import android.app.Application
import bisman.thesis.qualcomm.data.ObjectBoxStore
import bisman.thesis.qualcomm.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

class ChatApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize ObjectBox
        ObjectBoxStore.init(this)
        
        // Initialize Koin only if not already started
        if (GlobalContext.getOrNull() == null) {
            startKoin {
                androidLogger()
                androidContext(this@ChatApplication)
                modules(appModule)
            }
        }
    }
}