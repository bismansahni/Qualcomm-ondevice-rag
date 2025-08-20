package bisman.thesis.qualcomm.domain.watcher

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun PulsingDot(
    color: Color = MaterialTheme.colorScheme.primary,
    size: Float = 8f
) {
    val infiniteTransition = rememberInfiniteTransition()
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    Box(
        modifier = Modifier
            .size(size.dp)
            .scale(scale)
            .alpha(alpha)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            var isVisible by remember { mutableStateOf(false) }
            
            LaunchedEffect(key1 = Unit) {
                delay(index * 100L)
                isVisible = true
            }
            
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn() + scaleIn()
            ) {
                PulsingDot(color = color)
            }
        }
    }
}

@Composable
fun ShimmerEffect(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition()
    val translateAnimation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1200,
                easing = LinearEasing
            )
        )
    )
    
    Box(
        modifier = modifier
            .background(
                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFE0E0E0),
                        Color(0xFFF5F5F5),
                        Color(0xFFE0E0E0)
                    ),
                    start = androidx.compose.ui.geometry.Offset(translateAnimation, 0f),
                    end = androidx.compose.ui.geometry.Offset(translateAnimation + 200f, 0f)
                )
            )
    )
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AnimatedContent(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(300)) + 
                slideInVertically(
                    initialOffsetY = { it / 10 },
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ),
        exit = fadeOut(animationSpec = tween(200)) + 
               slideOutVertically(
                   targetOffsetY = { -it / 10 },
                   animationSpec = tween(200)
               ),
        content = content
    )
}