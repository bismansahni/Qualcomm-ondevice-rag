package bisman.thesis.qualcomm.data

import android.content.Context
import android.util.Log
import io.objectbox.BoxStore

object ObjectBoxStore {
    private const val TAG = "ObjectBoxStore"
    
    lateinit var store: BoxStore
        private set

    fun init(context: Context) {
        Log.d(TAG, "Initializing ObjectBox store...")
        try {
            if (::store.isInitialized) {
                Log.w(TAG, "Store already initialized, skipping...")
                return
            }
            
            store = MyObjectBox.builder().androidContext(context).build()
            Log.d(TAG, "ObjectBox store initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ObjectBox store", e)
            throw e
        }
    }
}
