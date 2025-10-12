package bisman.thesis.qualcomm

import android.os.Bundle
import android.system.Os
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import bisman.thesis.qualcomm.ui.screens.chat.ChatScreen
import bisman.thesis.qualcomm.ui.screens.docs.DocsScreen
import bisman.thesis.qualcomm.ui.theme.ChatAppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Paths

/**
 * Main activity for the Qualcomm Thesis Chat Application using Jetpack Compose.
 *
 * This activity serves as the entry point for the Compose-based UI and manages:
 * - Qualcomm Neural Network (QNN) library path configuration
 * - Model path initialization for on-device LLM and embeddings
 * - Navigation between Chat and Document Management screens
 * - ML model lifecycle management to prevent memory leaks
 *
 * The activity handles device-specific configurations based on Qualcomm SoC model
 * (Snapdragon 8 Elite, 8 Gen 3, or 8 Gen 2) to optimize HTP (Hexagon Tensor Processor) usage.
 *
 * Key responsibilities:
 * - Configure native library paths for Genie/QNN runtime
 * - Initialize model directories and HTP configuration
 * - Manage application navigation with animated transitions
 * - Handle model cleanup when app goes to background or is destroyed
 *
 * @see ChatScreen for the main chat interface
 * @see DocsScreen for document management interface
 */
class MainComposeActivity : ComponentActivity() {

    companion object {
        /** Tag for logging activity lifecycle and initialization events */
        const val TAG = "MainComposeActivity"

        /** Directory path where LLM models are stored in external cache */
        @JvmField
        var modelDirectory: String? = null

        /** Path to HTP (Hexagon Tensor Processor) configuration JSON file for the device's SoC */
        @JvmField
        var htpConfigPath: String? = null
    }

    /**
     * Called when the activity is starting.
     *
     * Initialization sequence:
     * 1. Sets up native library paths for Qualcomm QNN SDK (ADSP_LIBRARY_PATH, LD_LIBRARY_PATH)
     * 2. Initializes model directory and HTP config paths if null (app restart scenario)
     * 3. Enables edge-to-edge display
     * 4. Sets up Compose content with theme and navigation
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being
     *                           shut down then this Bundle contains the data it most recently
     *                           supplied. Note: Otherwise it is null.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "onCreate called")
        
        // Set up library paths for Genie/QNN
        try {
            val nativeLibPath = applicationContext.applicationInfo.nativeLibraryDir
            Os.setenv("ADSP_LIBRARY_PATH", nativeLibPath, true)
            Os.setenv("LD_LIBRARY_PATH", nativeLibPath, true)
            Log.d(TAG, "Native library paths set successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set native library paths", e)
            e.printStackTrace()
        }
        
        // Initialize model paths if they're null (happens when app is restarted after being killed)
        if (modelDirectory == null || htpConfigPath == null) {
            Log.w(TAG, "Model paths are null, reinitializing...")
            initializeModelPaths()
        }
        
        Log.d(TAG, "Using model directory: $modelDirectory")
        Log.d(TAG, "Using HTP config: $htpConfigPath")
        
        enableEdgeToEdge()
        setContent {
            ChatAppTheme {
                AppNavigation()
            }
        }
    }

    /**
     * Called when the activity is no longer visible to the user.
     *
     * Releases ML models to free memory when the app goes to the background. This ensures
     * proper cleanup even if ViewModels are not destroyed, preventing memory pressure and
     * potential ANRs (Application Not Responding) when the app is in the background.
     */
    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop called - releasing models")
        // Release models when app goes to background
        // This ensures cleanup even if ViewModel isn't destroyed
        (application as? ChatApplication)?.releaseModels()
    }

    /**
     * Performs final cleanup before the activity is destroyed.
     *
     * If the activity is finishing (user closed the app), this method forcefully terminates
     * the process to ensure a completely fresh start on next launch. This prevents issues
     * with cached native model state and ensures clean initialization of QNN runtime.
     */
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called - isFinishing: $isFinishing")
        if (isFinishing) {
            // App is actually closing, force process termination
            // This ensures next launch is completely fresh
            Log.d(TAG, "App finishing - killing process for clean state")
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(0)
        }
    }
}

/**
 * Defines the navigation graph for the application using Jetpack Compose Navigation.
 *
 * This composable sets up a [NavHost] with two destinations:
 * - "chat": The main chat interface with RAG-based document Q&A
 * - "docs": Document management screen for uploading and managing files
 *
 * Navigation transitions use slide animations (400ms duration, FastOutSlowInEasing) to provide
 * smooth visual feedback when moving between screens.
 *
 * @see ChatScreen for the chat destination
 * @see DocsScreen for the documents destination
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AppNavigation() {
    val navHostController = rememberNavController()
    
    NavHost(
        navController = navHostController,
        startDestination = "chat",
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(400, easing = FastOutSlowInEasing)
            )
        },
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(400, easing = FastOutSlowInEasing)
            )
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(400, easing = FastOutSlowInEasing)
            )
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(400, easing = FastOutSlowInEasing)
            )
        }
    ) {
        composable("chat") {
            ChatScreen(
                onOpenDocsClick = { 
                    navHostController.navigate("docs") {
                        launchSingleTop = true
                    }
                }
            )
        }
        
        composable("docs") { 
            DocsScreen(onBackClick = { navHostController.navigateUp() }) 
        }
    }
}

/**
 * Initializes model directory paths and HTP configuration based on the device's SoC model.
 *
 * This extension function determines the correct HTP (Hexagon Tensor Processor) configuration
 * for the device's Qualcomm SoC and sets up paths for:
 * - LLM models directory (external cache/models/llm)
 * - HTP config JSON file specific to the device's Snapdragon generation
 *
 * Supported SoC models:
 * - SM8750: Snapdragon 8 Elite
 * - SM8650: Snapdragon 8 Gen 3
 * - QCS8550: Snapdragon 8 Gen 2
 *
 * Falls back to Snapdragon 8 Gen 3 config for unsupported devices.
 *
 * @receiver ComponentActivity The activity context used to access external cache directory
 * @throws Exception if path initialization fails
 */
private fun ComponentActivity.initializeModelPaths() {
    try {
        // Get SoC model to determine HTP config
        val supportedSocModel = mapOf(
            "SM8750" to "qualcomm-snapdragon-8-elite.json",
            "SM8650" to "qualcomm-snapdragon-8-gen3.json",
            "QCS8550" to "qualcomm-snapdragon-8-gen2.json"
        )

        val socModel = android.os.Build.SOC_MODEL
        Log.d(MainComposeActivity.TAG, "Device SoC model: $socModel")
        
        if (!supportedSocModel.containsKey(socModel)) {
            Log.e(MainComposeActivity.TAG, "Unsupported device. SoC model: $socModel")
            // Use a default config as fallback
            val defaultConfig = "qualcomm-snapdragon-8-gen3.json"
            Log.w(MainComposeActivity.TAG, "Using default HTP config: $defaultConfig")
        }
        
        val externalDir = externalCacheDir?.absolutePath
        if (externalDir == null) {
            Log.e(MainComposeActivity.TAG, "External cache directory is null")
            return
        }
        
        // Set the model paths
        MainComposeActivity.modelDirectory = Paths.get(externalDir, "models", "llm").toString()
        MainComposeActivity.htpConfigPath = Paths.get(externalDir, "htp_config", 
            supportedSocModel[socModel] ?: "qualcomm-snapdragon-8-gen3.json").toString()
        
        Log.d(MainComposeActivity.TAG, "Model paths initialized:")
        Log.d(MainComposeActivity.TAG, "  modelDirectory: ${MainComposeActivity.modelDirectory}")
        Log.d(MainComposeActivity.TAG, "  htpConfigPath: ${MainComposeActivity.htpConfigPath}")
        
        // Verify the paths exist
        val modelDir = File(MainComposeActivity.modelDirectory!!)
        val htpConfig = File(MainComposeActivity.htpConfigPath!!)
        
        if (!modelDir.exists()) {
            Log.e(MainComposeActivity.TAG, "Model directory does not exist: ${MainComposeActivity.modelDirectory}")
            Log.w(MainComposeActivity.TAG, "App may need to be restarted from MainActivity to copy assets")
        } else {
            Log.d(MainComposeActivity.TAG, "Model directory exists with ${modelDir.listFiles()?.size ?: 0} files")
        }
        
        if (!htpConfig.exists()) {
            Log.e(MainComposeActivity.TAG, "HTP config file does not exist: ${MainComposeActivity.htpConfigPath}")
        } else {
            Log.d(MainComposeActivity.TAG, "HTP config file exists")
        }
    } catch (e: Exception) {
        Log.e(MainComposeActivity.TAG, "Error initializing model paths", e)
    }
}