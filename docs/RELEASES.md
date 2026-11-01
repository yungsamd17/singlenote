# Release workflow

1. Merge reviewed changes through a pull request and wait for the latest main
   build to pass. Set `versionName` and increase `versionCode` in that PR.
   Move the applicable [unreleased changelog](../CHANGELOGS.md) entries to a dated
   version section and leave a new Unreleased heading for later work.
2. Tag the tested main commit with `vX.Y.Z` and push the tag. The separate
   **Release** workflow validates that the tag matches `versionName` and belongs
   to main, then builds, tests, lints, signs, and publishes both APKs.
3. Curate the generated release notes into user-facing sections and bullets.
   Keep the APK attachments and a Full Changelog comparison link. Omit PR
   numbers, internal chores, and AI attribution.

To retry an existing tag, run **Release** manually and enter that tag. This does
not move tags or bump versions. Signing uses the existing `RELEASE_KEYSTORE_*`
and `RELEASE_KEY_*` repository secrets; missing signing fails the release.

The **Build** workflow still validates pushes to main and pull requests and
uploads `singlenote-debug-apk`. Release signing never falls back to the debug key.

## Updating an installed release

The release APK uses the repository's release keystore and application ID
`com.yungsamd17.singlenote`. To update an existing installation, the new APK
must use the same signing certificate and a higher `versionCode`. The v0.4.1
workflow used the same release-secret names, but matching names alone do not prove
the secret values or certificates match. Compare the old and new release APKs
with `apksigner verify --print-certs` before publication if the key is uncertain.
Do not replace a lost release key with the debug key. The separate `.preview`
package uses a debug key and cannot update the regular release.
