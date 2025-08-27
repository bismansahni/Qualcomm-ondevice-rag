package bisman.thesis.qualcomm.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
        private const val PREFS_NAME = "DocQAPrefs"
        private const val PREF_WATCHED_FOLDER = "watched_folder_path"
        private const val PREF_SERVICE_ENABLED = "sync_service_enabled"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }
        
        Log.d(TAG, "Device boot completed, checking if service should start")
        
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isServiceEnabled = prefs.getBoolean(PREF_SERVICE_ENABLED, false)
        val watchedFolder = prefs.getString(PREF_WATCHED_FOLDER, null)
        
        if (isServiceEnabled && !watchedFolder.isNullOrEmpty()) {
            Log.d(TAG, "Starting DocumentSyncService after boot")
            
            val serviceIntent = Intent(context, DocumentSyncService::class.java).apply {
                putExtra("folder_path", watchedFolder)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } else {
            Log.d(TAG, "Service not enabled or no folder configured, skipping start")
        }
    }
}