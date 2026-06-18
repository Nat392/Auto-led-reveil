plugins {
    id("com.android.application") version "9.2.1" apply false
    // CodeQL ne supporte pas encore Kotlin >= 2.3.30 (KotlinVersionTooRecentError).
    // Garder cette version jusqu'a ce que CodeQL mette a jour son extracteur Java/Kotlin.
    kotlin("android") version "2.3.21" apply false
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
}
