package com.kardos.tvads

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import android.os.Build
import android.os.Environment
import android.preference.PreferenceManager
import android.Manifest
import android.provider.Settings
import com.kardos.tvads.boot.SettingsManager
import com.kardos.tvads.boot.SettingsManagerConstants


class MainActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView

    private val LOG_TAG = "USB_PLAYER"
    private val REQ_STORAGE = 1001
    private var playbackStarted = false
    private val autostart = true

    private var currentVideoIndex = 0
    private lateinit var videoFiles: List<File>

    private val handler = Handler(Looper.getMainLooper())

    // ===== ПОВОРОТ =====
    private val rotations = listOf(0f, 180f)
    private var rotationState = 0
    private val PREFS_NAME = "player_settings"
    private val KEY_ROTATION = "rotation_state"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ensureAutostartDefaults()

        loadRotationPreference()
        initializePlayer()
        applyRotation()

        ensureStorageAccessThenStart()
    }

    private fun ensureAutostartDefaults() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        SettingsManager(this).setBoolean(SettingsManagerConstants.BOOT_APP_ENABLED, autostart)
        if (!prefs.contains(SettingsManagerConstants.LAUNCH_LIVE_CHANNELS)) {
            SettingsManager(this).setBoolean(SettingsManagerConstants.LAUNCH_LIVE_CHANNELS, false)
        }
        if (!prefs.contains(SettingsManagerConstants.ON_WAKEUP)) {
            SettingsManager(this).setBoolean(SettingsManagerConstants.ON_WAKEUP, autostart)
        }
        if (!prefs.contains(SettingsManagerConstants.LAUNCH_ACTIVITY)) {
            SettingsManager(this).setString(SettingsManagerConstants.LAUNCH_ACTIVITY, packageName)
        }

        if (autostart && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !Settings.canDrawOverlays(this)) {
            // Allow background activity start on Android 10+ when granted
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
        }
    }

    private fun ensureStorageAccessThenStart() {
        if (playbackStarted) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            // Request "All files access" via settings screen
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
            return
        }

        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
        } else {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (needed.isNotEmpty()) {
            requestPermissions(needed.toTypedArray(), REQ_STORAGE)
            return
        }

        playbackStarted = true
        findAndPlayFromUsb()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_STORAGE) {
            val granted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (granted) {
                ensureStorageAccessThenStart()
            } else {
                Log.e(LOG_TAG, "Storage permission denied")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ensureStorageAccessThenStart()
    }

    // ================= PLAYER =================

    private fun initializePlayer() {
        player = ExoPlayer.Builder(this).build()
        playerView = findViewById(R.id.player_view)
        playerView.player = player

        // Отключаем UI
        playerView.useController = false

        // Важно для вертикальных экранов
        @androidx.media3.common.util.UnstableApi
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
    @androidx.media3.common.util.UnstableApi
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
