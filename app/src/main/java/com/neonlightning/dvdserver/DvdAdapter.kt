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
    private val onClick: (Dvd) -> Unit
) : RecyclerView.Adapter<DvdAdapter.VH>() {

    private val items = mutableListOf<Dvd>()
    var textColor: Int = Color.WHITE
    var textDimColor: Int = Color.LTGRAY
    var accentColor: Int = Color.parseColor("#E91E63")
    var backgroundColor: Int = Color.parseColor("#101014")
    var panelColor: Int = Color.parseColor("#1B1B22")

    fun submit(items: List<Dvd>) {
        this.items.clear()
        this.items.addAll(items)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dvd, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val name: TextView = view.findViewById(R.id.nameText)
        private val path: TextView = view.findViewById(R.id.pathText)
        private val cover: ImageView = view.findViewById(R.id.coverThumb)

        fun bind(dvd: Dvd) {
            name.text = dvd.name
            path.text = dvd.path
            
            // Text color logic: if background is accent, black text might be better?
            // User wanted "text be black" for Hot Dog, but for others?
            // Let's use Color.BLACK if background is light/bright
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
            
            // Selector styling as requested
            val focused = GradientDrawable().apply {
                setColor(backgroundColor) // Selected (Focused) is Background Color
                setStroke(6, accentColor)
                cornerRadius = 0f
            }
            val normal = GradientDrawable().apply {
                setColor(accentColor) // Unselected (Normal) is Accent Color
                setStroke(2, Color.BLACK)
                cornerRadius = 0f
            }
            
            itemView.background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), focused)
                addState(intArrayOf(), normal)
            }
            
            // Text color logic: 
            // Unselected (Accent BG) -> Black if accent is bright, else White
            // Selected (Main BG) -> Theme's textColor (which is designed for BG)
            
            fun applyTextColors(hasFocus: Boolean) {
                if (hasFocus) {
                    name.setTextColor(textColor)
                    path.setTextColor(textColor)
                } else {
                    val isBright = isColorBright(accentColor)
                    val color = if (isBright) Color.BLACK else Color.WHITE
                    name.setTextColor(color)
                    path.setTextColor(color)
                }
                path.alpha = 0.7f
            }

            applyTextColors(itemView.hasFocus())
            
            itemView.setOnFocusChangeListener { _, hasFocus ->
                applyTextColors(hasFocus)
            }
            
            itemView.setOnClickListener { onClick(dvd) }
        }
        
        private fun isColorBright(color: Int): Boolean {
            val darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
            return darkness < 0.5
        }
    }
}
