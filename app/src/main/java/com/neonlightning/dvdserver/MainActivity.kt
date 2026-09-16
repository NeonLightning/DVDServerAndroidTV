package com.neonlightning.dvdserver

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.neonlightning.dvdserver.databinding.ActivityMainBinding
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: DvdAdapter
    
    private var rawTitles: List<DvdTitle> = emptyList()
    private var sortedTitles: List<DvdTitle> = emptyList()
    private var selectedDvd: Dvd? = null
    private var currentSortOrder = 0 // 0: Name, 1: Shortest, 2: Longest
    private val sortOptions = listOf("By Name", "Shortest First", "Longest First")
    private var currentThemeName = "Android TV"
    
    private var selectedTitleIdx = -1
    private var selectedAudioIdx = 0
    private var selectedSubIdx = 0 // 0 = Off

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = DvdAdapter { dvd -> onDvdSelected(dvd) }
        binding.dvdRecycler.layoutManager = LinearLayoutManager(this)
        binding.dvdRecycler.adapter = adapter

        binding.refreshButton.setOnClickListener { refresh() }
        binding.playButton.setOnClickListener { playCurrentSelection() }
        binding.sortButton.setOnClickListener { showSortDialog() }
        binding.settingsButton.setOnClickListener { 
            startActivity(Intent(this, SettingsActivity::class.java)) 
        }

        binding.clearCacheBtn.setOnClickListener {
            Toast.makeText(this, "Clearing server cache...", Toast.LENGTH_SHORT).show()
            thread { try { Api.clearCache() } catch (e: Exception) {} }
        }
        
        binding.titleSelectBtn.setOnClickListener { showTitleDialog() }
        binding.audioSelectBtn.setOnClickListener { showAudioDialog() }
        binding.subtitleSelectBtn.setOnClickListener { showSubtitleDialog() }

        binding.refreshButton.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        loadPreferences()
        refresh()
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences("dvd_server", MODE_PRIVATE)
        Api.baseUrl = prefs.getString("base_url", "http://192.168.1.100:4251") ?: "http://192.168.1.100:4251"
        
        currentThemeName = prefs.getString("theme", "Android TV") ?: "Android TV"
        applyTheme(currentThemeName)

        currentSortOrder = prefs.getInt("sort_order", 0)
        binding.sortButton.text = sortOptions[currentSortOrder]

        binding.autoplayToggle.isChecked = prefs.getBoolean("autoplay", false)
        binding.autoplayToggle.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("autoplay", isChecked).apply()
        }
    }

    private fun getDialogTheme(): Int {
        return when (currentThemeName) {
            "Hot Dog Stand" -> R.style.Theme_DVDServer_Dialog_HotDog
            "Light" -> R.style.Theme_DVDServer_Dialog_Light
            else -> R.style.Theme_DVDServer_Dialog
        }
    }

    private fun showSortDialog() {
        showThemedDialog("Sort Titles", sortOptions) { which ->
            currentSortOrder = which
            getSharedPreferences("dvd_server", MODE_PRIVATE)
                .edit().putInt("sort_order", which).apply()
            binding.sortButton.text = sortOptions[which]
            if (rawTitles.isNotEmpty()) setupTitleSelection()
        }
    }

    private fun showThemedDialog(titleStr: String, options: List<String>, onSelect: (Int) -> Unit) {
        val palette = getThemePalette(currentThemeName)
        val accentColor = palette.third.toColorInt()
        val textColor = palette.fourth.toColorInt()
        val bgColor = palette.first.toColorInt()
        val isAccentBright = isColorBright(accentColor)

        val builder = AlertDialog.Builder(this, getDialogTheme())
        builder.setTitle(titleStr)
        
        val dialogAdapter = object : ArrayAdapter<String>(this, R.layout.item_dialog_list, options) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.text = getItem(position)
                
                view.setTextColor(ColorStateList(
                    arrayOf(
                        intArrayOf(android.R.attr.state_focused),
                        intArrayOf(android.R.attr.state_selected),
                        intArrayOf(android.R.attr.state_activated),
                        intArrayOf()
                    ),
                    intArrayOf(
                        if (isAccentBright) Color.BLACK else Color.WHITE,
                        if (isAccentBright) Color.BLACK else Color.WHITE,
                        if (isAccentBright) Color.BLACK else Color.WHITE,
                        textColor
                    )
                ))
                
                val focused = GradientDrawable().apply { 
                    setColor(accentColor)
                    val borderColor = if (isAccentBright) Color.BLACK else Color.WHITE
                    setStroke(6, borderColor) 
                }
                val normal = ColorDrawable(Color.TRANSPARENT)
                
                view.background = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), focused)
                    addState(intArrayOf(android.R.attr.state_selected), focused)
                    addState(intArrayOf(android.R.attr.state_activated), focused)
                    addState(intArrayOf(), normal)
                }
                
                return view
            }
        }

        builder.setAdapter(dialogAdapter) { _, which -> onSelect(which) }
        val dialog = builder.create()
        
        dialog.setOnShowListener {
            val window = dialog.window
            if (window != null) {
                // Find all containers and apply background
                val decView = window.decorView
                decView.setBackgroundColor(bgColor)
                
                // Explicitly find and theme the title
                val titleId = resources.getIdentifier("alertTitle", "id", packageName)
                if (titleId != 0) {
                    val titleView = dialog.findViewById<TextView>(titleId)
                    titleView?.setTextColor(accentColor)
                }
                
                // Theme the message/content if any
                val messageId = resources.getIdentifier("message", "id", packageName)
                if (messageId != 0) {
                    val messageView = dialog.findViewById<TextView>(messageId)
                    messageView?.setTextColor(textColor)
                }
                
                // Theme list view
                dialog.listView.setBackgroundColor(bgColor)
                dialog.listView.selector = ColorDrawable(Color.TRANSPARENT)
            }
        }
        
        dialog.show()
    }

    private fun isColorBright(color: Int): Boolean {
        val darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
        return darkness < 0.5
    }

    private fun getThemePalette(name: String): Quintuple<String, String, String, String, String> {
        return when (name) {
            "Terminal" -> Quintuple("#000000", "#050a05", "#00ff41", "#00ff41", "#00aa33")
            "Midnight" -> Quintuple("#000000", "#0d0d0d", "#a78bfa", "#d8d8d8", "#888888")
            "Light" -> Quintuple("#F0F2F8", "#FFFFFF", "#2563EB", "#1A1F2E", "#5A6070")
            "Solarized" -> Quintuple("#002b36", "#073642", "#b58900", "#93a1a1", "#657b83")
            "Dracula" -> Quintuple("#282a36", "#21222c", "#bd93f9", "#f8f8f2", "#6272a4")
            "Nord" -> Quintuple("#2e3440", "#2b303b", "#88c0d0", "#d8dee9", "#8f9db0")
            "Gruvbox" -> Quintuple("#282828", "#1d2021", "#d79921", "#ebdbb2", "#a89984")
            "Tokyo Night" -> Quintuple("#1a1b26", "#16161e", "#7aa2f7", "#c0caf5", "#7f8bb0")
            "Catppuccin" -> Quintuple("#1e1e2e", "#181825", "#cba6f7", "#cdd6f4", "#a6adc8")
            "Rosé Pine" -> Quintuple("#191724", "#1f1d2e", "#c4a7e7", "#e0def4", "#908caa")
            "Forest" -> Quintuple("#0a1410", "#0f1d16", "#86efac", "#d4e0d8", "#8ea895")
            "Cyberpunk" -> Quintuple("#0d0221", "#10042a", "#00f5ff", "#e0d4ff", "#a78bfa")
            "Hot Dog Stand" -> Quintuple("#FFFF00", "#FFFF00", "#FF0000", "#000000", "#000000")
            else -> Quintuple("#101014", "#1B1B22", "#E91E63", "#FFFFFF", "#B9B9C2")
        }
    }

    private fun applyTheme(themeName: String) {
        val (bg, panel, accent, text, textDim) = getThemePalette(themeName)
        
        val bgColor = bg.toColorInt()
        val panelColor = panel.toColorInt()
        val accentColor = accent.toColorInt()
        val textColor = text.toColorInt()
        val textDimColor = textDim.toColorInt()

        binding.root.setBackgroundColor(bgColor)
        binding.sidebar.setBackgroundColor(panelColor)
        binding.appTitle.setTextColor(accentColor)
        
        adapter.textColor = textColor
        adapter.textDimColor = textDimColor
        adapter.accentColor = accentColor
        adapter.backgroundColor = bgColor
        adapter.panelColor = panelColor
        adapter.notifyDataSetChanged()
        
        fun createButtonBg(focusedColor: Int, normalColor: Int, isOutlined: Boolean = false): StateListDrawable {
            val focused = GradientDrawable().apply {
                setColor(focusedColor)
                if (isOutlined) setStroke(4, Color.BLACK)
                cornerRadius = 0f
            }
            val normal = GradientDrawable().apply {
                setColor(normalColor)
                if (isOutlined) setStroke(2, Color.BLACK)
                cornerRadius = 0f
            }
            return StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), focused)
                addState(intArrayOf(), normal)
            }
        }

        val isHotDog = themeName == "Hot Dog Stand"
        
        binding.playButton.backgroundTintList = null
        val playFocusedColor = if (isHotDog) Color.RED else Color.WHITE
        binding.playButton.background = createButtonBg(playFocusedColor, accentColor)
        binding.playButton.setTextColor(object : ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
            intArrayOf(if (isHotDog) Color.YELLOW else Color.BLACK, 
                       if (themeName == "Light" || isHotDog) Color.BLACK else Color.WHITE)
        ) {})

        listOf(binding.refreshButton, binding.settingsButton, binding.clearCacheBtn, binding.sortButton).forEach { btn ->
            btn.backgroundTintList = null
            btn.background = createButtonBg(accentColor, Color.TRANSPARENT, isOutlined = isHotDog)
            val normalTextColor = if (btn == binding.refreshButton) accentColor else textColor
            btn.setTextColor(object : ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
                intArrayOf(Color.BLACK, normalTextColor)
            ) {})
        }
        
        // --- Selection Buttons (Styled like Spinners were) ---
        fun createSpinnerBg(accent: Int, panel: Int): StateListDrawable {
            val focused = GradientDrawable().apply {
                setColor(panel)
                setStroke(6, accent)
                cornerRadius = 0f
            }
            val normal = GradientDrawable().apply {
                setColor(panel)
                setStroke(2, Color.parseColor("#383842"))
                cornerRadius = 0f
            }
            return StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), focused)
                addState(intArrayOf(), normal)
            }
        }
        
        listOf(binding.titleSelectBtn, binding.audioSelectBtn, binding.subtitleSelectBtn).forEach { sp ->
            sp.background = createSpinnerBg(accentColor, panelColor)
            sp.setTextColor(textColor)
        }
        
        binding.selectedDvdName.setTextColor(textColor)
        binding.selectedDvdPath.setTextColor(textDimColor)
        binding.statusText.setTextColor(textDimColor)
        
        val labels = listOf(
            binding.sidebar.findViewWithTag<TextView>("label_dvds"),
            binding.sidebar.findViewWithTag<TextView>("label_controls"),
            binding.mainContent.findViewWithTag<TextView>("label_select_title"),
            binding.mainContent.findViewWithTag<TextView>("label_sort_by"),
            binding.mainContent.findViewWithTag<TextView>("label_audio"),
            binding.mainContent.findViewWithTag<TextView>("label_subs"),
            binding.emptyState.findViewWithTag<TextView>("empty_text")
        )
        labels.forEach { it?.setTextColor(textDimColor) }
        
        binding.autoplayToggle.setTextColor(textColor)
        binding.autoplayToggle.buttonTintList = ColorStateList.valueOf(accentColor)
    }

    private fun refresh() {
        binding.statusText.text = "Syncing..."
        thread {
            try {
                val dvds = Api.listDvds()
                runOnUiThread {
                    adapter.submit(dvds)
                    binding.statusText.text = "${dvds.size} DVDs"
                }
            } catch (e: Exception) {
                runOnUiThread { binding.statusText.text = "Offline" }
            }
        }
    }

    private fun onDvdSelected(dvd: Dvd) {
        val prefs = getSharedPreferences("dvd_server", MODE_PRIVATE)
        val mode = prefs.getInt("cache_mode", 0)
        if (mode == 3 && selectedDvd != null) {
            val oldDvd = selectedDvd?.name
            thread { Api.clearCache(oldDvd) }
        }

        selectedDvd = dvd
        val params = binding.sidebar.layoutParams
        params.width = (180 * resources.displayMetrics.density).toInt()
        binding.sidebar.layoutParams = params

        binding.emptyState.visibility = View.GONE
        binding.dvdDetails.visibility = View.VISIBLE
        binding.selectedDvdName.text = dvd.name
        binding.selectedDvdPath.text = dvd.path
        
        if (dvd.cover != null) {
            binding.selectedDvdCover.visibility = View.VISIBLE
            Glide.with(this)
                .load(Api.fullUrl(dvd.cover))
                .into(binding.selectedDvdCover)
        } else {
            binding.selectedDvdCover.visibility = View.GONE
        }
        
        binding.statusText.text = "Loading..."
        thread {
            try {
                val loaded = Api.loadDvd(dvd.name)
                runOnUiThread {
                    rawTitles = loaded
                    binding.statusText.text = "Ready"
                    setupTitleSelection()
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Load failed", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun fmtDuration(sec: Double): String {
        val h = (sec / 3600).toInt()
        val m = ((sec % 3600) / 60).toInt()
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    private fun setupTitleSelection() {
        sortedTitles = when (currentSortOrder) {
            1 -> rawTitles.sortedBy { it.duration }
            2 -> rawTitles.sortedByDescending { it.duration }
            else -> rawTitles.sortedBy { it.file }
        }
        
        if (sortedTitles.isNotEmpty()) {
            selectedTitleIdx = 0
            updateTitleButton()
            setupTrackSelection(sortedTitles[0])
        }
    }
    
    private fun updateTitleButton() {
        if (selectedTitleIdx in sortedTitles.indices) {
            val it = sortedTitles[selectedTitleIdx]
            binding.titleSelectBtn.text = "${it.file} (${fmtDuration(it.duration)})"
        } else {
            binding.titleSelectBtn.text = "Choose Title..."
        }
    }

    private fun showTitleDialog() {
        if (sortedTitles.isEmpty()) return
        val labels = sortedTitles.map { "${it.file} (${fmtDuration(it.duration)})" }
        showThemedDialog("Select Title", labels) { which ->
            selectedTitleIdx = which
            updateTitleButton()
            setupTrackSelection(sortedTitles[which])
        }
    }

    private fun setupTrackSelection(title: DvdTitle) {
        selectedAudioIdx = 0
        selectedSubIdx = 0
        updateAudioButton(title)
        updateSubtitleButton(title)
    }
    
    private fun updateAudioButton(title: DvdTitle) {
        if (selectedAudioIdx in title.audio.indices) {
            val it = title.audio[selectedAudioIdx]
            binding.audioSelectBtn.text = "${it.title} (${it.codec})${if (!it.playable) " (!)" else ""}"
        }
    }
    
    private fun updateSubtitleButton(title: DvdTitle) {
        if (selectedSubIdx == 0) {
            binding.subtitleSelectBtn.text = "Off"
        } else {
            val idx = selectedSubIdx - 1
            if (idx in title.subtitles.indices) {
                val it = title.subtitles[idx]
                binding.subtitleSelectBtn.text = "${it.title} (${it.language})"
            }
        }
    }

    private fun showAudioDialog() {
        val titleIdx = selectedTitleIdx
        if (titleIdx !in sortedTitles.indices) return
        val title = sortedTitles[titleIdx]
        
        val labels = title.audio.map { 
            "${it.title} (${it.codec})${if (!it.playable) " (!)" else ""}"
        }
        showThemedDialog("Select Audio", labels) { which ->
            selectedAudioIdx = which
            updateAudioButton(title)
        }
    }

    private fun showSubtitleDialog() {
        val titleIdx = selectedTitleIdx
        if (titleIdx !in sortedTitles.indices) return
        val title = sortedTitles[titleIdx]
        
        val options = mutableListOf("Off")
        options += title.subtitles.map { "${it.title} (${it.language})" }
        
        showThemedDialog("Select Subtitles", options) { which ->
            selectedSubIdx = which
            updateSubtitleButton(title)
        }
    }

    private fun playCurrentSelection() {
        if (selectedTitleIdx !in sortedTitles.indices) return
        
        val title = sortedTitles[selectedTitleIdx]
        val audioTrackIndex = if (selectedAudioIdx in title.audio.indices) 
            title.audio[selectedAudioIdx].index else 0
        
        val prefs = getSharedPreferences("dvd_server", MODE_PRIVATE)
        val themeName = prefs.getString("theme", "Android TV") ?: "Android TV"
        val cacheMode = prefs.getInt("cache_mode", 0)

        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("dvdName", selectedDvd?.name)
            putExtra("titleIndexInSortedList", selectedTitleIdx)
            putExtra("initialAudio", audioTrackIndex)
            putExtra("autoplay", binding.autoplayToggle.isChecked)
            putExtra("sortOrder", currentSortOrder)
            putExtra("theme", themeName)
            putExtra("cacheMode", cacheMode)
            if (selectedSubIdx > 0) {
                val subIdx = selectedSubIdx - 1
                if (subIdx in title.subtitles.indices) {
                    putExtra("initialSubtitleId", title.subtitles[subIdx].id)
                }
            }
        }
        startActivity(intent)
    }

    private data class Quintuple<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)
}
