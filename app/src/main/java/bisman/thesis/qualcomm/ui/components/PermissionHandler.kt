package bisman.thesis.qualcomm.ui.components

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect

@Composable
fun StoragePermissionHandler(
    onPermissionGranted: () -> Unit,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var showPermissionDialog by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(false) }
    
    // Check if we have storage permission
    LaunchedEffect(Unit) {
        hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true // For older versions, we'll request permission when needed
        }
        
        if (hasPermission) {
            showPermissionDialog = false
            onPermissionGranted()
        }
    }
    
    // Re-check permission when returning from settings
    DisposableEffect(Unit) {
        val lifecycleObserver = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val isGranted = Environment.isExternalStorageManager()
                    if (isGranted && !hasPermission) {
                        hasPermission = true
                        showPermissionDialog = false
                        onPermissionGranted()
                    }
                }
            }
        }
        val lifecycle = (context as? androidx.lifecycle.LifecycleOwner)?.lifecycle
        lifecycle?.addObserver(lifecycleObserver)
        
        onDispose {
            lifecycle?.removeObserver(lifecycleObserver)
        }
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            hasPermission = true
            onPermissionGranted()
        } else {
            showPermissionDialog = true
        }
    }
    
    // Request permissions when needed
    if (!hasPermission) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            LaunchedEffect(Unit) {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                )
            }
        } else {
            // For Android 11+, show dialog only if not already granted
            LaunchedEffect(Unit) {
                if (!Environment.isExternalStorageManager()) {
                    showPermissionDialog = true
                }
            }
        }
    }
    
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Storage Permission Required") },
            text = { 
                Text(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        "This app needs storage permission to watch for new documents. Please grant storage access in settings."
                    } else {
                        "This app needs storage permission to watch for new documents. Please grant the permission."
                    }
                ) 
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.data = Uri.parse("package:${context.packageName}")
                            context.startActivity(intent)
                        } else {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.READ_EXTERNAL_STORAGE,
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                                )
                            )
                        }
                        showPermissionDialog = false
                    }
                ) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPermissionDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
    
    content()
}