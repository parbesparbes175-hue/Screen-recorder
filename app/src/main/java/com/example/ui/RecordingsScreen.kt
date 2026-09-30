package com.example.ui

import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.RecordingItem
import com.example.storage.MediaStoreHelper
import com.example.ui.theme.CarbonDark
import com.example.ui.theme.CardDark
import com.example.ui.theme.CrimsonRecord
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsScreen(
    recordings: List<RecordingItem>,
    onBack: () -> Unit,
    onDelete: (RecordingItem) -> Unit
) {
    val context = LocalContext.current
    var itemToDelete by remember { mutableStateOf<RecordingItem?>(null) }

    // Lazy thumbnail cache loaded on-demand in IO thread, never during recording
    val thumbnails = remember { mutableStateMapOf<Long, Bitmap?>() }

    BackHandler {
        onBack()
    }

    Scaffold(
        containerColor = CarbonDark,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "PUBG Gameplay Recordings",
                        color = TextWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("recordings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = NeonEmerald
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceDark
                )
            )
        }
    ) { padding ->
        if (recordings.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.VideocamOff,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No gameplay recordings found",
                        color = TextWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Recordings will be saved in Movies/GameCapture",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(recordings, key = { it.id }) { item ->
                    // Load thumbnail lazily in background
                    LaunchedEffect(item.id) {
                        if (!thumbnails.containsKey(item.id)) {
                            val bitmap = withContext(Dispatchers.IO) {
                                try {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        context.contentResolver.loadThumbnail(item.uri, Size(256, 144), null)
                                    } else {
                                        @Suppress("DEPRECATION")
                                        MediaStore.Video.Thumbnails.getThumbnail(
                                            context.contentResolver,
                                            item.id,
                                            MediaStore.Video.Thumbnails.MINI_KIND,
                                            null
                                        )
                                    }
                                } catch (_: Exception) {
                                    null
                                }
                            }
                            thumbnails[item.id] = bitmap
                        }
                    }

                    RecordingItemCard(
                        item = item,
                        thumbnail = thumbnails[item.id],
                        onPlay = { MediaStoreHelper.playRecording(context, item.uri) },
                        onShare = { MediaStoreHelper.shareRecording(context, item.uri, item.displayName) },
                        onDeleteClick = { itemToDelete = item }
                    )
                }
            }
        }
    }

    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            containerColor = SurfaceDark,
            title = {
                Text(
                    text = "Delete Recording?",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to permanently delete \"${item.displayName}\"?",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(item)
                        itemToDelete = null
                    },
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text("Delete", color = CrimsonRecord, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
fun RecordingItemCard(
    item: RecordingItem,
    thumbnail: Bitmap?,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() }
            .testTag("recording_card_${item.id}"),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail preview with play icon
            Box(
                modifier = Modifier
                    .size(width = 110.dp, height = 66.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = "Video thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = NeonEmerald,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Metadata
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    color = TextWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = MediaStoreHelper.formatDuration(item.durationMs),
                        color = NeonEmerald,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = " • ",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                    Text(
                        text = MediaStoreHelper.formatFileSize(item.sizeBytes),
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = MediaStoreHelper.formatDate(item.dateAddedSec),
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            // Actions
            IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(onClick = onDeleteClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = CrimsonRecord.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
