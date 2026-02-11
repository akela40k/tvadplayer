package com.kardos.tvads

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.storage.StorageManager
import android.util.Log
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.rotationMatrix
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.io.File
import androidx.media3.effect.*
import android.graphics.Matrix


class MainActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView

    private val LOG_TAG = "USB_PLAYER"

    private var currentVideoIndex = 0
    private lateinit var videoFiles: List<File>

    private val handler = Handler(Looper.getMainLooper())

    // ===== ПОВОРОТ =====
    private val rotations = listOf(0f, 90f)
    private var rotationState = 0
    private val PREFS_NAME = "player_settings"
    private val KEY_ROTATION = "rotation_state"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        loadRotationPreference()
        initializePlayer()
        applyRotation()

        findAndPlayFromUsb()
    }

    // ================= PLAYER =================

    private fun initializePlayer() {
        player = ExoPlayer.Builder(this).build()
        playerView = findViewById(R.id.player_view)
        playerView.player = player

        // Отключаем UI
        playerView.useController = false

        // Важно для вертикальных экранов
        playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL

        player.addListener(object : Player.Listener {

            override fun onPlayerError(error: PlaybackException) {
                Log.e(LOG_TAG, "Ошибка воспроизведения: ${error.message}")
                playNextVideo()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    playNextVideo()
                }
            }
        })
    }

    // ================= ПОВОРОТ =================

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {

        when (keyCode) {

            // MENU / Красная кнопка / OK (для эмулятора)
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_PROG_RED,
            KeyEvent.KEYCODE_DPAD_CENTER -> {

                switchSide()
                return true
            }
        }

        return super.onKeyDown(keyCode, event)
    }

    private fun switchSide() {
        rotationState = (rotationState + 1) % rotations.size
        applyRotation()
        saveRotationPreference()
    }

//    private fun applyRotation() {
//        val angle = rotations[rotationState]
//        playerView.rotation = angle
//        Log.i(LOG_TAG, "Установлена сторона: $angle°")
//    }
private fun applyRotation() {
    val angle = rotations[rotationState]

    try {
        player.setVideoEffects(
            listOf(
                MatrixTransformation { Matrix().apply { postRotate(angle) } }
            )
        )
        Log.i(LOG_TAG, "VideoEffect rotation applied: $angle°")
    } catch (e: Exception) {
        Log.e(LOG_TAG, "VideoEffect rotation failed, fallback to playerView.rotation", e)
        // fallback
        playerView.rotation = angle
        playerView.requestLayout()
    }
}


    private fun saveRotationPreference() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_ROTATION, rotationState).apply()
    }

    private fun loadRotationPreference() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        rotationState = prefs.getInt(KEY_ROTATION, 0)

        // 🔥 защита от старых значений
        if (rotationState >= rotations.size) {
            rotationState = 0
        }
    }

    // ================= USB =================

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
                    "Устройство: ${volume.getDescription(this)} — съемное=${volume.isRemovable} путь=${volume.directory}"
                )

                if (volume.isRemovable) {
                    val dir = volume.directory
                    if (dir != null && dir.exists() && dir.canRead()) {
                        Log.i(LOG_TAG, "Используем removable storage")
                        return dir
                    }
                }
            }

            // 🔥 ЭМУЛЯТОР fallback
            val internal = File("/storage/emulated/0/")
            if (internal.exists()) {
                Log.i(LOG_TAG, "Используем INTERNAL storage (эмулятор)")
                return internal
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

        if (currentVideoIndex >= videoFiles.size)
            currentVideoIndex = 0

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

    // ================= LIFECYCLE =================

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