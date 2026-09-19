package com.neonlightning.dvdserver

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import com.neonlightning.dvdserver.databinding.ActivitySettingsBinding
import kotlin.concurrent.thread

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var discoveryManager: DiscoveryManager
    private val discoveredServers = mutableMapOf<String, String>()
    private var currentThemeName = "Android TV"

    private val themeOptions = listOf(
        "Android TV", "Terminal", "Midnight", "Light", "Solarized", "Dracula",
        "Nord", "Gruvbox", "Tokyo Night", "Catppuccin", "Rosé Pine", "Forest", "Cyberpunk", "Hot Dog Stand"
    )
    private val cacheOptions = listOf("Manual Only", "On File Close", "On File End", "On Selection Change")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences("dvd_server", MODE_PRIVATE)
        
        currentThemeName = prefs.getString("theme", "Android TV") ?: "Android TV"
        binding.themeButton.text = currentThemeName
        applyVisualTheme(currentThemeName)
        
        val currentCacheMode = prefs.getInt("cache_mode", 0)
        binding.cacheModeButton.text = cacheOptions[currentCacheMode]

        val screensaverMinutes = prefs.getInt("screensaver_minutes", 2)
        val screensaverOptions = listOf("1 Minute", "2 Minutes", "5 Minutes", "10 Minutes", "Never")
        val screensaverValues = listOf(1, 2, 5, 10, -1)
        val currentScreensaverIdx = screensaverValues.indexOf(screensaverMinutes).let { if (it != -1) it else 1 }
        binding.screensaverTimeButton.text = screensaverOptions[currentScreensaverIdx]

        binding.screensaverTimeButton.setOnClickListener {
            showThemedListDialog("Inactivity Timeout", screensaverOptions) { which ->
                binding.screensaverTimeButton.text = screensaverOptions[which]
                getSharedPreferences("dvd_server", MODE_PRIVATE).edit().putInt("screensaver_minutes", screensaverValues[which]).apply()
            }
        }
        
        val appPrefs = AppPreferences(this)
        var currentUser = appPrefs.currentUser
        binding.profileButton.text = currentUser

        binding.profileButton.setOnClickListener {
            thread {
                val users = Api.getUsers()
                runOnUiThread {
                    val options = mutableListOf("Create New Profile...")
                    options.addAll(users)
                    showThemedListDialog("Select Profile", options) { which ->
                        if (which == 0) {
                            showCreateProfileDialog { newName ->
                                currentUser = newName
                                appPrefs.currentUser = newName
                                binding.profileButton.text = newName
                            }
                        } else {
                            val selectedUser = users[which - 1]
                            currentUser = selectedUser
                            appPrefs.currentUser = selectedUser
                            binding.profileButton.text = selectedUser
                        }
                    }
                }
            }
        }

        binding.currentServerText.text = "Current: ${Api.baseUrl}"

        binding.themeButton.setOnClickListener { 
            showThemedListDialog("Select Theme", themeOptions) { which ->
                val theme = themeOptions[which]
                currentThemeName = theme
                binding.themeButton.text = theme
                getSharedPreferences("dvd_server", MODE_PRIVATE).edit().putString("theme", theme).apply()
                applyVisualTheme(theme)
            }
        }
        
        binding.cacheModeButton.setOnClickListener { 
            showThemedListDialog("Cache Clearing Mode", cacheOptions) { which ->
                binding.cacheModeButton.text = cacheOptions[which]
                getSharedPreferences("dvd_server", MODE_PRIVATE).edit().putInt("cache_mode", which).apply()
            }
        }
        
        binding.serverSetupButton.setOnClickListener { 
            val options = mutableListOf("Enter Manually...")
            val names = discoveredServers.keys.toList()
            options.addAll(names)
            showThemedListDialog("Discovered Servers", options) { which ->
                if (which == 0) showManualIpDialog()
                else discoveredServers[names[which - 1]]?.let { saveServer(it) }
            }
        }
        
        // Removed doneButton listener since the button was removed from layout

        discoveryManager = DiscoveryManager(this) { name, url ->
            runOnUiThread { discoveredServers[name] = url }
        }
        discoveryManager.start()
    }

    override fun onDestroy() {
        discoveryManager.stop()
        super.onDestroy()
    }

    private fun getDialogTheme(): Int {
        return when (currentThemeName) {
            "Hot Dog Stand" -> R.style.Theme_DVDServer_Dialog_HotDog
            "Light" -> R.style.Theme_DVDServer_Dialog_Light
            else -> R.style.Theme_DVDServer_Dialog
        }
    }

    private fun showThemedListDialog(title: String, options: List<String>, onSelect: (Int) -> Unit) {
        val palette = getThemePalette(currentThemeName)
        val accentColor = palette.third.toColorInt()
        val textColor = palette.fourth.toColorInt()
        val bgColor = palette.first.toColorInt()
        val isAccentBright = isColorBright(accentColor)

        val builder = AlertDialog.Builder(this, getDialogTheme())
        builder.setTitle(title)

        val listAdapter = object : ArrayAdapter<String>(this, R.layout.item_dialog_list, options) {
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

        builder.setAdapter(listAdapter) { _, which -> onSelect(which) }
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
                
                // Theme list view
                dialog.listView.setBackgroundColor(bgColor)
                dialog.listView.selector = ColorDrawable(Color.TRANSPARENT)
            }
        }
        
        dialog.show()
    }

    private fun showManualIpDialog() {
        val palette = getThemePalette(currentThemeName)
        val bgColor = palette.first.toColorInt()

        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        input.setText(Api.baseUrl)
        input.setSelectAllOnFocus(true)
        input.setTextColor(if (currentThemeName == "Light" || currentThemeName == "Hot Dog Stand") Color.BLACK else Color.WHITE)

        val dialog = AlertDialog.Builder(this, getDialogTheme())
            .setTitle("Server Address")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                var value = input.text.toString().trim()
                if (!value.startsWith("http://") && !value.startsWith("https://")) value = "http://$value"
                val uri = Uri.parse(value)
                if (uri.port == -1) value = "$value:4251"
                saveServer(value.trimEnd('/'))
            }
            .create()
            
        dialog.window?.setBackgroundDrawable(ColorDrawable(bgColor))
        dialog.show()
    }

    private fun saveServer(url: String) {
        Api.baseUrl = url
        getSharedPreferences("dvd_server", MODE_PRIVATE).edit().putString("base_url", url).apply()
        binding.currentServerText.text = "Current: $url"
        Toast.makeText(this, "Server updated", Toast.LENGTH_SHORT).show()
    }

    private fun getThemePalette(name: String): Quintuple<String, String, String, String, String> {
        return when (name) {
            "Terminal" -> Quintuple("#000000", "#050a05", "#00ff41", "#00ff41", "#00aa33")
            "Midnight" -> Quintuple("#000000", "#0d0d0d", "#a78bfa", "#d8d8d8", "#888888")
            "Light" -> Quintuple("#F0F2F8", "#FFFFFF", "#2563EB", "#1A1F2E", "#5A6070")
            "Solarized" -> Quintuple("#002b36", "#073642", "#b58900", "#93a1a1", "#657b83")
            "Dracula" -> Quintuple("#282828", "#21222c", "#bd93f9", "#f8f8f2", "#6272a4")
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

    private fun isColorBright(color: Int): Boolean {
        val darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
        return darkness < 0.5
    }

    private fun applyVisualTheme(themeName: String) {
        val palette = getThemePalette(themeName)
        val bgColor = palette.first.toColorInt()
        val accentColor = palette.third.toColorInt()
        val textColor = palette.fourth.toColorInt()
        val textDimColor = palette.fifth.toColorInt()

        binding.settingsRoot.setBackgroundColor(bgColor)
        binding.settingsTitle.setTextColor(accentColor)
        
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

        val isHotDog = themeName == "Hot Dog Stand"
        
        // --- Selection Boxes (Styled like Spinners in MainActivity) ---
        listOf(binding.themeButton, binding.cacheModeButton, binding.screensaverTimeButton, binding.profileButton).forEach { sp ->
            sp.backgroundTintList = null
            sp.background = createSpinnerBg(accentColor, palette.second.toColorInt())
            sp.setTextColor(textColor)
        }
        
        // --- Regular Buttons (Server Setup) ---
        // Style "Server Setup" button to match the standard button theme
        listOf(binding.serverSetupButton).forEach { btn ->
            btn.backgroundTintList = null
            btn.background = createButtonBg(accentColor, Color.TRANSPARENT, isOutlined = isHotDog)
            btn.setTextColor(object : ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
                intArrayOf(Color.BLACK, accentColor)
            ) {})
        }
        
        binding.currentServerText.setTextColor(textDimColor)
        
        val labels = listOf(
            binding.settingsRoot.findViewWithTag<TextView>("label_appearance"),
            binding.settingsRoot.findViewWithTag<TextView>("label_ui_theme"),
            binding.settingsRoot.findViewWithTag<TextView>("label_user_profile"),
            binding.settingsRoot.findViewWithTag<TextView>("label_active_profile"),
            binding.settingsRoot.findViewWithTag<TextView>("label_cache_mgmt"),
            binding.settingsRoot.findViewWithTag<TextView>("label_auto_clearing"),
            binding.settingsRoot.findViewWithTag<TextView>("label_screensaver"),
            binding.settingsRoot.findViewWithTag<TextView>("label_timeout"),
            binding.settingsRoot.findViewWithTag<TextView>("label_server_conn"),
            binding.settingsRoot.findViewWithTag<TextView>("label_changes_hint")
        )
        labels.forEach { it?.setTextColor(textDimColor) }
    }

    private fun showCreateProfileDialog(onCreated: (String) -> Unit) {
        val palette = getThemePalette(currentThemeName)
        val bgColor = palette.first.toColorInt()

        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.hint = "Profile Name"
        input.setTextColor(if (currentThemeName == "Light" || currentThemeName == "Hot Dog Stand") Color.BLACK else Color.WHITE)
        input.setHintTextColor(Color.GRAY)

        val dialog = AlertDialog.Builder(this, getDialogTheme())
            .setTitle("New Profile")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty() && name.lowercase() != "guest") {
                    thread {
                        val success = Api.createUser(name)
                        runOnUiThread {
                            if (success) {
                                onCreated(name)
                                Toast.makeText(this, "Profile created: $name", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this, "Failed to create profile", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(bgColor))
        dialog.show()
    }

    private data class Quintuple<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)
}
