package com.neonlightning.dvdserver

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import com.bumptech.glide.Glide
import com.neonlightning.dvdserver.databinding.ActivityPlayerBinding
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

@UnstableApi
class PlayerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null

    private var dvdName = ""
    private var currentIndex = 0
    private var autoplay = false
    private var sortOrder = 0
    private var themeName = "Android TV"
    private var cacheMode = 0

    private var currentAudioIndex = 0
    private var currentSubtitleId: String? = null
    private var sortedTitles: List<DvdTitle> = emptyList()
    private var currentResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT

    private var currentUser = "Guest"
    private var savedPositionMs = 0L
    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            player?.let { p ->
                if (p.isPlaying) {
                    val posSec = p.currentPosition / 1000.0
                    val durSec = p.duration.let { if (it > 0) it / 1000.0 else sortedTitles.getOrNull(currentIndex)?.duration ?: 0.0 }
                    thread {
                        Api.saveProgress(currentUser, dvdName, currentIndex, posSec, durSec)
                    }
                }
            }
            progressHandler.postDelayed(this, 5000L)
        }
    }

    data class SubtitleCue(val startTimeMs: Long, val endTimeMs: Long, val imageUrl: String?, val text: String?)
    private var subtitleCues: List<SubtitleCue> = emptyList()
    private val subtitleHandler = Handler(Looper.getMainLooper())
    private val subtitleRunnable = object : Runnable {
        override fun run() {
            player?.let { p ->
                if (p.isPlaying) {
                    val pos = p.currentPosition
                    val cue = subtitleCues.find { pos in it.startTimeMs..it.endTimeMs }
                    if (cue != null) {
                        binding.subtitleContainer.visibility = View.VISIBLE
                        if (cue.imageUrl != null) {
                            binding.subtitleOverlayView.visibility = View.VISIBLE
                            binding.subtitleTextView.visibility = View.GONE
                            val fullImgUrl = Api.fullUrl(cue.imageUrl) ?: cue.imageUrl
                            Glide.with(this@PlayerActivity)
                                .load(fullImgUrl)
                                .into(binding.subtitleOverlayView)
                        } else if (cue.text != null) {
                            binding.subtitleOverlayView.visibility = View.GONE
                            binding.subtitleTextView.visibility = View.VISIBLE
                            binding.subtitleTextView.text = cue.text
                        }
                    } else {
                        binding.subtitleContainer.visibility = View.GONE
                    }
                }
            }
            subtitleHandler.postDelayed(this, 100L)
        }
    }

    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { binding.topBar.visibility = View.GONE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences("dvd_server", MODE_PRIVATE)
        Api.baseUrl = prefs.getString("base_url", Api.baseUrl) ?: Api.baseUrl
        currentUser = prefs.getString("current_user", "Guest") ?: "Guest"

        dvdName = intent.getStringExtra("dvdName") ?: ""
        currentIndex = intent.getIntExtra("titleIndexInSortedList", 0)
        autoplay = intent.getBooleanExtra("autoplay", false)
        sortOrder = intent.getIntExtra("sortOrder", 0)
        themeName = intent.getStringExtra("theme") ?: "Android TV"
        cacheMode = intent.getIntExtra("cacheMode", 0)
        currentAudioIndex = intent.getIntExtra("initialAudio", 0)
        currentSubtitleId = intent.getStringExtra("initialSubtitleId")

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding.root.setBackgroundColor(Color.BLACK)
        applyThemeToUI(themeName)

        // Configure PlayerView subtitle view to handle image/HTML output cleanly
        binding.playerView.subtitleView?.apply {
            setStyle(
                CaptionStyleCompat(
                    Color.WHITE,
                    Color.TRANSPARENT,
                    Color.TRANSPARENT,
                    androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                    Color.BLACK,
                    null
                )
            )
            setApplyEmbeddedStyles(false)
        }

        binding.backButton.setOnClickListener { finish() }
        binding.audioButton.setOnClickListener { showAudioDialog() }
        binding.subtitleButton.setOnClickListener { showSubtitleDialog() }
        binding.scaleButton.setOnClickListener { toggleScale() }

        showControls(true)
    }

    override fun onStart() {
        super.onStart()
        if (player == null) {
            loadDataAndStart()
        }
    }

    private fun showControls(requestFocus: Boolean = false) {
        binding.topBar.visibility = View.VISIBLE
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, 5000L)
        if (requestFocus && !binding.topBar.hasFocus()) {
            binding.backButton.requestFocus()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode != KeyEvent.KEYCODE_BACK) {
            showControls(false)
        }
        return super.onKeyDown(keyCode, event)
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (binding.topBar.visibility == View.VISIBLE) {
            if (binding.backButton.isFocused) {
                super.onBackPressed()
            } else {
                binding.topBar.visibility = View.GONE
                hideHandler.removeCallbacks(hideRunnable)
            }
        } else {
            showControls()
        }
    }

    private fun applyThemeToUI(theme: String) {
        val colors = when (theme) {
            "Terminal" -> Triple("#000000", "#050a05", "#00ff41")
            "Midnight" -> Triple("#000000", "#0d0d0d", "#a78bfa")
            "Light" -> Triple("#f0f2f8", "#ffffff", "#2563eb")
            "Solarized" -> Triple("#002b36", "#073642", "#b58900")
            "Dracula" -> Triple("#282a36", "#21222c", "#bd93f9")
            "Nord" -> Triple("#2e3440", "#2b303b", "#88c0d0")
            "Gruvbox" -> Triple("#282828", "#1d2021", "#d79921")
            "Tokyo Night" -> Triple("#1a1b26", "#16161e", "#7aa2f7")
            "Catppuccin" -> Triple("#1e1e2e", "#181825", "#cba6f7")
            "Rosé Pine" -> Triple("#191724", "#1f1d2e", "#c4a7e7")
            "Forest" -> Triple("#0a1410", "#0f1d16", "#86efac")
            "Cyberpunk" -> Triple("#0d0221", "#10042a", "#00f5ff")
            "Hot Dog Stand" -> Triple("#FFFF00", "#000000", "#FF0000")
            else -> Triple("#101014", "#1B1B22", "#E91E63")
        }

        val panelColor = Color.parseColor(colors.second)
        val accentColor = Color.parseColor(colors.third)
        binding.topBar.setBackgroundColor(panelColor)

        fun btnBg() = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 0f
            })
            addState(intArrayOf(), GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = 0f
            })
        }
        val txt = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
            intArrayOf(Color.BLACK, accentColor)
        )

        listOf(binding.backButton, binding.audioButton, binding.scaleButton, binding.subtitleButton).forEach {
            it.background = btnBg()
            it.setTextColor(txt)
        }
        binding.playerTitle.setTextColor(Color.WHITE)
    }

    private fun loadDataAndStart() {
        thread {
            try {
                val raw = Api.loadDvd(dvdName)
                sortedTitles = when (sortOrder) {
                    1 -> raw.sortedBy { it.duration }
                    2 -> raw.sortedByDescending { it.duration }
                    else -> raw.sortedBy { it.file }
                }
                val progressMap = Api.getProgress(currentUser, dvdName)
                val titleProgress = progressMap[currentIndex]
                if (titleProgress != null && titleProgress.position > 1.0 && titleProgress.watched == 0) {
                    savedPositionMs = (titleProgress.position * 1000).toLong()
                }

                runOnUiThread {
                    val title = sortedTitles.getOrNull(currentIndex)
                    if (title != null) {
                        initPlayer()
                    } else {
                        binding.playerTitle.text = "Error: Title missing"
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { binding.playerTitle.text = "Load Failed: ${e.message}" }
            }
        }
    }

    private fun initPlayer() {
        if (player != null) return
        val p = ExoPlayer.Builder(this).build()
        player = p
        binding.playerView.player = p
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    hideHandler.postDelayed(hideRunnable, 3000L)
                    progressHandler.post(progressRunnable)
                } else {
                    showControls(false)
                    progressHandler.removeCallbacks(progressRunnable)
                    player?.let { p ->
                        val posSec = p.currentPosition / 1000.0
                        val durSec = p.duration.let { if (it > 0) it / 1000.0 else sortedTitles.getOrNull(currentIndex)?.duration ?: 0.0 }
                        thread { Api.saveProgress(currentUser, dvdName, currentIndex, posSec, durSec) }
                    }
                }
            }
            override fun onPlayerError(e: PlaybackException) {
                val msg = when (e.errorCode) {
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "Network Connection Failed"
                    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "File Not Found on Server"
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "Codec Not Supported"
                    else -> e.localizedMessage ?: "Playback Error"
                }
                Toast.makeText(this@PlayerActivity, "Error: $msg", Toast.LENGTH_LONG).show()
                AppLogger.e("ExoPlayer error [${e.errorCode}]: ${e.message}", e)
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    if (autoplay && currentIndex + 1 < sortedTitles.size) {
                        currentIndex++
                        prepareMedia()
                    } else if (cacheMode == 2) {
                        thread { Api.clearCache(dvdName) }
                    }
                }
            }
        })
        prepareMedia()
    }

    private fun prepareMedia() {
        val title = sortedTitles.getOrNull(currentIndex) ?: return
        binding.playerTitle.text = "$dvdName • ${title.file}"

        val url = Api.streamUrl(title.index, currentAudioIndex)
        val builder = MediaItem.Builder().setUri(Uri.parse(url))

        val sub = title.subtitles.find { it.id == currentSubtitleId }
        subtitleCues = emptyList()
        subtitleHandler.removeCallbacks(subtitleRunnable)
        binding.subtitleContainer.visibility = View.GONE

        if (sub != null && sub.playable) {
            val subtitleUrl = Api.subtitleUrl(title.index, sub.id)
            val isSrt = sub.codec.contains("subrip", true) || sub.id.endsWith(".srt", true) || sub.filename?.endsWith(".srt", true) == true

            thread {
                try {
                    val conn = URL(subtitleUrl).openConnection() as HttpURLConnection
                    conn.connectTimeout = 5000
                    conn.readTimeout = 10000
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    val parsed = if (isSrt) parseSrt(text) else parseVtt(text)
                    if (parsed.isNotEmpty()) {
                        runOnUiThread {
                            subtitleCues = parsed
                            subtitleHandler.post(subtitleRunnable)
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.e("Failed to load/parse subtitles", e)
                }
            }
        }

        val currentPos = if (savedPositionMs > 0) {
            val pos = savedPositionMs
            savedPositionMs = 0L
            pos
        } else {
            player?.currentPosition ?: C.TIME_UNSET
        }
        player?.let { p ->
            p.setMediaItem(builder.build())
            p.prepare()
            if (currentPos != C.TIME_UNSET && currentPos > 0) {
                p.seekTo(currentPos)
            }
            p.play()
        }

        updateButtonLabels()
    }

    private fun parseVtt(vttText: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        val lines = vttText.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.contains("-->")) {
                val parts = line.split("-->").map { it.trim() }
                if (parts.size >= 2) {
                    val startMs = parseTimestamp(parts[0])
                    val endMs = parseTimestamp(parts[1].substringBefore(" "))
                    i++
                    val imageUrlLines = mutableListOf<String>()
                    while (i < lines.size && lines[i].isNotBlank()) {
                        imageUrlLines.add(lines[i].trim())
                        i++
                    }
                    val rawContent = imageUrlLines.joinToString(" ")
                    val imageUrl = extractUrlFromCue(rawContent)
                    if (imageUrl != null && startMs >= 0 && endMs > startMs) {
                        cues.add(SubtitleCue(startMs, endMs, imageUrl, null))
                    }
                }
            }
            i++
        }
        return cues
    }

    private fun parseSrt(srtText: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        val blocks = srtText.split(Regex("\\r?\\n\\r?\\n"))
        for (block in blocks) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.size >= 2) {
                val timeLineIndex = if (lines[0].all { it.isDigit() }) 1 else 0
                if (lines.size > timeLineIndex && lines[timeLineIndex].contains("-->")) {
                    val parts = lines[timeLineIndex].split("-->").map { it.trim() }
                    if (parts.size >= 2) {
                        val startMs = parseTimestamp(parts[0].replace(',', '.'))
                        val endMs = parseTimestamp(parts[1].substringBefore(" ").replace(',', '.'))
                        val textLines = lines.drop(timeLineIndex + 1)
                        val text = textLines.joinToString("\n")
                        if (startMs >= 0 && endMs > startMs && text.isNotEmpty()) {
                            cues.add(SubtitleCue(startMs, endMs, null, text))
                        }
                    }
                }
            }
        }
        return cues
    }

    private fun parseTimestamp(ts: String): Long {
        try {
            val parts = ts.split(":")
            if (parts.size == 3) {
                val hours = parts[0].toLong()
                val minutes = parts[1].toLong()
                val secParts = parts[2].split(".")
                val seconds = secParts[0].toLong()
                val millis = if (secParts.size > 1) secParts[1].take(3).padEnd(3, '0').toLong() else 0L
                return hours * 3600000 + minutes * 60000 + seconds * 1000 + millis
            } else if (parts.size == 2) {
                val minutes = parts[0].toLong()
                val secParts = parts[1].split(".")
                val seconds = secParts[0].toLong()
                val millis = if (secParts.size > 1) secParts[1].take(3).padEnd(3, '0').toLong() else 0L
                return minutes * 60000 + seconds * 1000 + millis
            }
        } catch (e: Exception) {}
        return -1L
    }

    private fun extractUrlFromCue(content: String): String? {
        if (content.contains("src=\"")) {
            val start = content.indexOf("src=\"") + 5
            val end = content.indexOf("\"", start)
            if (end > start) {
                return content.substring(start, end)
            }
        }
        val trimmed = content.trim()
        if ((trimmed.endsWith(".png", true) || trimmed.endsWith(".jpg", true) || trimmed.contains("/api/dvd/")) && !trimmed.contains(" ")) {
            return trimmed
        }
        return null
    }

    private fun updateButtonLabels() {
        val title = sortedTitles.getOrNull(currentIndex) ?: return
        val audioTrack = title.audio.find { it.index == currentAudioIndex }
        binding.audioButton.text = audioTrack?.title ?: "Audio"

        val sub = title.subtitles.find { it.id == currentSubtitleId }
        binding.subtitleButton.text = sub?.title ?: "Off"
    }

    private fun showAudioDialog() {
        val t = sortedTitles.getOrNull(currentIndex) ?: return
        val labels = t.audio.map { "${if (it.index == currentAudioIndex) "✓ " else ""}${it.title}" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Audio Track").setItems(labels) { _, w ->
            currentAudioIndex = t.audio[w].index
            prepareMedia()
        }.show()
    }

    private fun showSubtitleDialog() {
        val t = sortedTitles.getOrNull(currentIndex) ?: return
        val playableSubs = t.subtitles.filter { it.playable }
        val labels = mutableListOf("Off")
        labels.addAll(playableSubs.map { it.title })

        AlertDialog.Builder(this).setTitle("Subtitles").setItems(labels.toTypedArray()) { _, w ->
            currentSubtitleId = if (w == 0) null else playableSubs[w - 1].id
            prepareMedia()
            updateButtonLabels()
        }.show()
    }

    private fun toggleScale() {
        currentResizeMode = when (currentResizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        binding.playerView.resizeMode = currentResizeMode
        binding.scaleButton.text = when(currentResizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> "Scale: Fit"
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Scale: Zoom"
            else -> "Scale: Fill"
        }
    }

    override fun onStop() {
        progressHandler.removeCallbacks(progressRunnable)
        player?.let { p ->
            val posSec = p.currentPosition / 1000.0
            val durSec = p.duration.let { if (it > 0) it / 1000.0 else sortedTitles.getOrNull(currentIndex)?.duration ?: 0.0 }
            try {
                Api.saveProgress(currentUser, dvdName, currentIndex, posSec, durSec)
            } catch (e: Exception) {}
        }
        super.onStop()
        player?.release()
        player = null
    }

    override fun onDestroy() {
        if (cacheMode == 1) thread { Api.clearCache(dvdName) }
        progressHandler.removeCallbacks(progressRunnable)
        player?.let { p ->
            val posSec = p.currentPosition / 1000.0
            val durSec = p.duration.let { if (it > 0) it / 1000.0 else sortedTitles.getOrNull(currentIndex)?.duration ?: 0.0 }
            try {
                Api.saveProgress(currentUser, dvdName, currentIndex, posSec, durSec)
            } catch (e: Exception) {}
        }
        player?.release()
        player = null
        hideHandler.removeCallbacks(hideRunnable)
        super.onDestroy()
    }
}