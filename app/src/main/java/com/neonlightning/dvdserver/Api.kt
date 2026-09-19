package com.neonlightning.dvdserver

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Api {
    var baseUrl: String = "http://192.168.1.100:4251"

    fun encodePathSegments(p: String): String {
        // Correctly encodes segments like "Action (1990)" while preserving "/" separators
        return p.split("/").joinToString("/") { Uri.encode(it) }
    }

    private fun get(path: String): String {
        val url = URL(baseUrl.trimEnd('/') + path)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 5000
        conn.readTimeout = 15000
        return try {
            val code = conn.responseCode
            if (code !in 200..299) error("HTTP $code")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun post(path: String, json: JSONObject? = null): String {
        val url = URL(baseUrl.trimEnd('/') + path)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 5000
        conn.readTimeout = 30000
        if (json != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.outputStream.use { os ->
                os.write(json.toString().toByteArray(Charsets.UTF_8))
            }
        }
        return try {
            val code = conn.responseCode
            if (code !in 200..299) {
                val detail = conn.errorStream?.bufferedReader()?.use { it.readText() }
                error("HTTP $code${if (!detail.isNullOrBlank()) ": $detail" else ""}")
            }
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    fun getUsers(): List<String> {
        return try {
            val root = JSONObject(get("/api/users"))
            val arr = root.optJSONArray("users") ?: JSONArray()
            List(arr.length()) { i -> arr.getString(i) }
        } catch (e: Exception) {
            listOf("Guest")
        }
    }

    fun createUser(username: String): Boolean {
        return try {
            val json = JSONObject().apply { put("username", username) }
            val root = JSONObject(post("/api/users", json))
            root.optBoolean("success", false)
        } catch (e: Exception) {
            false
        }
    }

    fun getProgress(user: String, dvdName: String): Map<Int, ProgressInfo> {
        val encodedDvd = URLEncoder.encode(dvdName, "UTF-8")
        val encodedUser = URLEncoder.encode(user, "UTF-8")
        val result = mutableMapOf<Int, ProgressInfo>()
        try {
            val raw = get("/api/progress?user=$encodedUser&dvd=$encodedDvd")
            val root = JSONObject(raw)
            for (key in root.keys()) {
                val obj = root.getJSONObject(key)
                val idx = key.toIntOrNull() ?: obj.optInt("title_idx", 0)
                result[idx] = ProgressInfo(
                    title_idx = obj.optInt("title_idx", idx),
                    position = obj.optDouble("position", 0.0),
                    duration = obj.optDouble("duration", 0.0),
                    watched = obj.optInt("watched", 0)
                )
            }
        } catch (e: Exception) {}
        return result
    }

    fun saveProgress(user: String, dvdName: String, titleIndex: Int, position: Double, duration: Double) {
        try {
            val json = JSONObject().apply {
                put("user", user)
                put("dvd", dvdName)
                put("title_idx", titleIndex)
                put("position", position)
                put("duration", duration)
            }
            post("/api/progress", json)
        } catch (e: Exception) {}
    }

    fun resetProgress(user: String, dvdName: String, titleIndex: Int) {
        try {
            val json = JSONObject().apply {
                put("user", user)
                put("dvd", dvdName)
                put("title_idx", titleIndex)
                put("position", 0.0)
                put("duration", 0.0)
            }
            post("/api/progress", json)
        } catch (e: Exception) {}
    }

    fun listDvds(): List<Dvd> {
        val root = JSONObject(get("/api/dvds"))
        val arr = root.optJSONArray("dvds") ?: JSONArray()
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Dvd(
                name = o.optString("name"),
                display_name = o.optString("display_name", o.optString("name")),
                genre = o.optString("genre", ""),
                subpath = o.optString("subpath", ""),
                path = o.optString("path", ""),
                cover = if (o.has("cover")) o.getString("cover") else null
            )
        }
    }

    fun loadDvd(name: String): List<DvdTitle> {
        val encoded = encodePathSegments(name)
        val root = JSONObject(post("/api/dvd/load/$encoded"))
        val arr = root.optJSONArray("titles") ?: JSONArray()
        return List(arr.length()) { i -> parseTitle(i, arr.getJSONObject(i)) }
    }

    private fun parseTitle(index: Int, o: JSONObject): DvdTitle {
        val chapters = mutableListOf<Chapter>()
        val ca = o.optJSONArray("chapters") ?: JSONArray()
        for (i in 0 until ca.length()) {
            val c = ca.getJSONObject(i)
            chapters += Chapter(
                c.optInt("number"),
                c.optString("title"),
                c.optDouble("start"),
                c.optDouble("end")
            )
        }

        val subtitles = mutableListOf<SubtitleTrack>()
        val sa = o.optJSONArray("subtitles") ?: JSONArray()
        for (i in 0 until sa.length()) {
            val s = sa.getJSONObject(i)
            subtitles += SubtitleTrack(
                s.optString("id"),
                s.optString("source"),
                if (s.has("filename")) s.optString("filename") else null,
                s.optString("language", "und"),
                s.optString("title"),
                s.optBoolean("playable", false),
                s.optString("codec", ""),
                s.optInt("stream_index", -1)
            )
        }

        val audio = mutableListOf<AudioTrack>()
        val aa = o.optJSONArray("audio") ?: JSONArray()
        for (i in 0 until aa.length()) {
            val a = aa.getJSONObject(i)
            audio += AudioTrack(
                a.optInt("index"),
                a.optString("language", "und"),
                a.optString("title"),
                a.optString("codec"),
                a.optInt("channels"),
                a.optBoolean("playable", false)
            )
        }

        return DvdTitle(
            index,
            o.optString("file"),
            o.optString("path"),
            o.optDouble("duration"),
            chapters,
            subtitles,
            audio
        )
    }

    fun streamUrl(titleIndex: Int, audioIndex: Int): String =
        "${baseUrl.trimEnd('/')}/api/dvd/stream/$titleIndex?audio=$audioIndex"

    fun subtitleUrl(titleIndex: Int, subtitleId: String): String {
        // Encodes subtitle ID (e.g. file:movie.srt) for the URL path
        val encoded = Uri.encode(subtitleId)
        return "${baseUrl.trimEnd('/')}/api/dvd/subtitle/$titleIndex/$encoded"
    }

    fun fullUrl(path: String?): String? {
        if (path == null) return null
        if (path.startsWith("http")) return path
        return "${baseUrl.trimEnd('/')}/${path.trimStart('/')}"
    }

    fun clearCache(dvdName: String? = null) {
        val path = if (dvdName != null) {
            val encodedQuery = URLEncoder.encode(dvdName, "UTF-8")
            "/api/dvd/cache/clear?dvd=$encodedQuery"
        } else {
            "/api/dvd/cache/clear"
        }
        try { post(path) } catch (e: Exception) {}
    }
}
