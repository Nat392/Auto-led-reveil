plugins {
    // AGP 9+ integre Kotlin nativement ; le plugin org.jetbrains.kotlin.android ne doit
    // plus etre applique (cf. https://kotl.in/gradle/agp-built-in-kotlin).
    id("com.android.application") version "9.3.1" apply false
    // Version alignee sur le KGP 2.2.10 fourni par le support Kotlin integre d'AGP 9.
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
}
