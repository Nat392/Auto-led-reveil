import java.util.Properties
import org.gradle.api.tasks.testing.Test

plugins {
    id("com.android.application")
    kotlin("android")
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

fun String.escapeForBuildConfig(): String =
    replace("\\", "\\\\").replace("\"", "\\\"")

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

    packagingOptions {
        jniLibs {
            // Empêche le stripping de la librairie native utilisée par mockk
            pickFirsts += listOf("**/libmockkjvmtiagent.so")
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
            "\"${discordWebhookUrl.escapeForBuildConfig()}\""
        )
        buildConfigField(
            "String",
            "ZENGGE_BULB_MAC",
            "\"${zenggeBulbMac.escapeForBuildConfig()}\""
        )
        buildConfigField(
            "String",
            "ZENGGE_BULB_MAC_CHAMBRE",
            "\"${zenggeBulbMacChambre.escapeForBuildConfig()}\""
        )
        buildConfigField(
            "String",
            "ZENGGE_BULB_MAC_BUREAU",
            "\"${zenggeBulbMacBureau.escapeForBuildConfig()}\""
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
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
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("com.google.android.material:material:1.14.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("io.mockk:mockk:1.13.5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("org.json:json:20240303")

    androidTestImplementation("androidx.test:core:1.5.0")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.5.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("io.mockk:mockk-android:1.13.5")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
