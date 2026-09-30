package com.example.storage

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.model.RecordingItem
import java.io.File
import java.io.FileDescriptor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MediaStoreHelper {
    private const val TAG = "MediaStoreHelper"
    private const val RELATIVE_PATH = "Movies/GameCapture"

    /**
     * Creates a new MediaStore video entry in Movies/GameCapture and returns its URI and FileDescriptor.
     */
    fun createRecordingFile(context: Context): Pair<Uri?, FileDescriptor?> {
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val fileName = "GameCapture_$timestamp.mp4"

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Video.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, RELATIVE_PATH)
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        try {
            val fileUri = resolver.insert(collectionUri, values) ?: return Pair(null, null)
            val pfd = resolver.openFileDescriptor(fileUri, "rw")
            return Pair(fileUri, pfd?.fileDescriptor)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create MediaStore video file: ${e.message}", e)
            return Pair(null, null)
        }
    }

    /**
     * Finalizes the video entry in MediaStore by releasing IS_PENDING.
     */
    fun finalizeRecordingFile(context: Context, uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                context.contentResolver.update(uri, values, null, null)
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing IS_PENDING: ${e.message}", e)
            }
        }
    }

    /**
     * Queries all recorded videos in Movies/GameCapture.
     */
    fun queryRecordings(context: Context): List<RecordingItem> {
        val items = mutableListOf<RecordingItem>()
        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT
        )

        val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Video.Media.DISPLAY_NAME} LIKE ?"
        } else {
            "${MediaStore.Video.Media.DATA} LIKE ? OR ${MediaStore.Video.Media.DISPLAY_NAME} LIKE ?"
        }
        val selectionArgs = arrayOf("%GameCapture%", "GameCapture_%.mp4")
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(collectionUri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Recording.mp4"
                    val duration = cursor.getLong(durationCol)
                    val size = cursor.getLong(sizeCol)
                    val date = cursor.getLong(dateCol)
                    val width = cursor.getInt(widthCol)
                    val height = cursor.getInt(heightCol)
                    val contentUri = ContentUris.withAppendedId(collectionUri, id)

                    items.add(
                        RecordingItem(
                            id = id,
                            uri = contentUri,
                            displayName = name,
                            durationMs = duration,
                            sizeBytes = size,
                            dateAddedSec = date,
                            width = width,
                            height = height
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying recordings: ${e.message}", e)
        }
        return items
    }

    /**
     * Deletes a recording by Uri.
     */
    fun deleteRecording(context: Context, uri: Uri): Boolean {
        return try {
            val rows = context.contentResolver.delete(uri, null, null)
            rows > 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete recording: ${e.message}", e)
            false
        }
    }

    fun playRecording(context: Context, uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Cannot launch player: ${e.message}", e)
        }
    }

    fun shareRecording(context: Context, uri: Uri, title: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Share PUBG Gameplay").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Cannot share recording: ${e.message}", e)
        }
    }

    fun formatDuration(durationMs: Long): String {
        val totalSec = durationMs / 1000
        val minutes = totalSec / 60
        val seconds = totalSec % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return if (mb >= 1024) {
            val gb = mb / 1024.0
            String.format(Locale.US, "%.2f GB", gb)
        } else {
            String.format(Locale.US, "%.1f MB", mb)
        }
    }

    fun formatDate(timestampSec: Long): String {
        val date = Date(timestampSec * 1000)
        return SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(date)
    }
}
