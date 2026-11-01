# Translate Singlenote

Help make Singlenote available in your language. You can improve an existing
translation or contribute a new one through a [pull request](https://github.com/yungsamd17/singlenote/pulls).
English is the source language; Slovak (Slovenčina) is already available. The
codes below are examples for contributors, not a list of shipped translations.

## Find the source files

| File | Purpose |
|---|---|
| `app/src/main/res/values/strings.xml` | Complete English source and resource keys |
| `app/src/main/res/values-sk/strings.xml` | Existing Slovak translation; a working example |
| `app/src/main/res/xml/locales_config.xml` | Languages advertised to Android 13+ per-app settings |
| `app/src/main/java/com/yungsamd17/singlenote/ui/SettingsScreen.kt` | Single `languageEntries` list used by the selector and hub summary |
| `app/src/main/java/com/yungsamd17/singlenote/util/AppLanguage.kt` | Shared locale switching and persistence; normally needs no changes |

## Choose a language code

Use an Android locale qualifier for the resource directory and a BCP 47 language
tag for `LanguageEntry.code` and `locales_config.xml`. Simple two-letter language
codes are identical in both places.

| Language | Native name | Tag | Resource directory |
|---|---|---|---|
| English | English | `en` | `values/` (source; no separate `values-en` needed) |
| Slovak | Slovenčina | `sk` | `values-sk/` |
| German | Deutsch | `de` | `values-de/` |
| Czech | Čeština | `cs` | `values-cs/` |
| French | Français | `fr` | `values-fr/` |
| Spanish | Español | `es` | `values-es/` |
| Italian | Italiano | `it` | `values-it/` |
| Polish | Polski | `pl` | `values-pl/` |
| Ukrainian | Українська | `uk` | `values-uk/` |
| Portuguese | Português | `pt` | `values-pt/` |
| Brazilian Portuguese | Português (Brasil) | `pt-BR` | `values-pt-rBR/` |
| Arabic | العربية | `ar` | `values-ar/` |
| Japanese | 日本語 | `ja` | `values-ja/` |
| Korean | 한국어 | `ko` | `values-ko/` |
| Simplified Chinese | 简体中文 | `zh-Hans` | `values-b+zh+Hans/` |
| Traditional Chinese | 繁體中文 | `zh-Hant` | `values-b+zh+Hant/` |

Czech is `cs`, not `cz`; Japanese is `ja`, not `jp`. Do not use a country code as
a language code. Use a regional or script-specific locale only when the wording
needs it. Android's [resource qualifier reference](https://developer.android.com/guide/topics/resources/providing-resources#AlternativeResources)
explains legacy region qualifiers and `b+` qualifiers.

## Create or update the translation

Fork [the repository](https://github.com/yungsamd17/singlenote), create a focused
branch, then copy the English file. For example, to add German:

```bash
git switch -c feat/german-translation
mkdir -p app/src/main/res/values-de
cp app/src/main/res/values/strings.xml app/src/main/res/values-de/strings.xml
```

Translate the values, keeping the resource names unchanged. This is a short
example, not a complete replacement for the source file:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="settings_title">Einstellungen</string>
    <string name="settings_theme">Design</string>
    <string name="update_available">Neue Version %1$s verfügbar</string>
    <string name="about_version_line">v%1$s • %2$d</string>
</resources>
```

For an existing language, edit its file directly. Include all source keys, even
if some values intentionally remain unchanged. Review English additions when
updating an older translation. Android can fall back to English for missing
keys, but submitted translations should be complete.

## Preserve formatting and meaning

- Keep `name="..."` identifiers and existing preference keys unchanged.
- Keep numbered placeholders such as `%1$s` (text) and `%2$d` (integer), including
  their types. Their order in a sentence may change; their numbers must not.
- Preserve `\n` line breaks when they affect presentation. If plurals or string
  arrays are added, keep their structure and translate the appropriate values.
- Escape XML characters: `&amp;` for `&`, `&lt;` for `<`. Android string literals
  also require escaping straight apostrophes (`\'`) and quotes (`\"`) where used.
- Preserve `[label](url)` links and `**bold**` markers. Translate the label, not
  the URL. Preserve email addresses, issue references, product names and code.
- Keep language names in their native form: **English**, **Slovenčina**, **Deutsch**.
  The selector also has an English-name resource for its title; keep that English
  name consistent across translations.
- Keep legal and privacy claims equivalent to the source; flag uncertain wording
  in the PR for review. Do not introduce a different policy through translation.
- Prefer natural, concise wording. Check machine-assisted translations carefully;
  a literal translation that clips or changes an action's meaning needs revision.
- Do not commit `local.properties`, keystores, generated APKs, or build outputs.

## Register a new language

A new resource folder alone does not add an entry to Singlenote's language list.
For German, add name resources to the English file and every translated file,
including the new German file:

```xml
<string name="language_german_english">German</string>
<string name="language_german">Deutsch</string>
```

Add one entry to `languageEntries` in `SettingsScreen.kt`:

```kotlin
LanguageEntry("de", R.string.language_german_english, R.string.language_german),
```

Then add the locale to `app/src/main/res/xml/locales_config.xml`:

```xml
<locale android:name="de" />
```

Use the same BCP 47 tag in both places. `System` is already a special entry with
an empty code and follows the device language. Reuse `AppLanguage.setLanguage`;
do not add another locale persistence mechanism or change stored existing tags.
For regional/script tags, inspect the longer badge and both language names for
clipping. Mention any needed layout adjustment in your PR.

If you only translate XML, a maintainer can handle registration. State the tag,
English name, and native name in your PR so nothing is left ambiguous.

## Build and review

```bash
./gradlew -PpreviewBuild=true assembleDebug testDebugUnitTest lintDebug
```

See [building and previews](BUILDING.md) for SDK setup, installing the preview,
and troubleshooting. Review these cases on a device or emulator:

- Select the language in **Settings → Language** and reopen the app to check
  persistence. Select **System** again to verify the device-language fallback.
- Check Android 13+ per-app language settings when available, and Android 10–12
  when changing locale handling.
- Inspect the editor, archive, every settings page, selection dialogs, About,
  legal screens, widget configuration, notification and quick-settings tile.
- Check long labels, larger system text, light/dark themes, and a narrow screen.
  For right-to-left languages, check reading direction, icons, and alignment.
- Exercise strings with placeholders, errors, confirmation actions and links.
  Check that language changes preserve the themed transition and current note.

## Submit a pull request

Push the branch to your fork and [open a PR against Singlenote's main branch](https://github.com/yungsamd17/singlenote/compare/main...main?expand=1).
Choose **compare across forks** and select your fork and branch. Existing
submissions are listed under [Pull requests](https://github.com/yungsamd17/singlenote/pulls).

Include the language tag and both names, whether this is a new translation or an
update, the screens you checked, and any wording questions. Upload screenshots
of the translated interface directly to the PR rather than committing them.
If you cannot run the app, say so; do not mark device checks as passed.
See [CONTRIBUTING.md](../CONTRIBUTING.md) for review and commit conventions.
