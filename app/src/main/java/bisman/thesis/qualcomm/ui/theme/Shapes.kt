package bisman.thesis.qualcomm.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

// Custom shapes for specific components
val CardShape = RoundedCornerShape(16.dp)
val ButtonShape = RoundedCornerShape(12.dp)
val TextFieldShape = RoundedCornerShape(12.dp)
val DialogShape = RoundedCornerShape(24.dp)
val FABShape = RoundedCornerShape(16.dp)
val MessageBubbleShape = RoundedCornerShape(
    topStart = 16.dp,
    topEnd = 16.dp,
    bottomStart = 16.dp,
    bottomEnd = 4.dp
)
val UserMessageBubbleShape = RoundedCornerShape(
    topStart = 16.dp,
    topEnd = 16.dp,
    bottomStart = 4.dp,
    bottomEnd = 16.dp
)