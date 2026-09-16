package com.neonlightning.dvdserver

import android.R
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import com.neonlightning.dvdserver.databinding.ActivityPlayerBinding
import kotlin.concurrent.thread

@UnstableApi
class PlayerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPlayerBinding
    private lateinit var player: ExoPlayer

    private var dvdName = ""
    private var currentIndexInSortedList = 0
    private var autoplay = false
    private var sortOrder = 0
    private var themeName = "Android TV"
    private var cacheMode = 0 // 0: Manual Only, 1: On File Close, 2: On File End, 3: On Selection Change
    private var currentResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT

    private var currentAudioIndex = 0
    private var currentSubtitle: SubtitleTrack? = null
    private var sortedTitles: List<DvdTitle> = emptyList()
    private var currentTitle: DvdTitle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dvdName = intent.getStringExtra("dvdName") ?: "DVD"
        currentIndexInSortedList = intent.getIntExtra("titleIndexInSortedList", 0)
        autoplay = intent.getBooleanExtra("autoplay", false)
        sortOrder = intent.getIntExtra("sortOrder", 0)
        themeName = intent.getStringExtra("theme") ?: "Android TV"
        cacheMode = intent.getIntExtra("cacheMode", 0)
        
        currentAudioIndex = intent.getIntExtra("initialAudio", 0)
        val subId = intent.getStringExtra("initialSubtitleId")

        // Player background is ALWAYS black
        binding.root.setBackgroundColor(Color.BLACK)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyThemeToUI(themeName)

        binding.backButton.setOnClickListener { finish() }
        binding.audioButton.setOnClickListener { showAudioDialog() }
        binding.scaleButton.setOnClickListener { toggleScale() }
        binding.subtitleButton.setOnClickListener { showSubtitleDialog() }

        loadTitlesAndStart(subId)
    }

    private fun applyThemeToUI(theme: String) {
        val (_, panel, accent) = when (theme) {
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
        
        val panelColor = Color.parseColor(panel)
        val accentColor = Color.parseColor(accent)
        
        binding.topBar.setBackgroundColor(panelColor)
        
        // --- High Visibility Player Buttons ---
        fun createPlayerButtonBg(accent: Int): StateListDrawable {
            val focused = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 0f
            }
            val normal = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = 0f
            }
            return StateListDrawable().apply {
                addState(intArrayOf(R.attr.state_focused), focused)
                addState(intArrayOf(), normal)
            }
        }

        val buttonTextStates = ColorStateList(
            arrayOf(intArrayOf(R.attr.state_focused), intArrayOf()),
            intArrayOf(Color.BLACK, accentColor)
        )

        listOf(binding.backButton, binding.audioButton, binding.scaleButton, binding.subtitleButton).forEach { btn ->
            btn.background = createPlayerButtonBg(accentColor)
            btn.setTextColor(buttonTextStates)
        }
        
        binding.playerTitle.setTextColor(Color.WHITE)
    }

    private fun loadTitlesAndStart(initialSubId: String?) {
        Thread {
            try {
                val rawTitles = Api.loadDvd(dvdName)
                
                // Re-sort the list exactly as MainActivity did
                sortedTitles = when (sortOrder) {
                    1 -> rawTitles.sortedBy { it.duration }
                    2 -> rawTitles.sortedByDescending { it.duration }
                    else -> rawTitles.sortedBy { it.file }
                }
                
                currentTitle = sortedTitles.getOrNull(currentIndexInSortedList)
                
                initialSubId?.let { id ->
                    currentSubtitle = currentTitle?.subtitles?.find { it.id == id }
                }

                runOnUiThread { 
                    if (currentTitle != null) {
                        binding.playerTitle.text = "$dvdName • ${currentTitle?.file}"
                        startPlayback() 
                    } else {
                        binding.playerTitle.text = "Error: Title not found"
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    binding.playerTitle.text = "Could not load DVD: ${e.message}"
                }
            }
        }.start()
    }

    private fun startPlayback() {
        player = ExoPlayer.Builder(this).build()
        binding.playerView.player = player

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                binding.topBar.visibility = if (isPlaying) View.GONE else View.VISIBLE
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    if (autoplay) playNextTitle()
                    
                    // "On File End" logic
                    if (cacheMode == 2) {
                        thread { Api.clearCache(dvdName) }
                    }
                }
            }
        })

        prepareMedia()
    }

    private fun playNextTitle() {
        if (currentIndexInSortedList + 1 < sortedTitles.size) {
            currentIndexInSortedList++
            currentTitle = sortedTitles[currentIndexInSortedList]
            
            val filename = currentTitle?.file ?: ""
            binding.playerTitle.text = "$dvdName • $filename"
            
            // Reset tracks for the next title in sequence
            currentAudioIndex = 0
            currentSubtitle = null
            
            Toast.makeText(this, "Autoplay: $filename", Toast.LENGTH_SHORT).show()
            prepareMedia()
        } else {
            Toast.makeText(this, "End of DVD", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun prepareMedia() {
        val title = currentTitle ?: return
        val subtitle = currentSubtitle
        
        // CRITICAL: Use title.index (original server index) for API calls!
        val builder = MediaItem.Builder()
            .setUri(Uri.parse(Api.streamUrl(title.index, currentAudioIndex)))

        if (subtitle != null) {
            val config = MediaItem.SubtitleConfiguration.Builder(
                Uri.parse(Api.subtitleUrl(title.index, subtitle.id))
            )
                .setMimeType(MimeTypes.TEXT_VTT)
                .setLanguage(subtitle.language.takeIf { it != "und" })
                .setLabel(subtitle.title)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            builder.setSubtitleConfigurations(listOf(config))
        }

        player.setMediaItem(builder.build())
        player.prepare()
        player.playWhenReady = true
    }

    private fun showAudioDialog() {
        val title = currentTitle ?: return
        if (title.audio.isEmpty()) {
            AlertDialog.Builder(this).setTitle("Audio").setMessage("No audio tracks found.")
                .setPositiveButton("OK", null).show()
            return
        }

        val labels = title.audio.map { track ->
            "${if (track.index == currentAudioIndex) "✓ " else ""}${track.title}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Audio track")
            .setItems(labels) { _, which ->
                val track = title.audio[which]
                if (track.index != currentAudioIndex) {
                    currentAudioIndex = track.index
                    prepareMedia()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSubtitleDialog() {
        val title = currentTitle ?: return
        val options = mutableListOf("Off")
        options += title.subtitles.filter { it.playable }.map { it.title }

        AlertDialog.Builder(this)
            .setTitle("Subtitles")
            .setItems(options.toTypedArray()) { _, which ->
                currentSubtitle = if (which == 0) null else title.subtitles.filter { it.playable }[which - 1]
                prepareMedia()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toggleScale() {
        currentResizeMode = when (currentResizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        
        binding.playerView.resizeMode = currentResizeMode
        
        val label = when (currentResizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> "Scale: Fit"
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Scale: Zoom"
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Scale: Stretch"
            else -> "Scale"
        }
        binding.scaleButton.text = label
    }

    override fun onDestroy() {
        // "On File Close" logic
        if (cacheMode == 1) {
            thread { Api.clearCache(dvdName) }
        }

        if (::player.isInitialized) {
            player.release()
        }
        super.onDestroy()
    }
}
