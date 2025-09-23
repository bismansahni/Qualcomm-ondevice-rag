package bisman.thesis.qualcomm.ui.screens.chat

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import bisman.thesis.qualcomm.ui.theme.*
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onOpenDocsClick: () -> Unit,
    viewModel: ChatViewModel = koinViewModel()
) {
    var questionText by remember { mutableStateOf("") }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        GradientStart.copy(alpha = 0.05f),
                        GradientEnd.copy(alpha = 0.02f),
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
    ) {
        // Animated background particles
        BackgroundParticles()
        
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                ModernTopBar(onOpenDocsClick = onOpenDocsClick)
            },
            floatingActionButton = {
                ModernSendButton(
                    chatViewModel = viewModel,
                    questionText = questionText,
                    onQuestionSent = { questionText = "" }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .windowInsetsPadding(WindowInsets.ime)
            ) {
                // Chat messages
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    ChatMessages(chatViewModel = viewModel)
                }
                
                // Input area
                ModernInputArea(
                    chatViewModel = viewModel,
                    questionText = questionText,
                    onQuestionChange = { questionText = it }
                )
                
                Spacer(modifier = Modifier.height(Dimensions.paddingMedium))
            }
        }
    }
}

@Composable
private fun BackgroundParticles() {
    val infiniteTransition = rememberInfiniteTransition(label = "particles")
    
    repeat(8) { index ->
        val animationDelay = index * 1000
        val animationDuration = 4000 + Random.nextInt(2000)
        
        val offsetY by infiniteTransition.animateFloat(
            initialValue = 1200f,
            targetValue = -200f,
            animationSpec = infiniteRepeatable(
                animation = tween(animationDuration, delayMillis = animationDelay, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "particle_$index"
        )
        
        val offsetX = Random.nextFloat() * 400f
        val size = Random.nextInt(2, 6).dp
        val alpha = Random.nextFloat() * 0.1f + 0.05f
        
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.toInt(), offsetY.toInt()) }
                .size(size)
                .alpha(alpha)
                .background(
                    GradientStart,
                    CircleShape
                )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModernTopBar(onOpenDocsClick: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.animateContentSize()
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    modifier = Modifier
                        .size(Dimensions.iconSizeLarge)
                        .padding(end = Dimensions.paddingSmall),
                    tint = Primary
                )
                Text(
                    text = "AI Assistant",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        actions = {
            IconButton(
                onClick = onOpenDocsClick,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Primary.copy(alpha = 0.1f),
                                Color.Transparent
                            )
                        )
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = "Document Settings",
                    tint = Primary
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent
        ),
        scrollBehavior = scrollBehavior
    )
}

@Composable
private fun ChatMessages(chatViewModel: ChatViewModel) {
    val question by chatViewModel.questionState.collectAsState()
    val response by chatViewModel.responseState.collectAsState()
    val isGeneratingResponse by chatViewModel.isGeneratingResponseState.collectAsState()
    val retrievedContextList by chatViewModel.retrievedContextListState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    
    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(question, response, isGeneratingResponse) {
        if (listState.layoutInfo.totalItemsCount > 0) {
            scope.launch {
                delay(100)
                listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
            }
        }
    }
    
    if (question.trim().isEmpty()) {
        EmptyStateContent()
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Dimensions.paddingMedium),
            contentPadding = PaddingValues(vertical = Dimensions.paddingMedium),
            verticalArrangement = Arrangement.spacedBy(Dimensions.messageSpacing)
        ) {
            // User message
            item {
                AnimatedVisibility(
                    visible = true,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    UserMessageBubble(message = question)
                }
            }
            
            // Assistant response
            item {
                AnimatedVisibility(
                    visible = true,
                    enter = slideInVertically(
                        animationSpec = tween(300, delayMillis = 100)
                    ) + fadeIn(tween(300, delayMillis = 100)),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    AssistantMessageBubble(
                        response = response,
                        isGenerating = isGeneratingResponse
                    )
                }
            }
            
            // Context cards
            if (!isGeneratingResponse && retrievedContextList.isNotEmpty()) {
                item {
                    AnimatedVisibility(
                        visible = true,
                        enter = slideInVertically(
                            animationSpec = tween(300, delayMillis = 200)
                        ) + fadeIn(tween(300, delayMillis = 200))
                    ) {
                        ContextSection(retrievedContextList = retrievedContextList)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimensions.paddingLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Animated icon
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        )
        
        Box(
            modifier = Modifier
                .scale(scale)
                .size(120.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            GradientStart.copy(alpha = 0.3f),
                            GradientEnd.copy(alpha = 0.1f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
                .padding(Dimensions.paddingLarge),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Psychology,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = Primary
            )
        }
        
        Spacer(modifier = Modifier.height(Dimensions.paddingLarge))
        
        Text(
            text = "Ready to help you explore your documents",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(Dimensions.paddingSmall))
        
        Text(
            text = "Ask any question about your uploaded documents and get intelligent answers with relevant context.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 300.dp)
        )
        
        Spacer(modifier = Modifier.height(Dimensions.paddingExtraLarge))
        
        // Suggested prompts
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(Dimensions.paddingSmall),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            val suggestions = listOf(
                "What are the main topics in my documents?",
                "Summarize the key findings",
                "Find information about specific concepts"
            )
            
            items(suggestions) { suggestion ->
                SuggestionChip(text = suggestion)
            }
        }
    }
}

@Composable
private fun SuggestionChip(text: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* Handle suggestion click */ },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(Dimensions.cornerRadiusLarge)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(Dimensions.paddingMedium),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun UserMessageBubble(message: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ),
            colors = CardDefaults.cardColors(
                containerColor = UserMessageBackground
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = RoundedCornerShape(
                topStart = Dimensions.cornerRadiusLarge,
                topEnd = Dimensions.cornerRadiusSmall,
                bottomStart = Dimensions.cornerRadiusLarge,
                bottomEnd = Dimensions.cornerRadiusLarge
            )
        ) {
            Text(
                text = message,
                modifier = Modifier.padding(Dimensions.messageBubblePadding),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
        }
    }
}

@Composable
private fun AssistantMessageBubble(
    response: String,
    isGenerating: Boolean
) {
    val context = LocalContext.current
    
    // Log UI updates
    LaunchedEffect(response) {
        android.util.Log.d("ChatScreen", "AssistantMessageBubble recomposed with response length: ${response.length}")
    }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ),
            colors = CardDefaults.cardColors(
                containerColor = AssistantMessageBackground
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(
                topStart = Dimensions.cornerRadiusSmall,
                topEnd = Dimensions.cornerRadiusLarge,
                bottomStart = Dimensions.cornerRadiusLarge,
                bottomEnd = Dimensions.cornerRadiusLarge
            )
        ) {
            Column(
                modifier = Modifier.padding(Dimensions.messageBubblePadding)
            ) {
                if (response.isEmpty() && isGenerating) {
                    TypingIndicator()
                } else if (response.isNotEmpty()) {
                    MarkdownText(
                        markdown = response,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    
                    // Share button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(
                            onClick = {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, response)
                                    type = "text/plain"
                                }
                                val shareIntent = Intent.createChooser(sendIntent, null)
                                context.startActivity(shareIntent)
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share response",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(3) { index ->
            val infiniteTransition = rememberInfiniteTransition(label = "typing")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = index * 200),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_$index"
            )
            
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .alpha(alpha)
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        CircleShape
                    )
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Text(
            text = "AI is thinking...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun ContextSection(retrievedContextList: List<bisman.thesis.qualcomm.data.RetrievedContext>) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Dimensions.paddingSmall)
    ) {
        Text(
            text = "Sources",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = Dimensions.paddingSmall)
        )
        
        retrievedContextList.forEach { context ->
            ContextCard(retrievedContext = context)
        }
    }
}

@Composable
private fun ContextCard(retrievedContext: bisman.thesis.qualcomm.data.RetrievedContext) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = RetrievedContextBackground
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(Dimensions.cornerRadiusMedium)
    ) {
        Column(
            modifier = Modifier.padding(Dimensions.paddingMedium)
        ) {
            Text(
                text = "\"${retrievedContext.context}\"",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Normal
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(Dimensions.paddingSmall))
            
            Text(
                text = retrievedContext.fileName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ModernInputArea(
    chatViewModel: ChatViewModel,
    questionText: String,
    onQuestionChange: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surface,
                RoundedCornerShape(
                    topStart = Dimensions.cornerRadiusExtraLarge,
                    topEnd = Dimensions.cornerRadiusExtraLarge
                )
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = RoundedCornerShape(
                    topStart = Dimensions.cornerRadiusExtraLarge,
                    topEnd = Dimensions.cornerRadiusExtraLarge
                )
            )
    ) {
        OutlinedTextField(
            value = questionText,
            onValueChange = onQuestionChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimensions.paddingMedium)
                .animateContentSize(),
            placeholder = {
                Text(
                    text = "Ask me anything about your documents...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Primary.copy(alpha = 0.8f),
                unfocusedBorderColor = Color.Transparent,
                cursorColor = Primary,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            ),
            shape = RoundedCornerShape(Dimensions.cornerRadiusLarge),
            textStyle = MaterialTheme.typography.bodyLarge,
            maxLines = 4
        )
    }
}

@Composable
private fun ModernSendButton(
    chatViewModel: ChatViewModel,
    questionText: String,
    onQuestionSent: () -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isGeneratingResponse by chatViewModel.isGeneratingResponseState.collectAsState()
    
    // Animation states
    val scale by animateFloatAsState(
        targetValue = if (questionText.isNotBlank() && !isGeneratingResponse) 1f else 0.8f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "fab_scale"
    )
    
    val rotation by animateFloatAsState(
        targetValue = if (isGeneratingResponse) 360f else 0f,
        animationSpec = if (isGeneratingResponse) {
            infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        } else {
            spring()
        },
        label = "fab_rotation"
    )
    
    AnimatedVisibility(
        visible = questionText.isNotBlank(),
        enter = scaleIn() + fadeIn(),
        exit = scaleOut() + fadeOut()
    ) {
        FloatingActionButton(
            onClick = {
                // Prevent clicks while generating response
                if (isGeneratingResponse) {
                    return@FloatingActionButton
                }

                keyboardController?.hide()
                if (!chatViewModel.checkNumDocuments()) {
                    Toast.makeText(
                        context,
                        "Add documents to execute queries",
                        Toast.LENGTH_LONG
                    ).show()
                    return@FloatingActionButton
                }

                if (questionText.trim().isEmpty()) {
                    Toast.makeText(
                        context,
                        "Enter a query to execute",
                        Toast.LENGTH_LONG
                    ).show()
                    return@FloatingActionButton
                }

                try {
                    chatViewModel.getAnswer(questionText)
                    onQuestionSent()
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        "Error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            modifier = Modifier
                .scale(scale)
                .graphicsLayer { rotationZ = rotation }
                .alpha(if (isGeneratingResponse) 0.5f else 1f),  // Visual feedback when disabled
            containerColor = if (isGeneratingResponse) {
                Secondary
            } else {
                Primary
            },
            contentColor = Color.White,
            elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(
                defaultElevation = 8.dp,
                pressedElevation = 12.dp
            )
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send message",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}