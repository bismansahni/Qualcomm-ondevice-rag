package bisman.thesis.qualcomm.ui.screens.docs

import bisman.thesis.qualcomm.ui.components.AppProgressDialog
import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import bisman.thesis.qualcomm.data.Document
import bisman.thesis.qualcomm.ui.components.AppAlertDialog
import bisman.thesis.qualcomm.ui.components.createAlertDialog
import bisman.thesis.qualcomm.ui.components.StoragePermissionHandler
import bisman.thesis.qualcomm.utils.DocumentProcessingState
import bisman.thesis.qualcomm.ui.theme.*
import bisman.thesis.qualcomm.ui.theme.ChatAppTheme
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

private val showDocDetailDialog = mutableStateOf(false)
private val dialogDoc = mutableStateOf<Document?>(null)

// Helper function to get actual file path from URI
private fun getPathFromUri(context: Context, uri: Uri): String? {
    return try {
        val docId = DocumentsContract.getTreeDocumentId(uri)
        Log.d("getPathFromUri", "Document ID: $docId")

        when {
            docId.startsWith("primary:") -> {
                val path = "/storage/emulated/0/${docId.removePrefix("primary:")}"
                Log.d("getPathFromUri", "Primary storage path: $path")
                path
            }
            docId.contains(":") -> {
                // Handle SD card or other storage
                val split = docId.split(":")
                if (split.size == 2) {
                    val path = "/storage/${split[0]}/${split[1]}"
                    Log.d("getPathFromUri", "Secondary storage path: $path")
                    path
                } else {
                    Log.d("getPathFromUri", "Unknown storage format")
                    null
                }
            }
            else -> {
                Log.d("getPathFromUri", "Using raw path: ${uri.path}")
                uri.path
            }
        }
    } catch (e: Exception) {
        Log.e("getPathFromUri", "Error converting URI to path", e)
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocsScreen(onBackClick: (() -> Unit)) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }

    ChatAppTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceContainer
                        )
                    )
                )
        ) {
            // Main content
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                topBar = {
                    TopAppBar(
                        title = {
                            AnimatedVisibility(
                                visible = !isSearchExpanded,
                                enter = fadeIn() + slideInHorizontally(),
                                exit = fadeOut() + slideOutHorizontally()
                            ) {
                                Text(
                                    text = "Documents",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            AnimatedVisibility(
                                visible = isSearchExpanded,
                                enter = fadeIn() + slideInHorizontally(),
                                exit = fadeOut() + slideOutHorizontally()
                            ) {
                                SearchBar(
                                    searchQuery = searchQuery,
                                    onSearchQueryChange = { searchQuery = it },
                                    onSearchClose = {
                                        isSearchExpanded = false
                                        searchQuery = ""
                                    }
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = onBackClick,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Navigate Back",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        actions = {
                            if (!isSearchExpanded) {
                                IconButton(
                                    onClick = { isSearchExpanded = true },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                            CircleShape
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )
                },
                floatingActionButton = {
                    FloatingFolderButton()
                }
            ) { innerPadding ->
                val docsViewModel: DocsViewModel = koinViewModel()

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))

                        // Folder watcher card
                        AnimatedVisibility(
                            visible = true,
                            enter = slideInVertically(
                                initialOffsetY = { -it },
                                animationSpec = tween(600, easing = FastOutSlowInEasing)
                            ) + fadeIn(animationSpec = tween(600))
                        ) {
                            StoragePermissionHandler(
                                onPermissionGranted = {
                                    // Permission granted, watcher can function
                                }
                            ) {
                                GlassmorphicFolderWatcherCard(docsViewModel)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Documents list
                        DocsList(docsViewModel, searchQuery)
                    }
                }

                AppProgressDialog()
                AppAlertDialog()
                DocDetailDialog()
            }
        }
    }
}

@Composable
private fun SearchBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchClose: () -> Unit
) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        placeholder = {
            Text(
                "Search documents...",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        trailingIcon = {
            IconButton(onClick = onSearchClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close search",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = { /* Handle search */ }
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun FloatingFolderButton() {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )
    val rotation by animateFloatAsState(
        targetValue = if (isPressed) 15f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )

    val context = LocalContext.current
    val docsViewModel: DocsViewModel = koinViewModel()

    // Folder picker launcher
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            // Take persistable permission
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)

            // Get the actual path from the URI
            val path = getPathFromUri(context, uri)
            path?.let { folderPath ->
                try {
                    docsViewModel.documentWatcher.setWatchedFolderPath(folderPath)
                    // Start the sync service with the new folder
                    docsViewModel.startSyncService(context)
                } catch (e: Exception) {
                    Log.e("FloatingFolderButton", "Error setting up document sync", e)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .size(64.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = rotation
            }
    ) {
        FloatingActionButton(
            onClick = { folderPickerLauncher.launch(null) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        }
                    )
                }
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = "Select Folder",
                modifier = Modifier.size(28.dp)
            )
        }

        // Ripple effect
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            Color.Transparent
                        ),
                        radius = if (isPressed) 100f else 50f
                    ),
                    shape = CircleShape
                )
        )
    }
}

@Composable
private fun GlassmorphicFolderWatcherCard(docsViewModel: DocsViewModel) {
    val context = LocalContext.current
    var isInitialized by remember { mutableStateOf(false) }

    // Initialize document watcher
    LaunchedEffect(Unit) {
        try {
            docsViewModel.initDocumentWatcher(context)
            isInitialized = true
            // Start the sync service instead of just FileObserver
            val watchedPath = docsViewModel.documentWatcher.watchedFolderPath.value
            if (watchedPath != null) {
                // Check if service is already running
                if (!docsViewModel.isSyncServiceRunning(context)) {
                    docsViewModel.startSyncService(context)
                }
            }
        } catch (e: Exception) {
            Log.e("GlassmorphicFolderWatcherCard", "Failed to initialize document watcher", e)
            isInitialized = false
        }
    }

    val isWatching = if (isInitialized) {
        docsViewModel.documentWatcher.isWatching.collectAsState()
    } else {
        remember { mutableStateOf(false) }
    }

    val processingStatus = if (isInitialized) {
        docsViewModel.documentWatcher.processingStatus.collectAsState()
    } else {
        remember { mutableStateOf(bisman.thesis.qualcomm.domain.watcher.DocumentWatcher.ProcessingStatus.Idle) }
    }

    val watchedFolderPath = if (isInitialized) {
        docsViewModel.documentWatcher.watchedFolderPath.collectAsState()
    } else {
        remember { mutableStateOf<String?>(null) }
    }

    // Animation states
    val animatedAlpha by animateFloatAsState(
        targetValue = if (watchedFolderPath.value != null) 1f else 0.8f,
        animationSpec = tween(800)
    )

    val animatedScale by animateFloatAsState(
        targetValue = if (isWatching.value) 1.02f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = animatedAlpha
                scaleX = animatedScale
                scaleY = animatedScale
            },
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.3f),
                    Color.White.copy(alpha = 0.1f)
                )
            )
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = if (watchedFolderPath.value != null) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = RoundedCornerShape(20.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header with icon and status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        // Animated icon
                        val iconRotation by animateFloatAsState(
                            targetValue = if (isWatching.value) 360f else 0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(2000, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            )
                        )

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    color = if (watchedFolderPath.value != null) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    },
                                    shape = CircleShape
                                )
                                .graphicsLayer {
                                    rotationZ = if (isWatching.value) iconRotation else 0f
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isWatching.value) Icons.Default.Sync else Icons.Default.FolderOpen,
                                contentDescription = "Auto Import Status",
                                tint = if (watchedFolderPath.value != null) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column {
                            Text(
                                text = "Auto Import",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (watchedFolderPath.value != null) {
                                    if (isWatching.value) "🟢 Active - Watching for new documents"
                                    else "🟡 Configured - Ready to watch"
                                } else {
                                    "🔴 Select a folder to start auto import"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (watchedFolderPath.value != null) {
                                    if (isWatching.value) Success else Warning
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Folder path section
                if (watchedFolderPath.value != null) {
                    Column {
                        Text(
                            text = "Watched Folder:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = watchedFolderPath.value ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Processing status
                        if (isWatching.value) {
                            Spacer(modifier = Modifier.height(12.dp))
                            ProcessingStatusIndicator(processingStatus.value)
                        }
                    }
                } else {
                    // Empty state message
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Tap the folder button below to select a folder for automatic document import",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProcessingStatusIndicator(
    status: bisman.thesis.qualcomm.domain.watcher.DocumentWatcher.ProcessingStatus
) {
    AnimatedVisibility(
        visible = status !is bisman.thesis.qualcomm.domain.watcher.DocumentWatcher.ProcessingStatus.Idle,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut()
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = when (status) {
                    is bisman.thesis.qualcomm.domain.watcher.DocumentWatcher.ProcessingStatus.Processing ->
                        InfoContainer.copy(alpha = 0.3f)
                    is bisman.thesis.qualcomm.domain.watcher.DocumentWatcher.ProcessingStatus.Success ->
                        SuccessContainer.copy(alpha = 0.3f)
                    is bisman.thesis.qualcomm.domain.watcher.DocumentWatcher.ProcessingStatus.Error ->
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    else -> Color.Transparent
                }
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Animated icon based on status
                val icon = when (status) {
                    is bisman.thesis.qualcomm.domain.watcher.DocumentWatcher.ProcessingStatus.Processing -> Icons.Default.Sync
                    is bisman.thesis.qualcomm.domain.watcher.DocumentWatcher.ProcessingStatus.Success -> Icons.Default.CheckCircle
                    is bisman.thesis.qualcomm.domain.watcher.DocumentWatcher.ProcessingStatus.Error -> Icons.Default.Error
                    else -> Icons.Default.Info
                }

                val iconColor = when (status) {
                    is bisman.thesis.qualcomm.domain.watcher.DocumentWatcher.ProcessingStatus.Processing -> Info
                    is bisman.thesis.qualcomm.domain.watcher.DocumentWatcher.ProcessingStatus.Success -> Success
                    is bisman.thesis.qualcomm.domain.watcher.DocumentWatcher.ProcessingStatus.Error -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                }

                // Rotating animation for processing state
                val rotation by animateFloatAsState(
                    targetValue = if (status is bisman.thesis.qualcomm.domain.watcher.DocumentWatcher.ProcessingStatus.Processing) 360f else 0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    )
                )

                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer {
                            rotationZ = if (status is bisman.thesis.qualcomm.domain.watcher.DocumentWatcher.ProcessingStatus.Processing) rotation else 0f
                        }
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = when (status) {
                        is bisman.thesis.qualcomm.domain.watcher.DocumentWatcher.ProcessingStatus.Processing ->
                            "Processing: ${status.fileName}"
                        is bisman.thesis.qualcomm.domain.watcher.DocumentWatcher.ProcessingStatus.Success ->
                            "✓ Successfully imported: ${status.fileName}"
                        is bisman.thesis.qualcomm.domain.watcher.DocumentWatcher.ProcessingStatus.Error ->
                            "✗ Error processing: ${status.fileName}"
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when (status) {
                        is bisman.thesis.qualcomm.domain.watcher.DocumentWatcher.ProcessingStatus.Processing -> Info
                        is bisman.thesis.qualcomm.domain.watcher.DocumentWatcher.ProcessingStatus.Success -> Success
                        is bisman.thesis.qualcomm.domain.watcher.DocumentWatcher.ProcessingStatus.Error -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DocsList(
    docsViewModel: DocsViewModel,
    searchQuery: String
) {
    val allDocs by docsViewModel.getAllDocuments().collectAsState(initial = emptyList())

    // Filter documents based on search query
    val filteredDocs = remember(allDocs, searchQuery) {
        if (searchQuery.isBlank()) {
            allDocs
        } else {
            allDocs.filter { doc ->
                doc.docFileName.contains(searchQuery, ignoreCase = true) ||
                        doc.docText.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // Debug logging
    LaunchedEffect(filteredDocs) {
        Log.d("DocsList", "Filtered documents count: ${filteredDocs.size}")
    }

    if (filteredDocs.isEmpty()) {
        EmptyDocsState(hasSearchQuery = searchQuery.isNotBlank())
    } else {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(1),
            verticalItemSpacing = 12.dp,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(
                items = filteredDocs,
                key = { _, doc -> doc.docId }
            ) { index, doc ->
                AnimatedDocumentCard(
                    document = doc.copy(
                        docText = if (doc.docText.length > 200) {
                            doc.docText.substring(0, 200) + " ..."
                        } else {
                            doc.docText
                        }
                    ),
                    index = index,
                    onCardClick = {
                        dialogDoc.value = doc
                        showDocDetailDialog.value = true
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnimatedDocumentCard(
    document: Document,
    index: Int,
    onCardClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    var isSwipeToDelete by remember { mutableStateOf(false) }

    // Observe processing state
    val processingDocuments by DocumentProcessingState.processingDocuments.collectAsState()
    val processingProgress by DocumentProcessingState.processingProgress.collectAsState()
    val isProcessing = processingDocuments.contains(document.docFilePath)
    val progress = processingProgress[document.docFilePath] ?: 0

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )

    val offsetX by animateFloatAsState(
        targetValue = if (isSwipeToDelete) -300f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)
    )

    // Staggered animation entrance
    val animatedAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(
            durationMillis = 600,
            delayMillis = index * 100,
            easing = FastOutSlowInEasing
        )
    )

    val animatedTranslationY by animateFloatAsState(
        targetValue = 0f,
        animationSpec = tween(
            durationMillis = 600,
            delayMillis = index * 100,
            easing = FastOutSlowInEasing
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = animatedAlpha
                scaleX = scale
                scaleY = scale
                translationX = offsetX
                translationY = animatedTranslationY
            }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        },
                        onTap = { onCardClick() }
                    )
                },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
            ),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 4.dp,
                pressedElevation = 8.dp
            )
        ) {
            Box {
                // Background gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Header with file info and actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = document.docFileName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = DateUtils.getRelativeTimeSpanString(document.docAddedTime).toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Processing indicator
                            if (isProcessing) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Animated spinning icon
                                    val infiniteTransition = rememberInfiniteTransition()
                                    val rotation by infiniteTransition.animateFloat(
                                        initialValue = 0f,
                                        targetValue = 360f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(1000, easing = LinearEasing)
                                        )
                                    )

                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = "Processing",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .graphicsLayer { rotationZ = rotation }
                                    )

                                    Spacer(modifier = Modifier.width(4.dp))

                                    Text(
                                        text = "Processing... $progress%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                // Progress bar
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = progress / 100f,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(2.dp)
                                        .clip(RoundedCornerShape(1.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                )
                            }
                        }

                        // View button only
                        IconButton(
                            onClick = onCardClick,
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = "View document",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Document preview
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Article,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Preview",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = document.docText.trim().replace("\n", " "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyDocsState(hasSearchQuery: Boolean = false) {
    val infiniteTransition = rememberInfiniteTransition()
    val floatingOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Animated illustration
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .offset(y = floatingOffset.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (hasSearchQuery) Icons.Default.SearchOff else Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = if (hasSearchQuery) "No documents found" else "No documents yet",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (hasSearchQuery) {
                    "Try adjusting your search query or add more documents to get started"
                } else {
                    "Set up auto import by selecting a folder, or manually add documents to begin"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            if (!hasSearchQuery) {
                Spacer(modifier = Modifier.height(32.dp))

                // Animated arrow pointing to FAB
                val arrowAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.alpha(arrowAlpha)
                ) {
                    Text(
                        text = "Tap the folder button to get started",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DocDetailDialog() {
    var isVisible by remember { showDocDetailDialog }
    val doc by remember { dialogDoc }

    if (isVisible && doc != null) {
        Dialog(
            onDismissRequest = { isVisible = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.8f),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Header
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.secondary
                                        )
                                    )
                                )
                                .padding(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = doc?.docFileName ?: "",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Added ${DateUtils.getRelativeTimeSpanString(doc?.docAddedTime ?: 0)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                }

                                IconButton(
                                    onClick = { isVisible = false },
                                    modifier = Modifier
                                        .background(
                                            Color.White.copy(alpha = 0.2f),
                                            CircleShape
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = Color.White
                                    )
                                }
                            }
                        }

                        // Content
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(20.dp)
                        ) {
                            Text(
                                text = "Document Content",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Card(
                                modifier = Modifier.fillMaxSize(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = doc?.docText ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp)
                                        .verticalScroll(rememberScrollState()),
                                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                                )
                            }
                        }

                        // Action buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            val context = LocalContext.current

                            Button(
                                onClick = {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, doc?.docText)
                                        type = "text/plain"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, null)
                                    context.startActivity(shareIntent)
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Share")
                            }

                            OutlinedButton(
                                onClick = { isVisible = false },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline
                                )
                            ) {
                                Text("Close")
                            }
                        }
                    }
                }
            }
        }
    }
}