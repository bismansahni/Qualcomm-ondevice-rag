package bisman.thesis.qualcomm

import android.app.Application
import android.util.Log
import bisman.thesis.qualcomm.data.ObjectBoxStore
import bisman.thesis.qualcomm.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

class ChatApplication : Application() {
    
    companion object {
        private const val TAG = "ChatApplication"
        
        init {
            try {
                System.loadLibrary("chatapp")
                Log.d(TAG, "Native library 'chatapp' loaded successfully in Application")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library 'chatapp'", e)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error loading native library", e)
            }
        }
    }
    
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