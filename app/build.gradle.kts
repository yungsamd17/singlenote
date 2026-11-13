plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kover)
}

import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import kotlinx.kover.gradle.plugin.dsl.AggregationType
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

android {
    namespace = "com.yungsamd17.singlenote"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.yungsamd17.singlenote"
        if (providers.gradleProperty("previewBuild").orNull == "true") {
            applicationIdSuffix = ".preview"
            manifestPlaceholders["appLabel"] = "Singlenote Preview"
        } else {
            manifestPlaceholders["appLabel"] = "@string/app_name"
        }
        minSdk = 29
        targetSdk = 36
        versionCode = 23
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            // Dedicated upload key. CI provides it via the
            // RELEASE_KEYSTORE_* secrets (see build.yml); for a local signed
            // build, drop the keystore at the repo root as release.keystore
            // (gitignored, never commit it). No keystore means the release
            // build fails at signing time — by design, never silently
            // unsigned.
            val keystorePath = System.getenv("RELEASE_KEYSTORE_FILE")
            storeFile = if (keystorePath != null) file(keystorePath)
            else rootProject.file("release.keystore")
            storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "singlenote"
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            // Room and DataStore tests run on the JVM through Robolectric.
            isIncludeAndroidResources = true
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Room schema export location for @Database(exportSchema = true): the
// generated app/schemas/*.json is committed so migrations diff against it.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.glance.appwidget)
    // Glance only pulls WorkManager in at runtime, but the widget recovery
    // needs its API to inspect and clear the stuck "appWidget-<id>" work.
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Robolectric runs the real Room/SQLite stack on the JVM, so migrations
    // and DAO constraints are covered without a connected device.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
    // Real org.json for JVM unit tests: android.jar only ships stubs that
    // throw, so GithubReleases.parse needs this on the test classpath. The
    // test jar wins over android.jar because it comes first.
    testImplementation("org.json:json:20260814")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.ui.tooling)
}

// Coverage gate for the app's logic. The scope is the code a JVM test can
// reach: the data layer, the util helpers, the notification rules and the
// three ViewModels. Compose UI and Android components (activity, services,
// receivers, widget) need a device or a rendering tree, and Room's generated
// code is exercised through the DAO, so they stay outside the gate.
kover {
    reports {
        filters {
            includes {
                // Kover turns a package into the class pattern "<package>.*",
                // so the package names go in without a trailing wildcard.
                packages(
                    "com.yungsamd17.singlenote.data",
                    "com.yungsamd17.singlenote.util",
                    "com.yungsamd17.singlenote.notify",
                )
                classes(
                    "com.yungsamd17.singlenote.ui.NoteViewModel*",
                    "com.yungsamd17.singlenote.ui.ArchiveViewModel*",
                    "com.yungsamd17.singlenote.ui.SettingsViewModel*",
                )
            }
            excludes {
                classes(
                    // Room generates this from the DAO: it is exercised
                    // through the DAO, not line by line. The trailing "*"
                    // also catches the inner classes ("NoteDao_Impl$1").
                    "com.yungsamd17.singlenote.data.*_Impl*",
                    // Android components: they need a device, not a JVM test.
                    "com.yungsamd17.singlenote.notify.PinNotification*",
                    "com.yungsamd17.singlenote.notify.PinActionReceiver*",
                    "com.yungsamd17.singlenote.notify.PinBootReceiver*",
                )
                androidGeneratedClasses()
            }
        }
        variant("debug") {
            verify {
                rule {
                    bound {
                        minValue = 70
                        coverageUnits = CoverageUnit.LINE
                        aggregationForGroup = AggregationType.COVERED_PERCENTAGE
                    }
                }
            }
        }
    }
}

// Full failure details in CI logs so a red testDebugUnitTest names the
// asserting line (expected vs actual) instead of only the test method.
tasks.withType<Test> {
    testLogging {
        events("FAILED")
        exceptionFormat = TestExceptionFormat.FULL
        showCauses = true
        showStackTraces = true
    }
}
