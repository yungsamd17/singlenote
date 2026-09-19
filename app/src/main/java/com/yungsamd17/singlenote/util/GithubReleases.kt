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
 *
 * Updates never pipe a raw APK from here: there is no SHA-256 pinning, so
 * callers must open [Release.htmlUrl] (the release page) and let the user
 * verify the asset in the browser instead of downloading [Release.apkUrl]
 * directly.
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

        /**
     * True when [latestTag] (e.g. "v0.3.3") is newer than [current] (e.g.
     * BuildConfig.VERSION_NAME). Compares numeric dot-separated parts so
     * "v0.3.10" beats "v0.3.9"; unparseable parts count as 0.
     */
    fun isNewerTag(latestTag: String, current: String): Boolean {
        fun parts(version: String) = version.trim().trimStart('v', 'V')
            .split('.').map { it.toIntOrNull() ?: 0 }
        val latest = parts(latestTag)
        val installed = parts(current)
        for (i in 0 until maxOf(latest.size, installed.size)) {
            val difference = latest.getOrElse(i) { 0 } - installed.getOrElse(i) { 0 }
            if (difference != 0) return difference > 0
        }
        return false
    }

    internal fun parse(json: String): List<Release> {
        val releases = mutableListOf<Release>()
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            // Prereleases are not updates: keep them out of the changelog
            // top spot and the update check.
            if (item.optBoolean("prerelease")) continue
            val assets = item.optJSONArray("assets")
            var apkUrl: String? = null
            if (assets != null) {
                for (j in 0 until assets.length()) {
                    val asset = assets.getJSONObject(j)
                    // CI publishes versioned names like
                    // singlenote-v0.3.4-release.apk, never a bare
                    // app-release.apk: match the -release.apk suffix and
                    // skip debug builds.
                    val name = asset.optString("name")
                    if (name.endsWith("-release.apk") &&
                        !name.contains("debug", ignoreCase = true)
                    ) {
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
