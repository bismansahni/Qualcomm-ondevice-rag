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
    }
    
    override fun onCreate() {
        super.onCreate()
        
        Log.d(TAG, "ChatApplication onCreate called")
        
        // Initialize ObjectBox
        try {
            Log.d(TAG, "Initializing ObjectBox...")
            ObjectBoxStore.init(this)
            Log.d(TAG, "ObjectBox initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ObjectBox", e)
            throw e
        }
        
        // Initialize Koin only if not already started
        if (GlobalContext.getOrNull() == null) {
            Log.d(TAG, "Starting Koin...")
            startKoin {
                androidLogger()
                androidContext(this@ChatApplication)
                modules(appModule)
            }
            Log.d(TAG, "Koin started successfully")
        } else {
            Log.d(TAG, "Koin already initialized, skipping...")
        }
        
        Log.d(TAG, "ChatApplication initialization complete")
    }
}