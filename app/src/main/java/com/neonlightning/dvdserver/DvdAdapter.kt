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
        private const val TYPE_SUBHEADER = 1
        private const val TYPE_ITEM = 2
    }

    private val allGrouped = mutableMapOf<String, MutableMap<String, List<Dvd>>>()
    private val collapsedKeys = mutableSetOf<String>()
    private val flatItems = mutableListOf<Any>() // String (Genre Header), SubfolderNode (Subheader), or Dvd (item)

    var textColor: Int = Color.WHITE
    var textDimColor: Int = Color.LTGRAY
    var accentColor: Int = Color.parseColor("#E91E63")
    var backgroundColor: Int = Color.parseColor("#101014")
    var panelColor: Int = Color.parseColor("#1B1B22")

    data class SubfolderNode(val genre: String, val subpath: String, val label: String)

    fun submit(dvds: List<Dvd>) {
        allGrouped.clear()
        for (dvd in dvds) {
            val genre = if (dvd.genre.isBlank()) "Uncategorized" else dvd.genre
            val subpath = dvd.subpath.ifBlank { "" }
            allGrouped.getOrPut(genre) { mutableMapOf() }.getOrPut(subpath) { mutableListOf() }.let {
                (it as MutableList<Dvd>).add(dvd)
            }
            if (!collapsedKeys.contains(genre)) {
                collapsedKeys.add(genre)
            }
            if (subpath.isNotBlank()) {
                val subKey = "$genre::$subpath"
                if (!collapsedKeys.contains(subKey)) {
                    collapsedKeys.add(subKey)
                }
            }
        }
        rebuildFlatList()
    }

    private fun rebuildFlatList() {
        flatItems.clear()
        for ((genre, subpaths) in allGrouped.toSortedMap()) {
            flatItems.add(genre)
            if (!collapsedKeys.contains(genre)) {
                for ((subpath, dvds) in subpaths.toSortedMap()) {
                    if (subpath.isNotBlank()) {
                        val subKey = "$genre::$subpath"
                        flatItems.add(SubfolderNode(genre, subpath, subpath))
                        if (!collapsedKeys.contains(subKey)) {
                            flatItems.addAll(dvds.sortedBy { it.display_name })
                        }
                    } else {
                        flatItems.addAll(dvds.sortedBy { it.display_name })
                    }
                }
            }
        }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (flatItems[position]) {
            is String -> TYPE_HEADER
            is SubfolderNode -> TYPE_SUBHEADER
            else -> TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderVH(inflater.inflate(R.layout.item_genre_header, parent, false))
            TYPE_SUBHEADER -> SubheaderVH(inflater.inflate(R.layout.item_genre_header, parent, false))
            else -> ItemVH(inflater.inflate(R.layout.item_dvd, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderVH -> holder.bind(flatItems[position] as String)
            is SubheaderVH -> holder.bind(flatItems[position] as SubfolderNode)
            is ItemVH -> holder.bind(flatItems[position] as Dvd)
        }
    }

    override fun getItemCount() = flatItems.size

    inner class HeaderVH(view: View) : RecyclerView.ViewHolder(view) {
        private val headerText: TextView = view.findViewById(R.id.headerText)

        fun bind(genreLabel: String) {
            val isCollapsed = collapsedKeys.contains(genreLabel)
            val arrow = if (isCollapsed) "▶ " else "▼ "
            headerText.text = "$arrow$genreLabel"
            headerText.setTextColor(accentColor)
            headerText.setPadding(4, 16, 4, 6)

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
                toggleKey(genreLabel)
            }
        }
    }

    inner class SubheaderVH(view: View) : RecyclerView.ViewHolder(view) {
        private val headerText: TextView = view.findViewById(R.id.headerText)

        fun bind(node: SubfolderNode) {
            val subKey = "${node.genre}::${node.subpath}"
            val isCollapsed = collapsedKeys.contains(subKey)
            val arrow = if (isCollapsed) "📁 " else "📂 "
            headerText.text = "    $arrow${node.label}"
            headerText.setTextColor(textDimColor)
            headerText.setPadding(24, 12, 4, 4)

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
                toggleKey(subKey)
            }
        }
    }

    private fun toggleKey(key: String) {
        if (collapsedKeys.contains(key)) {
            collapsedKeys.remove(key)
        } else {
            collapsedKeys.add(key)
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
