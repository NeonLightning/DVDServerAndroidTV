package com.neonlightning.dvdserver

data class Dvd(
    val name: String,          // Full relative path as unique ID
    val display_name: String,  // Short label for UI display
    val genre: String,         // Top-level folder
    val subpath: String,       // Dirs between genre and DVD name
    val path: String,          // Absolute path on server
    val cover: String?
)

data class Chapter(
    val number: Int,
    val title: String,
    val start: Double,
    val end: Double
)

data class SubtitleTrack(
    val id: String,
    val source: String,
    val filename: String?,
    val language: String,
    val title: String,
    val playable: Boolean
)

data class AudioTrack(
    val index: Int,
    val language: String,
    val title: String,
    val codec: String,
    val channels: Int,
    val playable: Boolean
)

data class DvdTitle(
    val index: Int,
    val file: String,
    val path: String,
    val duration: Double,
    val chapters: List<Chapter>,
    val subtitles: List<SubtitleTrack>,
    val audio: List<AudioTrack>
)
