package com.brian.dialbuddy.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class PublishedRelease(
    val version: String,
    val downloadUrl: String?,
)

object ReleaseLookup {
    fun latest(): PublishedRelease? {
        val connection = URL(LATEST).openConnection() as HttpURLConnection
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "Vociferous")
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        return try {
            connection.inputStream.bufferedReader().use { reader ->
                val body = JSONObject(reader.readText())
                val version = body.optString("tag_name")
                if (version.isBlank()) return null
                PublishedRelease(version, apkUrl(body))
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun apkUrl(body: JSONObject): String? {
        val assets = body.optJSONArray("assets") ?: return null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name")
            if (!name.endsWith(".apk")) continue
            val url = asset.optString("browser_download_url")
            if (url.isNotBlank()) return url
        }
        return null
    }

    private const val LATEST = "https://api.github.com/repos/brian-and-kathi/vociferous/releases/latest"
}
