package com.neonlightning.dvdserver

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class DvdAdapter(
    private val onClick: (Dvd, Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    private val allGrouped = mutableMapOf<String, List<Dvd>>()
    private val collapsedGenres = mutableSetOf<String>()
    private val flatItems = mutableListOf<Any>() // Contains either String (header) or Dvd (item)

    var textColor: Int = Color.WHITE
    var textDimColor: Int = Color.LTGRAY
    var accentColor: Int = Color.parseColor("#E91E63")
    var backgroundColor: Int = Color.parseColor("#101014")
    var panelColor: Int = Color.parseColor("#1B1B22")

    fun submit(dvds: List<Dvd>) {
        allGrouped.clear()
        val grouped = dvds.groupBy { it.genre }
        for ((genre, list) in grouped) {
            val label = if (genre.isBlank()) "Uncategorized" else genre
            allGrouped[label] = list
            // Start collapsed by default if not explicitly tracked
            if (!collapsedGenres.contains(label)) {
                collapsedGenres.add(label)
            }
        }
        rebuildFlatList()
    }

    private fun rebuildFlatList() {
        flatItems.clear()
        for ((genreLabel, list) in allGrouped) {
            flatItems.add(genreLabel)
            if (!collapsedGenres.contains(genreLabel)) {
                flatItems.addAll(list)
            }
        }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (flatItems[position] is String) TYPE_HEADER else TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (viewType == TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_genre_header, parent, false)
            return HeaderVH(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_dvd, parent, false)
            return ItemVH(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is HeaderVH) {
            holder.bind(flatItems[position] as String)
        } else if (holder is ItemVH) {
            holder.bind(flatItems[position] as Dvd)
        }
    }

    override fun getItemCount() = flatItems.size

    inner class HeaderVH(view: View) : RecyclerView.ViewHolder(view) {
        private val headerText: TextView = view.findViewById(R.id.headerText)

        fun bind(genreLabel: String) {
            val isCollapsed = collapsedGenres.contains(genreLabel)
            val arrow = if (isCollapsed) "▶ " else "▼ "
            headerText.text = "$arrow$genreLabel"
            headerText.setTextColor(accentColor)
            
            val focused = GradientDrawable().apply {
                setColor(backgroundColor)
                setStroke(4, accentColor)
                cornerRadius = 0f
            }
            val normal = ColorDrawable(Color.TRANSPARENT)
            
            headerText.background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), focused)
                addState(intArrayOf(), normal)
            }
            
            headerText.isFocusable = true
            headerText.isFocusableInTouchMode = true
            
            headerText.setOnClickListener {
                toggleGenre(genreLabel)
            }
        }
    }

    private fun toggleGenre(genreLabel: String) {
        if (collapsedGenres.contains(genreLabel)) {
            collapsedGenres.remove(genreLabel)
        } else {
            collapsedGenres.add(genreLabel)
        }
        rebuildFlatList()
    }

    inner class ItemVH(view: View) : RecyclerView.ViewHolder(view) {
        private val name: TextView = view.findViewById(R.id.nameText)
        private val path: TextView = view.findViewById(R.id.pathText)
        private val cover: ImageView = view.findViewById(R.id.coverThumb)

        fun bind(dvd: Dvd) {
            name.text = dvd.display_name
            path.text = dvd.name
            
            val isBright = isColorBright(accentColor)
            val contentColor = if (isBright) Color.BLACK else Color.WHITE
            
            name.setTextColor(contentColor)
            path.setTextColor(contentColor)
            path.alpha = 0.7f
            
            if (dvd.cover != null && dvd.cover.isNotEmpty()) {
                cover.visibility = View.VISIBLE
                Glide.with(itemView.context)
                    .load(Api.fullUrl(dvd.cover))
                    .placeholder(ColorDrawable(Color.DKGRAY))
                    .into(cover)
            } else {
                cover.visibility = View.GONE
            }
            
            val focused = GradientDrawable().apply {
                setColor(backgroundColor)
                setStroke(6, accentColor)
                cornerRadius = 0f
            }
            val normal = GradientDrawable().apply {
                setColor(accentColor)
                setStroke(2, Color.BLACK)
                cornerRadius = 0f
            }
            
            itemView.background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), focused)
                addState(intArrayOf(), normal)
            }
            
            fun applyTextColors(hasFocus: Boolean) {
                if (hasFocus) {
                    name.setTextColor(textColor)
                    path.setTextColor(textColor)
                } else {
                    val bright = isColorBright(accentColor)
                    val color = if (bright) Color.BLACK else Color.WHITE
                    name.setTextColor(color)
                    path.setTextColor(color)
                }
                path.alpha = 0.7f
            }

            applyTextColors(itemView.hasFocus())
            
            itemView.setOnFocusChangeListener { _, hasFocus ->
                applyTextColors(hasFocus)
            }
            
            itemView.setOnClickListener { 
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onClick(dvd, pos)
                }
            }
        }
    }

    private fun isColorBright(color: Int): Boolean {
        val darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
        return darkness < 0.5
    }
}
