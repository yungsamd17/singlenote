package com.yungsamd17.singlenote.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Public GitHub release info, fetched on demand only (changelog sheet,
 * update check). No auth, no tracking — plain unauthenticated reads of a
 * public repo. Callers must catch [IOException] and show an offline state.
 */
object GithubReleases {

    data class Release(
        val tag: String,
        val name: String,
        val body: String,
        val htmlUrl: String,
        val apkUrl: String?,
    )

    suspend fun fetch(limit: Int = 20): List<Release> = withContext(Dispatchers.IO) {
        val connection =
            URL("$API/releases?per_page=$limit").openConnection() as HttpURLConnection
        try {
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "Singlenote-Android-App")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("GitHub releases: HTTP ${connection.responseCode}")
            }
            parse(connection.inputStream.bufferedReader().readText())
        } finally {
            connection.disconnect()
        }
    }

    internal fun parse(json: String): List<Release> {
        val releases = mutableListOf<Release>()
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val assets = item.optJSONArray("assets")
            var apkUrl: String? = null
            if (assets != null) {
                for (j in 0 until assets.length()) {
                    val asset = assets.getJSONObject(j)
                    if (asset.optString("name") == "app-release.apk") {
                        apkUrl = asset.optString("browser_download_url").ifBlank { null }
                        break
                    }
                }
            }
            releases += Release(
                tag = item.optString("tag_name"),
                name = item.optString("name").ifBlank { item.optString("tag_name") },
                body = item.optString("body"),
                htmlUrl = item.optString("html_url"),
                apkUrl = apkUrl
            )
        }
        return releases
    }

    private const val API = "https://api.github.com/repos/yungsamd17/singlenote"
}
