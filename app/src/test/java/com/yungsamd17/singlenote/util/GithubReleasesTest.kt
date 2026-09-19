package com.yungsamd17.singlenote.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [GithubReleases.parse] field mapping, prerelease filtering and APK
 * asset matching, plus the [GithubReleases.isNewerTag] version comparison.
 */
class GithubReleasesTest {

    private fun releaseJson(
        tag: String,
        name: String,
        prerelease: Boolean,
        assetsJson: String,
    ) = """
        {
          "tag_name": "$tag",
          "name": "$name",
          "body": "notes for $tag",
          "html_url": "https://github.com/yungsamd17/singlenote/releases/tag/$tag",
          "prerelease": $prerelease,
          "assets": [$assetsJson]
        }
    """.trimIndent()

    private val apkAsset = """
        {"name": "app-release.apk", "browser_download_url": "https://github.com/yungsamd17/singlenote/releases/download/v0.3.4/app-release.apk"}
    """.trimIndent()

    @Test
    fun parse_readsFieldsAndMatchesApkAsset() {
        val json = "[${releaseJson("v0.3.4", "Singlenote 0.3.4", false, apkAsset)}]"

        val releases = GithubReleases.parse(json)

        assertEquals(1, releases.size)
        assertEquals("v0.3.4", releases[0].tag)
        assertEquals("Singlenote 0.3.4", releases[0].name)
        assertEquals("notes for v0.3.4", releases[0].body)
        assertEquals(
            "https://github.com/yungsamd17/singlenote/releases/tag/v0.3.4",
            releases[0].htmlUrl
        )
        assertEquals(
            "https://github.com/yungsamd17/singlenote/releases/download/v0.3.4/app-release.apk",
            releases[0].apkUrl
        )
    }

    @Test
    fun parse_skipsPrereleases() {
        val json = "[" +
            releaseJson("v0.4.0-beta", "Beta", true, apkAsset) + "," +
            releaseJson("v0.3.4", "Stable", false, "") +
            "]"

        val releases = GithubReleases.parse(json)

        assertEquals(1, releases.size)
        assertEquals("v0.3.4", releases[0].tag)
    }

    @Test
    fun parse_nullApkUrlWhenNoMatchingAsset() {
        val otherAsset = """{"name": "checksums.txt", "browser_download_url": "https://example.com/sums"}"""
        val json = "[" +
            releaseJson("v0.3.4", "No apk", false, otherAsset) + "," +
            releaseJson("v0.3.3", "Empty assets", false, "") +
            "]"

        val releases = GithubReleases.parse(json)

        assertEquals(2, releases.size)
        assertNull(releases[0].apkUrl)
        assertNull(releases[1].apkUrl)
    }

    @Test
    fun parse_blankNameFallsBackToTag() {
        val json = "[${releaseJson("v0.3.4", "", false, "")}]"

        val releases = GithubReleases.parse(json)

        assertEquals(1, releases.size)
        assertEquals("v0.3.4", releases[0].name)
    }

    @Test
    fun isNewerTag_comparesNumerically() {
        assertTrue(GithubReleases.isNewerTag("v0.3.10", "0.3.9"))
        assertTrue(GithubReleases.isNewerTag("v1.0.0", "0.9.9"))
        assertFalse(GithubReleases.isNewerTag("v0.3.4", "0.3.4"))
        assertFalse(GithubReleases.isNewerTag("v0.3.3", "0.3.4"))
        assertFalse(GithubReleases.isNewerTag("v0.3", "0.3.0"))
    }
}
