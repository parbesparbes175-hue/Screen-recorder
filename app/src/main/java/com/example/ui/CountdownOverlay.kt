package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CarbonDark
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.TextWhite

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun CountdownOverlay(
    secondsRemaining: Int,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CarbonDark.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(NeonEmerald.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = secondsRemaining,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(150)) + scaleIn(initialScale = 0.5f))
                            .togetherWith(fadeOut(animationSpec = tween(150)))
                    },
                    label = "countdown"
                ) { count ->
                    Text(
                        text = if (count > 0) "$count" else "GO!",
                        color = NeonEmerald,
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Starting PUBG GameCapture...",
                color = TextWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Tap to cancel",
                color = Color.Gray,
                fontSize = 13.sp,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}
