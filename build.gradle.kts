plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

// AGP 9 embeds Kotlin (kotlin.android must NOT be applied) and forces the
// KGP/KSP pair below over its embedded default. Versions mirror
// libs.versions.toml (kotlin, ksp) — catalogs aren't visible here.
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
        classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.12")
    }
}
