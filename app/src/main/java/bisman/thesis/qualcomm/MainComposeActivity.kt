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

class MainComposeActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "MainComposeActivity"
        @JvmField
        var modelDirectory: String? = null
        @JvmField
        var htpConfigPath: String? = null
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set up library paths for Genie/QNN
        try {
            val nativeLibPath = applicationContext.applicationInfo.nativeLibraryDir
            Os.setenv("ADSP_LIBRARY_PATH", nativeLibPath, true)
            Os.setenv("LD_LIBRARY_PATH", nativeLibPath, true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Assets are already copied by MainActivity, just log the paths
        Log.d(TAG, "Using model directory: $modelDirectory")
        Log.d(TAG, "Using HTP config: $htpConfigPath")
        
        enableEdgeToEdge()
        setContent {
            ChatAppTheme {
                AppNavigation()
            }
        }
    }
}

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