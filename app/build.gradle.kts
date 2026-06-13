import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
    jacoco
    id("org.jlleitschuh.gradle.ktlint")
    id("io.gitlab.arturbosch.detekt")
}

jacoco {
    toolVersion = "0.8.12"
}

configure<KtlintExtension> {
    android.set(true)
    ignoreFailures.set(false)
    outputToConsole.set(true)
}

configure<DetektExtension> {
    toolVersion = "1.23.8"
    buildUponDefaultConfig = true
    allRules = false
    parallel = true
    ignoreFailures = false
    baseline = file("detekt-baseline.xml")
}

val localProperties =
    Properties().apply {
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use { load(it) }
        }
    }

fun String.escapeForBuildConfig(): String = replace("\\", "\\\\").replace("\"", "\\\"")

val discordWebhookUrl = localProperties.getProperty("DISCORD_WEBHOOK_URL", "").trim().trim('"')
val zenggeBulbMac = localProperties.getProperty("ZENGGE_BULB_MAC", "").trim().trim('"')
val zenggeBulbMacChambre = localProperties.getProperty("ZENGGE_BULB_MAC_CHAMBRE", "").trim().trim('"')
val zenggeBulbMacBureau = localProperties.getProperty("ZENGGE_BULB_MAC_BUREAU", "").trim().trim('"')

android {
    namespace = "com.example.alarmwatcher"
    compileSdk = 35

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/LICENSE-notice.md"
        }

        jniLibs {
            // Empêche le stripping de la librairie native utilisée par mockk
            pickFirsts += listOf("**/libmockkjvmtiagent.so")
            useLegacyPackaging = true
        }
    }

    defaultConfig {
        applicationId = "com.example.alarmwatcher"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField(
            "String",
            "DISCORD_WEBHOOK_URL",
            "\"${discordWebhookUrl.escapeForBuildConfig()}\"",
        )
        buildConfigField(
            "String",
            "ZENGGE_BULB_MAC",
            "\"${zenggeBulbMac.escapeForBuildConfig()}\"",
        )
        buildConfigField(
            "String",
            "ZENGGE_BULB_MAC_CHAMBRE",
            "\"${zenggeBulbMacChambre.escapeForBuildConfig()}\"",
        )
        buildConfigField(
            "String",
            "ZENGGE_BULB_MAC_BUREAU",
            "\"${zenggeBulbMacBureau.escapeForBuildConfig()}\"",
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            // signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("com.google.android.material:material:1.14.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("io.mockk:mockk:1.14.11")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("org.json:json:20260522")

    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.7.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("io.mockk:mockk-android:1.14.11")
    androidTestImplementation("androidx.work:work-testing:2.11.2")

    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.8")
}

tasks.named("check").configure {
    dependsOn("ktlintCheck", "detekt")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

val coverageExclusions =
    listOf(
        "**/R.class",
        "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "**/*\$Companion*.*",
        "**/*\$Lambda\$*.*",
        "**/*\$inlined\$*.*",
        "**/*_Factory*.*",
        "**/*_MembersInjector*.*",
        "**/*Hilt*.*",
        "**/*Dagger*.*",
        "**/di/**",
        "**/databinding/**",
    )

fun androidCoverageClassTree() =
    fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug").get().asFile) {
        exclude(coverageExclusions)
    } +
        fileTree(layout.buildDirectory.dir("intermediates/javac/debug/classes").get().asFile) {
            exclude(coverageExclusions)
        }

fun androidCoverageExecutionData() =
    fileTree(layout.buildDirectory.get().asFile) {
        include("jacoco/testDebugUnitTest.exec")
    }

tasks.register<JacocoReport>("jacocoDebugUnitTestReport") {
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/jacocoDebugUnitTestReport/jacocoDebugUnitTestReport.xml"))
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/jacocoDebugUnitTestReport/html"))
        csv.required.set(false)
    }

    sourceDirectories.setFrom(files("src/main/kotlin", "src/main/java"))
    classDirectories.setFrom(androidCoverageClassTree())
    executionData.setFrom(androidCoverageExecutionData())
}
