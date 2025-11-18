package com.kardos.tvads

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.storage.StorageManager
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private val LOG_TAG = "USB_PLAYER"

    private var currentVideoIndex = 0
    private lateinit var videoFiles: List<File>
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializePlayer()
        findAndPlayFromUsb()
    }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(this).build()
        playerView = findViewById(R.id.player_view)
        playerView.player = player

        // 🔥 Полностью отключаем UI плеера
        playerView.useController = false

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e(LOG_TAG, "Ошибка воспроизведения: ${error.message}")
                playNextVideo()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_ENDED -> playNextVideo()
                }
            }
        })
    }

    private fun findAndPlayFromUsb() {
        Log.i(LOG_TAG, "=== ПОИСК USB ФЛЕШКИ ===")

        val usbDevice = findUsbDevice()
        if (usbDevice != null) {
            Log.i(LOG_TAG, "✓ Найдена флешка: ${usbDevice.name}")
            playFromUsbDevice(usbDevice)
        } else {
            Log.i(LOG_TAG, "✗ Флешка не найдена")
        }
    }

    private fun findUsbDevice(): File? {
        return try {
            val storageManager = getSystemService(StorageManager::class.java)

            storageManager?.storageVolumes?.forEach { volume ->
                Log.i(
                    LOG_TAG,
                    "Устройство: ${volume.getDescription(this)} — съемное=${volume.isRemovable}"
                )

                if (volume.isRemovable) {
                    val dir = volume.directory
                    if (dir != null && dir.exists() && dir.canRead()) {
                        return dir
                    }
                }
            }

            null
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Ошибка поиска USB", e)
            null
        }
    }

    private fun playFromUsbDevice(usbDevice: File) {
        val videoFolder = findVideoFolder(usbDevice)
        val searchDir = videoFolder ?: usbDevice

        videoFiles = findVideoFiles(searchDir)

        if (videoFiles.isEmpty()) {
            Log.i(LOG_TAG, "✗ Видео не найдено")
            return
        }

        currentVideoIndex = 0
        playCurrentVideo()
    }

    private fun findVideoFolder(usb: File): File? {
        val folders = listOf("video", "Video", "VIDEO", "Videos", "VIDEOS")

        folders.forEach {
            val f = File(usb, it)
            if (f.exists() && f.isDirectory && f.canRead())
                return f
        }

        return null
    }

    private fun findVideoFiles(dir: File): List<File> {
        val priority = listOf("mp4", "mkv", "m4v", "webm")
        val all = priority + listOf("avi", "mov", "wmv", "flv", "3gp")

        val p = mutableListOf<File>()
        val o = mutableListOf<File>()

        dir.listFiles()?.forEach { f ->
            if (f.isFile && f.length() > 0) {
                val ext = f.extension.lowercase()
                if (ext in all) {
                    if (ext in priority) p.add(f) else o.add(f)
                }
            }
        }

        return p.sortedBy { it.name } + o.sortedBy { it.name }
    }

    private fun playCurrentVideo() {
        if (currentVideoIndex >= videoFiles.size) currentVideoIndex = 0

        val file = videoFiles[currentVideoIndex]
        Log.i(LOG_TAG, "▶ ${file.name}")

        try {
            val item = MediaItem.fromUri(Uri.fromFile(file))
            player.setMediaItem(item)
            player.prepare()
            player.play()
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Ошибка запуска видео", e)
            playNextVideo()
        }
    }

    private fun playNextVideo() {
        currentVideoIndex++
        handler.postDelayed({
            playCurrentVideo()
        }, 1000)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacksAndMessages(null)
        player.release()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
