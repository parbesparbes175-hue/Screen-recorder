package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.PresetType
import com.example.model.RecordingConfig
import com.example.model.VideoFps
import com.example.model.VideoResolution
import com.example.storage.MediaStoreHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("GameCapture Lite", appName)
    }

    @Test
    fun `verify performance preset parameters`() {
        val config = RecordingConfig.fromPreset(PresetType.PERFORMANCE)
        assertEquals(VideoResolution.RES_720P, config.resolution)
        assertEquals(VideoFps.FPS_60, config.fps)
        assertEquals(8_000_000, config.bitrate.bps)
    }

    @Test
    fun `verify balanced preset parameters`() {
        val config = RecordingConfig.fromPreset(PresetType.BALANCED)
        assertEquals(VideoResolution.RES_1080P, config.resolution)
        assertEquals(VideoFps.FPS_60, config.fps)
        assertEquals(12_000_000, config.bitrate.bps)
    }

    @Test
    fun `verify high quality preset parameters`() {
        val config = RecordingConfig.fromPreset(PresetType.HIGH_QUALITY)
        assertEquals(VideoResolution.RES_1080P, config.resolution)
        assertEquals(VideoFps.FPS_60, config.fps)
        assertEquals(16_000_000, config.bitrate.bps)
    }

    @Test
    fun `formatDuration formats correctly`() {
        assertEquals("00:00", MediaStoreHelper.formatDuration(0))
        assertEquals("01:05", MediaStoreHelper.formatDuration(65_000))
        assertEquals("10:30", MediaStoreHelper.formatDuration(630_000))
    }

    @Test
    fun `formatFileSize formats correctly`() {
        assertEquals("0 MB", MediaStoreHelper.formatFileSize(0))
        assertTrue(MediaStoreHelper.formatFileSize(10 * 1024 * 1024).contains("10.0 MB"))
    }
}
